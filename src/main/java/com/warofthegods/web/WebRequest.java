package com.warofthegods.web;

import java.util.function.Consumer;

import com.google.gson.JsonObject;

/**
 * A command waiting for the server thread: from a browser over the web map's WebSocket, or from a player's client in
 * the in-game RTS view. Both send the same commands and get the same replies.
 */
public final class WebRequest {

    public final Viewer viewer;
    public final String type;
    public final JsonObject body;
    private final Consumer<JsonObject> replies;

    public WebRequest(Viewer viewer, Consumer<JsonObject> replies, String type, JsonObject body) {
        this.viewer = viewer;
        this.replies = replies;
        this.type = type;
        this.body = body;
    }

    /** Answers this request; the reply carries the request's id, if it had one, so the browser can match it up. */
    public void reply(JsonObject message) {
        if (body.has("id")) {
            message.add("replyTo", body.get("id"));
        }
        replies.accept(message);
    }

    public void replyError(String error) {
        JsonObject message = WebJson.message("error");
        message.addProperty("error", error);
        reply(message);
    }
}
