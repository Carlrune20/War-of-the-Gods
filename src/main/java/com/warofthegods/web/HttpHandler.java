package com.warofthegods.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.warofthegods.WarOfTheGods;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;

/**
 * Answers plain HTTP requests: the web page itself, logging in with a link token, and map tiles (the blocks of the map,
 * see {@link MapTiles}). A WebSocket upgrade request from a logged-in browser is passed on to the WebSocket handlers
 * with its viewer attached.
 */
final class HttpHandler extends ChannelInboundHandlerAdapter {

    static final AttributeKey<Viewer> VIEWER = new AttributeKey<>("warofthegods.viewer");

    private static final String COOKIE = "wotg_session";
    private static final String STATIC_ROOT = "/assets/warofthegods/web/";
    private static final Map<String, String> STATIC_FILES = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> STATIC_CACHE = new ConcurrentHashMap<>();

    static {
        STATIC_FILES.put("/", "index.html");
        STATIC_FILES.put("/index.html", "index.html");
        STATIC_FILES.put("/app.js", "app.js");
        STATIC_FILES.put("/blocks.js", "blocks.js");
        STATIC_FILES.put("/render.js", "render.js");
        STATIC_FILES.put("/style.css", "style.css");
        // The same icons the RTS view draws.
        STATIC_FILES.put("/icons.png", "/assets/warofthegods/textures/gui/icons.png");
    }

    private final WebServer server;

    HttpHandler(WebServer server) {
        this.server = server;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof FullHttpRequest)) {
            ctx.fireChannelRead(msg);
            return;
        }
        FullHttpRequest request = (FullHttpRequest) msg;
        boolean passedOn = false;
        try {
            passedOn = handle(ctx, request);
        } catch (Exception e) {
            WarOfTheGods.LOG.warn("Web map request {} failed", request.getUri(), e);
            send(ctx, request, text(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Internal error"));
        } finally {
            if (!passedOn) {
                request.release();
            }
        }
    }

    /** Returns true if the request was handed on down the pipeline, which then owns it. */
    private boolean handle(ChannelHandlerContext ctx, FullHttpRequest request) throws IOException {
        if (!request.getDecoderResult()
            .isSuccess()) {
            send(ctx, request, text(HttpResponseStatus.BAD_REQUEST, "Bad request"));
            return false;
        }
        QueryStringDecoder query = new QueryStringDecoder(request.getUri());
        String path = query.path();
        HttpMethod method = request.getMethod();

        if (path.equals(WebServer.SOCKET_PATH)) {
            Viewer viewer = viewer(request);
            if (viewer == null || !sameOrigin(request)) {
                send(ctx, request, text(HttpResponseStatus.FORBIDDEN, "Not logged in"));
                return false;
            }
            ctx.channel()
                .attr(VIEWER)
                .set(viewer);
            ctx.fireChannelRead(request);
            return true;
        }

        if (method.equals(HttpMethod.GET) && STATIC_FILES.containsKey(path)) {
            String file = STATIC_FILES.get(path);
            FullHttpResponse response = bytes(HttpResponseStatus.OK, staticFile(file), contentType(file));
            response.headers()
                .set(HttpHeaders.Names.CACHE_CONTROL, "no-cache");
            send(ctx, request, response);
        } else if (path.equals("/api/login") && method.equals(HttpMethod.POST)) {
            login(ctx, request);
        } else if (path.equals("/api/logout") && method.equals(HttpMethod.POST)) {
            if (sameOrigin(request)) {
                server.getSessions()
                    .logout(sessionId(request));
            }
            FullHttpResponse response = json(HttpResponseStatus.OK, new JsonObject());
            response.headers()
                .add(HttpHeaders.Names.SET_COOKIE, COOKIE + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
            send(ctx, request, response);
        } else if (path.equals("/api/me") && method.equals(HttpMethod.GET)) {
            Viewer viewer = viewer(request);
            if (viewer == null) {
                send(ctx, request, text(HttpResponseStatus.UNAUTHORIZED, "Not logged in"));
            } else {
                send(ctx, request, json(HttpResponseStatus.OK, viewerJson(viewer)));
            }
        } else if (path.equals("/api/tile") && method.equals(HttpMethod.GET)) {
            tile(ctx, request, query);
        } else {
            send(ctx, request, text(HttpResponseStatus.NOT_FOUND, "Not found"));
        }
        return false;
    }

    private void login(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!sameOrigin(request)) {
            send(ctx, request, text(HttpResponseStatus.FORBIDDEN, "Wrong origin"));
            return;
        }
        String token = null;
        try {
            JsonElement body = new JsonParser().parse(
                request.content()
                    .toString(CharsetUtil.UTF_8));
            if (body.isJsonObject() && body.getAsJsonObject()
                .has("token")) {
                token = body.getAsJsonObject()
                    .get("token")
                    .getAsString();
            }
        } catch (RuntimeException e) {
            // Treated like a missing token below.
        }
        String session = server.getSessions()
            .login(token);
        if (session == null) {
            send(
                ctx,
                request,
                text(
                    HttpResponseStatus.UNAUTHORIZED,
                    "This link has expired or was already used. Run /rts web again."));
            return;
        }
        FullHttpResponse response = json(
            HttpResponseStatus.OK,
            viewerJson(
                server.getSessions()
                    .get(session)));
        response.headers()
            .add(
                HttpHeaders.Names.SET_COOKIE,
                COOKIE + "="
                    + session
                    + "; Path=/; HttpOnly; SameSite=Strict; Max-Age="
                    + WebSessions.sessionMillis() / 1000);
        send(ctx, request, response);
    }

    private void tile(ChannelHandlerContext ctx, FullHttpRequest request, QueryStringDecoder query) throws IOException {
        if (viewer(request) == null) {
            send(ctx, request, text(HttpResponseStatus.UNAUTHORIZED, "Not logged in"));
            return;
        }
        MapTiles tiles = server.getTiles();
        Integer x = intParam(query, "x");
        Integer z = intParam(query, "z");
        List<String> map = query.parameters()
            .get("m");
        // A tile of a map that is no longer shown, asked for by a page that has not caught up yet.
        boolean stale = tiles != null && map != null
            && !map.isEmpty()
            && !tiles.getId()
                .equals(map.get(0));
        byte[] blob = tiles == null || stale || x == null || z == null ? null : tiles.blob(x, z);
        if (blob == null) {
            send(ctx, request, text(HttpResponseStatus.NOT_FOUND, "Not read yet"));
            return;
        }
        FullHttpResponse response = bytes(HttpResponseStatus.OK, blob, "application/octet-stream");
        // Browsers ask with the map id and the tile's version in the address, so a given address never changes.
        response.headers()
            .set(HttpHeaders.Names.CACHE_CONTROL, "private, max-age=86400");
        send(ctx, request, response);
    }

    private static Integer intParam(QueryStringDecoder query, String name) {
        List<String> values = query.parameters()
            .get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(values.get(0));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static JsonObject viewerJson(Viewer viewer) {
        JsonObject json = new JsonObject();
        json.addProperty("name", viewer.getName());
        json.addProperty("observer", viewer.isObserver());
        if (!viewer.isObserver()) {
            json.addProperty(
                "uuid",
                viewer.getPlayer()
                    .toString());
        }
        return json;
    }

    private static String sessionId(FullHttpRequest request) {
        String header = request.headers()
            .get(HttpHeaders.Names.COOKIE);
        if (header == null) {
            return null;
        }
        try {
            for (Cookie cookie : CookieDecoder.decode(header)) {
                if (cookie.getName()
                    .equals(COOKIE)) {
                    return cookie.getValue();
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    private Viewer viewer(FullHttpRequest request) {
        return server.getSessions()
            .get(sessionId(request));
    }

    /**
     * Requests that change state or open a socket must come from the map page itself: if the browser says where the
     * request comes from, that has to be this server.
     */
    private static boolean sameOrigin(FullHttpRequest request) {
        String origin = request.headers()
            .get(HttpHeaders.Names.ORIGIN);
        if (origin == null) {
            return true;
        }
        String host = request.headers()
            .get(HttpHeaders.Names.HOST);
        try {
            URI uri = new URI(origin);
            String originHost = uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
            return host != null && originHost != null && host.equalsIgnoreCase(originHost);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] staticFile(String name) throws IOException {
        byte[] cached = STATIC_CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        try (InputStream in = HttpHandler.class.getResourceAsStream(name.startsWith("/") ? name : STATIC_ROOT + name)) {
            if (in == null) {
                throw new IOException("Missing web file " + name);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int read; (read = in.read(buffer)) != -1;) {
                out.write(buffer, 0, read);
            }
            byte[] data = out.toByteArray();
            STATIC_CACHE.put(name, data);
            return data;
        }
    }

    private static String contentType(String file) {
        if (file.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (file.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (file.endsWith(".png")) {
            return "image/png";
        }
        return "text/html; charset=utf-8";
    }

    private static FullHttpResponse bytes(HttpResponseStatus status, byte[] data, String contentType) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            status,
            Unpooled.wrappedBuffer(data));
        response.headers()
            .set(HttpHeaders.Names.CONTENT_TYPE, contentType);
        return response;
    }

    private static FullHttpResponse text(HttpResponseStatus status, String text) {
        return bytes(status, text.getBytes(CharsetUtil.UTF_8), "text/plain; charset=utf-8");
    }

    private static FullHttpResponse json(HttpResponseStatus status, JsonObject json) {
        FullHttpResponse response = bytes(
            status,
            json.toString()
                .getBytes(CharsetUtil.UTF_8),
            "application/json");
        response.headers()
            .set(HttpHeaders.Names.CACHE_CONTROL, "no-store");
        return response;
    }

    private static void send(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        HttpHeaders headers = response.headers();
        headers.set(
            HttpHeaders.Names.CONTENT_LENGTH,
            response.content()
                .readableBytes());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set(
            "Content-Security-Policy",
            "default-src 'self'; img-src 'self' blob:; connect-src 'self' ws: wss:; frame-ancestors 'none'");
        boolean keepAlive = HttpHeaders.isKeepAlive(request);
        if (keepAlive) {
            headers.set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }
        ChannelFuture future = ctx.writeAndFlush(response);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
