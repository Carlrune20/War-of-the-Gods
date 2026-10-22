package com.warofthegods.game;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumChatFormatting;

import com.warofthegods.Config;

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
    private Faction faction;
    private final EnumSet<Upgrade> upgrades = EnumSet.noneOf(Upgrade.class);
    /** The team's age, 1 to 3; 0 only while a save from before ages belonged to teams is read. */
    private int age = 1;
    /** Whether the team has had a finished town center; until then it cannot be defeated. */
    private boolean hadTownCenter;
    private boolean defeated;
    /** Ticks left before the team is defeated for having no town center, or -1 while it has one. */
    private int defeatTicks = -1;
    /** Whether the computer plays the team. */
    private boolean ai;
    /** Whether the computer, while it plays the team, only runs its economy with the team's workers. */
    private boolean aiEconomy;
    /** Whether the whole map has been shown to the team, as though explored, for testing. */
    private boolean revealed;
    /** Where Armadyl's eye watches for the team, until when, and when it can open again; not saved. */
    private double[] eye;
    private long eyeUntil;
    private long eyeReadyAt;

    Team(Game game, String name, EnumChatFormatting color) {
        this.game = game;
        this.name = name;
        this.color = color;
        this.faction = Faction.forColor(color);
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

    public Faction getFaction() {
        return faction;
    }

    /** Changes how the team's buildings and units look; buildings take on the new look as they next change. */
    public void setFaction(Faction faction) {
        this.faction = faction;
        game.changed();
    }

    public boolean has(Upgrade upgrade) {
        return upgrades.contains(upgrade);
    }

    public Collection<Upgrade> getUpgrades() {
        return Collections.unmodifiableSet(upgrades);
    }

    public void addUpgrade(Upgrade upgrade) {
        upgrades.add(upgrade);
        game.changed();
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = Math.max(1, Math.min(3, age));
        game.changed();
    }

    void setAgeQuietly(int age) {
        this.age = Math.max(1, Math.min(3, age));
    }

    public boolean hasHadTownCenter() {
        return hadTownCenter;
    }

    public void setHadTownCenter() {
        if (!hadTownCenter) {
            hadTownCenter = true;
            game.changed();
        }
    }

    public boolean isDefeated() {
        return defeated;
    }

    public void setDefeated() {
        defeated = true;
        defeatTicks = -1;
        game.changed();
    }

    /** Ticks left before the team is defeated for having no town center, or -1 when it is not counting down. */
    public int getDefeatTicks() {
        return defeatTicks;
    }

    public void setDefeatTicks(int ticks) {
        defeatTicks = ticks;
        game.changed();
    }

    public boolean isAi() {
        return ai;
    }

    public void setAi(boolean ai) {
        setAi(ai, false);
    }

    /**
     * Whether the computer only runs the team's economy: it commands all the team's workers, alongside the players,
     * and leaves the army and everything else to them. Only while it plays the team at all.
     */
    public boolean isAiEconomy() {
        return ai && aiEconomy;
    }

    /** Hands the team to the computer to play in full or only to run its economy, or takes it back. */
    public void setAi(boolean ai, boolean economyOnly) {
        this.ai = ai;
        this.aiEconomy = ai && economyOnly;
        game.changed();
    }

    /** "off", "economy" or "full": how much of the team the computer plays. */
    public String getAiMode() {
        return !ai ? "off" : aiEconomy ? "economy" : "full";
    }

    /** Whether the team has had the whole map revealed, so none of it is black in the fog of war. */
    public boolean isRevealed() {
        return revealed;
    }

    public void setRevealed(boolean revealed) {
        this.revealed = revealed;
        game.changed();
    }

    /** Where Armadyl's eye watches for the team at the world time, as x, z; null while it is closed. */
    public double[] getEye(long now) {
        return eye != null && now < eyeUntil ? eye : null;
    }

    /** The world time Armadyl's eye can open again for the team. */
    public long getEyeReadyAt() {
        return eyeReadyAt;
    }

    /** Opens Armadyl's eye over x, z for so many ticks, after which it stays shut for the rest of the cooldown. */
    public void openEye(double x, double z, long now, int ticks, int cooldown) {
        eye = new double[] { x, z };
        eyeUntil = now + ticks;
        eyeReadyAt = now + cooldown;
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

    /** Sets the team's amount of a resource; favor goes no higher than the most a team can hold. */
    public void setResource(Resource resource, long amount) {
        if (resource == Resource.FAVOR) {
            amount = Math.min(amount, Config.maxFavor);
        }
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
        tag.setString("faction", faction.id);
        NBTTagList upgradeList = new NBTTagList();
        for (Upgrade upgrade : upgrades) {
            upgradeList.appendTag(new NBTTagString(upgrade.id));
        }
        tag.setTag("upgrades", upgradeList);
        tag.setInteger("age", age);
        tag.setBoolean("hadTownCenter", hadTownCenter);
        tag.setBoolean("defeated", defeated);
        tag.setInteger("defeatTicks", defeatTicks);
        tag.setBoolean("ai", ai);
        tag.setBoolean("aiEconomy", aiEconomy);
        tag.setBoolean("revealed", revealed);
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
        if (!resourceTag.hasKey(Resource.GOLD.id)) {
            // Saved before Stone and Metal became Gold.
            team.resources.put(Resource.GOLD, resourceTag.getLong("stone") + resourceTag.getLong("metal"));
        }
        int[] base = tag.getIntArray("base");
        team.base = base.length == 2 ? base : null;
        team.townHallDue = tag.getBoolean("townHallDue");
        team.lastUnitNumber = tag.getInteger("lastUnitNumber");
        Faction faction = Faction.byId(tag.getString("faction"));
        if (faction != null) {
            team.faction = faction;
        }
        NBTTagList upgradeList = tag.getTagList("upgrades", 8);
        for (int i = 0; i < upgradeList.tagCount(); i++) {
            Upgrade upgrade = Upgrade.byId(upgradeList.getStringTagAt(i));
            if (upgrade != null) {
                team.upgrades.add(upgrade);
            }
        }
        team.age = tag.hasKey("age") ? tag.getInteger("age") : 0;
        team.hadTownCenter = tag.getBoolean("hadTownCenter");
        team.defeated = tag.getBoolean("defeated");
        team.defeatTicks = tag.hasKey("defeatTicks") ? tag.getInteger("defeatTicks") : -1;
        team.ai = tag.getBoolean("ai");
        team.aiEconomy = tag.getBoolean("aiEconomy");
        team.revealed = tag.getBoolean("revealed");
        return team;
    }
}
