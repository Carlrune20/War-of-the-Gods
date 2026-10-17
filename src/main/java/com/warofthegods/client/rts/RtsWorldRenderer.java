package com.warofthegods.client.rts;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import com.warofthegods.building.Schematic;

/**
 * What the RTS view draws into the world, after Minecraft has drawn it: building outlines in their team's colour,
 * a see-through copy of the building being placed, rings under selected units with a line to
 * where each is headed, the last order's marker and the edge of the map. Lines show faintly through what is in front
 * of them, so selected units stay visible behind a hill.
 */
public final class RtsWorldRenderer {

    private static final float[] SELECTED = { 0.45F, 1, 0.5F };
    private static final float[] ATTACK = { 1, 0.3F, 0.25F };
    private static final float[] RANGE = { 1, 0.85F, 0.35F };
    private static final float[] BORDER = { 1, 0.32F, 0.24F };
    private static final float[] GHOST = { 0.93F, 0.91F, 0.86F };
    /** How solid the building being placed looks. */
    private static final float GHOST_ALPHA = 0.55F;

    private final RtsClient client;
    private Tessellator tessellator;

    /** The building being placed, as blocks, and what it was made for, so it is only made again on a change. */
    private GhostBlocks ghostBlocks;
    private String ghostKey;
    private final Map<String, Schematic> looks = new HashMap<>();

    /** How far around what the camera looks at the fog of war is drawn, in blocks. */
    private static final int FOG_REACH = 144;
    /** Milliseconds before the fog is made again anyway, as the ground it lies on comes in. */
    private static final long FOG_REFRESH = 2000;
    /** The fog of war as made last, in a display list, and what it was made for. */
    private int fogList = -1;
    private int fogVersion = -1;
    private int fogX;
    private int fogZ;
    private long fogTime;

    RtsWorldRenderer(RtsClient client) {
        this.client = client;
    }

    /**
     * Draws the building being placed block by block, as it will look at the team's age, see-through; false when its
     * look cannot be had, to draw boxes instead.
     */
    private boolean blockGhost(RtsState.BuildingTypeInfo type, int rotation, RtsClient.Footprint spot,
        float partialTicks) {
        RtsState.TeamInfo team = client.state.myTeam();
        if (type == null || team == null) {
            return false;
        }
        String faction = team.faction == null || team.faction.isEmpty() ? "saradomin" : team.faction;
        String look = faction + "/" + type.id + "_l" + Math.max(1, Math.min(type.levels, team.age));
        String key = look + "@" + rotation + "," + spot.x + "," + spot.z + "," + spot.y;
        if (!key.equals(ghostKey)) {
            Schematic schematic = looks.get(look);
            if (schematic == null && !looks.containsKey(look)) {
                try {
                    schematic = Schematic.load(look);
                } catch (RuntimeException e) {
                    schematic = null;
                }
                looks.put(look, schematic);
            }
            ghostKey = key;
            ghostBlocks = schematic == null ? null : new GhostBlocks(schematic, rotation, spot.x, spot.z, spot.y);
        }
        if (ghostBlocks == null) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        mc.entityRenderer.enableLightmap(partialTicks);
        mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        // Blocks draw themselves opaque; the blend makes the whole building see-through alike.
        GL14.glBlendColor(1, 1, 1, GHOST_ALPHA);
        GL11.glBlendFunc(GL11.GL_CONSTANT_ALPHA, GL11.GL_ONE_MINUS_CONSTANT_ALPHA);
        RenderBlocks renderer = new RenderBlocks(ghostBlocks);
        tessellator.startDrawingQuads();
        for (int[] at : ghostBlocks.positions()) {
            Block block = ghostBlocks.getBlock(at[0], at[1], at[2]);
            try {
                renderer.renderBlockByRenderType(block, at[0], at[1], at[2]);
            } catch (RuntimeException e) {
                // A block that needs more of a world than this to draw is left out.
            }
        }
        tessellator.draw();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        mc.entityRenderer.disableLightmap(partialTicks);
        return true;
    }

    void render(float partialTicks) {
        RtsState state = client.state;
        if (state.game == null) {
            return;
        }
        tessellator = Tessellator.instance;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_ALPHA_TEST);

        if (client.fog.isOn()) {
            fog();
        }

        // The see-through building being placed first, without writing depth, so lines show through it: as it will
        // look where it fits, red where it does not. Building sites need no copy: their scaffolding stands in the
        // world.
        GL11.glDepthMask(false);
        RtsClient.Footprint spot = client.footprint();
        RtsState.BuildingTypeInfo placingType = spot == null ? null : state.buildingType(client.placing.type);
        if (spot != null && (!spot.ok || !blockGhost(placingType, spot.rotation, spot, partialTicks))) {
            tessellator.startDrawingQuads();
            ghost(
                placingType,
                spot.rotation,
                spot.x,
                spot.z,
                spot.sizeX,
                spot.sizeZ,
                spot.y,
                spot.ok ? GHOST : ATTACK,
                0.4F);
            tessellator.draw();
        }
        RtsClient.WallSpot wall = client.wallSpot();
        if (wall != null) {
            tessellator.startDrawingQuads();
            for (int i = 0; i < wall.pieces.size(); i++) {
                RtsClient.Footprint piece = wall.pieces.get(i);
                RtsState.BuildingTypeInfo pieceType = state.buildingType(wall.types.get(i));
                if (pieceType != null) {
                    ghost(
                        pieceType,
                        piece.rotation,
                        piece.x,
                        piece.z,
                        piece.sizeX,
                        piece.sizeZ,
                        piece.y,
                        piece.ok ? GHOST : ATTACK,
                        0.4F);
                }
            }
            tessellator.draw();
        }
        GL11.glDepthMask(true);

        GL11.glLineWidth(2.5F);
        // Twice: faintly through anything in front, then fully where nothing is.
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        lines(partialTicks, 0.3F);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        lines(partialTicks, 1);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void lines(float partialTicks, float alpha) {
        RtsState state = client.state;
        tessellator.startDrawing(GL11.GL_LINES);
        for (RtsState.BuildingInfo b : state.buildings.values()) {
            if (state.isEnemyTeam(b.team) && !client.fog.isExplored(b.x, b.z, b.sizeX, b.sizeZ)) {
                continue;
            }
            RtsState.TeamInfo team = state.teamsById.get(b.team);
            boolean chosen = client.selectedBuildings.contains(b.id);
            float[] color = chosen ? SELECTED : team == null ? new float[] { 0.8F, 0.8F, 0.8F } : rgb(team.color);
            rect(b.x - 0.5, b.z - 0.5, b.x + b.sizeX + 0.5, b.z + b.sizeZ + 0.5, b.y + 1.1, color, alpha);
            // How far towers shoot, for the ones selected and the one under the mouse.
            if (b.range > 0 && (chosen || b.id == client.hoverBuilding)) {
                rangeRing(b.x + b.sizeX / 2.0, b.z + b.sizeZ / 2.0, b.range, alpha);
            }
            // Where the new units of a selected building go.
            if (chosen && !Double.isNaN(b.rallyX)) {
                double rallyY = client.groundHeight(b.rallyX, b.rallyZ) + 0.15;
                line(b.x + b.sizeX / 2.0, b.y + 1.1, b.z + b.sizeZ / 2.0, b.rallyX, rallyY, b.rallyZ, SELECTED, alpha);
                ring(b.rallyX, rallyY, b.rallyZ, 0.6, SELECTED, alpha);
            }
        }
        RtsClient.Footprint spot = client.footprint();
        if (spot != null) {
            rect(
                spot.x,
                spot.z,
                spot.x + spot.sizeX,
                spot.z + spot.sizeZ,
                spot.y + 1.1,
                spot.ok ? SELECTED : ATTACK,
                alpha);
        }
        RtsClient.WallSpot wall = client.wallSpot();
        if (wall != null) {
            for (RtsClient.Footprint piece : wall.pieces) {
                rect(
                    piece.x,
                    piece.z,
                    piece.x + piece.sizeX,
                    piece.z + piece.sizeZ,
                    piece.y + 1.1,
                    piece.ok ? SELECTED : ATTACK,
                    alpha);
            }
        }
        for (String id : client.selected) {
            RtsState.UnitInfo unit = state.units.get(id);
            if (unit == null) {
                continue;
            }
            double[] at = client.positionOf(unit, partialTicks);
            ring(at[0], at[1] + 0.05, at[2], 0.7, SELECTED, alpha);
            RtsState.Thing target = unit.target == null ? null
                : state.units.containsKey(unit.target) ? state.units.get(unit.target)
                    : state.players.containsKey(unit.target) ? state.players.get(unit.target)
                        : state.mobs.get(unit.target);
            double[] to = target == null ? null : client.positionOf(target, partialTicks);
            if (to != null && !client.fog.isVisible(to[0], to[2])) {
                to = null;
            }
            if ("attack".equals(unit.order) && to != null) {
                line(at[0], at[1] + 0.1, at[2], to[0], to[1] + 0.1, to[2], ATTACK, alpha);
            } else if (!"attack".equals(unit.order) && !"idle".equals(unit.order) && !Double.isNaN(unit.toX)) {
                double y = client.groundHeight(unit.toX, unit.toZ);
                line(at[0], at[1] + 0.1, at[2], unit.toX, y + 0.1, unit.toZ, SELECTED, alpha);
            }
        }
        double[] mark = client.orderMark;
        if (mark != null) {
            double age = (System.currentTimeMillis() - client.orderMarkTime) / 1000.0;
            if (age > 1) {
                client.orderMark = null;
            } else {
                ring(
                    mark[0],
                    mark[1] + 0.1,
                    mark[2],
                    0.5 + 1.5 * age,
                    new float[] { (float) mark[3], (float) mark[4], (float) mark[5] },
                    alpha * (float) (1 - age));
            }
        }
        border(alpha);
        tessellator.draw();
    }

    /**
     * The fog of war near the camera: solid black over all the ground the viewer's team does not see now. It lies on
     * top of every column the client has, sides down cliffs included, and is kept in a display list, made again when what is seen changes or the camera moves on.
     */
    private void fog() {
        FogOfWar fog = client.fog;
        RtsCamera camera = client.getCamera();
        int cx = fog.cellX(camera.focusX) >> 4;
        int cz = fog.cellZ(camera.focusZ) >> 4;
        long now = System.currentTimeMillis();
        if (fogList < 0) {
            fogList = GLAllocation.generateDisplayLists(1);
            fogVersion = -1;
        }
        if (fogVersion != fog.version() || cx != fogX || cz != fogZ || now - fogTime > FOG_REFRESH) {
            fogVersion = fog.version();
            fogX = cx;
            fogZ = cz;
            fogTime = now;
            GL11.glNewList(fogList, GL11.GL_COMPILE);
            makeFog(camera);
            GL11.glEndList();
        }
        GL11.glCallList(fogList);
    }

    private void makeFog(RtsCamera camera) {
        RtsState.GameInfo game = client.state.game;
        FogOfWar fog = client.fog;
        int x0 = Math.max(game.minX, (int) camera.focusX - FOG_REACH);
        int x1 = Math.min(game.maxX, (int) camera.focusX + FOG_REACH);
        int z0 = Math.max(game.minZ, (int) camera.focusZ - FOG_REACH);
        int z1 = Math.min(game.maxZ, (int) camera.focusZ + FOG_REACH);
        if (x0 > x1 || z0 > z1) {
            return;
        }
        int width = x1 - x0 + 1;
        int[] heights = new int[width * (z1 - z0 + 1)];
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                heights[(z - z0) * width + x - x0] = (int) client.groundHeight(x, z);
            }
        }
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(0, 0, 0, 1);
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                if (fog.isVisible(x + 0.5, z + 0.5)) {
                    continue;
                }
                double y = heights[(z - z0) * width + x - x0] + 0.02;
                quad(x, y, z, x, y, z + 1, x + 1, y, z + 1, x + 1, y, z);
                // Down the sides to lower neighbours, so a cliff is covered as well as its top.
                double west = x > x0 ? heights[(z - z0) * width + x - 1 - x0] : y;
                double east = x < x1 ? heights[(z - z0) * width + x + 1 - x0] : y;
                double north = z > z0 ? heights[(z - 1 - z0) * width + x - x0] : y;
                double south = z < z1 ? heights[(z + 1 - z0) * width + x - x0] : y;
                if (west < y) {
                    quad(x, west, z, x, y, z, x, y, z + 1, x, west, z + 1);
                }
                if (east < y) {
                    quad(x + 1, east, z, x + 1, east, z + 1, x + 1, y, z + 1, x + 1, y, z);
                }
                if (north < y) {
                    quad(x, north, z, x + 1, north, z, x + 1, y, z, x, y, z);
                }
                if (south < y) {
                    quad(x, south, z + 1, x, y, z + 1, x + 1, y, z + 1, x + 1, south, z + 1);
                }
            }
        }
        tessellator.draw();
        GL11.glDepthMask(true);
    }

    /** The edge of the map near the camera, along the ground. */
    private void border(float alpha) {
        RtsState.GameInfo game = client.state.game;
        RtsCamera camera = client.getCamera();
        int reach = 96;
        int fromX = Math.max(game.minX, (int) camera.focusX - reach);
        int toX = Math.min(game.maxX + 1, (int) camera.focusX + reach);
        int fromZ = Math.max(game.minZ, (int) camera.focusZ - reach);
        int toZ = Math.min(game.maxZ + 1, (int) camera.focusZ + reach);
        for (int edge = 0; edge < 2; edge++) {
            double z = edge == 0 ? game.minZ : game.maxZ + 1;
            if (Math.abs(z - camera.focusZ) < reach) {
                for (int x = fromX; x < toX; x++) {
                    line(x, edgeY(x, z), z, x + 1, edgeY(x + 1, z), z, BORDER, alpha);
                }
            }
            double x = edge == 0 ? game.minX : game.maxX + 1;
            if (Math.abs(x - camera.focusX) < reach) {
                for (int zz = fromZ; zz < toZ; zz++) {
                    line(x, edgeY(x, zz), zz, x, edgeY(x, zz + 1), zz + 1, BORDER, alpha);
                }
            }
        }
    }

    private double edgeY(double x, double z) {
        return Math.max(client.groundHeight(x - 0.5, z - 0.5), client.groundHeight(x + 0.5, z + 0.5)) + 0.15;
    }

    /** See-through boxes in the shape of a building: one per run of equal-height columns along x. */
    private void ghost(RtsState.BuildingTypeInfo type, int rotation, int minX, int minZ, int footX, int footZ,
        int groundY, float[] color, float alpha) {
        int[] shape = RtsState.turnedShape(type, rotation);
        int sizeX = shape[0];
        // Cut to the footprint, for a short segment of wall.
        int endX = Math.min(sizeX, footX);
        int endZ = Math.min(shape[1], footZ);
        tessellator.setColorRGBA_F(color[0], color[1], color[2], alpha);
        for (int z = 0; z < endZ; z++) {
            int x = 0;
            while (x < endX) {
                int h = shape[2 + x + z * sizeX];
                int run = 1;
                while (x + run < endX && shape[2 + x + run + z * sizeX] == h) {
                    run++;
                }
                if (h > 0) {
                    box(minX + x, groundY + 1, minZ + z, run, Math.max(0.2, h - 1), 1);
                }
                x += run;
            }
        }
    }

    private void box(double x, double y, double z, double sx, double sy, double sz) {
        double x1 = x + sx;
        double y1 = y + sy;
        double z1 = z + sz;
        // Bottom, top, then the four sides.
        quad(x, y, z, x1, y, z, x1, y, z1, x, y, z1);
        quad(x, y1, z, x, y1, z1, x1, y1, z1, x1, y1, z);
        quad(x, y, z, x, y1, z, x1, y1, z, x1, y, z);
        quad(x, y, z1, x1, y, z1, x1, y1, z1, x, y1, z1);
        quad(x, y, z, x, y, z1, x, y1, z1, x, y1, z);
        quad(x1, y, z, x1, y1, z, x1, y1, z1, x1, y, z1);
    }

    private void quad(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz,
        double dx, double dy, double dz) {
        tessellator.addVertex(ax, ay, az);
        tessellator.addVertex(bx, by, bz);
        tessellator.addVertex(cx, cy, cz);
        tessellator.addVertex(dx, dy, dz);
    }

    private void line(double ax, double ay, double az, double bx, double by, double bz, float[] color, float alpha) {
        tessellator.setColorRGBA_F(color[0], color[1], color[2], alpha);
        tessellator.addVertex(ax, ay, az);
        tessellator.addVertex(bx, by, bz);
    }

    private void rect(double x0, double z0, double x1, double z1, double y, float[] color, float alpha) {
        line(x0, y, z0, x1, y, z0, color, alpha);
        line(x1, y, z0, x1, y, z1, color, alpha);
        line(x1, y, z1, x0, y, z1, color, alpha);
        line(x0, y, z1, x0, y, z0, color, alpha);
    }

    /** A ring along the ground around x, z, rising and falling with it. */
    private void rangeRing(double x, double z, double radius, float alpha) {
        int segments = 72;
        double lastX = x + radius;
        double lastZ = z;
        double lastY = client.groundHeight(lastX, lastZ) + 0.15;
        for (int i = 1; i <= segments; i++) {
            double a = 2 * Math.PI * i / segments;
            double nextX = x + Math.cos(a) * radius;
            double nextZ = z + Math.sin(a) * radius;
            double nextY = client.groundHeight(nextX, nextZ) + 0.15;
            line(lastX, lastY, lastZ, nextX, nextY, nextZ, RANGE, alpha);
            lastX = nextX;
            lastY = nextY;
            lastZ = nextZ;
        }
    }

    private void ring(double x, double y, double z, double radius, float[] color, float alpha) {
        int segments = 20;
        for (int i = 0; i < segments; i++) {
            double a = 2 * Math.PI * i / segments;
            double b = 2 * Math.PI * (i + 1) / segments;
            line(
                x + Math.cos(a) * radius,
                y,
                z + Math.sin(a) * radius,
                x + Math.cos(b) * radius,
                y,
                z + Math.sin(b) * radius,
                color,
                alpha);
        }
    }

    private static float[] rgb(int color) {
        return new float[] { (color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F };
    }
}
