package com.warofthegods.building;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;

import com.warofthegods.game.Building;
import com.warofthegods.game.Faction;
import com.warofthegods.game.Game;
import com.warofthegods.game.Team;
import com.warofthegods.web.WebMap;

/**
 * The bridges workers build, a piece at a time: each piece a block long and three wide, a deck of planks with a rail
 * either side, standing on posts that go down to the bottom of the water under every other piece. Laid in the world
 * here rather than from a structure file, as how far down the posts go depends on the water under each piece.
 *
 * <p>
 * The deck lies a block over the water, level with the banks, or in the ground where a piece is on land. A site
 * shows its posts first, then a plank along the middle, then the whole deck; the rails go on last. A damaged bridge
 * loses rails, then planks at the edges, but its middle stays walkable until it is gone.
 */
final class Bridges {

    /** How far down the posts go at most. */
    private static final int MAX_DEPTH = 24;

    private Bridges() {}

    /** The blocks across a piece, as x, z: the middle one second. */
    private static int[][] cells(Building piece) {
        int[][] cells = new int[3][];
        for (int i = 0; i < 3; i++) {
            cells[i] = (piece.rotation & 1) == 0 ? new int[] { piece.minX, piece.minZ + i }
                : new int[] { piece.minX + i, piece.minZ };
        }
        return cells;
    }

    /** Whether the piece stands on posts: every other one along the bridge. */
    private static boolean hasPosts(Building piece) {
        return ((piece.rotation & 1) == 0 ? piece.minX : piece.minZ) % 2 == 0;
    }

    /** Planks of the team's god: oak for Saradomin, spruce for Zamorak. */
    private static int plankMeta(Game game, Building piece) {
        Team team = game.getTeam(piece.team);
        return team != null && team.getFaction() == Faction.ZAMORAK ? 1 : 0;
    }

    /** Whether a post may go down through the block: water, air or plants, nothing solid. */
    private static boolean open(Block block) {
        Material material = block.getMaterial();
        return material.isLiquid() || material == Material.air
            || material == Material.plants
            || material == Material.vine
            || block == Blocks.fence;
    }

    /** Puts the piece's look into the world, as it is built and as it is damaged. */
    static void show(WorldServer world, Game game, Building piece) {
        int y = piece.groundY;
        int meta = plankMeta(game, piece);
        int stage = piece.isComplete() ? 4 : Math.min(3, 1 + (int) (piece.getBuildProgress() * 3));
        double health = piece.getHealth() / (double) piece.getMaxHealth();
        int damage = !piece.isComplete() ? 0 : health < 0.25 ? 3 : health < 0.5 ? 2 : health < 0.75 ? 1 : 0;
        int[][] cells = cells(piece);
        // Which side loses its rail and edge plank first, so a damaged bridge looks broken rather than trimmed.
        int worse = ((piece.minX * 31 + piece.minZ * 17) & 1) == 0 ? 0 : 2;
        for (int i = 0; i < 3; i++) {
            int x = cells[i][0];
            int z = cells[i][1];
            boolean edge = i != 1;
            if (edge && hasPosts(piece)) {
                for (int down = y - 1; down > y - 1 - MAX_DEPTH && down > 0; down--) {
                    Block block = world.getBlock(x, down, z);
                    if (!open(block)) {
                        break;
                    }
                    if (block != Blocks.fence) {
                        world.setBlock(x, down, z, Blocks.fence, 0, 2);
                    }
                }
            }
            boolean deck = stage >= 3 || stage == 2 && !edge;
            if (edge && (damage >= 3 || damage >= 2 && i == worse)) {
                deck = false;
            }
            if (deck) {
                world.setBlock(x, y, z, Blocks.planks, meta, 2);
            } else if (world.getBlock(x, y, z) == Blocks.planks) {
                world.setBlock(x, y, z, below(world, x, y, z), 0, 2);
            }
            boolean rail = edge && stage >= 4 && damage < 2 && !(damage == 1 && i == worse);
            if (rail) {
                world.setBlock(x, y + 1, z, Blocks.fence, 0, 2);
            } else if (!world.isAirBlock(x, y + 1, z)) {
                world.setBlock(x, y + 1, z, Blocks.air, 0, 2);
            }
            for (int up = y + 2; up <= y + 3 && up <= 255; up++) {
                if (!world.isAirBlock(x, up, z)) {
                    world.setBlock(x, up, z, Blocks.air, 0, 2);
                }
            }
        }
        WebMap.blocksChanged(world, piece.minX - 1, piece.minZ - 1, piece.getMaxX() + 1, piece.getMaxZ() + 1);
    }

    /**
     * What goes back where a plank of the deck was: grass on land, where the deck was laid into the ground, and air
     * over water.
     */
    private static Block below(WorldServer world, int x, int y, int z) {
        Block under = world.getBlock(x, y - 1, z);
        return under.getMaterial()
            .isSolid() && under != Blocks.fence ? Blocks.grass : Blocks.air;
    }

    /** Takes a destroyed piece away: its deck, rails and posts, the water coming back where the posts stood. */
    static void tearDown(WorldServer world, Building piece) {
        int y = piece.groundY;
        for (int[] cell : cells(piece)) {
            int x = cell[0];
            int z = cell[1];
            if (world.getBlock(x, y + 1, z) == Blocks.fence) {
                world.setBlock(x, y + 1, z, Blocks.air, 0, 2);
            }
            for (int down = y - 1; down > y - 1 - MAX_DEPTH && down > 0; down--) {
                if (world.getBlock(x, down, z) != Blocks.fence) {
                    break;
                }
                world.setBlock(x, down, z, waterBeside(world, x, down, z) ? Blocks.water : Blocks.air, 0, 2);
            }
            if (world.getBlock(x, y, z) == Blocks.planks) {
                world.setBlock(x, y, z, below(world, x, y, z), 0, 2);
            }
        }
        WebMap.blocksChanged(world, piece.minX - 1, piece.minZ - 1, piece.getMaxX() + 1, piece.getMaxZ() + 1);
    }

    private static boolean waterBeside(WorldServer world, int x, int y, int z) {
        return world.getBlock(x + 1, y, z)
            .getMaterial() == Material.water
            || world.getBlock(x - 1, y, z)
                .getMaterial() == Material.water
            || world.getBlock(x, y, z + 1)
                .getMaterial() == Material.water
            || world.getBlock(x, y, z - 1)
                .getMaterial() == Material.water
            || world.getBlock(x, y + 1, z)
                .getMaterial() == Material.water;
    }
}
