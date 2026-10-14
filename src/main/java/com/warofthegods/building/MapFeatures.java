package com.warofthegods.building;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.world.WorldServer;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Game;
import com.warofthegods.game.GoldMine;
import com.warofthegods.game.Settlement;
import com.warofthegods.game.Team;
import com.warofthegods.web.WebMap;
import com.warofthegods.world.ChunkProviderRts;
import com.warofthegods.world.GameWorld;
import com.warofthegods.world.Misthalin;

/**
 * The things on a map that belong to no team: the bridges over the river, settlements, where town centers stand, gold
 * mines, the only source of gold, and the animals that graze near each town center. Bridges are built as soon as the
 * game world is up. Once every team's first town center is up, the rest is laid out the same way round each team, so
 * no team starts better off.
 */
public final class MapFeatures {

    /** Blocks kept clear around a settlement or gold mine when laying out the next. */
    private static final int SPACING = 6;

    private final Map<String, Schematic> schematics = new HashMap<>();

    /** A neutral structure, such as settlement or gold_mine; loaded the first time. */
    public Schematic schematic(String name) {
        Schematic schematic = schematics.get(name);
        if (schematic == null) {
            schematic = Schematic.load("neutral/" + name);
            schematics.put(name, schematic);
        }
        return schematic;
    }

    /**
     * Lays out the map's towers, settlements, gold mines and herds once every team has its first town center. Each
     * team gets the same: four towers round its town center, settlements spread over its side of the map, turned
     * the same way round the middle of the map as the team is, a gold mine behind every settlement and one more,
     * chickens and livestock grazing near its town center and wild herds over its side of the map.
     */
    void placeIfDue(Game game, WorldServer world) {
        if (game.areFeaturesPlaced()) {
            return;
        }
        List<Team> teams = new ArrayList<>(game.getTeams());
        for (Team team : teams) {
            if (team.getBase() == null || team.isTownHallDue()) {
                return;
            }
        }
        for (Team team : teams) {
            Buildings.get()
                .raiseStartingTowers(game, world, team);
        }
        int n = Math.max(2, teams.size());
        Schematic camp = Buildings.get()
            .schematic(BuildingType.TOWN_HALL);
        int settlements = settlementsPerTeam(game);
        double sector = 2 * Math.PI / n;
        for (int i = 0; i < teams.size(); i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / n;
            for (int k = 0; k < settlements; k++) {
                // Spread over the team's side of the map: far round to either side out near the edge, and nearer
                // the middle in between, so every team has the same spread on its side.
                double t = settlements == 1 ? 0.6 : -1 + 2.0 * k / (settlements - 1);
                double out = Math.abs(t) > 0.5 ? 0.36 - 0.04 * (k % 2) : 0.15 + 0.04 * (k % 2);
                int[] spot = around(game, angle + t * 0.42 * sector, out);
                int rotation = facingMiddle(game, spot[0], spot[1]);
                int[] found = findSpot(
                    game,
                    world,
                    spot[0],
                    spot[1],
                    camp.sizeX(rotation),
                    camp.sizeZ(rotation),
                    6,
                    64);
                if (found != null) {
                    Settlement settlement = game.addSettlement(
                        found[0],
                        found[1],
                        camp.sizeX(rotation),
                        camp.sizeZ(rotation),
                        found[2],
                        rotation);
                    Buildings.clearTrees(
                        game,
                        world,
                        settlement.minX,
                        settlement.minZ,
                        settlement.sizeX,
                        settlement.sizeZ,
                        settlement.groundY);
                    showSettlement(world, settlement);
                }
            }
        }
        Schematic mine = schematic("gold_mine");
        for (Settlement settlement : new ArrayList<>(game.getSettlements())) {
            // Behind the settlement, away from the middle of the map.
            double dx = settlement.getCenterX() - game.getCenterX();
            double dz = settlement.getCenterZ() - game.getCenterZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1) {
                dx = 1;
                length = 1;
            }
            double out = Math.max(settlement.sizeX, settlement.sizeZ) / 2.0 + SPACING + mine.width / 2.0 + 2;
            int x = (int) Math.round(settlement.getCenterX() + dx / length * out);
            int z = (int) Math.round(settlement.getCenterZ() + dz / length * out);
            addMine(game, world, x, z, 32);
        }
        for (int i = 0; i < teams.size(); i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / n;
            for (int k = 0; k < Config.extraGoldMines; k++) {
                // Between the team and the middle, off to the side its extra settlement is not on.
                double turn = (k % 2 == 0 ? -1 : 1) * Math.PI / 4.5 * (1 + k / 2) / Math.max(1, n / 2.0);
                int[] spot = around(game, angle + turn, 0.2);
                addMine(game, world, spot[0], spot[1], 48);
            }
        }
        for (Team team : teams) {
            placeHerds(game, world, team);
        }
        for (int i = 0; i < teams.size(); i++) {
            placeWildHerds(game, world, -Math.PI / 2 + 2 * Math.PI * i / n, sector);
        }
        game.setFeaturesPlaced();
        WarOfTheGods.LOG.info(
            "Laid out {} settlements and {} gold mines",
            game.getSettlements()
                .size(),
            game.getMines()
                .size());
    }

    /** Settlements laid out for each team besides the one it starts on, by the size of the map. */
    static int settlementsPerTeam(Game game) {
        return Math.max(1, (int) Math.round(game.getSize() / (double) Config.settlementEvery));
    }

    /**
     * Herds of chickens, pigs, cows and sheep spread over a team's side of the map, the same on every side: more on
     * bigger maps.
     */
    private static void placeWildHerds(Game game, WorldServer world, double angle, double sector) {
        int herds = Math.max(1, (int) Math.round(game.getSize() / (double) Config.herdEvery));
        long seed = game.getSeed();
        for (int k = 0; k < herds; k++) {
            double t = -1 + 2.0 * (k + 0.5) / herds;
            // From near the middle out toward the edge, by the golden ratio so they do not line up.
            double out = 0.08 + 0.32 * ((k * 0.618034 + 0.3) % 1);
            int[] spot = around(game, angle + t * 0.45 * sector, out);
            int kind = (int) Math.floorMod(seed + k, 4L) - 1;
            herd(world, spot[0], spot[1], kind, kind < 0 ? 5 : 4);
        }
    }

    /** The spot at an angle round the middle of the map, a fraction of the map's size out. */
    private static int[] around(Game game, double angle, double fraction) {
        return new int[] { (int) Math.round(game.getCenterX() + Math.cos(angle) * game.getSize() * fraction),
            (int) Math.round(game.getCenterZ() + Math.sin(angle) * game.getSize() * fraction) };
    }

    /**
     * A flock of chickens to one side of the team's town center and a herd of livestock to the other, the same for
     * every team: pigs, cows or sheep, picked by the map's seed.
     */
    private static void placeHerds(Game game, WorldServer world, Team team) {
        Building center = game.getTownHall(team);
        if (center == null) {
            return;
        }
        double cx = center.minX + center.sizeX / 2.0;
        double cz = center.minZ + center.sizeZ / 2.0;
        double dx = cx - game.getCenterX();
        double dz = cz - game.getCenterZ();
        double length = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        // Across the line from the middle of the map, and a little out from the town center.
        double sideX = -dz / length;
        double sideZ = dx / length;
        double reach = Math.max(center.sizeX, center.sizeZ) / 2.0 + 12;
        int livestock = (int) Math.floorMod(game.getSeed(), 3L);
        herd(world, cx + sideX * reach, cz + sideZ * reach, -1, Config.startingChickens);
        herd(world, cx - sideX * reach, cz - sideZ * reach, livestock, Config.startingLivestock);
    }

    /** Animals of a kind round x, z: -1 chickens, 0 pigs, 1 cows, 2 sheep. */
    private static void herd(WorldServer world, double x, double z, int kind, int count) {
        int[] ground = GameWorld.findDryGround(world, (int) Math.floor(x), (int) Math.floor(z), 16);
        for (int i = 0; i < count; i++) {
            EntityAnimal animal = kind < 0 ? new EntityChicken(world)
                : kind == 0 ? new EntityPig(world) : kind == 1 ? new EntityCow(world) : new EntitySheep(world);
            if (animal instanceof EntitySheep) {
                ((EntitySheep) animal).setFleeceColor(EntitySheep.getRandomFleeceColor(world.rand));
            }
            int ax = ground[0] + world.rand.nextInt(5) - 2;
            int az = ground[2] + world.rand.nextInt(5) - 2;
            animal.setLocationAndAngles(
                ax + 0.5,
                world.getTopSolidOrLiquidBlock(ax, az),
                az + 0.5,
                world.rand.nextFloat() * 360,
                0);
            world.spawnEntityInWorld(animal);
        }
    }

    private void addMine(Game game, WorldServer world, int x, int z, int search) {
        Schematic schematic = schematic("gold_mine");
        int[] spot = findSpot(game, world, x, z, schematic.width, schematic.length, 8, search);
        if (spot == null) {
            return;
        }
        GoldMine mine = game.addMine(spot[0], spot[1], schematic.width, spot[2], Config.goldMineGold);
        paste(world, schematic, mine.minX, mine.minZ, mine.size, mine.size, mine.groundY, 0);
    }

    /** The quarter turn that faces a spot toward the middle of the map. */
    static int facingMiddle(Game game, int x, int z) {
        int dx = game.getCenterX() - x;
        int dz = game.getCenterZ() - z;
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
    }

    /**
     * Looks for a spot near x, z for a footprint: inside the map, clear of buildings, settlements and gold mines, dry
     * and no steeper than maxSlope. Returns its corner and ground y as minX, minZ, groundY, or null.
     */
    static int[] findSpot(Game game, WorldServer world, int x, int z, int sizeX, int sizeZ, int maxSlope,
        int maxDistance) {
        for (int ring = 0; ring <= maxDistance; ring += 4) {
            int steps = ring == 0 ? 1 : 16;
            for (int step = 0; step < steps; step++) {
                double angle = 2 * Math.PI * step / steps;
                int minX = x + (int) Math.round(Math.cos(angle) * ring) - sizeX / 2;
                int minZ = z + (int) Math.round(Math.sin(angle) * ring) - sizeZ / 2;
                if (!clear(game, minX, minZ, sizeX, sizeZ)) {
                    continue;
                }
                Buildings.Site site = Buildings.survey(world, minX, minZ, sizeX, sizeZ);
                if (site.water == 0 && site.slope <= maxSlope && site.groundY > 2) {
                    return new int[] { minX, minZ, site.groundY };
                }
            }
        }
        return null;
    }

    private static boolean clear(Game game, int minX, int minZ, int sizeX, int sizeZ) {
        int edge = 4;
        if (minX < game.getMinX() + edge || minZ < game.getMinZ() + edge
            || minX + sizeX - 1 > game.getMaxX() - edge
            || minZ + sizeZ - 1 > game.getMaxZ() - edge) {
            return false;
        }
        for (Building building : game.getBuildings()) {
            if (building.overlaps(minX, minZ, sizeX, sizeZ, SPACING)) {
                return false;
            }
        }
        for (Settlement settlement : game.getSettlements()) {
            if (settlement.overlaps(minX, minZ, sizeX, sizeZ, SPACING)) {
                return false;
            }
        }
        for (GoldMine mine : game.getMines()) {
            if (mine.overlaps(minX, minZ, sizeX, sizeZ, SPACING)) {
                return false;
            }
        }
        return !bridgeNear(game, minX, minZ, sizeX, sizeZ, SPACING + 4);
    }

    /** Whether a footprint comes within margin blocks of a bridge. */
    public static boolean bridgeNear(Game game, int minX, int minZ, int sizeX, int sizeZ, int margin) {
        Misthalin layout = GameWorld.layout(game);
        if (layout == null) {
            return false;
        }
        for (Misthalin.Bridge bridge : layout.getBridges()) {
            if (minX < bridge.minX + bridge.sizeX + margin && bridge.minX < minX + sizeX + margin
                && minZ < bridge.minZ + bridge.sizeZ + margin
                && bridge.minZ < minZ + sizeZ + margin) {
                return true;
            }
        }
        return false;
    }

    /** Puts an abandoned settlement into the world. */
    public void showSettlement(WorldServer world, Settlement settlement) {
        paste(
            world,
            schematic("settlement"),
            settlement.minX,
            settlement.minZ,
            settlement.sizeX,
            settlement.sizeZ,
            settlement.groundY,
            settlement.rotation);
    }

    /** A mined out gold mine is left as bare rock. */
    public void deplete(WorldServer world, GoldMine mine) {
        paste(world, schematic("gold_mine_depleted"), mine.minX, mine.minZ, mine.size, mine.size, mine.groundY, 0);
        WarOfTheGods.LOG.info("Gold mine #{} is mined out", mine.id);
    }

    private static void paste(WorldServer world, Schematic schematic, int minX, int minZ, int sizeX, int sizeZ,
        int groundY, int rotation) {
        schematic.paste(world, minX, minZ, groundY, rotation);
        WebMap.blocksChanged(world, minX - 1, minZ - 1, minX + sizeX, minZ + sizeZ);
    }

    /** What map feature x, y, z is a block of, as "a gold mine", "a settlement" or "a bridge"; null for none. */
    public static String featureWithBlock(Game game, int x, int y, int z) {
        if (mineWithBlock(game, x, y, z) != null) {
            return "a gold mine";
        }
        if (freeSettlementWithBlock(game, x, y, z) != null) {
            return "a settlement";
        }
        if (bridgeWithBlock(game, x, y, z) != null) {
            return "a bridge";
        }
        return null;
    }

    /** Whether x, y, z is one of a bridge's blocks, down to the river bed. */
    public static Misthalin.Bridge bridgeWithBlock(Game game, int x, int y, int z) {
        Misthalin layout = game.areBridgesPlaced() ? GameWorld.layout(game) : null;
        Misthalin.Bridge bridge = layout == null ? null : layout.bridgeAt(x, z);
        return bridge != null && y >= ChunkProviderRts.BANK - 3 && y <= ChunkProviderRts.BANK + 3 ? bridge : null;
    }

    /** Builds the bridges over the river once the game world is up; they cannot be broken. */
    void placeBridgesIfDue(Game game, WorldServer world) {
        if (game.areBridgesPlaced()) {
            return;
        }
        Misthalin layout = GameWorld.layout(game);
        if (layout == null) {
            return;
        }
        Schematic schematic = schematic("bridge");
        for (Misthalin.Bridge bridge : layout.getBridges()) {
            paste(
                world,
                schematic,
                bridge.minX,
                bridge.minZ,
                bridge.sizeX,
                bridge.sizeZ,
                ChunkProviderRts.BANK,
                bridge.rotation);
        }
        game.setBridgesPlaced();
        WarOfTheGods.LOG.info(
            "Built {} bridges over the river",
            layout.getBridges()
                .size());
    }

    /** Whether x, y, z is one of a gold mine's blocks. */
    public static GoldMine mineWithBlock(Game game, int x, int y, int z) {
        GoldMine mine = game.mineAt(x + 0.5, z + 0.5, 0);
        return mine != null && y >= mine.groundY - 1 && y <= mine.groundY + 6 ? mine : null;
    }

    /** Whether x, y, z is one of the blocks of a settlement nobody holds. */
    public static Settlement freeSettlementWithBlock(Game game, int x, int y, int z) {
        Settlement settlement = game.freeSettlementNear(x + 0.5, z + 0.5, 0);
        return settlement != null && y >= settlement.groundY - 2 && y <= settlement.groundY + 8 ? settlement : null;
    }
}
