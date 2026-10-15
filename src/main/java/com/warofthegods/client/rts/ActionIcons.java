package com.warofthegods.client.rts;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.warofthegods.WarOfTheGods;

/**
 * The square icons on the build, train and upgrade buttons: buildings drawn from their structure, units' faces and
 * upgrades' items, all in actions.png, named in actions.json. tools/textures/actions.py draws both.
 */
final class ActionIcons {

    private static final ResourceLocation SHEET = new ResourceLocation(
        WarOfTheGods.MODID,
        "textures/gui/actions.png");
    private static final ResourceLocation NAMES = new ResourceLocation(
        WarOfTheGods.MODID,
        "textures/gui/actions.json");

    private static Map<String, Integer> index;
    private static int size = 64;
    private static int columns = 16;
    private static int rows = 1;

    private ActionIcons() {}

    private static Map<String, Integer> index() {
        if (index != null) {
            return index;
        }
        index = new HashMap<>();
        try (InputStream in = Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(NAMES)
            .getInputStream()) {
            JsonObject json = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
            size = json.get("size")
                .getAsInt();
            columns = json.get("columns")
                .getAsInt();
            int i = 0;
            for (JsonElement name : json.getAsJsonArray("names")) {
                index.put(name.getAsString(), i++);
            }
            int needed = (i + columns - 1) / columns;
            rows = 1;
            while (rows < needed) {
                rows *= 2;
            }
        } catch (Exception e) {
            WarOfTheGods.LOG.warn("Could not read the action icons", e);
        }
        return index;
    }

    static String building(String faction, String type) {
        return "building/" + faction + "/" + type;
    }

    static String face(String faction, String kind) {
        return "face/" + faction + "/" + kind;
    }

    static String upgrade(String id) {
        return "upgrade/" + id;
    }

    static boolean has(String name) {
        return name != null && index().containsKey(name);
    }

    /** Draws the named icon as a square of the given size; returns false when there is no such icon. */
    static boolean draw(String name, int x, int y, int drawn) {
        Integer i = name == null ? null : index().get(name);
        if (i == null) {
            return false;
        }
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(SHEET);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_BLEND);
        Gui.func_152125_a(
            x,
            y,
            (i % columns) * size,
            (i / columns) * size,
            size,
            size,
            drawn,
            drawn,
            columns * size,
            rows * size);
        return true;
    }
}
