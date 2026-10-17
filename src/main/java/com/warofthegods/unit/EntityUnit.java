package com.warofthegods.unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.building.Buildings;
import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Faction;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.GoldMine;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Spell;
import com.warofthegods.game.Team;
import com.warofthegods.game.Tree;
import com.warofthegods.game.UnitKind;
import com.warofthegods.game.Upgrade;
import com.warofthegods.rts.Commanders;
import com.warofthegods.web.WebMap;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A unit of a team, with the player model in its team's skin. Soldiers carry a sword or a bow and follow orders from
 * the web map: walk somewhere, attack something, or stand guard, in which case they fight enemies and enemy buildings
 * that come close and walk back to their post afterwards. Workers never fight: they walk, build at building sites,
 * work farms, mine gold, chop trees and hunt animals, carrying what they gather to the nearest town hall or
 * storehouse whenever their hands are full. Swords and tools are stone in Age I, iron in Age II and diamond in Age
 * III; workers hold the tool for the job they are at, and what they carry only on the way to a store.
 *
 * <p>
 * Walking uses vanilla pathfinding in legs of at most {@link #LEG} blocks toward the goal, since vanilla paths only
 * reach a few dozen blocks. Long-range planning comes later.
 */
public class EntityUnit extends EntityCreature {

    public enum Order {
        IDLE,
        MOVE,
        ATTACK,
        /** A worker building at a building site, or on its way there. */
        BUILD,
        /** A worker working at a farm, or carrying what it gathered there to a store. */
        WORK,
        /** A soldier attacking an enemy building. */
        ATTACK_BUILDING,
        /** A worker chopping a tree, or carrying the wood to a store. */
        CHOP,
        /** A worker putting up torches. */
        LIGHT,
        /** A worker hunting an animal, carving food off its carcass, or carrying the food to a store. */
        HUNT,
        /** Keeping its place in formation behind a player. */
        FOLLOW,
        /** A worker mining a gold mine, or carrying the gold to a store. */
        MINE
    }

    /** How a unit fights when it has no orders to: how far it looks for enemies and how far it chases them. */
    public enum Stance {

        /** Looks further, and chases enemies far from its post. */
        AGGRESSIVE("aggressive", "Aggressive", 1.5, 40),
        /** Fights what comes near, and goes back to its post. */
        DEFENSIVE("defensive", "Defensive", 1, 20),
        /** Never leaves its spot: fights only what it can reach from there. */
        HOLD("hold", "Hold ground", 1, 0),
        /** Never fights unless ordered, not even back. */
        PASSIVE("passive", "Passive", 0, 0);

        public final String id;
        public final String displayName;
        final double rangeFactor;
        final double chaseRange;

        Stance(String id, String displayName, double rangeFactor, double chaseRange) {
            this.id = id;
            this.displayName = displayName;
            this.rangeFactor = rangeFactor;
            this.chaseRange = chaseRange;
        }

        public static Stance byId(String id) {
            for (Stance stance : values()) {
                if (stance.id.equalsIgnoreCase(id) || stance.name()
                    .equalsIgnoreCase(id)) {
                    return stance;
                }
            }
            return null;
        }
    }

    /** Blocks walked per path before planning the next one. */
    private static final double LEG = 24;
    /** How close a unit has to get to where it was sent. */
    private static final double ARRIVED = 1.5;
    /** Range at which guarding units notice enemies. */
    private static final double GUARD_RANGE = 10;
    /** Times in a row a unit may fail to make progress before it gives up on an order. */
    private static final int MAX_STALLS = 6;
    private static final double SPEED = 1.0;
    /**
     * Movement speed: a mob's walking pace goes with the square of it, and at this it walks as fast as a player,
     * about 4.3 blocks a second.
     */
    private static final double MOVEMENT_SPEED = 0.316;
    /** Ticks an archer draws its bow for, as long as a player takes to draw one fully. */
    private static final int DRAW_TICKS = 20;
    /** How much faster than a warrior a rider goes. */
    private static final double MOUNTED_SPEED = 1.5;
    /**
     * How wide a rider and its horse are. Wider than a man, but not so wide that it needs paths two blocks across;
     * the height stays a man's, so it fits under what a man does.
     */
    private static final float MOUNTED_WIDTH = 0.9F;
    /** How tall a centaur stands, to the top of its head. */
    private static final float CENTAUR_HEIGHT = 2.4F;
    /** Ticks a rider has to be on the move before arrows start to miss it, and how often they then miss. */
    private static final int MOUNTED_DODGE_TICKS = 20;
    private static final float MOUNTED_DODGE_CHANCE = 0.5F;
    /** What arrows that hit a rider do, and blows up close, as a share of their damage. */
    private static final float MOUNTED_ARROW_DAMAGE = 0.75F;
    private static final float MOUNTED_MELEE_DAMAGE = 1.25F;
    /** How much harder a Zamorak mage's fire strikes than a dark wizard's. */
    private static final int MAGE_EXTRA_DAMAGE = 4;
    /**
     * A lesser demon hurls a fireball between its blows every so often, at least and at most this many ticks apart:
     * the fireball's damage, how far it throws one and how long it takes to wind up.
     */
    private static final int FIREBALL_MIN_TICKS = 200;
    private static final int FIREBALL_MAX_TICKS = 300;
    private static final float FIREBALL_DAMAGE = 10;
    private static final double FIREBALL_RANGE = 16;
    private static final int FIREBALL_WINDUP = 12;
    /** Ticks an archer waits after a shot before drawing again. */
    private static final int ARCHER_REST = 10;
    /** Damage a worker's hit does to an animal it hunts. */
    private static final float HUNT_DAMAGE = 5;
    /** How far a worker looks for another animal once a carcass is eaten up. */
    private static final double NEXT_PREY_RANGE = 24;
    /** How close a worker has to be to a carcass to carve it. */
    private static final double CARVE_REACH = 1.8;
    /** How far a target may move off before archers stop heading for the spots they were given to shoot from. */
    private static final double SPOT_DRIFT = 6;
    /** How far from its post a guarding unit follows whoever hit it, unless its stance lets it chase further. */
    private static final double PURSUIT_RANGE = 48;

    /** Swords, axes, pickaxes and hoes: stone, then iron and diamond as the team researches them. */
    private static final Item[] SWORDS = { Items.stone_sword, Items.iron_sword, Items.diamond_sword };
    private static final Item[] AXES = { Items.stone_axe, Items.iron_axe, Items.diamond_axe };
    private static final Item[] PICKAXES = { Items.stone_pickaxe, Items.iron_pickaxe, Items.diamond_pickaxe };
    private static final Item[] HOES = { Items.stone_hoe, Items.iron_hoe, Items.diamond_hoe };
    /** How far archers shoot, before upgrades. */
    public static final double ARCHER_RANGE = 14;
    /** How far archers shoot at buildings. */
    private static final double ARCHER_BUILDING_RANGE = 9;
    /** How close a worker has to be to the foot of a tree to chop it: a player's reach. */
    private static final double CHOP_REACH = 4.5;
    /** How close to the foot of a tree a worker touching its leaves can chop it from. */
    private static final double LEAVES_REACH = 8;
    /** How close a worker has to be to where a torch goes. */
    private static final double TORCH_REACH = 2.5;
    /** How close a worker has to be to a gold mine's rock to mine it. */
    private static final double MINE_REACH = 2.5;
    /** How far a worker looks for the next gold mine once one is mined out. */
    private static final int NEXT_MINE_RANGE = 64;
    /** How far round a storehouse it has just put up a worker looks for a tree or gold mine to work. */
    private static final int STORE_GATHER_RANGE = 32;
    /** How far a worker looks for the next tree once one falls. */
    private static final int NEXT_TREE_RANGE = 14;

    private static final int WATCH_COLOR = 20;
    private static final int WATCH_KIND = 21;
    private static final int WATCH_FACTION = 22;
    private static final int WATCH_DRAWING = 23;
    private static final int WATCH_PRAYER = 24;
    /** Whether its team has made it elite, for its look; well clear of the ids above for others to add to. */
    private static final int WATCH_ELITE = 29;
    /** Health and damage an Elite Black Knight or a Justiciar has over a plain knight. */
    private static final int ELITE_HEALTH = 20;
    private static final int ELITE_DAMAGE = 3;
    /** How much harder a hero hits a myth unit. */
    private static final float HERO_VS_MYTH_DAMAGE = 2.0F;
    /** Ticks the computer waits after switching a hero's prayer before it switches again. */
    private static final int AI_PRAYER_SWITCH_TICKS = 100;

    private String teamId = "";
    private UnitKind kind = UnitKind.SOLDIER;
    private int number;
    /** The foot of the trunk of the tree a worker chops. */
    private int treeX;
    private int treeY;
    private int treeZ;
    private int chopTicks;
    /** The gold mine a worker mines. */
    private int mineId;
    private int mineTicks;
    /** Where a worker still has to put torches, in order: x, y, z each. */
    private final List<int[]> torches = new ArrayList<>();
    private Order order = Order.IDLE;
    private Stance stance = Stance.DEFENSIVE;
    /** The protection prayer it has up, if its kind can pray. */
    private Prayer prayer = Prayer.NONE;
    /** Ticks before the computer switches the prayer again. */
    private int prayerSwitchTicks;
    /** The tick a protected hit landed on, during which nothing knocks the unit back. */
    private int steadyTick = -1;
    /** Ticks in a row it has been on the move; a rider that has been for a while is hard to hit with arrows. */
    private int movingTicks;
    private double goalX;
    private double goalZ;
    private double postX;
    private double postZ;
    private boolean hasPost;
    /** The unit or player an attack order is against, kept across restarts. */
    private UUID targetId;
    private EntityLivingBase target;
    /** Whether the target was picked up while guarding rather than ordered. */
    private boolean defending;
    /** Whether the target picked up while guarding is one that hit the unit, which it follows further. */
    private boolean provoked;
    /** Enemies to attack one after another once the target is down, from a shift-click or an attack box. */
    private final List<UUID> attackQueue = new ArrayList<>();
    /** Whether the unit is on an attack move: going to x, z, fighting whatever enemies it meets on the way. */
    private boolean attackMove;
    private double attackMoveX;
    private double attackMoveZ;
    /**
     * Where to walk next, one after another, once at the goal of the move it is on, from shift-clicks: x, z and 1 for
     * an attack move or 0; not saved.
     */
    private final List<double[]> moveQueue = new ArrayList<>();
    /** The building a build, work or attack order is for. */
    private int buildingId;
    /** Building sites a worker builds next, one after another, once done with the one it is on. */
    private final List<Integer> buildQueue = new ArrayList<>();
    /** The building that trained it, or 0. */
    private int trainedAt;
    /** Whether the computer trained it, and so commands it while it plays the team. */
    private boolean aiControlled;
    /** The player it follows, and its place behind them: blocks to their right and behind them. */
    private UUID leaderId;
    private double followAcross;
    private double followBehind;
    /** Whether a worker is at the building site or workplace it was sent to, working. */
    private boolean working;
    /** What a worker carries, by resource ordinal. */
    private final long[] carrying = new long[Resource.values().length];
    /** Whether a worker is taking what it carries to a store. */
    private boolean delivering;
    /** The animal a hunting worker is after, and where it was last seen. */
    private UUID preyId;
    private EntityLivingBase prey;
    private double preyX;
    private double preyZ;
    /** The carcass a hunting worker carves, or 0. */
    private int carcassId;
    private int carveTicks;
    /** Ticks an archer has drawn its bow for; 0 when it is not drawing. */
    private int drawTicks;
    /** Whether the archer drew its bow this tick. */
    private boolean drewThisTick;
    /** On a client, ticks the archer has been seen drawing its bow for. */
    private int clientDrawTicks;
    /** Where an archer ordered to attack goes to shoot from, in a line with the others, and where its target was. */
    private boolean hasSpot;
    private double spotX;
    private double spotZ;
    private double anchorX;
    private double anchorZ;

    private int attackCooldown;
    /** Ticks until a lesser demon may hurl its next fireball, and ticks left of the throw it is winding up. */
    private int fireballTicks = FIREBALL_MIN_TICKS;
    private int hurlTicks;
    private int planTicks;
    private int scanTicks;
    private int progressTicks;
    private double progressX;
    private double progressZ;
    private int stalls;
    /** Ticks in a row the unit has been inside a solid block. */
    private int buriedTicks;
    /** Waypoints of the long route being walked, and where it leads; not saved, planned again after a restart. */
    private List<double[]> route;
    private double routeX = Double.NaN;
    private double routeZ = Double.NaN;

    public EntityUnit(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        getNavigator().setCanSwim(true);
        getNavigator().setAvoidsWater(false);
        tasks.addTask(0, new EntityAISwimming(this));
        experienceValue = 0;
        for (int slot = 0; slot < 5; slot++) {
            setEquipmentDropChance(slot, 0);
        }
        planTicks = rand.nextInt(10);
        scanTicks = rand.nextInt(10);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(WATCH_COLOR, 0xFFFFFF);
        dataWatcher.addObject(WATCH_KIND, (byte) 0);
        dataWatcher.addObject(WATCH_FACTION, (byte) 0);
        dataWatcher.addObject(WATCH_DRAWING, (byte) 0);
        dataWatcher.addObject(WATCH_PRAYER, (byte) 0);
        dataWatcher.addObject(WATCH_ELITE, (byte) 0);
    }

    /** On a client, whether its team has made it elite: an Elite Black Knight or a Justiciar. */
    public boolean isWatchedElite() {
        return dataWatcher.getWatchableObjectByte(WATCH_ELITE) != 0;
    }

    /** Whether its team has the upgrade that makes its kind elite. */
    private static boolean isElite(UnitKind kind, Team team) {
        return team != null && kind.eliteUpgrade() != null && team.has(kind.eliteUpgrade());
    }

    /** On a client, the protection prayer it has up, shown above its head. */
    public Prayer getWatchedPrayer() {
        return Prayer.byOrdinal(dataWatcher.getWatchableObjectByte(WATCH_PRAYER));
    }

    public Prayer getPrayer() {
        return prayer;
    }

    /** Puts up a protection prayer, or none; returns false when its kind cannot have that one up. */
    public boolean setPrayer(Prayer prayer) {
        if (!prayer.allowedFor(kind)) {
            return false;
        }
        this.prayer = prayer;
        if (getWatchedPrayer() != prayer) {
            dataWatcher.updateObject(WATCH_PRAYER, (byte) prayer.ordinal());
        }
        return true;
    }

    /** On a client, how long the archer has been drawing its bow for, in ticks; 0 when it is not drawing. */
    public int getDrawTicks() {
        return clientDrawTicks;
    }

    private void setDrawing(boolean drawing) {
        if ((dataWatcher.getWatchableObjectByte(WATCH_DRAWING) != 0) != drawing) {
            dataWatcher.updateObject(WATCH_DRAWING, (byte) (drawing ? 1 : 0));
        }
    }

    /** The bow in its hand bends as it is drawn, as a player's does. */
    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getItemIcon(ItemStack stack, int pass) {
        if (stack != null && stack.getItem() == Items.bow && clientDrawTicks > 0) {
            return Items.bow.getItemIconForUseDuration(clientDrawTicks >= 18 ? 2 : clientDrawTicks > 13 ? 1 : 0);
        }
        return super.getItemIcon(stack, pass);
    }

    /** The RGB color of its team, for drawing its team sash; known on clients too. */
    public int getTeamColor() {
        return dataWatcher.getWatchableObjectInt(WATCH_COLOR);
    }

    /** Its kind, as clients know it, for picking its skin. */
    public UnitKind getWatchedKind() {
        int ordinal = dataWatcher.getWatchableObjectByte(WATCH_KIND);
        return ordinal >= 0 && ordinal < UnitKind.values().length ? UnitKind.values()[ordinal] : UnitKind.SOLDIER;
    }

    /** Its team's faction, as clients know it, for picking its skin. */
    public Faction getWatchedFaction() {
        int ordinal = dataWatcher.getWatchableObjectByte(WATCH_FACTION);
        return ordinal >= 0 && ordinal < Faction.values().length ? Faction.values()[ordinal] : Faction.SARADOMIN;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(Config.unitHealth);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(MOVEMENT_SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(48);
        getAttributeMap().registerAttribute(SharedMonsterAttributes.attackDamage)
            .setBaseValue(1);
    }

    /** Makes a new unit a member of the team, with its name, skin and tool. */
    void setTeam(Team team, UnitKind kind, int number) {
        this.teamId = team.getId();
        this.kind = kind;
        this.number = number;
        updateTeam(team);
        setHealth(getMaxHealth());
    }

    /**
     * What it holds when its hands are free: an archer's bow, a warrior's sword, and for a worker the tool for its
     * job: a hoe at a farm, a pickaxe at a gold mine, a sword for hunting and otherwise an axe. They are stone until
     * the team researches iron and then diamond ones.
     */
    private Item tool() {
        Team team = team();
        // Blue wizards hold a staff, Zamorak mages Zamorak's own and dark wizards nothing, casting from bare hands;
        // priests and monks a holy book; a demon its claws, and a centaur the bow its model has in hand.
        if (kind.isCaster()) {
            return kind == UnitKind.ZAMORAK_MAGE ? WarOfTheGods.zamorakStaff
                : kind == UnitKind.DARK_WIZARD ? null : Items.stick;
        }
        if (kind.worships()) {
            return Items.book;
        }
        if (kind.hasOwnModel()) {
            return null;
        }
        if (kind.ranged) {
            return Items.bow;
        }
        if (kind == UnitKind.TRADIGEE) {
            return Items.diamond_sword;
        }
        if (kind.fights() || order == Order.HUNT) {
            return SWORDS[tier(team, Upgrade.FORGED_BLADES, Upgrade.TEMPERED_STEEL)];
        }
        if (order == Order.MINE) {
            return PICKAXES[tier(team, Upgrade.PICKAXES, Upgrade.DIAMOND_PICKAXES)];
        }
        if (order == Order.WORK) {
            Building workplace = building();
            if (workplace != null && workplace.type == BuildingType.FARM) {
                return HOES[tier(team, Upgrade.PLOUGHS, Upgrade.DIAMOND_HOES)];
            }
        }
        return AXES[tier(team, Upgrade.SHARP_AXES, Upgrade.DIAMOND_AXES)];
    }

    /** 0 for stone, 1 once the team has the iron upgrade and 2 with the diamond one. */
    private static int tier(Team team, Upgrade iron, Upgrade diamond) {
        return team == null ? 0 : team.has(diamond) ? 2 : team.has(iron) ? 1 : 0;
    }

    /** Keeps name, skin and strength in step with the team's name, color, faction and upgrades. */
    void updateTeam(Team team) {
        // A hero goes by his own name, in his team's color.
        String name = kind.hero ? team.getColor() + kind.displayName
            : team.getColor() + team.getName()
                + " "
                + (isElite(kind, team) ? kind.eliteName()
                    .toLowerCase() : kind.lowerName())
                + " "
                + number;
        if (!name.equals(getCustomNameTag())) {
            setCustomNameTag(name);
            setAlwaysRenderNameTag(true);
        }
        if (getTeamColor() != team.getRgb()) {
            dataWatcher.updateObject(WATCH_COLOR, team.getRgb());
        }
        if (getWatchedKind() != kind) {
            dataWatcher.updateObject(WATCH_KIND, (byte) kind.ordinal());
        }
        if (getWatchedFaction() != team.getFaction()) {
            dataWatcher.updateObject(
                WATCH_FACTION,
                (byte) team.getFaction()
                    .ordinal());
        }
        // Units from before skins wore their team's color as leather armor; applyUpgrades puts on what they wear now.
        applyUpgrades(team);
    }

    /** Sets its health and strength by its kind and the team's upgrades, healing what it gains. */
    public void applyUpgrades(Team team) {
        double health = Config.unitHealth * kind.healthFactor;
        double damage = 1;
        if (kind.fights()) {
            health += (team.has(Upgrade.PADDED_ARMOR) ? 6 : 0) + (team.has(Upgrade.PLATE_ARMOR) ? 8 : 0);
        }
        if (kind.fights() && !kind.ranged) {
            damage += kind.extraDamage + (team.has(Upgrade.FORGED_BLADES) ? 2 : 0)
                + (team.has(Upgrade.TEMPERED_STEEL) ? 2 : 0);
        }
        boolean elite = isElite(kind, team);
        if (elite) {
            health += ELITE_HEALTH;
            damage += ELITE_DAMAGE;
        }
        if (isWatchedElite() != elite) {
            dataWatcher.updateObject(WATCH_ELITE, (byte) (elite ? 1 : 0));
        }
        IAttributeInstance maxHealth = getEntityAttribute(SharedMonsterAttributes.maxHealth);
        double gained = health - maxHealth.getBaseValue();
        if (gained != 0) {
            maxHealth.setBaseValue(health);
            setHealth((float) Math.max(1, Math.min(health, getHealth() + Math.max(0, gained))));
        }
        IAttributeInstance attack = getEntityAttribute(SharedMonsterAttributes.attackDamage);
        if (attack.getBaseValue() != damage) {
            attack.setBaseValue(damage);
        }
        // Riders and centaurs go faster than men on foot, and their horses make them wider.
        double speed = MOVEMENT_SPEED * (kind.isFast() ? MOUNTED_SPEED : 1);
        IAttributeInstance movement = getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        if (movement.getBaseValue() != speed) {
            movement.setBaseValue(speed);
        }
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance)
            .setBaseValue(kind == UnitKind.LESSER_DEMON ? 1 : 0);
        fitSize(kind);
        wearArmor(team);
        showHeld();
    }

    /**
     * Warriors and archers show their team's armor upgrades, boots included: chainmail with padded armor, iron with
     * plate. It is only for show; the upgrades' extra health is what protects them. Workers wear none, and knights and
     * heroes keep their own look.
     */
    private void wearArmor(Team team) {
        Item[] armor = new Item[5];
        if (kind.showsArmor() && team.has(Upgrade.PLATE_ARMOR)) {
            armor[4] = Items.iron_helmet;
            armor[3] = Items.iron_chestplate;
            armor[2] = Items.iron_leggings;
            armor[1] = Items.iron_boots;
        } else if (kind.showsArmor() && team.has(Upgrade.PADDED_ARMOR)) {
            armor[4] = Items.chainmail_helmet;
            armor[3] = Items.chainmail_chestplate;
            armor[1] = Items.chainmail_boots;
        }
        for (int slot = 1; slot < 5; slot++) {
            ItemStack worn = getEquipmentInSlot(slot);
            Item wornItem = worn == null ? null : worn.getItem();
            if (wornItem != armor[slot]) {
                setCurrentItemOrArmor(slot, armor[slot] == null ? null : new ItemStack(armor[slot]));
            }
        }
    }

    /** Armor on units is only for show. */
    @Override
    public int getTotalArmorValue() {
        return 0;
    }

    private Team team() {
        Game game = GameManager.getGame();
        return game == null ? null : game.getTeam(teamId);
    }

    /** Most a worker carries before taking it to a store. */
    private int capacity() {
        Team team = team();
        return Config.carryCapacity + (team != null && team.has(Upgrade.BASKETS) ? 10 : 0);
    }

    public String getTeamId() {
        return teamId;
    }

    public int getNumber() {
        return number;
    }

    public UnitKind getKind() {
        return kind;
    }

    public boolean isWorker() {
        return kind == UnitKind.WORKER;
    }

    /** Whether the unit can raise or repair a building of the type: dark wizards a stone circle, workers the rest. */
    public boolean canRaise(BuildingType type) {
        return type.raisedByWizards() ? kind == UnitKind.DARK_WIZARD : isWorker();
    }

    /** The foot of the tree a worker chops, as x, y, z, or null when it chops none. */
    public int[] getTree() {
        return order == Order.CHOP ? new int[] { treeX, treeY, treeZ } : null;
    }

    /** The gold mine a worker mines, or 0. */
    public int getMineId() {
        return order == Order.MINE ? mineId : 0;
    }

    /** Whether the unit is a worker at a gold mine, mining it. */
    public boolean isMining() {
        return order == Order.MINE && working && !delivering;
    }

    /** Whether the unit is a worker at a tree, chopping it. */
    public boolean isChopping() {
        return order == Order.CHOP && working && !delivering;
    }

    /** The building a build, work or attack order is for, or 0. */
    public int getBuildingId() {
        return order == Order.BUILD || order == Order.WORK || order == Order.ATTACK_BUILDING ? buildingId : 0;
    }

    /** Whether the unit is a worker at the building site with that id, building it. */
    public boolean isBuildingAt(int siteId) {
        return order == Order.BUILD && working && buildingId == siteId;
    }

    /** Whether the unit is a worker at the farm with that id, working with room left to carry more. */
    public boolean isWorkingAt(int workplaceId) {
        return order == Order.WORK && working && !delivering && buildingId == workplaceId;
    }

    /** Whether the unit is a worker sent to work the farm with that id: on its way, working it or carrying from it. */
    public boolean isSentToWork(int workplaceId) {
        return order == Order.WORK && buildingId == workplaceId;
    }

    /** Whether a worker is on its way to a store with what it carries. */
    public boolean isDelivering() {
        return (order == Order.WORK || order == Order.CHOP || order == Order.HUNT || order == Order.MINE) && delivering;
    }

    /** Whether the unit is a worker carving a carcass. */
    public boolean isCarving() {
        return order == Order.HUNT && working && !delivering && carcassId != 0;
    }

    /** What the worker carries, in all. */
    public long getCarried() {
        long total = 0;
        for (long amount : carrying) {
            total += amount;
        }
        return total;
    }

    /** What the worker carries of each resource. */
    public long getCarrying(Resource resource) {
        return carrying[resource.ordinal()];
    }

    /** Adds a harvest to what the worker carries; once its hands are full it sets off for a store. */
    public void gather(long[] amounts) {
        for (int i = 0; i < carrying.length; i++) {
            carrying[i] += amounts[i];
        }
        if (getCarried() >= capacity()) {
            delivering = true;
            working = false;
            startOrder();
        }
        showHeld();
    }

    /** Holds the tool for what it is doing, or on the way to a store what it carries most of. */
    private void showHeld() {
        int most = -1;
        if (delivering) {
            for (int i = 0; i < carrying.length; i++) {
                if (carrying[i] > 0 && (most < 0 || carrying[i] > carrying[most])) {
                    most = i;
                }
            }
        }
        Item item = most < 0 ? tool()
            : order == Order.HUNT && most == Resource.FOOD.ordinal() ? Items.beef : CARRIED_ITEMS[most];
        ItemStack held = getHeldItem();
        if (item == null ? held != null : held == null || held.getItem() != item) {
            setCurrentItemOrArmor(0, item == null ? null : new ItemStack(item));
        }
    }

    private static final Item[] CARRIED_ITEMS = { Items.wheat, Item.getItemFromBlock(Blocks.log), Items.gold_ingot };

    public Order getOrder() {
        return order;
    }

    public Stance getStance() {
        return stance;
    }

    void setStance(Stance stance) {
        this.stance = stance;
        if (defending && stance == Stance.PASSIVE) {
            backToPost();
        }
    }

    /** Where the unit is headed: its goal, its target or its post; null when it stands at its post. */
    public double[] getHeading() {
        switch (order) {
            case MOVE:
                return new double[] { goalX, goalZ };
            case ATTACK:
                return target == null ? null : new double[] { target.posX, target.posZ };
            case BUILD:
            case WORK: {
                Building building = delivering ? store() : building();
                return building == null || working ? null : building.nearestOutside(posX, posZ);
            }
            case ATTACK_BUILDING: {
                Building building = targetBuilding();
                return building == null || building.distanceTo(posX, posZ) <= buildingReach() ? null
                    : building.nearestOutside(posX, posZ);
            }
            case LIGHT:
                return torches.isEmpty() ? null : new double[] { torches.get(0)[0] + 0.5, torches.get(0)[2] + 0.5 };
            case HUNT: {
                if (delivering) {
                    Building store = store();
                    return store == null ? null : store.nearestOutside(posX, posZ);
                }
                Hunting.Carcass carcass = Hunting.get(carcassId);
                if (carcass != null) {
                    return working ? null : new double[] { carcass.x, carcass.z };
                }
                return prey == null ? null : new double[] { prey.posX, prey.posZ };
            }
            case CHOP: {
                if (delivering) {
                    Building store = store();
                    return store == null ? null : store.nearestOutside(posX, posZ);
                }
                return working ? null : new double[] { treeX + 0.5, treeZ + 0.5 };
            }
            case MINE: {
                if (delivering) {
                    Building store = store();
                    return store == null ? null : store.nearestOutside(posX, posZ);
                }
                GoldMine mine = mine();
                return working || mine == null ? null : mine.nearestOutside(posX, posZ);
            }
            case FOLLOW: {
                double[] slot = followSlot();
                return slot == null || distanceTo(slot[0], slot[1]) <= ARRIVED ? null : slot;
            }
            default:
                return hasPost && distanceTo(postX, postZ) > 2 ? new double[] { postX, postZ } : null;
        }
    }

    public int getTrainedAt() {
        return trainedAt;
    }

    public void setTrainedAt(int buildingId) {
        trainedAt = buildingId;
    }

    /** Whether the computer trained it, and so commands it while it plays the team. */
    public boolean isAiControlled() {
        return aiControlled;
    }

    public void setAiControlled(boolean aiControlled) {
        this.aiControlled = aiControlled;
    }

    public UUID getTargetId() {
        if (order == Order.FOLLOW) {
            return leaderId;
        }
        if (order == Order.HUNT) {
            return carcassId == 0 && prey != null ? prey.getUniqueID() : null;
        }
        return order == Order.ATTACK && target != null ? target.getUniqueID() : null;
    }

    // ---------------------------------------------------------------- orders

    void orderMove(double x, double z) {
        dropPlans();
        order = Order.MOVE;
        goalX = x;
        goalZ = z;
        target = null;
        targetId = null;
        defending = false;
        startOrder();
    }

    /** Sends a worker to a building site to build it, to a damaged building to repair it, or to a farm to work it. */
    void orderBuild(Building building) {
        dropPlans();
        order = building.isComplete() && !building.isDamaged() ? Order.WORK : Order.BUILD;
        buildingId = building.id;
        working = false;
        delivering = order == Order.WORK && getCarried() >= capacity();
        target = null;
        targetId = null;
        defending = false;
        startOrder();
    }

    /**
     * Has a worker build the site once it has built the one it is on and those queued before; one doing anything else
     * starts on it now.
     */
    void queueBuild(Building building) {
        if (order != Order.BUILD || building.isComplete() && !building.isDamaged()) {
            orderBuild(building);
            return;
        }
        if (building.id != buildingId && !buildQueue.contains(building.id)) {
            buildQueue.add(building.id);
        }
    }

    /** Building sites still queued after the one the worker is on. */
    public int getQueuedBuilds() {
        return order == Order.BUILD ? buildQueue.size() : 0;
    }

    /** Starts on the next queued site that still stands unfinished; false when there is none. */
    private boolean buildNext() {
        Game game = GameManager.getGame();
        while (game != null && !buildQueue.isEmpty()) {
            Building next = game.getBuilding(buildQueue.remove(0));
            if (next != null && next.team.equals(teamId) && (!next.isComplete() || next.isDamaged())) {
                List<Integer> rest = new ArrayList<>(buildQueue);
                orderBuild(next);
                buildQueue.addAll(rest);
                return true;
            }
        }
        return false;
    }

    /**
     * Sends a worker that has just put up a storehouse to the tree or gold mine nearest it, as that is what it was put
     * up for; false when there is neither near it.
     */
    private boolean gatherNear(Game game, Building store) {
        if (game == null || !isWorker()) {
            return false;
        }
        double x = store.getCenterX();
        double z = store.getCenterZ();
        GoldMine mine = Units.nearestMine(game, x, z, STORE_GATHER_RANGE);
        int[] tree = Trees.nearest(worldObj, game, x, z, STORE_GATHER_RANGE);
        double mineDistance = mine == null ? Double.MAX_VALUE : mine.distanceTo(x, z);
        double treeDistance = tree == null ? Double.MAX_VALUE : Math.hypot(tree[0] + 0.5 - x, tree[2] + 0.5 - z);
        if (mine != null && mineDistance <= treeDistance) {
            orderMine(mine);
            return true;
        }
        if (tree != null) {
            orderChop(tree[0], tree[1], tree[2]);
            return true;
        }
        return false;
    }

    /** Sends a worker to chop the tree with its trunk's foot at x, y, z. */
    void orderChop(int x, int y, int z) {
        order = Order.CHOP;
        treeX = x;
        treeY = y;
        treeZ = z;
        working = false;
        chopTicks = 0;
        delivering = getCarried() >= capacity();
        target = null;
        targetId = null;
        defending = false;
        startOrder();
    }

    /** Sends a worker to mine a gold mine. */
    void orderMine(GoldMine mine) {
        order = Order.MINE;
        mineId = mine.id;
        working = false;
        mineTicks = 0;
        delivering = getCarried() >= capacity();
        target = null;
        targetId = null;
        defending = false;
        startOrder();
    }

    /** Sends a worker to put torches on the spots, one after another. */
    void orderLight(List<int[]> spots) {
        order = Order.LIGHT;
        torches.clear();
        torches.addAll(spots);
        working = false;
        target = null;
        targetId = null;
        defending = false;
        startOrder();
    }

    /** How many torches the worker still has to put up. */
    public int getTorchesLeft() {
        return order == Order.LIGHT ? torches.size() : 0;
    }

    void orderAttackBuilding(Building building) {
        // Priests and monks do not fight.
        if (!kind.fights() && !isWorker()) {
            return;
        }
        dropPlans();
        order = Order.ATTACK_BUILDING;
        buildingId = building.id;
        target = null;
        targetId = null;
        defending = false;
        startOrder();
    }

    void orderAttack(EntityLivingBase target) {
        if (!kind.fights() && !isWorker()) {
            return;
        }
        dropPlans();
        engage(target);
    }

    /**
     * Makes the move the unit was just ordered on an attack move: it attacks any enemy, or enemy building, it comes
     * across on the way, and goes on once that is down. Workers, who do not fight, just go.
     */
    void makeAttackMove() {
        if (order == Order.MOVE && kind.fights()) {
            attackMove = true;
            attackMoveX = goalX;
            attackMoveZ = goalZ;
        }
    }

    /**
     * Attacks the target once done with the enemies it was already ordered at; a unit doing anything else attacks it
     * now.
     */
    void queueAttack(EntityLivingBase target) {
        if (order != Order.ATTACK || defending || resolveTarget() == null) {
            orderAttack(target);
            return;
        }
        UUID id = target.getUniqueID();
        if (!id.equals(targetId) && !attackQueue.contains(id)) {
            attackQueue.add(id);
        }
    }

    /**
     * Walks to x, z once done with the moves it is on and those queued before, fighting on the way with attack; a
     * unit doing anything else goes now.
     */
    void queueMove(double x, double z, boolean attack) {
        if (order != Order.MOVE && !attackMove || defending) {
            orderMove(x, z);
            if (attack) {
                makeAttackMove();
            }
            return;
        }
        moveQueue.add(new double[] { x, z, attack && kind.fights() ? 1 : 0 });
    }

    /** Where the unit will be once done with the moves it is on and has queued; where it stands if not moving. */
    double[] lastGoal() {
        if (!moveQueue.isEmpty()) {
            double[] last = moveQueue.get(moveQueue.size() - 1);
            return new double[] { last[0], last[1] };
        }
        if (attackMove) {
            return new double[] { attackMoveX, attackMoveZ };
        }
        return order == Order.MOVE ? new double[] { goalX, goalZ } : new double[] { posX, posZ };
    }

    /** The moves queued after the one it is on, as x, z each. */
    public List<double[]> getQueuedMoves() {
        List<double[]> moves = new ArrayList<>();
        for (double[] move : moveQueue) {
            moves.add(new double[] { move[0], move[1] });
        }
        return moves;
    }

    /** On to the next queued move, if there is one. */
    private boolean nextMove() {
        if (moveQueue.isEmpty()) {
            return false;
        }
        double[] next = moveQueue.remove(0);
        order = Order.MOVE;
        goalX = next[0];
        goalZ = next[1];
        attackMove = next[2] > 0;
        attackMoveX = goalX;
        attackMoveZ = goalZ;
        target = null;
        targetId = null;
        defending = false;
        hasSpot = false;
        startOrder();
        return true;
    }

    /** Whether the unit is on an attack move. */
    public boolean isAttackMoving() {
        return attackMove;
    }

    /** Enemies still queued to attack after the one the unit is on. */
    public int getQueuedAttacks() {
        return order == Order.ATTACK ? attackQueue.size() : 0;
    }

    /** Attacks the target, keeping the attack move and the queue it is on. */
    private void engage(EntityLivingBase target) {
        order = Order.ATTACK;
        this.target = target;
        targetId = target.getUniqueID();
        defending = false;
        hasSpot = false;
        startOrder();
    }

    /** Forgets the enemies queued to attack and any attack move, as a new order does. */
    private void dropPlans() {
        attackQueue.clear();
        attackMove = false;
        moveQueue.clear();
    }

    /**
     * Done with a target it was ordered at: on to the next queued enemy still alive, else on with the attack move,
     * else guards where it stands. Returns false only when it guards.
     */
    private boolean nextAttack() {
        while (!attackQueue.isEmpty()) {
            EntityLivingBase next = Units.get()
                .findLiving(worldObj, attackQueue.remove(0));
            if (next != null && next.isEntityAlive()) {
                engage(next);
                return true;
            }
        }
        if (attackMove) {
            order = Order.MOVE;
            goalX = attackMoveX;
            goalZ = attackMoveZ;
            target = null;
            targetId = null;
            defending = false;
            hasSpot = false;
            startOrder();
            return true;
        }
        getNavigator().clearPathEntity();
        guardHere();
        return false;
    }

    /** On an attack move: the nearest enemy, or else enemy building, in sight to fight; false when there is none. */
    private boolean attackOnTheWay() {
        if (--scanTicks > 0) {
            return false;
        }
        scanTicks = 10 + rand.nextInt(5);
        double range = Math.max(GUARD_RANGE, guardRange());
        EntityLivingBase enemy = Units.get()
            .findEnemyNear(this, range);
        if (enemy != null) {
            engage(enemy);
            return true;
        }
        Building building = Units.get()
            .findEnemyBuildingNear(this, kind.ranged ? ARCHER_BUILDING_RANGE : GUARD_RANGE);
        if (building != null) {
            order = Order.ATTACK_BUILDING;
            buildingId = building.id;
            target = null;
            targetId = null;
            defending = false;
            startOrder();
            return true;
        }
        return false;
    }

    /** An archer's attack, shooting from x, z, in a line with the other archers sent along. */
    void orderAttack(EntityLivingBase target, double x, double z) {
        orderAttack(target);
        hasSpot = true;
        spotX = x;
        spotZ = z;
        anchorX = target.posX;
        anchorZ = target.posZ;
    }

    /** Sends a worker to hunt the animal, carve food off its carcass and carry the food home. */
    void orderHunt(EntityLivingBase animal) {
        dropPlans();
        order = Order.HUNT;
        prey = animal;
        preyId = animal.getUniqueID();
        preyX = animal.posX;
        preyZ = animal.posZ;
        carcassId = 0;
        carveTicks = 0;
        working = false;
        delivering = getCarried() >= capacity();
        target = null;
        targetId = null;
        defending = false;
        startOrder();
    }

    /** Sends a worker to carve food off a carcass and carry it home, then hunt on around there. */
    void orderCarve(Hunting.Carcass carcass) {
        dropPlans();
        order = Order.HUNT;
        prey = null;
        preyId = null;
        preyX = carcass.x;
        preyZ = carcass.z;
        carcassId = carcass.id;
        carveTicks = 0;
        working = false;
        delivering = getCarried() >= capacity();
        target = null;
        targetId = null;
        defending = false;
        startOrder();
    }

    /** Stops and guards the spot the unit is standing on. */
    void orderStop() {
        buildQueue.clear();
        dropPlans();
        guardHere();
        getNavigator().clearPathEntity();
    }

    private void startOrder() {
        // A new order drops the queued building sites; buildNext puts the rest back.
        buildQueue.clear();
        stalls = 0;
        planTicks = 0;
        progressTicks = 0;
        progressX = posX;
        progressZ = posZ;
        getNavigator().clearPathEntity();
        if (!worldObj.isRemote) {
            showHeld();
        }
    }

    /**
     * Follows a player, keeping a place in formation behind them: across blocks to their right and behind blocks
     * behind them, as they go.
     */
    void orderFollow(Entity leader, double across, double behind) {
        dropPlans();
        order = Order.FOLLOW;
        leaderId = leader.getUniqueID();
        followAcross = across;
        followBehind = behind;
        target = null;
        targetId = null;
        defending = false;
        working = false;
        delivering = false;
        planTicks = 0;
        getNavigator().clearPathEntity();
        showHeld();
    }

    /** Where it stands while following, as x, z; null when the player it follows is gone. */
    private double[] followSlot() {
        Entity leader = Units.leader(worldObj, leaderId);
        if (leader == null) {
            return null;
        }
        double[] heading = Units.followHeading(leader);
        // To the right of the way they go.
        double rx = -heading[1];
        double rz = heading[0];
        return new double[] { leader.posX - heading[0] * followBehind + rx * followAcross,
            leader.posZ - heading[1] * followBehind + rz * followAcross };
    }

    private void tickFollow() {
        double[] slot = followSlot();
        if (slot == null) {
            getNavigator().clearPathEntity();
            guardHere();
            return;
        }
        if (distanceTo(slot[0], slot[1]) < 0.8) {
            getNavigator().clearPathEntity();
            return;
        }
        // The place moves with the player, so the way there is planned often.
        if (--planTicks <= 0 || getNavigator().noPath()) {
            planTicks = 10;
            walkToward(slot[0], slot[1]);
        }
    }

    private void guardHere() {
        dropPlans();
        order = Order.IDLE;
        hasSpot = false;
        working = false;
        delivering = false;
        target = null;
        targetId = null;
        defending = false;
        postX = posX;
        postZ = posZ;
        hasPost = true;
    }

    /** Goes back to guarding its post, if it has one. */
    private void backToPost() {
        order = Order.IDLE;
        hasSpot = false;
        working = false;
        delivering = false;
        target = null;
        targetId = null;
        defending = false;
        stalls = 0;
        planTicks = 0;
        getNavigator().clearPathEntity();
    }

    // ---------------------------------------------------------------- behaviour

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    /** Creatures leave arm swings to mobs and players; units swing their swords and tools too. */
    @Override
    public void onLivingUpdate() {
        updateArmSwingProgress();
        super.onLivingUpdate();
        // On the move while it covers ground; standing still, or nearly, starts the count again.
        double dx = posX - prevPosX;
        double dz = posZ - prevPosZ;
        movingTicks = dx * dx + dz * dz > 0.0025 ? movingTicks + 1 : 0;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (worldObj.isRemote) {
            clientDrawTicks = dataWatcher.getWatchableObjectByte(WATCH_DRAWING) != 0 ? clientDrawTicks + 1 : 0;
            fitSize(getWatchedKind());
        }
    }

    /** A rider on its horse is wider than a man on foot, a centaur as wide and taller, and a demon taller still. */
    private void fitSize(UnitKind kind) {
        float wide = kind.isMounted() || kind.hasOwnModel() ? MOUNTED_WIDTH : 0.6F;
        float tall = kind == UnitKind.CENTAUR ? CENTAUR_HEIGHT
            : 1.8F * Math.max(1, kind == UnitKind.LESSER_DEMON ? kind.scale() : 1);
        if (width != wide || height != tall) {
            setSize(wide, tall);
        }
    }

    /** A chaos priest praying at a temple faces it and now and then raises its hands, sparks rising round it. */
    public void pray(Building temple) {
        getLookHelper().setLookPosition(temple.getCenterX(), temple.groundY + 2, temple.getCenterZ(), 10, 30);
        if (rand.nextInt(4) == 0) {
            swingItem();
            ((WorldServer) worldObj).func_147487_a("witchMagic", posX, posY + 2.1, posZ, 3, 0.2, 0.2, 0.2, 0.02);
        }
    }

    /** Ticks in a row it has been on the move. */
    public int getMovingTicks() {
        return movingTicks;
    }

    @Override
    protected void updateAITick() {
        super.updateAITick();
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (prayerSwitchTicks > 0) {
            prayerSwitchTicks--;
        }
        if (fireballTicks > 0) {
            fireballTicks--;
        }
        if (hurlTicks > 0 && order != Order.ATTACK) {
            // Called off before the throw.
            hurlTicks = 0;
            setDrawing(false);
        }
        // Sagittare always protects from missiles; units that cannot pray have none up.
        if (!prayer.allowedFor(kind)) {
            setPrayer(Prayer.always(kind));
        }
        drewThisTick = false;
        // Wedged in a block, as can happen when a building goes up round a unit or it is pushed into terrain: out
        // to the nearest open ground.
        // Wandering about on top of a tree's leaves, which it can walk on but not find its way down from, is as bad.
        buriedTicks = isEntityInsideOpaqueBlock() || order != Order.CHOP && onLeaves() ? buriedTicks + 1 : 0;
        if (buriedTicks >= 20) {
            buriedTicks = 0;
            rescue();
        }
        tickOrder();
        if (!drewThisTick && drawTicks > 0) {
            drawTicks = 0;
            setDrawing(false);
        }
    }

    /**
     * Draws an archer's bow a tick further; returns true once it is fully drawn, when the arrow goes, after which it
     * rests a moment before drawing again.
     */
    private boolean drawBow() {
        if (attackCooldown > 0) {
            return false;
        }
        drewThisTick = true;
        if (drawTicks == 0) {
            setDrawing(true);
        }
        if (++drawTicks < DRAW_TICKS) {
            return false;
        }
        drawTicks = 0;
        drewThisTick = false;
        setDrawing(false);
        attackCooldown = ARCHER_REST;
        return true;
    }

    private void tickOrder() {
        switch (order) {
            case MOVE:
                tickMove();
                break;
            case ATTACK:
                tickAttack();
                break;
            case BUILD:
                tickBuild();
                break;
            case WORK:
                tickWork();
                break;
            case ATTACK_BUILDING:
                tickAttackBuilding();
                break;
            case CHOP:
                tickChop();
                break;
            case MINE:
                tickMine();
                break;
            case LIGHT:
                tickLight();
                break;
            case HUNT:
                tickHunt();
                break;
            case FOLLOW:
                tickFollow();
                break;
            default:
                tickGuard();
        }
    }

    private void tickMove() {
        if (distanceTo(goalX, goalZ) < ARRIVED) {
            getNavigator().clearPathEntity();
            if (nextMove()) {
                return;
            }
            attackMove = false;
            guardHere();
            return;
        }
        if (attackMove && kind.fights() && attackOnTheWay()) {
            return;
        }
        if (getNavigator().noPath() && --planTicks <= 0) {
            planTicks = 20;
            walkToward(goalX, goalZ);
        }
        if (!madeProgress() && !nextMove()) {
            guardHere();
        }
    }

    private Building building() {
        Game game = GameManager.getGame();
        Building building = game == null ? null : game.getBuilding(buildingId);
        return building != null && building.team.equals(teamId) ? building : null;
    }

    private void tickBuild() {
        Building site = building();
        if (site == null) {
            if (!buildNext()) {
                getNavigator().clearPathEntity();
                guardHere();
            }
            return;
        }
        if (site.isComplete() && !site.isDamaged()) {
            // Built or repaired, by this worker and others: on to the next queued site, or farmers stay to work it.
            if (buildNext()) {
                return;
            }
            // Only so many work a farm: the rest go to another nearby with room, or stand by.
            Game game = GameManager.getGame();
            Building workplace = game == null || !Units.takesWorkers(site) ? null
                : Units.workplaceFor(game, this, site);
            if (workplace != null) {
                orderBuild(workplace);
            } else if (site.type != BuildingType.STOREHOUSE || !gatherNear(game, site)) {
                getNavigator().clearPathEntity();
                guardHere();
            }
            return;
        }
        if (site.distanceTo(posX, posZ) <= Buildings.REACH) {
            if (!working) {
                working = true;
                getNavigator().clearPathEntity();
            }
            getLookHelper().setLookPosition(site.getCenterX(), site.groundY + 1, site.getCenterZ(), 10, 30);
            if (ticksExisted % 15 == 0) {
                swingItem();
            }
            progressTicks = 0;
            return;
        }
        working = false;
        walkTo(site);
    }

    private void tickWork() {
        Building workplace = building();
        if (workplace == null || !workplace.type.isWorkplace()) {
            getNavigator().clearPathEntity();
            guardHere();
            return;
        }
        if (!workplace.isComplete()) {
            order = Order.BUILD;
            working = false;
            return;
        }
        if (delivering) {
            deliver();
            return;
        }
        if (workplace.distanceTo(posX, posZ) <= Buildings.REACH) {
            if (!working) {
                working = true;
                getNavigator().clearPathEntity();
            }
            getLookHelper()
                .setLookPosition(workplace.getCenterX(), workplace.groundY + 1, workplace.getCenterZ(), 10, 30);
            // Now and then, as a farmer hoeing.
            if (rand.nextInt(25) == 0) {
                swingItem();
            }
            progressTicks = 0;
            return;
        }
        working = false;
        walkTo(workplace);
    }

    /**
     * Chops the tree, carrying the wood to a store whenever its hands are full. When the tree falls it goes on to
     * the nearest tree around; when there is none, it takes home what it has and waits there.
     */
    private void tickChop() {
        if (delivering) {
            deliver();
            return;
        }
        Game game = GameManager.getGame();
        if (game == null) {
            return;
        }
        if (!Trees.isLog(worldObj.getBlock(treeX, treeY, treeZ))) {
            // Felled, by this worker or another: on to the next.
            int[] next = Trees.nearest(worldObj, game, treeX + 0.5, treeZ + 0.5, NEXT_TREE_RANGE);
            if (next != null) {
                orderChop(next[0], next[1], next[2]);
            } else if (getCarried() > 0) {
                delivering = true;
                working = false;
                startOrder();
            } else {
                getNavigator().clearPathEntity();
                guardHere();
            }
            return;
        }
        double dx = treeX + 0.5 - posX;
        double dz = treeZ + 0.5 - posZ;
        double away = Math.sqrt(dx * dx + dz * dz);
        // Leaves often stand in the way of the trunk; a worker in among them can chop it from there.
        if (away > CHOP_REACH && !(away <= LEAVES_REACH && touchingLeaves())) {
            working = false;
            if (getNavigator().noPath() && --planTicks <= 0) {
                planTicks = 20;
                // To the side of the trunk facing the worker; the trunk itself is solid.
                double distance = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
                walkToward(treeX + 0.5 - dx / distance * 1.2, treeZ + 0.5 - dz / distance * 1.2);
            }
            if (!madeProgress()) {
                guardHere();
            }
            return;
        }
        if (!working) {
            working = true;
            getNavigator().clearPathEntity();
        }
        progressTicks = 0;
        getLookHelper().setLookPosition(treeX + 0.5, treeY + 1, treeZ + 0.5, 10, 30);
        if (ticksExisted % 12 == 0) {
            swingItem();
            worldObj.playSoundAtEntity(this, "dig.wood", 0.5F, 0.8F + rand.nextFloat() * 0.4F);
        }
        Team team = game.getTeam(teamId);
        int needed = Config.chopSeconds * 20;
        if (team != null && team.has(Upgrade.DIAMOND_AXES)) {
            needed = needed / 2;
        } else if (team != null && team.has(Upgrade.SHARP_AXES)) {
            needed = needed * 2 / 3;
        }
        if (++chopTicks < Math.max(1, needed)) {
            return;
        }
        chopTicks = 0;
        Tree tree = Trees.track(worldObj, game, treeX, treeY, treeZ);
        long[] wood = new long[carrying.length];
        wood[Resource.WOOD.ordinal()] = game.chopTree(tree, Config.chopWood);
        // Cracks spread up the trunk as it is chopped.
        int stage = (int) (10 * (1 - tree.getWood() / (double) tree.maxWood));
        worldObj.destroyBlockInWorldPartially((int) (tree.key() & 0x7FFFFFFF), treeX, treeY, treeZ, Math.min(9, stage));
        if (tree.getWood() <= 0) {
            worldObj.destroyBlockInWorldPartially((int) (tree.key() & 0x7FFFFFFF), treeX, treeY, treeZ, -1);
            Trees.fell(worldObj, game, tree);
        }
        if (wood[Resource.WOOD.ordinal()] > 0) {
            gather(wood);
        }
    }

    private GoldMine mine() {
        Game game = GameManager.getGame();
        return game == null ? null : game.getMine(mineId);
    }

    /**
     * Mines the gold mine, carrying the gold to a store whenever its hands are full. When the mine is mined out it goes
     * on to the nearest mine around; when there is none, it takes home what it has and waits there.
     */
    private void tickMine() {
        if (delivering) {
            deliver();
            return;
        }
        Game game = GameManager.getGame();
        GoldMine mine = mine();
        if (game == null) {
            return;
        }
        if (mine == null || mine.isDepleted()) {
            GoldMine next = Units.nearestMine(game, posX, posZ, NEXT_MINE_RANGE);
            if (next != null) {
                orderMine(next);
            } else if (getCarried() > 0) {
                delivering = true;
                working = false;
                startOrder();
            } else {
                getNavigator().clearPathEntity();
                guardHere();
            }
            return;
        }
        if (mine.distanceTo(posX, posZ) > MINE_REACH) {
            working = false;
            if (getNavigator().noPath() && --planTicks <= 0) {
                planTicks = 20;
                double[] spot = mine.nearestOutside(posX, posZ);
                walkToward(spot[0], spot[1]);
            }
            if (!madeProgress()) {
                guardHere();
            }
            return;
        }
        if (!working) {
            working = true;
            getNavigator().clearPathEntity();
        }
        progressTicks = 0;
        getLookHelper().setLookPosition(mine.getCenterX(), mine.groundY + 1.5, mine.getCenterZ(), 10, 30);
        if (ticksExisted % 12 == 0) {
            swingItem();
            worldObj.playSoundAtEntity(this, "dig.stone", 0.5F, 0.8F + rand.nextFloat() * 0.4F);
        }
        Team team = game.getTeam(teamId);
        int needed = Config.mineSeconds * 20;
        if (team != null && team.has(Upgrade.DIAMOND_PICKAXES)) {
            needed = needed / 2;
        } else if (team != null && team.has(Upgrade.PICKAXES)) {
            needed = needed * 2 / 3;
        }
        if (++mineTicks < Math.max(1, needed)) {
            return;
        }
        mineTicks = 0;
        long[] gold = new long[carrying.length];
        gold[Resource.GOLD.ordinal()] = game.mine(mine, Config.mineGold);
        if (mine.isDepleted()) {
            Buildings.get()
                .features()
                .deplete((WorldServer) worldObj, game, mine);
        }
        if (gold[Resource.GOLD.ordinal()] > 0) {
            gather(gold);
        }
    }

    /** Whether any leaves are right up against it. */
    private boolean touchingLeaves() {
        AxisAlignedBB box = boundingBox.expand(0.3, 0.1, 0.3);
        for (int x = MathHelper.floor_double(box.minX); x <= MathHelper.floor_double(box.maxX); x++) {
            for (int y = MathHelper.floor_double(box.minY); y <= MathHelper.floor_double(box.maxY); y++) {
                for (int z = MathHelper.floor_double(box.minZ); z <= MathHelper.floor_double(box.maxZ); z++) {
                    if (worldObj.getBlock(x, y, z)
                        .getMaterial() == Material.leaves) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Walks to each spot in turn and puts a torch there, paying for it in wood, unless the spot has been lit or built
     * on meanwhile. Out of wood, or out of spots, it stands guard where it is.
     */
    private void tickLight() {
        if (torches.isEmpty()) {
            getNavigator().clearPathEntity();
            guardHere();
            return;
        }
        int[] spot = torches.get(0);
        if (!Torches.stillDark(worldObj, spot[0], spot[1], spot[2])) {
            torches.remove(0);
            startOrder();
            return;
        }
        if (distanceTo(spot[0] + 0.5, spot[2] + 0.5) > TORCH_REACH || Math.abs(posY - spot[1]) > 3) {
            if (getNavigator().noPath() && --planTicks <= 0) {
                planTicks = 20;
                walkToward(spot[0] + 0.5, spot[2] + 0.5);
            }
            if (!madeProgress()) {
                // Cannot get there: on to the next one.
                torches.remove(0);
                startOrder();
            }
            return;
        }
        Game game = GameManager.getGame();
        Team team = game == null ? null : game.getTeam(teamId);
        if (team == null) {
            return;
        }
        if (Config.torchWood > 0) {
            Map<Resource, Long> cost = new EnumMap<>(Resource.class);
            cost.put(Resource.WOOD, (long) Config.torchWood);
            if (!team.spend(cost)) {
                getNavigator().clearPathEntity();
                guardHere();
                return;
            }
        }
        getLookHelper().setLookPosition(spot[0] + 0.5, spot[1], spot[2] + 0.5, 10, 30);
        swingItem();
        worldObj.setBlock(spot[0], spot[1], spot[2], Blocks.torch, 5, 3);
        worldObj.playSoundEffect(spot[0] + 0.5, spot[1] + 0.5, spot[2] + 0.5, "dig.wood", 0.6F, 1.2F);
        WebMap.blocksChanged(worldObj, spot[0], spot[2], spot[0], spot[2]);
        torches.remove(0);
        startOrder();
    }

    /**
     * Hunts an animal down, then carves food off its carcass, carrying it to a store whenever its hands are full and
     * coming back for more. Once the carcass is eaten up it goes after the nearest animal around; with none left, it
     * takes home what it has and waits there.
     */
    private void tickHunt() {
        if (delivering) {
            deliver();
            return;
        }
        Hunting.Carcass carcass = Hunting.get(carcassId);
        if (carcass == null) {
            carcassId = 0;
            EntityLivingBase animal = resolvePrey();
            if (animal != null) {
                chase(animal);
                return;
            }
            // Down, by this worker or another unit: to its carcass, if it left one, or on to the next.
            Hunting.Carcass left = Hunting.nearest(preyX, preyZ, 4);
            EntityLivingBase next = left == null ? nearestPrey() : null;
            if (left != null) {
                carcassId = left.id;
                working = false;
                startOrder();
            } else if (next != null) {
                orderHunt(next);
            } else if (getCarried() > 0) {
                delivering = true;
                working = false;
                startOrder();
            } else {
                getNavigator().clearPathEntity();
                guardHere();
            }
            return;
        }
        if (distanceTo(carcass.x, carcass.z) > CARVE_REACH) {
            working = false;
            if (getNavigator().noPath() && --planTicks <= 0) {
                planTicks = 20;
                walkToward(carcass.x, carcass.z);
            }
            if (!madeProgress()) {
                guardHere();
            }
            return;
        }
        if (!working) {
            working = true;
            getNavigator().clearPathEntity();
        }
        progressTicks = 0;
        getLookHelper().setLookPosition(carcass.x, carcass.y, carcass.z, 10, 30);
        if (ticksExisted % 15 == 0) {
            swingItem();
        }
        if (++carveTicks < Math.max(1, Config.huntSeconds * 20)) {
            return;
        }
        carveTicks = 0;
        long[] food = new long[carrying.length];
        food[Resource.FOOD.ordinal()] = Hunting.carve(carcass, Config.huntFood);
        if (food[Resource.FOOD.ordinal()] > 0) {
            gather(food);
        }
        if (carcass.getFood() <= 0) {
            carcassId = 0;
            prey = null;
            preyId = null;
            preyX = carcass.x;
            preyZ = carcass.z;
        }
    }

    /** Runs the animal down and hits it until it falls. */
    private void chase(EntityLivingBase animal) {
        working = false;
        preyX = animal.posX;
        preyZ = animal.posZ;
        getLookHelper().setLookPositionWithEntity(animal, 30, 30);
        double distanceSq = getDistanceSq(animal.posX, animal.boundingBox.minY, animal.posZ);
        double reach = width * 2 * width * 2 + animal.width;
        if (distanceSq <= reach + 1) {
            if (attackCooldown <= 0) {
                attackCooldown = 15;
                swingItem();
                animal.attackEntityFrom(DamageSource.causeMobDamage(this), HUNT_DAMAGE);
            }
            progressTicks = 0;
            return;
        }
        if (--planTicks <= 0) {
            planTicks = 10 + rand.nextInt(5);
            if (Math.sqrt(distanceSq) > LEG || !getNavigator().tryMoveToEntityLiving(animal, SPEED)) {
                walkToward(animal.posX, animal.posZ);
            }
        }
        if (!madeProgress()) {
            guardHere();
        }
    }

    private EntityLivingBase resolvePrey() {
        if (prey != null && (!prey.isEntityAlive() || prey.worldObj != worldObj)) {
            prey = null;
            preyId = null;
        }
        if (prey == null && preyId != null) {
            prey = Units.get()
                .findLiving(worldObj, preyId);
            if (prey == null || !prey.isEntityAlive()) {
                prey = null;
                preyId = null;
            }
        }
        return prey;
    }

    /** The nearest animal worth hunting around where the last one fell, or null. */
    private EntityLivingBase nearestPrey() {
        EntityLivingBase best = null;
        double bestDistance = NEXT_PREY_RANGE * NEXT_PREY_RANGE;
        for (Object object : worldObj
            .getEntitiesWithinAABB(EntityLivingBase.class, boundingBox.expand(NEXT_PREY_RANGE, 6, NEXT_PREY_RANGE))) {
            EntityLivingBase other = (EntityLivingBase) object;
            if (!other.isEntityAlive() || Hunting.foodOf(other) <= 0) {
                continue;
            }
            double dx = other.posX - preyX;
            double dz = other.posZ - preyZ;
            if (dx * dx + dz * dz < bestDistance) {
                best = other;
                bestDistance = dx * dx + dz * dz;
            }
        }
        return best;
    }

    /** The nearest town hall or storehouse of the unit's team, or null. */
    private Building store() {
        Game game = GameManager.getGame();
        return game == null ? null : Buildings.nearestStore(game, teamId, posX, posZ);
    }

    /** Carries what the worker gathered to the nearest store and hands it to the team, then goes back to work. */
    private void deliver() {
        working = false;
        Building store = store();
        if (store == null) {
            // Nowhere to take it; wait where it is until there is.
            getNavigator().clearPathEntity();
            progressTicks = 0;
            return;
        }
        if (store.distanceTo(posX, posZ) > Buildings.REACH) {
            walkTo(store);
            return;
        }
        Game game = GameManager.getGame();
        Team team = game.getTeam(teamId);
        if (team != null) {
            for (Resource resource : Resource.values()) {
                if (carrying[resource.ordinal()] > 0) {
                    team.addResource(resource, carrying[resource.ordinal()]);
                }
            }
        }
        Arrays.fill(carrying, 0);
        delivering = false;
        startOrder();
    }

    /** The enemy building an attack order is against, or null once it is gone or no longer an enemy's. */
    private Building targetBuilding() {
        Game game = GameManager.getGame();
        Building building = game == null ? null : game.getBuilding(buildingId);
        if (building == null) {
            return null;
        }
        Team team = game.getTeam(teamId);
        Team owner = game.getTeam(building.team);
        return team != null && owner != null && game.areEnemies(team, owner) ? building : null;
    }

    private void tickAttackBuilding() {
        Building building = targetBuilding();
        if (building == null) {
            if (defending) {
                backToPost();
            } else {
                nextAttack();
            }
            return;
        }
        // Enemies coming at a unit on guard, or on an attack move, come first.
        if ((defending || attackMove) && --scanTicks <= 0) {
            scanTicks = 10 + rand.nextInt(5);
            EntityLivingBase enemy = Units.get()
                .findEnemyNear(this, guardRange());
            if (enemy != null) {
                if (defending) {
                    defend(enemy);
                } else {
                    engage(enemy);
                }
                return;
            }
        }
        if (building.distanceTo(posX, posZ) <= buildingReach()) {
            getNavigator().clearPathEntity();
            getLookHelper().setLookPosition(building.getCenterX(), building.groundY + 1, building.getCenterZ(), 30, 30);
            // Knights hit buildings harder than warriors, and heroes harder still.
            int damage = Config.unitBuildingDamage * (1 + (kind.extraDamage + kind.extraArrowDamage) / 3);
            if (kind.ranged) {
                if (drawBow()) {
                    // A wizard's spell, or an archer's arrow; either way the building takes the same.
                    if (kind.isCaster()) {
                        EntitySpell spell = new EntitySpell(worldObj, this, teamId, pickSpell(null), 0);
                        spell.aimAt(building.getCenterX(), building.groundY + 2, building.getCenterZ());
                        worldObj.spawnEntityInWorld(spell);
                    } else {
                        EntityTeamArrow arrow = new EntityTeamArrow(worldObj, this);
                        arrow.aimAt(building.getCenterX(), building.groundY + 2, building.getCenterZ(), 0);
                        worldObj.spawnEntityInWorld(arrow);
                        playSound("random.bow", 1.0F, 1.0F / (rand.nextFloat() * 0.4F + 0.8F));
                    }
                    Buildings.get()
                        .damage(building, damage / 2, getCommandSenderName().replaceAll("\u00a7.", ""));
                }
            } else if (attackCooldown <= 0) {
                attackCooldown = 20;
                swingItem();
                Buildings.get()
                    .damage(building, damage, getCommandSenderName().replaceAll("\u00a7.", ""));
            }
            progressTicks = 0;
            return;
        }
        walkTo(building);
    }

    private double buildingReach() {
        return kind.ranged ? ARCHER_BUILDING_RANGE : Buildings.REACH;
    }

    /** How far an archer shoots, with its team's upgrades. */
    double archerRange() {
        Team team = team();
        return ARCHER_RANGE + kind.extraRange + (team != null && team.has(Upgrade.FLETCHING) ? 4 : 0);
    }

    /** Range at which a guarding unit notices enemies: farther for archers, who shoot from where they stand. */
    private double guardRange() {
        return (kind.ranged ? archerRange() : GUARD_RANGE) * stance.rangeFactor;
    }

    /** Whether the unit can hit the target from where it stands, by sword or by bow. */
    private boolean inReach(EntityLivingBase target) {
        double distanceSq = getDistanceSq(target.posX, target.boundingBox.minY, target.posZ);
        if (kind.ranged) {
            return distanceSq <= archerRange() * archerRange();
        }
        double reach = width * 2 * width * 2 + target.width;
        return distanceSq <= reach + 1;
    }

    private void shoot(EntityLivingBase victim) {
        if (kind.isCaster()) {
            cast(victim);
            return;
        }
        Team team = team();
        EntityTeamArrow arrow = new EntityTeamArrow(worldObj, this);
        arrow.aim(
            victim,
            Config.archerDamage + kind.extraArrowDamage + (team != null && team.has(Upgrade.FLETCHING) ? 1 : 0));
        arrow.setFlaming(team != null && team.has(Upgrade.FIRE_ARROWS));
        worldObj.spawnEntityInWorld(arrow);
        playSound("random.bow", 1.0F, 1.0F / (rand.nextFloat() * 0.4F + 0.8F));
    }

    /** Casts a spell at the victim: now and then a curse it is not already under, otherwise a strike. */
    private void cast(EntityLivingBase victim) {
        Spell spell = pickSpell(victim);
        float damage = spell.isCurse() ? 0 : spell.damage + (kind == UnitKind.ZAMORAK_MAGE ? MAGE_EXTRA_DAMAGE : 0);
        EntitySpell bolt = new EntitySpell(worldObj, this, teamId, spell, damage);
        bolt.aim(victim);
        worldObj.spawnEntityInWorld(bolt);
        playSound(spell == Spell.FIRE ? "mob.ghast.fireball" : "random.fizz", 0.6F, 1.2F + rand.nextFloat() * 0.3F);
    }

    /**
     * A lesser demon's fireball, between its blows: when it is due and the target is in sight and in range, the demon
     * stops and winds up, its arm raised, and then hurls it. Returns whether it is busy throwing this tick.
     */
    private boolean hurlFireball(EntityLivingBase victim) {
        if (hurlTicks > 0) {
            getNavigator().clearPathEntity();
            if (--hurlTicks > 0) {
                return true;
            }
            setDrawing(false);
            fireballTicks = FIREBALL_MIN_TICKS + rand.nextInt(FIREBALL_MAX_TICKS - FIREBALL_MIN_TICKS + 1);
            EntitySpell ball = new EntitySpell(worldObj, this, teamId, Spell.FIREBALL, FIREBALL_DAMAGE);
            ball.aim(victim);
            worldObj.spawnEntityInWorld(ball);
            swingItem();
            playSound("mob.ghast.fireball", 1.5F, 0.6F + rand.nextFloat() * 0.2F);
            return true;
        }
        if (fireballTicks > 0 || getDistanceSqToEntity(victim) > FIREBALL_RANGE * FIREBALL_RANGE
            || !getEntitySenses().canSee(victim)) {
            return false;
        }
        hurlTicks = FIREBALL_WINDUP;
        setDrawing(true);
        getNavigator().clearPathEntity();
        ((WorldServer) worldObj).func_147487_a("flame", posX, posY + height * 0.8, posZ, 15, 0.4, 0.4, 0.4, 0.02);
        playSound("mob.blaze.breathe", 1.0F, 0.6F);
        return true;
    }

    /** One of the spells it knows: a curse the victim is not under, one time in three, else a strike at random. */
    private Spell pickSpell(EntityLivingBase victim) {
        List<Spell> strikes = new ArrayList<>();
        List<Spell> curses = new ArrayList<>();
        for (Spell spell : kind.spells()) {
            if (!spell.isCurse()) {
                strikes.add(spell);
            } else if (victim != null && !victim.isPotionActive(spell.potion)) {
                curses.add(spell);
            }
        }
        if (!curses.isEmpty() && (strikes.isEmpty() || rand.nextInt(3) == 0)) {
            return curses.get(rand.nextInt(curses.size()));
        }
        return strikes.isEmpty() ? Spell.FIRE : strikes.get(rand.nextInt(strikes.size()));
    }

    /** Walks toward the nearest side of a building; gives up and guards when stuck too long. */
    private void walkTo(Building building) {
        if (getNavigator().noPath() && --planTicks <= 0) {
            planTicks = 20;
            double[] spot = building.nearestOutside(posX, posZ);
            walkToward(spot[0], spot[1]);
        }
        if (!madeProgress()) {
            guardHere();
        }
    }

    private void tickAttack() {
        EntityLivingBase current = resolveTarget();
        boolean tooFar = defending && hasPost
            && current != null
            && (stance == Stance.HOLD ? !inReach(current) : current.getDistance(postX, current.posY, postZ) > leash());
        if (current == null || tooFar) {
            if (defending) {
                backToPost();
            } else {
                nextAttack();
            }
            return;
        }
        getLookHelper().setLookPositionWithEntity(current, 30, 30);
        if (kind == UnitKind.LESSER_DEMON && hurlFireball(current)) {
            progressTicks = 0;
            return;
        }
        double distanceSq = getDistanceSq(current.posX, current.boundingBox.minY, current.posZ);
        if (hasSpot) {
            // First into line with the other archers, unless the target has gone elsewhere meanwhile.
            double drift = Math.hypot(current.posX - anchorX, current.posZ - anchorZ);
            if (drift > SPOT_DRIFT || distanceTo(spotX, spotZ) <= 1.2) {
                hasSpot = false;
                getNavigator().clearPathEntity();
            } else {
                if (getNavigator().noPath() && --planTicks <= 0) {
                    planTicks = 20;
                    walkToward(spotX, spotZ);
                }
                if (!madeProgress()) {
                    hasSpot = false;
                }
                return;
            }
        }
        if (kind.ranged && distanceSq <= archerRange() * archerRange() && getEntitySenses().canSee(current)) {
            getNavigator().clearPathEntity();
            if (drawBow()) {
                shoot(current);
            }
            progressTicks = 0;
            return;
        }
        double reach = width * 2 * width * 2 + current.width;
        if (!kind.ranged && distanceSq <= reach) {
            if (attackCooldown <= 0 && getEntitySenses().canSee(current)) {
                attackCooldown = 20;
                swingItem();
                attackEntityAsMob(current);
            }
            progressTicks = 0;
            return;
        }
        if (--planTicks <= 0) {
            planTicks = 10 + rand.nextInt(5);
            if (Math.sqrt(distanceSq) > LEG || !getNavigator().tryMoveToEntityLiving(current, SPEED)) {
                walkToward(current.posX, current.posZ);
            }
        }
        if (!madeProgress()) {
            if (defending) {
                backToPost();
            } else {
                // Out of reach: on to the next enemy queued, if any.
                nextAttack();
            }
        }
    }

    private void tickGuard() {
        if (!hasPost) {
            postX = posX;
            postZ = posZ;
            hasPost = true;
        }
        if (kind.fights() && stance != Stance.PASSIVE && --scanTicks <= 0) {
            scanTicks = 10 + rand.nextInt(5);
            EntityLivingBase enemy = Units.get()
                .findEnemyNear(this, guardRange());
            if (enemy != null && (stance != Stance.HOLD || inReach(enemy))) {
                defend(enemy);
                return;
            }
            double buildingRange = kind.ranged ? ARCHER_BUILDING_RANGE
                : stance == Stance.HOLD ? Buildings.REACH : GUARD_RANGE;
            Building building = Units.get()
                .findEnemyBuildingNear(this, buildingRange);
            if (building != null) {
                order = Order.ATTACK_BUILDING;
                buildingId = building.id;
                defending = true;
                startOrder();
                return;
            }
        }
        if (distanceTo(postX, postZ) > 2 && getNavigator().noPath() && --planTicks <= 0) {
            planTicks = 40;
            walkToward(postX, postZ);
        }
    }

    private void defend(EntityLivingBase enemy) {
        defend(enemy, false);
    }

    /** Fights the enemy from guard; one that hit the unit it follows further from its post. */
    private void defend(EntityLivingBase enemy, boolean hitBy) {
        order = Order.ATTACK;
        target = enemy;
        targetId = null;
        defending = true;
        startOrder();
        provoked = hitBy;
    }

    /**
     * How far from the unit's post an enemy it picked up on guard may be before it gives up and goes back. An archer
     * shoots from where it stands, so its range adds to how far it walks; whoever hit the unit it follows further.
     */
    private double leash() {
        double chase = provoked ? Math.max(stance.chaseRange, PURSUIT_RANGE) : stance.chaseRange;
        return chase + (kind.ranged ? archerRange() : 0);
    }

    private EntityLivingBase resolveTarget() {
        if (target != null && (!target.isEntityAlive() || target.worldObj != worldObj)) {
            target = null;
        }
        if (target instanceof EntityPlayer && Commanders.get()
            .isCommanding(target.getUniqueID())) {
            // They went up to command; their body stayed here.
            target = Commanders.get()
                .bodyOf(target.getUniqueID());
        }
        if (target == null && targetId != null) {
            target = Units.get()
                .findLiving(worldObj, targetId);
            if (target == null || !target.isEntityAlive()) {
                target = null;
                targetId = null;
            }
        }
        return target;
    }

    /**
     * Plans a path toward x, z: straight at it at most {@link #LEG} blocks, or if that fails a shorter leg off to
     * either side. When nothing works it walks straight ahead and hopes.
     */
    private void walkToward(double x, double z) {
        double[] next = nextWaypoint(x, z);
        x = next[0];
        z = next[1];
        double dx = x - posX;
        double dz = z - posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.01) {
            return;
        }
        double side = stalls % 2 == 0 ? 1 : -1;
        double[] angles = stalls == 0 ? new double[] { 0, 35, -35, 70, -70 }
            : new double[] { 35 * side, -35 * side, 70 * side, -70 * side, 0 };
        for (double leg : new double[] { Math.min(distance, LEG), Math.min(distance, LEG / 2) }) {
            for (double degrees : angles) {
                double angle = Math.toRadians(degrees);
                double ux = (dx * Math.cos(angle) - dz * Math.sin(angle)) / distance;
                double uz = (dx * Math.sin(angle) + dz * Math.cos(angle)) / distance;
                double wx = posX + ux * leg;
                double wz = posZ + uz * leg;
                Game game = GameManager.getGame();
                if (game != null) {
                    wx = Math.max(game.getMinX() + 0.5, Math.min(game.getMaxX() + 0.5, wx));
                    wz = Math.max(game.getMinZ() + 0.5, Math.min(game.getMaxZ() + 0.5, wz));
                }
                int wy = worldObj.getTopSolidOrLiquidBlock(MathHelper.floor_double(wx), MathHelper.floor_double(wz));
                if (getNavigator().tryMoveToXYZ(wx, wy, wz, SPEED)) {
                    return;
                }
            }
        }
        getMoveHelper().setMoveTo(posX + dx / distance * 4, posY, posZ + dz / distance * 4, SPEED);
    }

    /**
     * Where to head for now on the way to x, z: past one leg away, the next waypoint of a route planned around what
     * is in the way; close by, or with no route, x, z itself.
     */
    private double[] nextWaypoint(double x, double z) {
        if (distanceTo(x, z) <= LEG) {
            route = null;
            return new double[] { x, z };
        }
        if (route == null || Math.abs(routeX - x) > 3 || Math.abs(routeZ - z) > 3) {
            Game game = GameManager.getGame();
            route = game == null ? null : Routes.find(worldObj, game, teamId, posX, posZ, x, z);
            routeX = x;
            routeZ = z;
            if (route == null) {
                // No time to plan this tick, or no way found: straight at it, and plan again next time.
                return new double[] { x, z };
            }
        }
        while (!route.isEmpty() && distanceTo(route.get(0)[0], route.get(0)[1]) < 3) {
            route.remove(0);
        }
        return route.isEmpty() ? new double[] { x, z } : route.get(0);
    }

    /** Checks every two seconds that the unit got somewhere; returns false once it has been stuck too long. */
    private boolean madeProgress() {
        if (++progressTicks < 40) {
            return true;
        }
        progressTicks = 0;
        double moved = distanceTo(progressX, progressZ);
        progressX = posX;
        progressZ = posZ;
        if (moved >= 1.5) {
            stalls = 0;
            return true;
        }
        stalls++;
        planTicks = 0;
        route = null;
        getNavigator().clearPathEntity();
        if (stalls == 2) {
            // Stuck in a hole, on a ledge or in a corner of a building: a hop out to open ground, as in Millenaire.
            rescue();
        }
        return stalls < MAX_STALLS;
    }

    /**
     * The emergency way out for a stuck unit: puts it on the nearest open ground two or more blocks away, with room
     * to stand, out of water and off every building's footprint but a bridge's. Returns false if there is none within 8
     * blocks.
     */
    public boolean rescue() {
        int x0 = MathHelper.floor_double(posX);
        int y0 = MathHelper.floor_double(posY);
        int z0 = MathHelper.floor_double(posZ);
        Game game = GameManager.getGame();
        for (int radius = 2; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = x0 + dx;
                    int z = z0 + dz;
                    Building building = game == null ? null : game.getBuildingAt(x + 0.5, z + 0.5);
                    if (building != null && building.type != BuildingType.BRIDGE) {
                        continue;
                    }
                    for (int dy = 3; dy >= -12; dy--) {
                        int y = y0 + dy;
                        if (standable(x, y, z)) {
                            getNavigator().clearPathEntity();
                            setPositionAndUpdate(x + 0.5, y, z + 0.5);
                            stalls = 0;
                            progressX = posX;
                            progressZ = posZ;
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Whether the unit stands on leaves, up a tree. */
    private boolean onLeaves() {
        return onGround && worldObj
            .getBlock(MathHelper.floor_double(posX), MathHelper.floor_double(posY - 0.2), MathHelper.floor_double(posZ))
            .getMaterial() == Material.leaves;
    }

    /**
     * Whether a unit can stand with its feet at x, y, z: solid ground under it that is not leaves, two free blocks,
     * no water.
     */
    private boolean standable(int x, int y, int z) {
        Material ground = worldObj.getBlock(x, y - 1, z)
            .getMaterial();
        Material feet = worldObj.getBlock(x, y, z)
            .getMaterial();
        Material head = worldObj.getBlock(x, y + 1, z)
            .getMaterial();
        return ground.isSolid() && ground != Material.leaves
            && !feet.blocksMovement()
            && !feet.isLiquid()
            && !head.blocksMovement()
            && !head.isLiquid();
    }

    private double distanceTo(double x, double z) {
        double dx = x - posX;
        double dz = z - posZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---------------------------------------------------------------- combat

    @Override
    public boolean attackEntityAsMob(Entity victim) {
        float damage = (float) getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue();
        return victim.attackEntityFrom(DamageSource.causeMobDamage(this), damage);
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        // Only the server knows the teams; on a client the hit is just shown, and the server decides.
        if (worldObj.isRemote) {
            return super.attackEntityFrom(source, amount);
        }
        // A friendly arrow flies on past; turned away as a friend's hit, it would bounce off and drop.
        if (Combat.passesFriend(source, this)) {
            return false;
        }
        if (attacker != null && Units.get()
            .isAlly(this, attacker)) {
            return false;
        }
        // Heroes hit myth units, such as lesser demons, twice as hard, blows and arrows alike.
        if (kind.isMyth() && attacker instanceof EntityUnit && ((EntityUnit) attacker).kind.hero) {
            amount *= HERO_VS_MYTH_DAMAGE;
        }
        // A protection prayer against the hit's style takes most of its damage and all of its knockback.
        Prayer style = Prayer.styleOf(source);
        if (style != Prayer.NONE && style == prayer) {
            amount *= Prayer.DAMAGE_TAKEN;
            steadyTick = ticksExisted;
        }
        // Riders: on the move for over a second, every other arrow misses and flies on past. Those that hit do less,
        // a blow up close more; a blow at the horse is a blow at its rider.
        if (kind.isMounted()) {
            if (source.isProjectile()) {
                if (movingTicks > MOUNTED_DODGE_TICKS && rand.nextFloat() < MOUNTED_DODGE_CHANCE) {
                    Combat.flyPast(source, this);
                    return false;
                }
                amount *= MOUNTED_ARROW_DAMAGE;
            } else if (attacker instanceof EntityLivingBase && source.getSourceOfDamage() == attacker
                && !source.isMagicDamage()) {
                    amount *= MOUNTED_MELEE_DAMAGE;
                }
        }
        boolean hurt = super.attackEntityFrom(source, amount);
        computerPrays(style);
        // Guarding units hit back; units on the move keep going.
        if (hurt && kind.fights()
            && stance != Stance.PASSIVE
            && attacker instanceof EntityLivingBase
            && attacker != this
            && (order == Order.IDLE || defending)) {
            defend((EntityLivingBase) attacker, true);
        }
        return hurt;
    }

    /**
     * No knockback from a hit a prayer protected against, nor from what the attacker adds to it, as a sprint; and
     * none at all for a lesser demon, which nothing pushes about.
     */
    @Override
    public void knockBack(Entity attacker, float amount, double dx, double dz) {
        if (steadyTick != ticksExisted && kind != UnitKind.LESSER_DEMON) {
            super.knockBack(attacker, amount, dx, dz);
        }
    }

    @Override
    public void addVelocity(double x, double y, double z) {
        if (steadyTick != ticksExisted && kind != UnitKind.LESSER_DEMON) {
            super.addVelocity(x, y, z);
        }
    }

    /**
     * When the computer commands a hero who can switch prayers, it puts up the one against the style he was just hit
     * with, then leaves it a few seconds before switching again.
     */
    private void computerPrays(Prayer style) {
        if (kind != UnitKind.TRADIGEE || style == Prayer.NONE || style == prayer || prayerSwitchTicks > 0) {
            return;
        }
        Team team = team();
        if (team == null || !team.isAi()) {
            return;
        }
        // Playing alongside people, the computer commands only the units it trained.
        boolean alongsidePlayers = !team.getMembers()
            .isEmpty();
        if (alongsidePlayers && !aiControlled) {
            return;
        }
        setPrayer(style);
        prayerSwitchTicks = AI_PRAYER_SWITCH_TICKS;
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!worldObj.isRemote) {
            Units.get()
                .forget(this);
        }
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setString("Team", teamId);
        tag.setString("Kind", kind.id);
        tag.setInteger("Building", buildingId);
        int[] queued = new int[buildQueue.size()];
        for (int i = 0; i < queued.length; i++) {
            queued[i] = buildQueue.get(i);
        }
        tag.setIntArray("BuildQueue", queued);
        int[] carried = new int[carrying.length];
        for (int i = 0; i < carried.length; i++) {
            carried[i] = (int) Math.min(Integer.MAX_VALUE, carrying[i]);
        }
        tag.setIntArray("Carrying", carried);
        tag.setBoolean("Delivering", delivering);
        tag.setInteger("Number", number);
        tag.setIntArray("Tree", new int[] { treeX, treeY, treeZ });
        tag.setInteger("Mine", mineId);
        int[] spots = new int[torches.size() * 3];
        for (int i = 0; i < torches.size(); i++) {
            System.arraycopy(torches.get(i), 0, spots, i * 3, 3);
        }
        tag.setIntArray("Torches", spots);
        // A fight picked up while guarding is not worth keeping; the unit is back on guard after a restart.
        tag.setString("Stance", stance.id);
        tag.setString("Prayer", prayer.id);
        // Nor is following someone, who may not be online when the server starts again.
        tag.setString("Order", defending || order == Order.FOLLOW ? Order.IDLE.name() : order.name());
        tag.setInteger("TrainedAt", trainedAt);
        tag.setBoolean("AiControlled", aiControlled);
        tag.setDouble("GoalX", goalX);
        tag.setDouble("GoalZ", goalZ);
        tag.setBoolean("HasPost", hasPost);
        tag.setDouble("PostX", postX);
        tag.setDouble("PostZ", postZ);
        if (targetId != null && !defending) {
            tag.setString("Target", targetId.toString());
        }
        StringBuilder enemies = new StringBuilder();
        for (UUID id : attackQueue) {
            enemies.append(enemies.length() > 0 ? "," : "")
                .append(id);
        }
        tag.setString("AttackQueue", enemies.toString());
        tag.setBoolean("AttackMove", attackMove);
        tag.setDouble("AttackMoveX", attackMoveX);
        tag.setDouble("AttackMoveZ", attackMoveZ);
        if (order == Order.HUNT && preyId != null) {
            tag.setString("Prey", preyId.toString());
        }
        tag.setDouble("PreyX", preyX);
        tag.setDouble("PreyZ", preyZ);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        teamId = tag.getString("Team");
        UnitKind savedKind = UnitKind.byId(tag.getString("Kind"));
        kind = savedKind == null ? UnitKind.SOLDIER : savedKind;
        buildingId = tag.getInteger("Building");
        buildQueue.clear();
        for (int id : tag.getIntArray("BuildQueue")) {
            buildQueue.add(id);
        }
        trainedAt = tag.getInteger("TrainedAt");
        aiControlled = tag.getBoolean("AiControlled");
        int[] carried = tag.getIntArray("Carrying");
        for (int i = 0; i < carrying.length; i++) {
            carrying[i] = i < carried.length ? carried[i] : 0;
        }
        if (carried.length == 4) {
            // Saved before Stone and Metal became Gold.
            carrying[Resource.GOLD.ordinal()] = carried[2] + carried[3];
        }
        delivering = tag.getBoolean("Delivering");
        number = tag.getInteger("Number");
        int[] tree = tag.getIntArray("Tree");
        if (tree.length == 3) {
            treeX = tree[0];
            treeY = tree[1];
            treeZ = tree[2];
        }
        mineId = tag.getInteger("Mine");
        torches.clear();
        int[] spots = tag.getIntArray("Torches");
        for (int i = 0; i + 2 < spots.length; i += 3) {
            torches.add(new int[] { spots[i], spots[i + 1], spots[i + 2] });
        }
        Stance savedStance = Stance.byId(tag.getString("Stance"));
        stance = savedStance == null ? Stance.DEFENSIVE : savedStance;
        Prayer savedPrayer = Prayer.byId(tag.getString("Prayer"));
        if (savedPrayer == null || !setPrayer(savedPrayer)) {
            setPrayer(Prayer.always(kind));
        }
        try {
            order = Order.valueOf(tag.getString("Order"));
        } catch (IllegalArgumentException e) {
            order = Order.IDLE;
        }
        goalX = tag.getDouble("GoalX");
        goalZ = tag.getDouble("GoalZ");
        hasPost = tag.getBoolean("HasPost");
        postX = tag.getDouble("PostX");
        postZ = tag.getDouble("PostZ");
        targetId = null;
        if (tag.hasKey("Target")) {
            try {
                targetId = UUID.fromString(tag.getString("Target"));
            } catch (IllegalArgumentException e) {
                order = Order.IDLE;
            }
        }
        attackQueue.clear();
        for (String id : tag.getString("AttackQueue")
            .split(",")) {
            try {
                if (!id.isEmpty()) {
                    attackQueue.add(UUID.fromString(id));
                }
            } catch (IllegalArgumentException e) {
                // Skip it.
            }
        }
        attackMove = tag.getBoolean("AttackMove");
        attackMoveX = tag.getDouble("AttackMoveX");
        attackMoveZ = tag.getDouble("AttackMoveZ");
        if (order == Order.ATTACK && targetId == null) {
            order = Order.IDLE;
        }
        // Carcasses are not kept across restarts; a hunter goes after the next animal where the last one fell.
        preyId = null;
        if (tag.hasKey("Prey")) {
            try {
                preyId = UUID.fromString(tag.getString("Prey"));
            } catch (IllegalArgumentException e) {
                preyId = null;
            }
        }
        preyX = tag.hasKey("PreyX") ? tag.getDouble("PreyX") : posX;
        preyZ = tag.hasKey("PreyZ") ? tag.getDouble("PreyZ") : posZ;
    }
}
