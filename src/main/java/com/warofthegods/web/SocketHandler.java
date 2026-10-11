package com.warofthegods.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * Reads messages from a browser's WebSocket. Each is a JSON object with a "type". The browser opens with "hello"
 * and is then sent the game, the map tiles and the live state; everything else is queued for the server thread.
 */
final class SocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    /** Messages a browser may have waiting on the server thread before more are refused. */
    private static final int MAX_QUEUED = 256;

    private final WebServer server;

    SocketHandler(WebServer server) {
        this.server = server;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (!(frame instanceof TextWebSocketFrame)) {
            return;
        }
        Viewer viewer = ctx.channel()
            .attr(HttpHandler.VIEWER)
            .get();
        if (viewer == null) {
            ctx.close();
            return;
        }
        JsonObject body;
        String type;
        try {
            JsonElement parsed = new JsonParser().parse(((TextWebSocketFrame) frame).text());
            body = parsed.getAsJsonObject();
            type = body.get("type")
                .getAsString();
        } catch (RuntimeException e) {
            return;
        }
        if (type.equals("hello")) {
            server.welcome(ctx.channel());
        } else if (server.getInbox()
            .size() < MAX_QUEUED) {
                server.getInbox()
                    .add(new WebRequest(viewer, ctx.channel(), type, body));
            }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
