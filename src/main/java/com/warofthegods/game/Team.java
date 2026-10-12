package com.warofthegods.game;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;

public class Team {

    /** Colors a team may use, in the order new teams are given them. */
    public static final EnumChatFormatting[] COLORS = { EnumChatFormatting.RED, EnumChatFormatting.BLUE,
        EnumChatFormatting.GREEN, EnumChatFormatting.YELLOW, EnumChatFormatting.AQUA, EnumChatFormatting.LIGHT_PURPLE,
        EnumChatFormatting.GOLD, EnumChatFormatting.WHITE, EnumChatFormatting.DARK_RED, EnumChatFormatting.DARK_BLUE,
        EnumChatFormatting.DARK_GREEN, EnumChatFormatting.DARK_AQUA, EnumChatFormatting.DARK_PURPLE,
        EnumChatFormatting.GRAY, EnumChatFormatting.DARK_GRAY, EnumChatFormatting.BLACK };

    private final Game game;
    private final String name;
    private EnumChatFormatting color;
    /** Player UUID to last known player name. */
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private final EnumMap<Resource, Long> resources = new EnumMap<>(Resource.class);
    /** Where the team's units are trained, or null until one is chosen. */
    private int[] base;
    /** Whether a town hall is still to go up at the base; once it is destroyed, it stays gone. */
    private boolean townHallDue;
    private int lastUnitNumber;

    Team(Game game, String name, EnumChatFormatting color) {
        this.game = game;
        this.name = name;
        this.color = color;
        for (Resource resource : Resource.values()) {
            resources.put(resource, 0L);
        }
    }

    public String getName() {
        return name;
    }

    /** Team names are matched case-insensitively; this is the key used for lookups and alliances. */
    public String getId() {
        return idOf(name);
    }

    public EnumChatFormatting getColor() {
        return color;
    }

    public void setColor(EnumChatFormatting color) {
        this.color = color;
        game.changed();
    }

    public String getColoredName() {
        return color + name + EnumChatFormatting.RESET;
    }

    /** The x and z of the team's base, or null if it has none yet. */
    public int[] getBase() {
        return base == null ? null : base.clone();
    }

    /** Moves the base; a new town hall goes up there. */
    public void setBase(int x, int z) {
        base = new int[] { x, z };
        townHallDue = true;
        game.changed();
    }

    public boolean isTownHallDue() {
        return townHallDue;
    }

    public void setTownHallDue(boolean due) {
        townHallDue = due;
        game.changed();
    }

    /** Numbers units of the team 1, 2, 3... for their names. */
    public int nextUnitNumber() {
        lastUnitNumber++;
        game.changed();
        return lastUnitNumber;
    }

    /** The RGB color of the team's chat color. */
    public int getRgb() {
        return rgbOf(color);
    }

    public Map<UUID, String> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    void addMember(UUID player, String playerName) {
        members.put(player, playerName);
    }

    boolean removeMember(UUID player) {
        return members.remove(player) != null;
    }

    public long getResource(Resource resource) {
        return resources.get(resource);
    }

    public void setResource(Resource resource, long amount) {
        resources.put(resource, Math.max(0, amount));
        game.changed();
    }

    public void addResource(Resource resource, long amount) {
        setResource(resource, getResource(resource) + amount);
    }

    public boolean canAfford(Map<Resource, Long> cost) {
        for (Map.Entry<Resource, Long> entry : cost.entrySet()) {
            if (getResource(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Takes the whole cost if the team can afford it, otherwise takes nothing. */
    public boolean spend(Map<Resource, Long> cost) {
        if (!canAfford(cost)) {
            return false;
        }
        for (Map.Entry<Resource, Long> entry : cost.entrySet()) {
            resources.put(entry.getKey(), getResource(entry.getKey()) - entry.getValue());
        }
        game.changed();
        return true;
    }

    public static String idOf(String name) {
        return name.toLowerCase();
    }

    /** The RGB color chat shows for a formatting code. */
    public static int rgbOf(EnumChatFormatting color) {
        switch (color) {
            case BLACK:
                return 0x000000;
            case DARK_BLUE:
                return 0x0000AA;
            case DARK_GREEN:
                return 0x00AA00;
            case DARK_AQUA:
                return 0x00AAAA;
            case DARK_RED:
                return 0xAA0000;
            case DARK_PURPLE:
                return 0xAA00AA;
            case GOLD:
                return 0xFFAA00;
            case GRAY:
                return 0xAAAAAA;
            case DARK_GRAY:
                return 0x555555;
            case BLUE:
                return 0x5555FF;
            case GREEN:
                return 0x55FF55;
            case AQUA:
                return 0x55FFFF;
            case RED:
                return 0xFF5555;
            case LIGHT_PURPLE:
                return 0xFF55FF;
            case YELLOW:
                return 0xFFFF55;
            default:
                return 0xFFFFFF;
        }
    }

    public static EnumChatFormatting colorByName(String name) {
        for (EnumChatFormatting color : COLORS) {
            if (color.name()
                .equalsIgnoreCase(name)) {
                return color;
            }
        }
        return null;
    }

    NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("name", name);
        tag.setString("color", color.name());

        NBTTagList memberList = new NBTTagList();
        for (Map.Entry<UUID, String> member : members.entrySet()) {
            NBTTagCompound memberTag = new NBTTagCompound();
            memberTag.setLong(
                "uuidMost",
                member.getKey()
                    .getMostSignificantBits());
            memberTag.setLong(
                "uuidLeast",
                member.getKey()
                    .getLeastSignificantBits());
            memberTag.setString("name", member.getValue());
            memberList.appendTag(memberTag);
        }
        tag.setTag("members", memberList);

        NBTTagCompound resourceTag = new NBTTagCompound();
        for (Resource resource : Resource.values()) {
            resourceTag.setLong(resource.id, getResource(resource));
        }
        tag.setTag("resources", resourceTag);
        if (base != null) {
            tag.setIntArray("base", base);
        }
        tag.setBoolean("townHallDue", townHallDue);
        tag.setInteger("lastUnitNumber", lastUnitNumber);
        return tag;
    }

    static Team readFromNBT(Game game, NBTTagCompound tag) {
        EnumChatFormatting color = colorByName(tag.getString("color"));
        Team team = new Team(game, tag.getString("name"), color == null ? EnumChatFormatting.WHITE : color);

        NBTTagList memberList = tag.getTagList("members", 10);
        for (int i = 0; i < memberList.tagCount(); i++) {
            NBTTagCompound memberTag = memberList.getCompoundTagAt(i);
            UUID uuid = new UUID(memberTag.getLong("uuidMost"), memberTag.getLong("uuidLeast"));
            team.members.put(uuid, memberTag.getString("name"));
        }

        NBTTagCompound resourceTag = tag.getCompoundTag("resources");
        for (Resource resource : Resource.values()) {
            team.resources.put(resource, resourceTag.getLong(resource.id));
        }
        int[] base = tag.getIntArray("base");
        team.base = base.length == 2 ? base : null;
        team.townHallDue = tag.getBoolean("townHallDue");
        team.lastUnitNumber = tag.getInteger("lastUnitNumber");
        return team;
    }
}
