package com.warofthegods.game;

import java.util.LinkedHashMap;
import java.util.Map;

import com.warofthegods.Config;

/** The kinds of unit a team can train. Ids are stored in save data, so only add new values. */
public enum UnitKind {

    /** Fights up close with a sword; trained at a barracks. Saved as soldier, from before archers. */
    SOLDIER("soldier", "Warrior"),
    /** Builds, farms, mines gold and chops trees, never fights; trained at the town center. */
    WORKER("worker", "Worker"),
    /** Shoots arrows from a distance; trained at a barracks. */
    ARCHER("archer", "Archer");

    public final String id;
    public final String displayName;

    UnitKind(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** What training one costs, leaving out resources it costs none of. */
    public Map<Resource, Long> cost() {
        long[] amounts = this == WORKER ? Config.workerCost : this == ARCHER ? Config.archerCost : Config.unitCost;
        Map<Resource, Long> cost = new LinkedHashMap<>();
        for (Resource resource : Resource.values()) {
            if (amounts[resource.ordinal()] > 0) {
                cost.put(resource, amounts[resource.ordinal()]);
            }
        }
        return cost;
    }

    /** Whether it fights; workers do not. */
    public boolean fights() {
        return this != WORKER;
    }

    /** warrior is accepted for soldier, the name players see. */
    public static UnitKind byId(String id) {
        if ("warrior".equalsIgnoreCase(id) || "warriors".equalsIgnoreCase(id)) {
            return SOLDIER;
        }
        for (UnitKind kind : values()) {
            if (kind.id.equalsIgnoreCase(id) || kind.name()
                .equalsIgnoreCase(id)) {
                return kind;
            }
        }
        return null;
    }
}
