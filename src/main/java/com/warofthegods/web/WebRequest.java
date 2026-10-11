package com.warofthegods.web;

import com.google.gson.JsonObject;

import io.netty.channel.Channel;

/** A message a browser sent over its WebSocket, waiting for the server thread. */
public final class WebRequest {

    public final Viewer viewer;
    public final Channel channel;
    public final String type;
    public final JsonObject body;

    WebRequest(Viewer viewer, Channel channel, String type, JsonObject body) {
        this.viewer = viewer;
        this.channel = channel;
        this.type = type;
        this.body = body;
    }

    /** Answers this request; the reply carries the request's id, if it had one, so the browser can match it up. */
    public void reply(JsonObject message) {
        if (body.has("id")) {
            message.add("replyTo", body.get("id"));
        }
        WebServer.send(channel, message);
    }

    public void replyError(String error) {
        JsonObject message = WebJson.message("error");
        message.addProperty("error", error);
        reply(message);
    }
}
