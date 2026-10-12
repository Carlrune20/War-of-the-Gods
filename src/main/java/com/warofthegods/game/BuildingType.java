package com.warofthegods.game;

import java.util.LinkedHashMap;
import java.util.Map;

import com.warofthegods.Config;

/** The kinds of building. Ids are stored in save data, so only add new values. */
public enum BuildingType {

    /** Every team starts with one; trains workers and takes in what workers carry. Workers cannot build more. */
    TOWN_HALL("town_hall", "Town hall", "townHall", null, UnitKind.WORKER, false, true),
    /** Workers working at it gather food and wood. */
    FARM("farm", "Farm", "farm", "farm", null, true, false),
    /** Trains soldiers. */
    BARRACKS("barracks", "Barracks", "barracks", "barracks", UnitKind.SOLDIER, true, false),
    /** Takes in what workers carry, so they need not walk all the way to the town hall. */
    STOREHOUSE("storehouse", "Storehouse", "storehouse", "storehouse", null, true, true),
    /** Workers working at it gather stone and a little metal. */
    QUARRY("quarry", "Quarry", "quarry", "quarry", null, true, false);

    public final String id;
    public final String displayName;
    /** Name in the config file, such as farm in farmCostWood. */
    public final String configName;
    /** File name under assets/warofthegods/structures, without .schematic. */
    public final String schematic;
    /** The unit it trains, or null. */
    public final UnitKind trains;
    /** Whether workers can build it. */
    public final boolean buildable;
    /** Whether workers bring what they gather here. */
    public final boolean storesResources;

    BuildingType(String id, String displayName, String configName, String schematic, UnitKind trains, boolean buildable,
        boolean storesResources) {
        this.id = id;
        this.displayName = displayName;
        this.configName = configName;
        this.schematic = schematic == null ? id : schematic;
        this.trains = trains;
        this.buildable = buildable;
        this.storesResources = storesResources;
    }

    /** Whether workers can work here, gathering resources. */
    public boolean isWorkplace() {
        return this == FARM || this == QUARRY;
    }

    /** What building one costs, leaving out resources it costs none of. */
    public Map<Resource, Long> cost() {
        long[] amounts = Config.buildingCost.get(this);
        Map<Resource, Long> cost = new LinkedHashMap<>();
        for (Resource resource : Resource.values()) {
            if (amounts[resource.ordinal()] > 0) {
                cost.put(resource, amounts[resource.ordinal()]);
            }
        }
        return cost;
    }

    public int maxHealth() {
        return Config.buildingHealth.get(this);
    }

    /** Ticks of work one worker puts in to build it; two workers take half as long. */
    public int buildTicks() {
        return Math.max(1, Config.buildSeconds.get(this) * 20);
    }

    /** Resources one worker gathers here per harvest, by resource ordinal; all zero where nothing is gathered. */
    public long[] yield() {
        long[] amounts = new long[Resource.values().length];
        if (this == FARM) {
            amounts[Resource.FOOD.ordinal()] = Config.farmFood;
            amounts[Resource.WOOD.ordinal()] = Config.farmWood;
        } else if (this == QUARRY) {
            amounts[Resource.STONE.ordinal()] = Config.quarryStone;
            amounts[Resource.METAL.ordinal()] = Config.quarryMetal;
        }
        return amounts;
    }

    /** Most workers that can work here at once. */
    public int maxWorkers() {
        return this == FARM ? Config.farmWorkers : this == QUARRY ? Config.quarryWorkers : 0;
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
