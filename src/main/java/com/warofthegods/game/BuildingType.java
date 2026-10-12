package com.warofthegods.game;

import java.util.LinkedHashMap;
import java.util.Map;

import com.warofthegods.Config;

/** The kinds of building. Ids are stored in save data, so only add new values. */
public enum BuildingType {

    /** Every team starts with one; trains workers. Workers cannot build more. */
    TOWN_HALL("town_hall", "Town hall", "town_hall", UnitKind.WORKER, false),
    /** Yields food and wood while workers work at it. */
    FARM("farm", "Farm", "farm", null, true),
    /** Trains soldiers. */
    BARRACKS("barracks", "Barracks", "barracks", UnitKind.SOLDIER, true);

    public final String id;
    public final String displayName;
    /** File name under assets/warofthegods/structures, without .schematic. */
    public final String schematic;
    /** The unit it trains, or null. */
    public final UnitKind trains;
    /** Whether workers can build it. */
    public final boolean buildable;

    BuildingType(String id, String displayName, String schematic, UnitKind trains, boolean buildable) {
        this.id = id;
        this.displayName = displayName;
        this.schematic = schematic;
        this.trains = trains;
        this.buildable = buildable;
    }

    /** What building one costs, leaving out resources it costs none of. */
    public Map<Resource, Long> cost() {
        long[] amounts = this == FARM ? Config.farmCost : this == BARRACKS ? Config.barracksCost : new long[4];
        Map<Resource, Long> cost = new LinkedHashMap<>();
        for (Resource resource : Resource.values()) {
            if (amounts[resource.ordinal()] > 0) {
                cost.put(resource, amounts[resource.ordinal()]);
            }
        }
        return cost;
    }

    public static BuildingType byId(String id) {
        for (BuildingType type : values()) {
            if (type.id.equalsIgnoreCase(id) || type.name()
                .equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
