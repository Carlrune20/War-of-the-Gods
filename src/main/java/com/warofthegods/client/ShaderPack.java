package com.warofthegods.client;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * The mod's OptiFine shader pack (shaderpack/ in the source), which gives each map a look of its own: installing it
 * where OptiFine finds it, and drawing past it what the mod draws with plain OpenGL.
 */
public final class ShaderPack {

    /** The pack in the mod's jar, its folder in the shaderpacks folder, and the file there saying which it is. */
    private static final String RESOURCE = "/assets/warofthegods/shaderpack/WarOfTheGods.zip";
    private static final String FOLDER_NAME = "War of the Gods";
    private static final String VERSION_FILE = "installed-by-war-of-the-gods.txt";

    /** The far terrain's program and where it is in the jar, and the pack's buffer for what glows. */
    private static final String FAR_TERRAIN = "/assets/warofthegods/shaders/far_terrain/far_terrain";
    private static final int GLOW_BUFFER = 4;

    private static IntBuffer drawBuffers;
    private static IntBuffer farTerrainBuffers;
    private static int farTerrainProgram;
    private static boolean farTerrainProgramTried;
    private static Field currentPackName;
    private static int program;
    private static boolean bypassing;

    private ShaderPack() {}

    /**
     * Puts the pack in the shaderpacks folder when OptiFine is installed and the config allows, replacing an older
     * copy, so players only have to pick it in Video Settings > Shaders. It goes in as a folder rather than a zip:
     * OptiFine keeps the zip of the pack in use open, and Windows won't replace an open file.
     */
    public static void install(File gameDir) {
        if (!Config.installShaderPack || !hasOptiFineShaders()) {
            return;
        }
        try (InputStream in = ShaderPack.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return;
            }
            byte[] pack = readAll(in);
            String version = Integer.toHexString(Arrays.hashCode(pack)) + " " + pack.length;
            File dir = new File(new File(gameDir, "shaderpacks"), FOLDER_NAME);
            File versionFile = new File(dir, VERSION_FILE);
            if (versionFile.isFile() && version.equals(new String(Files.readAllBytes(versionFile.toPath()), UTF_8))) {
                return;
            }
            delete(new File(dir, "shaders"));
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(pack))) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                    File file = new File(dir, entry.getName());
                    if (!file.getCanonicalPath()
                        .startsWith(dir.getCanonicalPath() + File.separator)) {
                        continue;
                    }
                    if (entry.isDirectory()) {
                        file.mkdirs();
                    } else {
                        file.getParentFile()
                            .mkdirs();
                        Files.write(file.toPath(), readAll(zip));
                    }
                }
            }
            Files.write(versionFile.toPath(), version.getBytes(UTF_8));
            WarOfTheGods.LOG.info("Installed the War of the Gods shader pack in {}", dir);
        } catch (IOException e) {
            WarOfTheGods.LOG.warn("Could not install the War of the Gods shader pack", e);
        }
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                delete(child);
            }
        }
        file.delete();
    }

    /**
     * Has OptiFine read the pack's block.properties again, for the glowing blocks of this mod. OptiFine reads it at
     * startup, before mods have added their blocks, and blocks get new ids from each server joined, so it is read
     * again whenever a world loads.
     */
    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!event.world.isRemote || !hasOptiFineShaders()) {
            return;
        }
        try {
            Field field = Class.forName("shadersmod.client.Shaders")
                .getDeclaredField("shaderPack");
            field.setAccessible(true);
            Object pack = field.get(null);
            if (pack != null) {
                Class.forName("shadersmod.client.BlockAliases")
                    .getMethod("update", Class.forName("shadersmod.client.IShaderPack"))
                    .invoke(null, pack);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            WarOfTheGods.LOG.warn("Could not have OptiFine read the shader pack's block ids again", e);
        }
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new ShaderPack());
    }

    private static boolean hasOptiFineShaders() {
        return ShaderPack.class.getClassLoader()
            .getResource("shadersmod/client/Shaders.class") != null;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) > 0;) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * Draws what follows with plain OpenGL, as without shaders, until {@link #endPlain()}: the far terrain and lines
     * of the RTS view set up OpenGL's fixed pipeline (texture coordinates made from positions, textures turned off for
     * lines), which a shader pack's programs know nothing of. While OptiFine draws the world into its buffers with a
     * shader program, the program is set aside and only the colour buffer is drawn to, which the pack's last passes
     * then colour like the rest of the world. Without shaders it does nothing.
     */
    public static void beginPlain() {
        bypassing = false;
        if (!GLContext.getCapabilities().OpenGL20) {
            return;
        }
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program == 0 || GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) == 0) {
            return;
        }
        int max = Math.min(16, GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS));
        if (drawBuffers == null || drawBuffers.capacity() < max) {
            drawBuffers = BufferUtils.createIntBuffer(max);
        }
        drawBuffers.clear();
        for (int i = 0; i < max; i++) {
            int buffer = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i);
            if (buffer == GL11.GL_NONE) {
                break;
            }
            drawBuffers.put(buffer);
        }
        drawBuffers.flip();
        bypassing = true;
        GL20.glUseProgram(0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
    }

    /**
     * Like {@link #beginPlain()}, for the RTS view's far terrain: under this mod's pack it is drawn with a program of
     * its own instead (assets/warofthegods/shaders/far_terrain), lit and coloured like the map's blocks and with its
     * lava and embers glowing into the pack's bloom. Under another pack, or if the program won't compile, it is drawn
     * plain.
     */
    public static void beginFarTerrain(long worldTime) {
        beginPlain();
        if (!bypassing || !isOwnPackInUse()) {
            return;
        }
        int lod = farTerrainProgram();
        if (lod == 0) {
            return;
        }
        GL20.glUseProgram(lod);
        GL20.glUniform1i(GL20.glGetUniformLocation(lod, "light"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(lod, "worldDay"), (int) (worldTime / 24000L));
        if (farTerrainBuffers == null) {
            farTerrainBuffers = BufferUtils.createIntBuffer(2);
            farTerrainBuffers.put(GL30.GL_COLOR_ATTACHMENT0)
                .put(GL30.GL_COLOR_ATTACHMENT0 + GLOW_BUFFER)
                .flip();
        }
        GL20.glDrawBuffers(farTerrainBuffers);
    }

    /** Whether OptiFine's shader pack in use is this mod's. */
    private static boolean isOwnPackInUse() {
        try {
            if (currentPackName == null) {
                currentPackName = Class.forName("shadersmod.client.Shaders")
                    .getDeclaredField("currentshadername");
                currentPackName.setAccessible(true);
            }
            return FOLDER_NAME.equals(currentPackName.get(null));
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    /** The far terrain's program, made the first time it is needed; 0 if it could not be. */
    private static int farTerrainProgram() {
        if (farTerrainProgramTried) {
            return farTerrainProgram;
        }
        farTerrainProgramTried = true;
        try {
            int vertex = compile(GL20.GL_VERTEX_SHADER, FAR_TERRAIN + ".vsh");
            int fragment = compile(GL20.GL_FRAGMENT_SHADER, FAR_TERRAIN + ".fsh");
            int program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(program, 4096);
                GL20.glDeleteProgram(program);
                throw new IOException(log);
            }
            farTerrainProgram = program;
        } catch (IOException e) {
            WarOfTheGods.LOG.warn("Could not make the far terrain's shader program; it is drawn plain", e);
        }
        return farTerrainProgram;
    }

    private static int compile(int type, String resource) throws IOException {
        String source;
        try (InputStream in = ShaderPack.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing " + resource);
            }
            source = new String(readAll(in), UTF_8);
        }
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 4096);
            GL20.glDeleteShader(shader);
            throw new IOException(resource + ": " + log);
        }
        return shader;
    }

    /** Puts back the shader program and buffers {@link #beginPlain()} set aside. */
    public static void endPlain() {
        if (!bypassing) {
            return;
        }
        bypassing = false;
        if (drawBuffers.hasRemaining()) {
            GL20.glDrawBuffers(drawBuffers);
        }
        GL20.glUseProgram(program);
    }
}
