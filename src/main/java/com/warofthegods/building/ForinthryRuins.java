package com.warofthegods.building;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * The ruins of Forinthry, more threatening than Misthalin's: the great Graveyards of Shadows where ghosts walk, the
 * old fortresses, walled towns of cobwebbed ruins full of ghosts, the undead and red spiders, bandits' camps of tents
 * and lookouts round a fire, and smaller ruins (graveyards, burnt chapels and houses, broken watchtowers and Zamorak's
 * altars) with red spiders in their cobwebs, ghosts or hellhounds. Each is a schematic made by
 * tools/structures/wilderness.py, in a few variants (a bandit camp is several, laid out together), placed as the world
 * is
 * generated just as {@link Ruins} lays out Misthalin's, and haunted the same way (see {@code Mobs}). The old
 * fortresses are too big for that: {@link Forinthry} picks where they go and which variant each is, and each goes in a
 * chunk at a time as the chunks it covers are filled in.
 */
final class ForinthryRuins {

    /** The kinds of ruin: their schematics' names, how common they are, and whether they are big. */
    enum Design {

        GRAVEYARD_OF_SHADOWS("graveyard_of_shadows", "Graveyard of Shadows", 2, true, false, 6),
        /** Laid out piece by piece round its fire, see {@link #banditCamp}. */
        BANDIT_CAMP("camp", "bandit camp", 3, true, false, 0),
        GRAVEYARD("graveyard", "haunted graveyard", 2, false, true, 6),
        CHAPEL("chapel", "burnt chapel", 2, false, true, 2),
        HOUSE("house", "burnt-out house", 3, false, true, 2),
        WATCHTOWER("watchtower", "broken watchtower", 2, false, true, 2),
        CHAOS_ALTAR("chaos_altar", "altar of Zamorak", 1, false, true, 2);

        final String file;
        final String name;
        final int weight;
        final boolean big;
        /** Whether it comes with cobwebs too, for red spiders. */
        final boolean webbed;
        /** How many variants tools/structures/wilderness.py draws of it. */
        final int variants;

        Design(String file, String name, int weight, boolean big, boolean webbed, int variants) {
            this.file = file;
            this.name = name;
            this.weight = weight;
            this.big = big;
            this.webbed = webbed;
            this.variants = variants;
        }
    }

    /** The bandit camp's pieces and how many variants of each wilderness.py draws. */
    private static final int CAMP_VARIANTS = 2;
    private static final int CAMP_TENTS = 6;
    /** The square, from a block in from the chunk's corner, that a bandit camp is laid out in. */
    private static final int CAMP_SPAN = 30;
    /** Blocks kept clear between a ruin and a lake, pond or old fortress. */
    private static final int FEATURE_SPACING = 6;
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
     * Lays out ruins as the world is generated, one site in about every {@link Config#ruinEvery} blocks square.
     * Everything stays inside the chunk and the ones east and south of it, which are already there.
     */
    static void populate(Game game, WorldServer world, int chunkX, int chunkZ, long seed) {
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        Forinthry layout = GameWorld.forinthry(game);
        if (!game.isInside(baseX + 16, baseZ + 16)) {
            return;
        }
        Random random = new Random(chunkX * 341873128712L + chunkZ * 132897987541L + seed + 2);
        if (random.nextDouble() >= 256.0 / ((double) Config.ruinEvery * Config.ruinEvery)) {
            return;
        }
        Design design = pick(random);
        if (design == Design.BANDIT_CAMP) {
            banditCamp(game, world, layout, baseX + 1, baseZ + 1, random);
            return;
        }
        boolean webs = design.webbed && random.nextInt(100) < 35;
        String file = design.file + "_" + (1 + random.nextInt(design.variants)) + (webs ? "_webs" : "");
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
                || layout != null && layout.nearFeature(minX, minZ, sizeX, sizeZ, FEATURE_SPACING)
                || nearStart(game, layout, minX + sizeX / 2.0, minZ + sizeZ / 2.0)) {
                continue;
            }
            Buildings.Site site = Buildings.survey(world, minX, minZ, sizeX, sizeZ);
            if (fits(site, schematic)) {
                Ruin ruin = game.addRuin(minX, minZ, sizeX, sizeZ, site.groundY, design.name, haunt);
                schematic.paste(world, minX, minZ, site.groundY, rotation);
                WebMap.blocksChanged(world, minX - 1, minZ - 1, minX + sizeX, minZ + sizeZ);
                RuinLoot.stock(world, ruin, new Random(random.nextLong()));
                return;
            }
        }
    }

    /**
     * A bandits' camp, laid out piece by piece: the fire in the middle, tents round it facing it, a lookout out toward
     * the edge, a cage and their stash, each set on the ground where it lands so the camp follows the lie of the land.
     * Pieces that would fall on water or a steep slope are left out; the camp is marked as one ruin round them all.
     */
    private static void banditCamp(Game game, WorldServer world, Forinthry layout, int minX, int minZ, Random random) {
        if (!Ruins.clear(game, minX, minZ, CAMP_SPAN, CAMP_SPAN)
            || layout != null && layout.nearFeature(minX, minZ, CAMP_SPAN, CAMP_SPAN, FEATURE_SPACING)
            || nearStart(game, layout, minX + CAMP_SPAN / 2.0, minZ + CAMP_SPAN / 2.0)) {
            return;
        }
        int centerX = minX + CAMP_SPAN / 2 + random.nextInt(5) - 2;
        int centerZ = minZ + CAMP_SPAN / 2 + random.nextInt(5) - 2;
        List<String> pieces = new ArrayList<>();
        int tents = 2 + random.nextInt(3);
        for (int i = 0; i < tents; i++) {
            pieces.add("camp_tent_" + (1 + random.nextInt(CAMP_TENTS)));
        }
        pieces.add("camp_lookout_" + (1 + random.nextInt(CAMP_VARIANTS)));
        pieces.add("camp_stash_" + (1 + random.nextInt(CAMP_VARIANTS)));
        if (random.nextInt(3) > 0) {
            pieces.add("camp_cage_" + (1 + random.nextInt(CAMP_VARIANTS)));
        }
        if (random.nextBoolean()) {
            pieces.add("camp_stash_" + (1 + random.nextInt(CAMP_VARIANTS)));
        }
        List<int[]> placed = new ArrayList<>();
        List<Schematic> schematics = new ArrayList<>();
        Schematic fire = schematic("camp_fire_" + (1 + random.nextInt(CAMP_VARIANTS)));
        int[] firePlace = { centerX - fire.width / 2, centerZ - fire.length / 2, fire.width, fire.length, 0, 0 };
        Buildings.Site fireSite = Buildings.survey(world, firePlace[0], firePlace[1], fire.width, fire.length);
        if (!fits(fireSite, fire)) {
            return;
        }
        firePlace[5] = fireSite.groundY;
        placed.add(firePlace);
        schematics.add(fire);
        double turn = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < pieces.size(); i++) {
            Schematic piece = schematic(pieces.get(i));
            boolean lookout = pieces.get(i)
                .startsWith("camp_lookout");
            for (int tries = 0; tries < 12; tries++) {
                // Spread round the fire, each a little off its share of the circle.
                double angle = turn + (i + random.nextDouble() * 0.8) * Math.PI * 2 / pieces.size()
                    + (tries > 0 ? random.nextDouble() * Math.PI : 0);
                double radius = (lookout ? 10 : 6) + random.nextDouble() * 4;
                double dx = Math.cos(angle);
                double dz = Math.sin(angle);
                // Its front, z = 0 at rotation 0, toward the fire: rotation 0 faces north, then clockwise.
                int rotation = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 3 : 1) : (dz > 0 ? 0 : 2);
                int sizeX = piece.sizeX(rotation);
                int sizeZ = piece.sizeZ(rotation);
                int x = (int) Math.round(centerX + dx * radius - sizeX / 2.0);
                int z = (int) Math.round(centerZ + dz * radius - sizeZ / 2.0);
                if (x < minX || z < minZ
                    || x + sizeX > minX + CAMP_SPAN
                    || z + sizeZ > minZ + CAMP_SPAN
                    || overlaps(placed, x, z, sizeX, sizeZ)) {
                    continue;
                }
                Buildings.Site site = Buildings.survey(world, x, z, sizeX, sizeZ);
                // Near the fire's level, so the camp holds together and its chests are found.
                if (fits(site, piece) && Math.abs(site.groundY - fireSite.groundY) <= MAX_SLOPE) {
                    placed.add(new int[] { x, z, sizeX, sizeZ, rotation, site.groundY });
                    schematics.add(piece);
                    break;
                }
            }
        }
        if (placed.size() < 4) {
            return;
        }
        int x0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (int[] place : placed) {
            x0 = Math.min(x0, place[0]);
            z0 = Math.min(z0, place[1]);
            x1 = Math.max(x1, place[0] + place[2]);
            z1 = Math.max(z1, place[1] + place[3]);
        }
        Ruin ruin = game.addRuin(x0, z0, x1 - x0, z1 - z0, fireSite.groundY, Design.BANDIT_CAMP.name, Ruin.BANDITS);
        for (int i = 0; i < placed.size(); i++) {
            int[] place = placed.get(i);
            schematics.get(i)
                .paste(world, place[0], place[1], place[5], place[4]);
        }
        WebMap.blocksChanged(world, x0 - 1, z0 - 1, x1, z1);
        RuinLoot.fillChests(world, ruin, new int[] { x0, z0, x1, z1 }, new Random(random.nextLong()));
    }

    private static boolean fits(Buildings.Site site, Schematic schematic) {
        return site.water == 0 && site.slope <= MAX_SLOPE
            && site.groundY > ChunkProviderRts.BANK
            && site.groundY + schematic.height < 255;
    }

    /** Whether a footprint comes within a block of one already placed (x, z, size x, size z first). */
    private static boolean overlaps(List<int[]> placed, int x, int z, int sizeX, int sizeZ) {
        for (int[] place : placed) {
            if (x < place[0] + place[2] + 1 && place[0] < x + sizeX + 1
                && z < place[1] + place[3] + 1
                && place[1] < z + sizeZ + 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * The part of an old fortress inside the chunks being filled in, its chests stocked, and the fortress marked as a
     * ruin the first time a part of it goes in.
     */
    static void oldFortress(Game game, WorldServer world, Forinthry.Keep keep, int groundY, int chunkX, int chunkZ,
        long seed) {
        int[] clip = { chunkX * 16 + 8, chunkZ * 16 + 8, chunkX * 16 + 24, chunkZ * 16 + 24 };
        Ruin ruin = null;
        for (Ruin other : game.getRuins()) {
            if (other.minX == keep.minX && other.minZ == keep.minZ && other.haunt == Ruin.FORTRESS) {
                ruin = other;
            }
        }
        if (ruin == null) {
            ruin = game.addRuin(
                keep.minX,
                keep.minZ,
                Forinthry.KEEP_SIZE,
                Forinthry.KEEP_SIZE,
                groundY,
                "old fortress",
                Ruin.FORTRESS);
        }
        schematic("old_fortress_" + keep.variant).paste(world, keep.minX, keep.minZ, groundY, keep.rotation, clip);
        WebMap.blocksChanged(world, clip[0] - 1, clip[1] - 1, clip[2], clip[3]);
        RuinLoot.fillChests(world, ruin, clip, new Random(seed * 17 + chunkX * 31L + chunkZ));
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
}
