package com.warofthegods.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class WebJsonTest {

    @Test
    void messageCarriesItsType() {
        JsonObject message = WebJson.message("state");

        assertEquals(
            "state",
            message.get("type")
                .getAsString());
        assertEquals(
            1,
            message.entrySet()
                .size());
    }

    @Test
    void tilesMessageListsEachTileWithItsVersion() {
        Map<String, Integer> versions = new LinkedHashMap<>();
        versions.put("0,0", 3);
        versions.put("-1,2", 7);

        JsonObject parsed = new JsonParser().parse(WebJson.tilesMessage(versions))
            .getAsJsonObject();

        assertEquals(
            "tiles",
            parsed.get("type")
                .getAsString());
        JsonObject tiles = parsed.getAsJsonObject("tiles");
        assertEquals(
            3,
            tiles.get("0,0")
                .getAsInt());
        assertEquals(
            7,
            tiles.get("-1,2")
                .getAsInt());
    }

    @Test
    void tilesMessageWithNoTilesHasAnEmptyList() {
        JsonObject parsed = new JsonParser().parse(WebJson.tilesMessage(new LinkedHashMap<>()))
            .getAsJsonObject();

        assertEquals(
            0,
            parsed.getAsJsonObject("tiles")
                .entrySet()
                .size());
    }
}
