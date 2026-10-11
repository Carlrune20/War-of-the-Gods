package com.warofthegods.web;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.google.gson.JsonObject;
import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * The web map server: plain HTTP for the page, login and map tiles, and a WebSocket at {@link #SOCKET_PATH} that
 * streams live game state to browsers and carries their commands back. It runs on its own Netty threads; anything
 * touching the game goes through {@link #inbox} to the server thread.
 */
public final class WebServer {

    public static final String SOCKET_PATH = "/ws";
    private static final int MAX_REQUEST_BYTES = 64 * 1024;

    private static WebServer instance;

    private final WebSessions sessions = new WebSessions();
    private final ChannelGroup sockets = new DefaultChannelGroup("warofthegods-web", GlobalEventExecutor.INSTANCE);
    private final Queue<WebRequest> inbox = new ConcurrentLinkedQueue<>();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    private volatile MapTiles tiles;
    private volatile String gameMessage = WebJson.message("game")
        .toString();
    private volatile String stateMessage;

    /** The running server, or null when the web map is off or failed to start. */
    public static WebServer get() {
        return instance;
    }

    public static void start() {
        if (!Config.webEnabled || instance != null) {
            return;
        }
        WebServer server = new WebServer();
        try {
            server.bind(Config.webBindAddress, Config.webPort);
            instance = server;
            WarOfTheGods.LOG.info(
                "Web map listening on {}:{}, links point to {}",
                Config.webBindAddress,
                Config.webPort,
                publicUrl());
        } catch (Exception e) {
            server.close();
            WarOfTheGods.LOG.error(
                "Could not start the web map on {}:{}. Is the port in use? Change it under web in"
                    + " config/warofthegods.cfg",
                Config.webBindAddress,
                Config.webPort,
                e);
        }
    }

    public static void stop() {
        if (instance != null) {
            instance.close();
            instance = null;
            WarOfTheGods.LOG.info("Web map stopped");
        }
    }

    /** Base address for links, without a trailing slash. */
    public static String publicUrl() {
        String url = Config.webPublicUrl.trim();
        if (url.isEmpty()) {
            return "http://localhost:" + Config.webPort;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private void bind(String address, int port) throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("warofthegods-web-boss", true));
        workerGroup = new NioEventLoopGroup(2, new DefaultThreadFactory("warofthegods-web", true));
        ServerBootstrap bootstrap = new ServerBootstrap().group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {

                @Override
                protected void initChannel(SocketChannel channel) {
                    ChannelPipeline pipeline = channel.pipeline();
                    pipeline.addLast(new HttpServerCodec());
                    pipeline.addLast(new HttpObjectAggregator(MAX_REQUEST_BYTES));
                    pipeline.addLast(new HttpHandler(WebServer.this));
                    pipeline.addLast(new WebSocketServerProtocolHandler(SOCKET_PATH));
                    pipeline.addLast(new SocketHandler(WebServer.this));
                }
            });
        serverChannel = bootstrap.bind(new InetSocketAddress(address, port))
            .sync()
            .channel();
    }

    private void close() {
        sockets.close();
        if (serverChannel != null) {
            serverChannel.close()
                .awaitUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    public WebSessions getSessions() {
        return sessions;
    }

    /** Commands browsers sent, for the server thread to run. */
    public Queue<WebRequest> getInbox() {
        return inbox;
    }

    public MapTiles getTiles() {
        return tiles;
    }

    /** Switches to the map of a new game and tells every browser. */
    public void setGame(MapTiles tiles, JsonObject gameMessage) {
        this.tiles = tiles;
        this.gameMessage = gameMessage.toString();
        this.stateMessage = null;
        broadcast(this.gameMessage);
    }

    public void setState(JsonObject stateMessage) {
        this.stateMessage = stateMessage.toString();
        broadcast(this.stateMessage);
    }

    public void tilesChanged(Map<String, Integer> versions) {
        if (!versions.isEmpty()) {
            broadcast(WebJson.tilesMessage(versions));
        }
    }

    public void broadcast(String message) {
        if (!sockets.isEmpty()) {
            sockets.write(new TextWebSocketFrame(message));
            sockets.flush();
        }
    }

    /** Sends a browser that just connected everything it needs to draw the map, then keeps it updated. */
    void welcome(Channel channel) {
        sockets.add(channel);
        channel.write(new TextWebSocketFrame(gameMessage));
        MapTiles current = tiles;
        if (current != null) {
            channel.write(new TextWebSocketFrame(WebJson.tilesMessage(current.allVersions())));
        }
        String state = stateMessage;
        if (state != null) {
            channel.write(new TextWebSocketFrame(state));
        }
        channel.flush();
    }

    public static void send(Channel channel, JsonObject message) {
        channel.writeAndFlush(new TextWebSocketFrame(message.toString()));
    }
}
