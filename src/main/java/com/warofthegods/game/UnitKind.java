package com.warofthegods.game;

import java.util.LinkedHashMap;
import java.util.Map;

import com.warofthegods.Config;

/** The kinds of unit a team can train. Names are stored in save data, so only add new values. */
public enum UnitKind {

    /** Fights; trained at a barracks. */
    SOLDIER("soldier", "Soldier"),
    /** Builds and farms, never fights; trained at the town hall. */
    WORKER("worker", "Worker");

    public final String id;
    public final String displayName;

    UnitKind(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** What training one costs, leaving out resources it costs none of. */
    public Map<Resource, Long> cost() {
        long[] amounts = this == WORKER ? Config.workerCost : Config.unitCost;
        Map<Resource, Long> cost = new LinkedHashMap<>();
        for (Resource resource : Resource.values()) {
            if (amounts[resource.ordinal()] > 0) {
                cost.put(resource, amounts[resource.ordinal()]);
            }
        }
        return cost;
    }

    public static UnitKind byId(String id) {
        for (UnitKind kind : values()) {
            if (kind.id.equalsIgnoreCase(id) || kind.name()
                .equalsIgnoreCase(id)) {
                return kind;
            }
        }
        return null;
    }
}
