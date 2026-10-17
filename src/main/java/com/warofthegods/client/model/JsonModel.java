package com.warofthegods.client.model;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;

import org.lwjgl.opengl.GL11;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A box model made by tools/models/generate.py: a tree of named parts, each turning about its pivot, holding boxes laid
 * out in the texture the way Minecraft's own models lay them out. Each part is a pair of renderers: one that only
 * places and turns, with the next parts under it, and one under that holding the part's boxes, so a pass can draw
 * some parts' boxes and not others while every part still moves with the parts it hangs from.
 */
public final class JsonModel {

    private final List<ModelRenderer> roots = new ArrayList<>();
    private final Map<String, ModelRenderer> parts = new HashMap<>();
    private final Map<String, float[]> rest = new HashMap<>();
    private final List<ModelRenderer> geometry = new ArrayList<>();
    private final List<ModelRenderer> glowing = new ArrayList<>();
    public final int textureWidth;
    public final int textureHeight;

    /**
     * Loads assets/warofthegods/models/name.json for the model, sizing its texture as the file says. Its renderers
     * are left out of the model's list of boxes, which only arrows stuck in an entity use.
     */
    public JsonModel(ModelBase model, String name) {
        JsonObject json = read(name);
        JsonArray texture = json.getAsJsonArray("texture");
        textureWidth = texture.get(0)
            .getAsInt();
        textureHeight = texture.get(1)
            .getAsInt();
        model.textureWidth = textureWidth;
        model.textureHeight = textureHeight;
        int before = model.boxList.size();
        for (JsonElement element : json.getAsJsonArray("parts")) {
            JsonObject part = element.getAsJsonObject();
            String partName = part.get("name")
                .getAsString();
            ModelRenderer node = new ModelRenderer(model, partName);
            float[] pivot = floats(part.getAsJsonArray("pivot"));
            float[] rotation = floats(part.getAsJsonArray("rotation"));
            node.setRotationPoint(pivot[0], pivot[1], pivot[2]);
            node.rotateAngleX = rotation[0];
            node.rotateAngleY = rotation[1];
            node.rotateAngleZ = rotation[2];
            ModelRenderer boxes = new ModelRenderer(model, partName + "_boxes");
            boxes.setTextureSize(textureWidth, textureHeight);
            for (JsonElement boxElement : part.getAsJsonArray("boxes")) {
                JsonObject box = boxElement.getAsJsonObject();
                float[] uv = floats(box.getAsJsonArray("uv"));
                float[] from = floats(box.getAsJsonArray("from"));
                float[] size = floats(box.getAsJsonArray("size"));
                boxes.setTextureOffset((int) uv[0], (int) uv[1]);
                boxes.mirror = box.get("mirror")
                    .getAsBoolean();
                boxes.addBox(
                    from[0],
                    from[1],
                    from[2],
                    Math.round(size[0]),
                    Math.round(size[1]),
                    Math.round(size[2]),
                    box.get("inflate")
                        .getAsFloat());
            }
            node.addChild(boxes);
            geometry.add(boxes);
            if (part.has("glow") && part.get("glow")
                .getAsBoolean()) {
                glowing.add(boxes);
            }
            JsonElement parent = part.get("parent");
            if (parent == null || parent.isJsonNull()) {
                roots.add(node);
            } else {
                parts.get(parent.getAsString())
                    .addChild(node);
            }
            parts.put(partName, node);
            rest.put(partName, rotation);
        }
        while (model.boxList.size() > before) {
            model.boxList.remove(model.boxList.size() - 1);
        }
    }

    private static JsonObject read(String name) {
        String path = "/assets/warofthegods/models/" + name + ".json";
        try (InputStream in = JsonModel.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing model " + path);
            }
            return new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read model " + path, e);
        }
    }

    private static float[] floats(JsonArray array) {
        float[] values = new float[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.get(i)
                .getAsFloat();
        }
        return values;
    }

    /** The named part, to turn; null if there is none. */
    public ModelRenderer part(String name) {
        return parts.get(name);
    }

    /** Puts every part back as the model was made, before an animation turns them anew. */
    public void resetPose() {
        for (Map.Entry<String, float[]> entry : rest.entrySet()) {
            ModelRenderer part = parts.get(entry.getKey());
            part.rotateAngleX = entry.getValue()[0];
            part.rotateAngleY = entry.getValue()[1];
            part.rotateAngleZ = entry.getValue()[2];
        }
    }

    /** Turns a part from where it rests by x, y and z radians. */
    public void turn(String name, float x, float y, float z) {
        ModelRenderer part = parts.get(name);
        float[] at = rest.get(name);
        if (part != null) {
            part.rotateAngleX = at[0] + x;
            part.rotateAngleY = at[1] + y;
            part.rotateAngleZ = at[2] + z;
        }
    }

    public void render(float scale) {
        for (ModelRenderer root : roots) {
            root.render(scale);
        }
    }

    /**
     * Draws the parts that glow over again at full brightness, unlit, on top of themselves as just drawn; nothing
     * when no part glows.
     */
    public void renderGlow(float scale) {
        if (glowing.isEmpty()) {
            return;
        }
        for (ModelRenderer boxes : geometry) {
            boxes.showModel = glowing.contains(boxes);
        }
        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        render(scale);
        GL11.glEnable(GL11.GL_LIGHTING);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        for (ModelRenderer boxes : geometry) {
            boxes.showModel = true;
        }
    }
}
