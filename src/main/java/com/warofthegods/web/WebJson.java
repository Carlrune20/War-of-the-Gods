package com.warofthegods.web;

import java.util.Map;

import com.google.gson.JsonObject;

/** Building the JSON messages sent to browsers. */
public final class WebJson {

    private WebJson() {}

    /** An empty message of the given type: {"type": type}. */
    public static JsonObject message(String type) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type);
        return message;
    }

    /** {"type": "tiles", "tiles": {"x,z": version, ...}}: map tiles that browsers should (re)load. */
    static String tilesMessage(Map<String, Integer> versions) {
        JsonObject tiles = new JsonObject();
        for (Map.Entry<String, Integer> entry : versions.entrySet()) {
            tiles.addProperty(entry.getKey(), entry.getValue());
        }
        JsonObject message = message("tiles");
        message.add("tiles", tiles);
        return message.toString();
    }
}
