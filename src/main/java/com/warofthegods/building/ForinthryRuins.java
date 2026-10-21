package com.warofthegods.building;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.world.WorldServer;

import com.warofthegods.Config;
import com.warofthegods.game.Game;
import com.warofthegods.game.Ruin;
import com.warofthegods.web.WebMap;
import com.warofthegods.world.ChunkProviderRts;
import com.warofthegods.world.Forinthry;
import com.warofthegods.world.GameWorld;

/**
 * The ruins of Forinthry, more threatening than Misthalin's: the great Graveyard of Shadows where ghosts walk, the
 * ruins of big fortresses where hill giants and gargoyles lair, bandits' camps, and smaller ruins (graveyards,
 * burnt chapels and houses, broken watchtowers and Zamorak's altars) with red spiders in their cobwebs, ghosts or
 * hellhounds. Each is a schematic made by tools/structures/wilderness.py, in a few variants, laid out as the world is
 * generated just as {@link Ruins} lays out Misthalin's, and haunted the same way (see {@code Mobs}). Each lava maze
 * also gets a chest of loot in its middle, which hellhounds guard.
 */
final class ForinthryRuins {

    /** The kinds of ruin: their schematics' names, how common they are, and whether they are big. */
    enum Design {

        GRAVEYARD_OF_SHADOWS("graveyard_of_shadows", "Graveyard of Shadows", 2, true, false),
        FORTRESS("fortress", "ruined fortress", 2, true, false),
        BANDIT_CAMP("bandit_camp", "bandit camp", 3, true, false),
        GRAVEYARD("graveyard", "haunted graveyard", 2, false, true),
        CHAPEL("chapel", "burnt chapel", 2, false, true),
        HOUSE("house", "burnt-out house", 3, false, true),
        WATCHTOWER("watchtower", "broken watchtower", 2, false, true),
        CHAOS_ALTAR("chaos_altar", "altar of Zamorak", 1, false, true);

        final String file;
        final String name;
        final int weight;
        final boolean big;
        /** Whether it comes with cobwebs too, for red spiders. */
        final boolean webbed;

        Design(String file, String name, int weight, boolean big, boolean webbed) {
            this.file = file;
            this.name = name;
            this.weight = weight;
            this.big = big;
            this.webbed = webbed;
        }
    }

    /** How many variants of each design tools/structures/wilderness.py draws. */
    private static final int VARIANTS = 2;
    /** Blocks kept clear between a ruin and a lava maze. */
    private static final int MAZE_SPACING = 6;
    private static final int TRIES = 6;
    private static final int MAX_SLOPE = 3;

    private static final Map<String, Schematic> SCHEMATICS = new HashMap<>();

    private ForinthryRuins() {}

    private static Schematic schematic(String name) {
        Schematic schematic = SCHEMATICS.get(name);
        if (schematic == null) {
            schematic = Schematic.load("forinthry/" + name);
            SCHEMATICS.put(name, schematic);
        }
        return schematic;
    }

    /**
     * Lays out ruins as the world is generated, one site in about every {@link Config#ruinEvery} blocks square, and
     * a lava maze's chest when its middle is in this chunk. Everything stays inside the chunk and the ones east and
     * south of it, which are already there.
     */
    static void populate(Game game, WorldServer world, int chunkX, int chunkZ, long seed) {
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        Forinthry layout = GameWorld.forinthry(game);
        if (layout != null) {
            for (Forinthry.Maze maze : layout.getMazes()) {
                int cx = maze.getCenterX();
                int cz = maze.getCenterZ();
                if (cx >= baseX + 8 && cx < baseX + 24 && cz >= baseZ + 8 && cz < baseZ + 24) {
                    mazeChest(game, world, maze, new Random(seed * 13 + cx * 31L + cz));
                }
            }
        }
        if (!game.isInside(baseX + 16, baseZ + 16)) {
            return;
        }
        Random random = new Random(chunkX * 341873128712L + chunkZ * 132897987541L + seed + 2);
        if (random.nextDouble() >= 256.0 / ((double) Config.ruinEvery * Config.ruinEvery)) {
            return;
        }
        Design design = pick(random);
        boolean webs = design.webbed && random.nextInt(100) < 35;
        String file = design.file + "_" + (1 + random.nextInt(VARIANTS)) + (webs ? "_webs" : "");
        place(game, world, layout, baseX, baseZ, design, schematic(file), haunt(design, webs, random), random);
    }

    private static Design pick(Random random) {
        int total = 0;
        for (Design design : Design.values()) {
            total += design.weight;
        }
        int roll = random.nextInt(total);
        for (Design design : Design.values()) {
            roll -= design.weight;
            if (roll < 0) {
                return design;
            }
        }
        return Design.HOUSE;
    }

    /** What haunts a ruin of the design: its own lot for the big ones, spiders where it has cobwebs. */
    private static int haunt(Design design, boolean webs, Random random) {
        switch (design) {
            case GRAVEYARD_OF_SHADOWS:
                return Ruin.GHOSTS;
            case FORTRESS:
                return Ruin.GIANTS;
            case BANDIT_CAMP:
                return Ruin.BANDITS;
            case CHAOS_ALTAR:
                return webs ? Ruin.SPIDERS : random.nextInt(3) > 0 ? Ruin.HELLHOUNDS : Ruin.QUIET;
            case GRAVEYARD:
                return webs ? Ruin.SPIDERS : random.nextInt(4) > 0 ? Ruin.GHOSTS : Ruin.QUIET;
            default:
                return webs ? Ruin.SPIDERS : random.nextInt(5) < 2 ? Ruin.GHOSTS : Ruin.QUIET;
        }
    }

    private static void place(Game game, WorldServer world, Forinthry layout, int baseX, int baseZ, Design design,
        Schematic schematic, int haunt, Random random) {
        int rotation = random.nextInt(4);
        int sizeX = schematic.sizeX(rotation);
        int sizeZ = schematic.sizeZ(rotation);
        for (int tries = 0; tries < TRIES; tries++) {
            // Anywhere in this chunk and the next ones that the ruin fits, a block in from their edges; small ones
            // round the middle of the two, as in Misthalin.
            int from = design.big ? 1 : 8;
            int room = (design.big ? 30 : 23) - Math.max(sizeX, sizeZ);
            if (room <= 0) {
                return;
            }
            int minX = baseX + from + random.nextInt(room);
            int minZ = baseZ + from + random.nextInt(room);
            if (!Ruins.clear(game, minX, minZ, sizeX, sizeZ)
                || layout != null && layout.nearMaze(minX, minZ, sizeX, sizeZ, MAZE_SPACING)
                || nearStart(game, layout, minX + sizeX / 2.0, minZ + sizeZ / 2.0)) {
                continue;
            }
            Buildings.Site site = Buildings.survey(world, minX, minZ, sizeX, sizeZ);
            if (site.water == 0 && site.slope <= MAX_SLOPE
                && site.groundY > ChunkProviderRts.BANK
                && site.groundY + schematic.height < 255) {
                Ruin ruin = game.addRuin(minX, minZ, sizeX, sizeZ, site.groundY, design.name, haunt);
                schematic.paste(world, minX, minZ, site.groundY, rotation);
                WebMap.blocksChanged(world, minX - 1, minZ - 1, minX + sizeX, minZ + sizeZ);
                RuinLoot.stock(world, ruin, new Random(random.nextLong()));
                return;
            }
        }
    }

    /** Whether a spot is near where a team starts, though it has not started there yet. */
    private static boolean nearStart(Game game, Forinthry layout, double x, double z) {
        if (layout == null) {
            return false;
        }
        double spacing = Math.max(40, game.getSize() * 0.08);
        for (double[] base : layout.getBases()) {
            if (Math.hypot(base[0] - x, base[1] - z) < spacing) {
                return true;
            }
        }
        return false;
    }

    /** The chest in a lava maze's middle cell, kept as a ruin hellhounds guard, so it is marked on the map too. */
    private static void mazeChest(Game game, WorldServer world, Forinthry.Maze maze, Random random) {
        int middle = maze.cells / 2;
        int minX = maze.minX + middle * Forinthry.PITCH + Forinthry.WALL;
        int minZ = maze.minZ + middle * Forinthry.PITCH + Forinthry.WALL;
        for (Ruin ruin : game.getRuins()) {
            if (ruin.overlaps(minX, minZ, Forinthry.PATH, Forinthry.PATH, 0)) {
                return;
            }
        }
        int groundY = world.getTopSolidOrLiquidBlock(minX + 1, minZ + 1) - 1;
        Ruin ruin = game.addRuin(minX, minZ, Forinthry.PATH, Forinthry.PATH, groundY, "lava maze", Ruin.HELLHOUNDS);
        RuinLoot.stock(world, ruin, random, true);
    }
}
