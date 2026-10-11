package com.warofthegods.web;

import java.util.HashMap;
import java.util.Map;

/**
 * Something a browser can ask the game to do, run on the server thread. Later phases register unit and building
 * orders here; each handler checks for itself that the viewer is allowed to do what it asks.
 */
public interface WebCommand {

    void run(WebRequest request);

    Map<String, WebCommand> REGISTRY = new HashMap<>();

    static void register(String type, WebCommand command) {
        REGISTRY.put(type, command);
    }
}
