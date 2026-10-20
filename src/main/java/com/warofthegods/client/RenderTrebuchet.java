package com.warofthegods.client;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.warofthegods.building.Schematic;
import com.warofthegods.unit.EntityUnit;

/**
 * Draws a trebuchet from the three structures tools/structures/generate.py makes for it, block by block as the world
 * draws them, at half size: the frame standing still, the arm turning on the axle, and the counterweight hanging
 * upright from the arm's short end. When it throws, the arm swings up over the top, faster and faster as the weight
 * drops, lets the stone go, and is wound back down until it is cocked again with a new stone in the sling. A pennant
 * in its team's color flies over each tower.
 */
final class RenderTrebuchet {

    /** How big it is drawn: the frame, 7 by 11 blocks, comes out about as long as a house. */
    private static final float SCALE = 0.5F;
    /** Where the axle runs, in the frame's blocks, and where on the arm it passes through, in the arm's. */
    private static final double AXLE_X = 3.5;
    private static final double AXLE_Y = 9.5;
    private static final double AXLE_Z = 5.5;
    private static final double PIVOT_Y = 1.5;
    private static final double PIVOT_Z = 3.5;
    /** How far the arm's short and long ends reach from the axle. */
    private static final double SHORT_ARM = 3;
    private static final double LONG_ARM = 6;
    /** The top of the counterweight's chain, which it hangs from, in its own blocks. */
    private static final double HINGE_X = 1.5;
    private static final double HINGE_Y = 3;
    private static final double HINGE_Z = 1.5;
    /**
     * The arm's tilt in degrees, its long end down from level toward the back: cocked, with the counterweight raised
     * at the front clear of the axle, and thrown, past upright toward the front.
     */
    private static final float COCKED = 48;
    private static final float THROWN = -125;
    /**
     * Ticks the swing takes, coming upright as the stone goes (see {@link EntityUnit#TREBUCHET_RELEASE}); ticks it
     * rocks at the top after; and ticks before the next throw by which it is wound down and loaded again.
     */
    private static final float SWING = 9;
    private static final float ROCK = 10;
    private static final float LOADED_BEFORE = 10;
    /** Pennant pole and cloth, in the frame's blocks. */
    private static final double POLE_BOTTOM = 10;
    private static final double POLE_TOP = 12.6;
    private static final double FLAG_LENGTH = 1.6;
    private static final double FLAG_HEIGHT = 0.8;
    private static final int POLE_COLOR = 0x5A3D22;

    private final Part frame = new Part(Schematic.load("neutral/trebuchet_frame"), Part.ALL);
    private final Part arm = new Part(Schematic.load("neutral/trebuchet_arm"), Part.NO_STONE);
    private final Part stone = new Part(arm.schematic, Part.STONE_ONLY);
    private final Part weight = new Part(Schematic.load("neutral/trebuchet_counterweight"), Part.ALL);
    private final RenderBlocks renderBlocks = new RenderBlocks();
    /** For each trebuchet seen: the number of throws it showed, and the tick its latest throw was seen to begin. */
    private final Map<EntityUnit, int[]> throwsSeen = new WeakHashMap<>();

    void render(EntityUnit unit, double x, double y, double z, float partialTicks) {
        float sinceThrow = sinceThrow(unit, partialTicks);
        float tilt = armTilt(sinceThrow);
        boolean loaded = sinceThrow < EntityUnit.TREBUCHET_RELEASE
            || sinceThrow >= EntityUnit.TREBUCHET_RELOAD - LOADED_BEFORE;
        int brightness = unit.getBrightnessForRender(partialTicks);
        float yaw = unit.prevRotationYawHead
            + MathHelper.wrapAngleTo180_float(unit.rotationYawHead - unit.prevRotationYawHead) * partialTicks;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        // Turned so its front, the frame's z = 0 side, faces the way it looks.
        GL11.glRotatef(180 - yaw, 0, 1, 0);
        if (unit.deathTime > 0) {
            // Broken, it keels over and sinks.
            float dying = Math.min(1, (unit.deathTime + partialTicks - 1) / 20);
            GL11.glTranslatef(0, -dying * 0.8F, 0);
            GL11.glRotatef(dying * 30, 0, 0, 1);
        }
        GL11.glScalef(SCALE, SCALE, SCALE);
        // The grass the frame stands on in its structure is left out, so the wheels touch the ground.
        GL11.glTranslated(-frame.schematic.width / 2.0, -1, -frame.schematic.length / 2.0);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1, 1, 1, 1);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        draw(frame, brightness);

        double sin = Math.sin(Math.toRadians(tilt));
        double cos = Math.cos(Math.toRadians(tilt));
        GL11.glPushMatrix();
        GL11.glTranslated(AXLE_X, AXLE_Y, AXLE_Z);
        GL11.glRotatef(tilt, 1, 0, 0);
        GL11.glTranslated(-0.5, -PIVOT_Y, -PIVOT_Z);
        draw(arm, brightness);
        GL11.glPopMatrix();

        // The counterweight hangs straight down from the short end, wherever the arm has it.
        GL11.glPushMatrix();
        GL11.glTranslated(AXLE_X - HINGE_X, AXLE_Y + SHORT_ARM * sin - HINGE_Y, AXLE_Z - SHORT_ARM * cos - HINGE_Z);
        draw(weight, brightness);
        GL11.glPopMatrix();

        // The stone hangs in the sling under the long end until it is let go.
        if (loaded) {
            // Its block sits under the arm's tip in the arm's structure; moved to hang under the tip as it is now.
            int tip = arm.schematic.length - 1;
            GL11.glPushMatrix();
            GL11.glTranslated(AXLE_X - 0.5, AXLE_Y - LONG_ARM * sin - 1.5, AXLE_Z + LONG_ARM * cos - 0.5 - tip);
            draw(stone, brightness);
            GL11.glPopMatrix();
        }

        pennants(unit.getTeamColor());
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
    }

    /**
     * Ticks since the trebuchet's latest throw began, or longer than a reload when none was seen: a throw seen
     * starting is one whose count changed while it was in sight, so one already loaded in view is not swung.
     */
    private float sinceThrow(EntityUnit unit, float partialTicks) {
        int count = unit.getWatchedThrows();
        int[] seen = throwsSeen.get(unit);
        if (seen == null) {
            seen = new int[] { count, Integer.MIN_VALUE / 2 };
            throwsSeen.put(unit, seen);
        } else if (seen[0] != count) {
            seen[0] = count;
            seen[1] = unit.ticksExisted;
        }
        return unit.ticksExisted - (float) seen[1] + partialTicks;
    }

    /** The arm's tilt so many ticks after a throw began. */
    static float armTilt(float sinceThrow) {
        if (sinceThrow < 0 || sinceThrow >= EntityUnit.TREBUCHET_RELOAD) {
            return COCKED;
        }
        if (sinceThrow < SWING) {
            // Faster and faster as the counterweight drops.
            float t = sinceThrow / SWING;
            return COCKED + (THROWN - COCKED) * t * t;
        }
        if (sinceThrow < SWING + ROCK) {
            // Rocking back a little at the top before it settles.
            float t = (sinceThrow - SWING) / ROCK;
            return THROWN + 12 * (float) Math.sin(Math.PI * t);
        }
        // Wound back down, slowly at first and last.
        float windEnd = EntityUnit.TREBUCHET_RELOAD - LOADED_BEFORE;
        float t = Math.min(1, (sinceThrow - SWING - ROCK) / (windEnd - SWING - ROCK));
        float eased = t * t * (3 - 2 * t);
        return THROWN + (COCKED - THROWN) * eased;
    }

    /** Draws every block of a part where its structure has it, lit as the trebuchet is. */
    private void draw(Part part, int brightness) {
        part.brightness = brightness;
        renderBlocks.blockAccess = part;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        Schematic schematic = part.schematic;
        for (int y = 0; y < schematic.height; y++) {
            for (int z = 0; z < schematic.length; z++) {
                for (int x = 0; x < schematic.width; x++) {
                    Block block = part.getBlock(x, y, z);
                    if (block != Blocks.air) {
                        renderBlocks.renderBlockByRenderType(block, x, y, z);
                    }
                }
            }
        }
        tessellator.draw();
    }

    /** A pole on the end of the axle over each tower, with a pennant of the team's color streaming back from it. */
    private void pennants(int teamColor) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        for (double side : new double[] { 1.5, 5.5 }) {
            box(tessellator, side - 0.06, POLE_BOTTOM, AXLE_Z - 0.06, side + 0.06, POLE_TOP, AXLE_Z + 0.06, POLE_COLOR);
            box(
                tessellator,
                side - 0.04,
                POLE_TOP - FLAG_HEIGHT - 0.1,
                AXLE_Z + 0.06,
                side + 0.04,
                POLE_TOP - 0.1,
                AXLE_Z + 0.06 + FLAG_LENGTH,
                teamColor);
        }
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** A box of one color, its sides shaded as the world shades blocks. */
    private static void box(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1,
        int color) {
        shade(t, color, 1.0F);
        t.addVertex(x0, y1, z0);
        t.addVertex(x0, y1, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x1, y1, z0);
        shade(t, color, 0.5F);
        t.addVertex(x0, y0, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x1, y0, z1);
        t.addVertex(x0, y0, z1);
        shade(t, color, 0.8F);
        t.addVertex(x0, y0, z0);
        t.addVertex(x0, y1, z0);
        t.addVertex(x1, y1, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x0, y0, z1);
        t.addVertex(x1, y0, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x0, y1, z1);
        shade(t, color, 0.6F);
        t.addVertex(x0, y0, z0);
        t.addVertex(x0, y0, z1);
        t.addVertex(x0, y1, z1);
        t.addVertex(x0, y1, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x1, y1, z0);
        t.addVertex(x1, y1, z1);
        t.addVertex(x1, y0, z1);
    }

    private static void shade(Tessellator t, int color, float factor) {
        t.setColorOpaque_F(
            (color >> 16 & 0xFF) / 255F * factor,
            (color >> 8 & 0xFF) / 255F * factor,
            (color & 0xFF) / 255F * factor);
    }

    /**
     * One of the trebuchet's structures as a little world of its own for the block renderer: its blocks, but for the
     * grass it stands on, which is not drawn, and with the stone in the arm's sling drawn apart from the arm.
     */
    private static final class Part implements IBlockAccess {

        /** Which of a structure's blocks a part has: all, all but the stone, or only the stone. */
        static final int ALL = 0;
        static final int NO_STONE = 1;
        static final int STONE_ONLY = 2;

        final Schematic schematic;
        private final int which;
        int brightness;

        Part(Schematic schematic, int which) {
            this.schematic = schematic;
            this.which = which;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= schematic.width || y >= schematic.height || z >= schematic.length) {
                return Blocks.air;
            }
            Block block = schematic.block(x, y, z);
            boolean isStone = block == Blocks.cobblestone;
            if (block == null || block == Blocks.grass
                || which == NO_STONE && isStone
                || which == STONE_ONLY && !isStone) {
                return Blocks.air;
            }
            return block;
        }

        @Override
        public TileEntity getTileEntity(int x, int y, int z) {
            return null;
        }

        @Override
        public int getLightBrightnessForSkyBlocks(int x, int y, int z, int minLight) {
            return brightness;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            return getBlock(x, y, z) == Blocks.air ? 0 : schematic.meta(x, y, z);
        }

        @Override
        public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
            return 0;
        }

        @Override
        public boolean isAirBlock(int x, int y, int z) {
            return getBlock(x, y, z) == Blocks.air;
        }

        @Override
        public BiomeGenBase getBiomeGenForCoords(int x, int z) {
            return BiomeGenBase.plains;
        }

        @Override
        public int getHeight() {
            return 256;
        }

        @Override
        public boolean extendedLevelsInChunkCache() {
            return false;
        }

        @Override
        public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean otherwise) {
            return getBlock(x, y, z).isSideSolid(this, x, y, z, side);
        }
    }
}
