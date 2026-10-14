package com.warofthegods.building;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

/**
 * The things on a map that belong to no team: settlements, where town centers stand, and gold mines, the only source
 * of gold. Once every team's first town center is up, free settlements are laid out between the teams, a gold mine
 * behind every settlement, and a few more mines out in the open, so each team has gold close to home and more worth
 * fighting over.
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

    /** Lays out the map's settlements and gold mines once every team has its first town center. */
    void placeIfDue(Game game, WorldServer world) {
        if (game.areFeaturesPlaced()) {
            return;
        }
        List<Team> teams = new java.util.ArrayList<>(game.getTeams());
        for (Team team : teams) {
            if (team.getBase() == null || team.isTownHallDue()) {
                return;
            }
        }
        int n = Math.max(2, teams.size());
        double radius = game.getSize() * 0.3;
        Schematic camp = Buildings.get()
            .schematic(BuildingType.TOWN_HALL);
        for (int k = 0; k < Config.extraSettlements; k++) {
            // Between the teams' bases, on the same circle.
            double angle = -Math.PI / 2 + Math.PI / n + 2 * Math.PI * k / Math.max(1, Config.extraSettlements);
            int x = (int) Math.round(game.getCenterX() + Math.cos(angle) * radius);
            int z = (int) Math.round(game.getCenterZ() + Math.sin(angle) * radius);
            int rotation = facingMiddle(game, x, z);
            int[] spot = findSpot(game, world, x, z, camp.sizeX(rotation), camp.sizeZ(rotation), 6, 64);
            if (spot != null) {
                Settlement settlement = game
                    .addSettlement(spot[0], spot[1], camp.sizeX(rotation), camp.sizeZ(rotation), spot[2], rotation);
                showSettlement(world, settlement);
            }
        }
        Schematic mine = schematic("gold_mine");
        for (Settlement settlement : new java.util.ArrayList<>(game.getSettlements())) {
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
        for (int k = 0; k < Config.extraGoldMines; k++) {
            double angle = Math.PI / 4 + 2 * Math.PI * k / Math.max(1, Config.extraGoldMines);
            int x = (int) Math.round(game.getCenterX() + Math.cos(angle) * game.getSize() * 0.15);
            int z = (int) Math.round(game.getCenterZ() + Math.sin(angle) * game.getSize() * 0.15);
            addMine(game, world, x, z, 48);
        }
        game.setFeaturesPlaced();
        WarOfTheGods.LOG.info(
            "Laid out {} settlements and {} gold mines",
            game.getSettlements()
                .size(),
            game.getMines()
                .size());
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
        return true;
    }

    /** Puts a free settlement's standing stones into the world. */
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

    /** Whether x, y, z is one of a gold mine's blocks. */
    public static GoldMine mineWithBlock(Game game, int x, int y, int z) {
        GoldMine mine = game.mineAt(x + 0.5, z + 0.5, 0);
        return mine != null && y >= mine.groundY - 1 && y <= mine.groundY + 6 ? mine : null;
    }

    /** Whether x, y, z is one of the standing stones of a settlement nobody holds. */
    public static Settlement freeSettlementWithBlock(Game game, int x, int y, int z) {
        Settlement settlement = game.freeSettlementNear(x + 0.5, z + 0.5, 0);
        return settlement != null && y >= settlement.groundY - 2 && y <= settlement.groundY + 8 ? settlement : null;
    }
}
