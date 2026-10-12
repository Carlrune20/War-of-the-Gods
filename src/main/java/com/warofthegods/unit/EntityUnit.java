package com.warofthegods.unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.warofthegods.Config;
import com.warofthegods.building.Buildings;
import com.warofthegods.game.Building;
import com.warofthegods.game.Faction;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Team;
import com.warofthegods.game.Tree;
import com.warofthegods.game.UnitKind;
import com.warofthegods.game.Upgrade;
import com.warofthegods.rts.Commanders;
import com.warofthegods.web.WebMap;

/**
 * A unit of a team, with the player model and its team's color on dyed leather armor. Soldiers carry an iron sword
 * and follow orders from the web map: walk somewhere, attack something, or stand guard, in which case they fight
 * enemies and enemy buildings that come close and walk back to their post afterwards. Workers carry an axe and never
 * fight: they walk, build at building sites, and work farms and quarries, carrying what they gather to the nearest
 * town hall or storehouse whenever their hands are full.
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
        /** A worker working at a farm or quarry, or carrying what it gathered there to a store. */
        WORK,
        /** A soldier attacking an enemy building. */
        ATTACK_BUILDING,
        /** A worker chopping a tree, or carrying the wood to a store. */
        CHOP,
        /** A worker putting up torches. */
        LIGHT
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
    /** How far archers shoot, before upgrades. */
    public static final double ARCHER_RANGE = 14;
    /** How far archers shoot at buildings. */
    private static final double ARCHER_BUILDING_RANGE = 9;
    /** How close a worker has to be to the foot of a tree to chop it. */
    private static final double CHOP_REACH = 2.2;
    /** How close a worker has to be to where a torch goes. */
    private static final double TORCH_REACH = 2.5;
    /** How far a worker looks for the next tree once one falls. */
    private static final int NEXT_TREE_RANGE = 14;

    private static final int WATCH_COLOR = 20;
    private static final int WATCH_KIND = 21;
    private static final int WATCH_FACTION = 22;

    private String teamId = "";
    private UnitKind kind = UnitKind.SOLDIER;
    private int number;
    /** The foot of the trunk of the tree a worker chops. */
    private int treeX;
    private int treeY;
    private int treeZ;
    private int chopTicks;
    /** Where a worker still has to put torches, in order: x, y, z each. */
    private final List<int[]> torches = new ArrayList<>();
    private Order order = Order.IDLE;
    private Stance stance = Stance.DEFENSIVE;
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
    /** The building a build, work or attack order is for. */
    private int buildingId;
    /** Whether a worker is at the building site or workplace it was sent to, working. */
    private boolean working;
    /** What a worker carries, by resource ordinal. */
    private final long[] carrying = new long[Resource.values().length];
    /** Whether a worker is taking what it carries to a store. */
    private boolean delivering;

    private int attackCooldown;
    private int planTicks;
    private int scanTicks;
    private int progressTicks;
    private double progressX;
    private double progressZ;
    private int stalls;
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
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.3);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(48);
        getAttributeMap().registerAttribute(SharedMonsterAttributes.attackDamage)
            .setBaseValue(1);
    }

    /** Makes a new unit a member of the team, with its name, skin and tool. */
    void setTeam(Team team, UnitKind kind, int number) {
        this.teamId = team.getId();
        this.kind = kind;
        this.number = number;
        setCurrentItemOrArmor(0, new ItemStack(tool()));
        updateTeam(team);
        setHealth(getMaxHealth());
    }

    /** What it holds when its hands are free: a worker's axe, a warrior's sword, an archer's bow. */
    private Item tool() {
        return kind == UnitKind.WORKER ? Items.iron_axe : kind == UnitKind.ARCHER ? Items.bow : Items.iron_sword;
    }

    /** Keeps name, skin and strength in step with the team's name, color, faction and upgrades. */
    void updateTeam(Team team) {
        String name = team.getColor() + team.getName() + " " + kind.displayName.toLowerCase() + " " + number;
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
        // Units from before skins wore their team's color as leather armor.
        for (int slot = 1; slot < 5; slot++) {
            if (getEquipmentInSlot(slot) != null) {
                setCurrentItemOrArmor(slot, null);
            }
        }
        applyUpgrades(team);
    }

    /** Sets its health and strength by its kind and the team's upgrades, healing what it gains. */
    public void applyUpgrades(Team team) {
        double health = Config.unitHealth;
        double damage = 1;
        if (kind.fights()) {
            health += (team.has(Upgrade.PADDED_ARMOR) ? 6 : 0) + (team.has(Upgrade.PLATE_ARMOR) ? 8 : 0);
        }
        if (kind == UnitKind.SOLDIER) {
            damage += (team.has(Upgrade.FORGED_BLADES) ? 2 : 0) + (team.has(Upgrade.TEMPERED_STEEL) ? 2 : 0);
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

    /** The foot of the tree a worker chops, as x, y, z, or null when it chops none. */
    public int[] getTree() {
        return order == Order.CHOP ? new int[] { treeX, treeY, treeZ } : null;
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

    /** Whether the unit is a worker at the farm or quarry with that id, working with room left to carry more. */
    public boolean isWorkingAt(int workplaceId) {
        return order == Order.WORK && working && !delivering && buildingId == workplaceId;
    }

    /** Whether a worker is on its way to a store with what it carries. */
    public boolean isDelivering() {
        return (order == Order.WORK || order == Order.CHOP) && delivering;
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
        showCarrying();
    }

    /** Holds what it carries most of, or its axe when empty-handed. */
    private void showCarrying() {
        int most = -1;
        for (int i = 0; i < carrying.length; i++) {
            if (carrying[i] > 0 && (most < 0 || carrying[i] > carrying[most])) {
                most = i;
            }
        }
        Item item = most < 0 ? tool() : CARRIED_ITEMS[most];
        ItemStack held = getHeldItem();
        if (held == null || held.getItem() != item) {
            setCurrentItemOrArmor(0, new ItemStack(item));
        }
    }

    private static final Item[] CARRIED_ITEMS = { Items.wheat, Item.getItemFromBlock(Blocks.log),
        Item.getItemFromBlock(Blocks.cobblestone), Items.iron_ingot };

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
            case CHOP: {
                if (delivering) {
                    Building store = store();
                    return store == null ? null : store.nearestOutside(posX, posZ);
                }
                return working ? null : new double[] { treeX + 0.5, treeZ + 0.5 };
            }
            default:
                return hasPost && distanceTo(postX, postZ) > 2 ? new double[] { postX, postZ } : null;
        }
    }

    public UUID getTargetId() {
        return order == Order.ATTACK && target != null ? target.getUniqueID() : null;
    }

    // ---------------------------------------------------------------- orders

    void orderMove(double x, double z) {
        order = Order.MOVE;
        goalX = x;
        goalZ = z;
        target = null;
        targetId = null;
        defending = false;
        startOrder();
    }

    /** Sends a worker to a building site to build it, or to a farm or quarry to work it. */
    void orderBuild(Building building) {
        order = building.isComplete() ? Order.WORK : Order.BUILD;
        buildingId = building.id;
        working = false;
        delivering = order == Order.WORK && getCarried() >= capacity();
        target = null;
        targetId = null;
        defending = false;
        startOrder();
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
        order = Order.ATTACK_BUILDING;
        buildingId = building.id;
        target = null;
        targetId = null;
        defending = false;
        startOrder();
    }

    void orderAttack(EntityLivingBase target) {
        order = Order.ATTACK;
        this.target = target;
        targetId = target.getUniqueID();
        defending = false;
        startOrder();
    }

    /** Stops and guards the spot the unit is standing on. */
    void orderStop() {
        guardHere();
        getNavigator().clearPathEntity();
    }

    private void startOrder() {
        stalls = 0;
        planTicks = 0;
        progressTicks = 0;
        progressX = posX;
        progressZ = posZ;
        getNavigator().clearPathEntity();
    }

    private void guardHere() {
        order = Order.IDLE;
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

    @Override
    protected void updateAITick() {
        super.updateAITick();
        if (attackCooldown > 0) {
            attackCooldown--;
        }
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
            case LIGHT:
                tickLight();
                break;
            default:
                tickGuard();
        }
    }

    private void tickMove() {
        if (distanceTo(goalX, goalZ) < ARRIVED) {
            getNavigator().clearPathEntity();
            guardHere();
            return;
        }
        if (getNavigator().noPath() && --planTicks <= 0) {
            planTicks = 20;
            walkToward(goalX, goalZ);
        }
        if (!madeProgress()) {
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
            getNavigator().clearPathEntity();
            guardHere();
            return;
        }
        if (site.isComplete()) {
            // Built, by this worker and others: farmers and quarrymen stay to work it.
            if (site.type.isWorkplace()) {
                orderBuild(site);
            } else {
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
            if (ticksExisted % 30 == 0) {
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
            int[] next = Trees.nearest(worldObj, game, treeX + 0.5, treeY, treeZ + 0.5, NEXT_TREE_RANGE);
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
        if (Math.sqrt(dx * dx + dz * dz) > CHOP_REACH) {
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
        if (team != null && team.has(Upgrade.SHARP_AXES)) {
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
        showCarrying();
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
                getNavigator().clearPathEntity();
                guardHere();
            }
            return;
        }
        // Enemies coming at a unit on guard come first.
        if (defending && --scanTicks <= 0) {
            scanTicks = 10 + rand.nextInt(5);
            EntityLivingBase enemy = Units.get()
                .findEnemyNear(this, guardRange());
            if (enemy != null) {
                defend(enemy);
                return;
            }
        }
        if (building.distanceTo(posX, posZ) <= buildingReach()) {
            getNavigator().clearPathEntity();
            getLookHelper().setLookPosition(building.getCenterX(), building.groundY + 1, building.getCenterZ(), 30, 30);
            if (attackCooldown <= 0) {
                int damage = Config.unitBuildingDamage;
                if (kind == UnitKind.ARCHER) {
                    attackCooldown = 30;
                    damage /= 2;
                    EntityTeamArrow arrow = new EntityTeamArrow(worldObj, this);
                    arrow.aimAt(building.getCenterX(), building.groundY + 2, building.getCenterZ(), 0);
                    worldObj.spawnEntityInWorld(arrow);
                    playSound("random.bow", 1.0F, 1.0F / (rand.nextFloat() * 0.4F + 0.8F));
                } else {
                    attackCooldown = 20;
                    swingItem();
                }
                Buildings.get()
                    .damage(building, damage, getCommandSenderName().replaceAll("\u00a7.", ""));
            }
            progressTicks = 0;
            return;
        }
        walkTo(building);
    }

    private double buildingReach() {
        return kind == UnitKind.ARCHER ? ARCHER_BUILDING_RANGE : Buildings.REACH;
    }

    /** How far an archer shoots, with its team's upgrades. */
    private double archerRange() {
        Team team = team();
        return ARCHER_RANGE + (team != null && team.has(Upgrade.FLETCHING) ? 4 : 0);
    }

    /** Range at which a guarding unit notices enemies: farther for archers, who shoot from where they stand. */
    private double guardRange() {
        return (kind == UnitKind.ARCHER ? archerRange() : GUARD_RANGE) * stance.rangeFactor;
    }

    /** Whether the unit can hit the target from where it stands, by sword or by bow. */
    private boolean inReach(EntityLivingBase target) {
        double distanceSq = getDistanceSq(target.posX, target.boundingBox.minY, target.posZ);
        if (kind == UnitKind.ARCHER) {
            return distanceSq <= archerRange() * archerRange();
        }
        double reach = width * 2 * width * 2 + target.width;
        return distanceSq <= reach + 1;
    }

    private void shoot(EntityLivingBase victim) {
        Team team = team();
        EntityTeamArrow arrow = new EntityTeamArrow(worldObj, this);
        arrow.aim(victim, 3 + (team != null && team.has(Upgrade.FLETCHING) ? 1 : 0));
        worldObj.spawnEntityInWorld(arrow);
        playSound("random.bow", 1.0F, 1.0F / (rand.nextFloat() * 0.4F + 0.8F));
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
            && (stance == Stance.HOLD ? !inReach(current)
                : current.getDistance(postX, current.posY, postZ) > stance.chaseRange);
        if (current == null || tooFar) {
            if (defending) {
                backToPost();
            } else {
                getNavigator().clearPathEntity();
                guardHere();
            }
            return;
        }
        getLookHelper().setLookPositionWithEntity(current, 30, 30);
        double distanceSq = getDistanceSq(current.posX, current.boundingBox.minY, current.posZ);
        if (kind == UnitKind.ARCHER && distanceSq <= archerRange() * archerRange()
            && getEntitySenses().canSee(current)) {
            getNavigator().clearPathEntity();
            if (attackCooldown <= 0) {
                attackCooldown = 30;
                shoot(current);
            }
            progressTicks = 0;
            return;
        }
        double reach = width * 2 * width * 2 + current.width;
        if (kind != UnitKind.ARCHER && distanceSq <= reach) {
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
                guardHere();
            }
        }
    }

    private void tickGuard() {
        if (!hasPost) {
            postX = posX;
            postZ = posZ;
            hasPost = true;
        }
        if (!isWorker() && stance != Stance.PASSIVE && --scanTicks <= 0) {
            scanTicks = 10 + rand.nextInt(5);
            EntityLivingBase enemy = Units.get()
                .findEnemyNear(this, guardRange());
            if (enemy != null && (stance != Stance.HOLD || inReach(enemy))) {
                defend(enemy);
                return;
            }
            double buildingRange = kind == UnitKind.ARCHER ? ARCHER_BUILDING_RANGE
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
        order = Order.ATTACK;
        target = enemy;
        targetId = null;
        defending = true;
        startOrder();
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
            route = game == null ? null : Routes.find(worldObj, game, posX, posZ, x, z);
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
        return stalls < MAX_STALLS;
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
        if (attacker != null && Units.get()
            .isAlly(this, attacker)) {
            return false;
        }
        boolean hurt = super.attackEntityFrom(source, amount);
        // Guarding units hit back; units on the move keep going.
        if (hurt && !isWorker()
            && stance != Stance.PASSIVE
            && attacker instanceof EntityLivingBase
            && attacker != this
            && (order == Order.IDLE || defending)) {
            defend((EntityLivingBase) attacker);
        }
        return hurt;
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
        int[] carried = new int[carrying.length];
        for (int i = 0; i < carried.length; i++) {
            carried[i] = (int) Math.min(Integer.MAX_VALUE, carrying[i]);
        }
        tag.setIntArray("Carrying", carried);
        tag.setBoolean("Delivering", delivering);
        tag.setInteger("Number", number);
        tag.setIntArray("Tree", new int[] { treeX, treeY, treeZ });
        int[] spots = new int[torches.size() * 3];
        for (int i = 0; i < torches.size(); i++) {
            System.arraycopy(torches.get(i), 0, spots, i * 3, 3);
        }
        tag.setIntArray("Torches", spots);
        // A fight picked up while guarding is not worth keeping; the unit is back on guard after a restart.
        tag.setString("Stance", stance.id);
        tag.setString("Order", defending ? Order.IDLE.name() : order.name());
        tag.setDouble("GoalX", goalX);
        tag.setDouble("GoalZ", goalZ);
        tag.setBoolean("HasPost", hasPost);
        tag.setDouble("PostX", postX);
        tag.setDouble("PostZ", postZ);
        if (targetId != null && !defending) {
            tag.setString("Target", targetId.toString());
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        teamId = tag.getString("Team");
        UnitKind savedKind = UnitKind.byId(tag.getString("Kind"));
        kind = savedKind == null ? UnitKind.SOLDIER : savedKind;
        buildingId = tag.getInteger("Building");
        int[] carried = tag.getIntArray("Carrying");
        for (int i = 0; i < carrying.length; i++) {
            carrying[i] = i < carried.length ? carried[i] : 0;
        }
        delivering = tag.getBoolean("Delivering");
        number = tag.getInteger("Number");
        int[] tree = tag.getIntArray("Tree");
        if (tree.length == 3) {
            treeX = tree[0];
            treeY = tree[1];
            treeZ = tree[2];
        }
        torches.clear();
        int[] spots = tag.getIntArray("Torches");
        for (int i = 0; i + 2 < spots.length; i += 3) {
            torches.add(new int[] { spots[i], spots[i + 1], spots[i + 2] });
        }
        Stance savedStance = Stance.byId(tag.getString("Stance"));
        stance = savedStance == null ? Stance.DEFENSIVE : savedStance;
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
        if (order == Order.ATTACK && targetId == null) {
            order = Order.IDLE;
        }
    }
}
