package com.warofthegods.client.model;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.client.Glow;

/**
 * A box model made by tools/models/generate.py: a tree of named parts, each turning about its pivot, holding boxes laid
 * out in the texture the way Minecraft's own models lay them out. Each part is a pair of renderers: one that only
 * places and turns, with the next parts under it, and one under that holding the part's boxes, so a pass can draw
 * some parts' boxes and not others while every part still moves with the parts it hangs from.
 * <p>
 * Models are read through the game's resources, so a resource pack can replace them, and every model is read again
 * when the resources reload (F3+T): edit a model in Blockbench, turn it back into JSON with tools/models/blockbench.py
 * and see it in the game straight away. A file that will not read leaves the model as it was.
 */
public final class JsonModel {

    private static final Logger LOG = LogManager.getLogger("WarOfTheGods");
    private static final List<JsonModel> ALL = new ArrayList<>();
    private static boolean listening;

    private final ModelBase model;
    private final String name;
    private final List<ModelRenderer> roots = new ArrayList<>();
    private final Map<String, ModelRenderer> parts = new HashMap<>();
    private final Map<String, float[]> rest = new HashMap<>();
    private final Map<String, float[]> restPivots = new HashMap<>();
    private final List<ModelRenderer> geometry = new ArrayList<>();
    private final List<ModelRenderer> glowing = new ArrayList<>();
    private int textureWidth;
    private int textureHeight;

    /**
     * Loads assets/warofthegods/models/name.json for the model, sizing its texture as the file says. Its renderers
     * are left out of the model's list of boxes, which only arrows stuck in an entity use.
     */
    public JsonModel(ModelBase model, String name) {
        this.model = model;
        this.name = name;
        build(read(name, false));
        ALL.add(this);
        listen();
    }

    /** Reads every model again when the game's resources reload. */
    private static void listen() {
        if (listening) {
            return;
        }
        IResourceManager manager = Minecraft.getMinecraft()
            .getResourceManager();
        if (manager instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) manager).registerReloadListener(new IResourceManagerReloadListener() {

                @Override
                public void onResourceManagerReload(IResourceManager resources) {
                    for (JsonModel each : ALL) {
                        each.reload();
                    }
                }
            });
            listening = true;
        }
    }

    private void reload() {
        JsonObject json;
        try {
            json = read(name, true);
        } catch (RuntimeException e) {
            LOG.warn("Keeping the model {} as it was: {}", name, e.toString());
            return;
        }
        List<ModelRenderer> oldRoots = new ArrayList<>(roots);
        Map<String, ModelRenderer> oldParts = new HashMap<>(parts);
        Map<String, float[]> oldRest = new HashMap<>(rest);
        Map<String, float[]> oldPivots = new HashMap<>(restPivots);
        List<ModelRenderer> oldGeometry = new ArrayList<>(geometry);
        List<ModelRenderer> oldGlowing = new ArrayList<>(glowing);
        int width = textureWidth;
        int height = textureHeight;
        try {
            build(json);
        } catch (RuntimeException e) {
            LOG.warn("Keeping the model {} as it was: {}", name, e.toString());
            restore(roots, oldRoots);
            restore(geometry, oldGeometry);
            restore(glowing, oldGlowing);
            parts.clear();
            parts.putAll(oldParts);
            rest.clear();
            rest.putAll(oldRest);
            restPivots.clear();
            restPivots.putAll(oldPivots);
            model.textureWidth = textureWidth = width;
            model.textureHeight = textureHeight = height;
        }
    }

    private static <T> void restore(List<T> list, List<T> old) {
        list.clear();
        list.addAll(old);
    }

    private void build(JsonObject json) {
        roots.clear();
        parts.clear();
        rest.clear();
        restPivots.clear();
        geometry.clear();
        glowing.clear();
        JsonArray texture = json.getAsJsonArray("texture");
        textureWidth = texture.get(0)
            .getAsInt();
        textureHeight = texture.get(1)
            .getAsInt();
        model.textureWidth = textureWidth;
        model.textureHeight = textureHeight;
        int before = model.boxList.size();
        try {
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
                restPivots.put(partName, pivot);
            }
        } finally {
            while (model.boxList.size() > before) {
                model.boxList.remove(model.boxList.size() - 1);
            }
        }
    }

    /**
     * The model's file, from the game's resources (so a resource pack can replace it) or, before the mod's own
     * resources are loaded and unless only the resources will do, from the mod's jar.
     */
    private static JsonObject read(String name, boolean resourcesOnly) {
        try (InputStream in = Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(new ResourceLocation(WarOfTheGods.MODID, "models/" + name + ".json"))
            .getInputStream()) {
            return parse(in);
        } catch (java.io.IOException | RuntimeException e) {
            if (resourcesOnly) {
                throw new IllegalStateException("Could not read model " + name, e);
            }
        }
        String path = "/assets/warofthegods/models/" + name + ".json";
        try (InputStream in = JsonModel.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing model " + path);
            }
            return parse(in);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read model " + path, e);
        }
    }

    private static JsonObject parse(InputStream in) {
        return new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8))
            .getAsJsonObject();
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

    /** Puts every part back as the model was made, before an animation turns and moves them anew. */
    public void resetPose() {
        for (Map.Entry<String, float[]> entry : rest.entrySet()) {
            ModelRenderer part = parts.get(entry.getKey());
            part.rotateAngleX = entry.getValue()[0];
            part.rotateAngleY = entry.getValue()[1];
            part.rotateAngleZ = entry.getValue()[2];
            float[] pivot = restPivots.get(entry.getKey());
            part.setRotationPoint(pivot[0], pivot[1], pivot[2]);
        }
    }

    /** Moves a part's pivot from where it rests by x, y and z. */
    public void move(String name, float x, float y, float z) {
        ModelRenderer part = parts.get(name);
        if (part != null) {
            float[] at = restPivots.get(name);
            part.setRotationPoint(at[0] + x, at[1] + y, at[2] + z);
        }
    }

    /** Copies a part's turn and move away from its rest from a part of another model, from how that one rests. */
    public void follow(String name, ModelRenderer other, float restX, float restY, float restZ) {
        turn(name, other.rotateAngleX, other.rotateAngleY, other.rotateAngleZ);
        move(name, other.rotationPointX - restX, other.rotationPointY - restY, other.rotationPointZ - restZ);
    }

    /** Whether it has the named part. */
    public boolean has(String name) {
        return parts.containsKey(name);
    }

    /** Shows or hides a part, and the parts hanging from it with it. */
    public void show(String name, boolean shown) {
        ModelRenderer part = parts.get(name);
        if (part != null) {
            part.showModel = shown;
        }
    }

    /**
     * Draws only the named part, even when it is hidden, and the parts hanging from it, where they are on the model;
     * nothing when there is no such part.
     */
    public void renderOnly(String name, float scale) {
        ModelRenderer part = parts.get(name);
        if (part == null) {
            return;
        }
        boolean shown = part.showModel;
        part.showModel = true;
        for (ModelRenderer boxes : geometry) {
            boxes.showModel = false;
        }
        showBoxes(part);
        render(scale);
        for (ModelRenderer boxes : geometry) {
            boxes.showModel = true;
        }
        part.showModel = shown;
    }

    private void showBoxes(ModelRenderer node) {
        if (node.childModels == null) {
            return;
        }
        for (Object child : node.childModels) {
            ModelRenderer renderer = (ModelRenderer) child;
            if (geometry.contains(renderer)) {
                renderer.showModel = true;
            } else {
                showBoxes(renderer);
            }
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
     * Draws the parts that glow over again on top of themselves as just drawn, as {@link Glow} draws them, an entity's
     * through the shaders' program; nothing when no part glows.
     */
    public void renderGlow(float scale, boolean entity) {
        if (glowing.isEmpty()) {
            return;
        }
        for (ModelRenderer boxes : geometry) {
            boxes.showModel = glowing.contains(boxes);
        }
        Glow.begin(entity);
        render(scale);
        Glow.end();
        for (ModelRenderer boxes : geometry) {
            boxes.showModel = true;
        }
    }
}
