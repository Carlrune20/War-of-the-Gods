package com.warofthegods.game;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.warofthegods.Config;

/**
 * What a mining site is mostly made of, named as in Old School RuneScape with Minecraft's ores standing in: emerald
 * for adamantite and diamond for runite. Whatever its rocks, a site yields gold to the team that mines it; the rarer
 * its best ore, the more. Saves keep kinds by id, so only append new values.
 */
public enum MineKind {

    COAL("coal", "Coal mine", Blocks.coal_ore, 0.6, 30),
    IRON("iron", "Iron mine", Blocks.iron_ore, 0.8, 28),
    GOLD("gold", "Gold mine", Blocks.gold_ore, 1.0, 22),
    ADAMANTITE("adamantite", "Adamantite mine", Blocks.emerald_ore, 1.3, 12),
    RUNITE("runite", "Runite mine", Blocks.diamond_ore, 1.6, 8);

    public final String id;
    public final String displayName;
    /** The ore its best rocks show. */
    public final Block ore;
    /** Gold it holds, as a share of {@link Config#goldMineGold}. */
    private final double richness;
    /** How often a site of this kind is laid out, against the others. */
    public final int weight;

    MineKind(String id, String displayName, Block ore, double richness, int weight) {
        this.id = id;
        this.displayName = displayName;
        this.ore = ore;
        this.richness = richness;
        this.weight = weight;
    }

    /** Gold a new site of this kind holds. */
    public int gold() {
        return Math.max(1, (int) Math.round(Config.goldMineGold * richness));
    }

    /** The ores of its lesser rocks, which stand among the best ones as in a mine of mixed rocks. */
    public Block[] lesserOres() {
        switch (this) {
            case COAL:
                return new Block[] { Blocks.coal_ore, Blocks.redstone_ore };
            case IRON:
                return new Block[] { Blocks.coal_ore, Blocks.redstone_ore };
            case GOLD:
                return new Block[] { Blocks.iron_ore, Blocks.redstone_ore };
            case ADAMANTITE:
                return new Block[] { Blocks.coal_ore, Blocks.iron_ore };
            default:
                return new Block[] { Blocks.coal_ore, Blocks.iron_ore, Blocks.emerald_ore };
        }
    }

    /** A kind picked by how common each is, from a roll of 0 up to the sum of the weights. */
    public static MineKind byRoll(int roll) {
        for (MineKind kind : values()) {
            roll -= kind.weight;
            if (roll < 0) {
                return kind;
            }
        }
        return COAL;
    }

    public static int totalWeight() {
        int total = 0;
        for (MineKind kind : values()) {
            total += kind.weight;
        }
        return total;
    }

    public static MineKind byId(String id) {
        for (MineKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return GOLD;
    }
}
