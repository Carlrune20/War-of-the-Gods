package com.warofthegods.unit;

import java.util.Arrays;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAISwimming;
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
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Resource;
import com.warofthegods.game.Team;
import com.warofthegods.game.UnitKind;

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
        ATTACK_BUILDING
    }

    /** Blocks walked per path before planning the next one. */
    private static final double LEG = 24;
    /** How close a unit has to get to where it was sent. */
    private static final double ARRIVED = 1.5;
    /** Range at which guarding units notice enemies. */
    private static final double GUARD_RANGE = 10;
    /** How far from its post a guarding unit chases an enemy before giving up. */
    private static final double CHASE_RANGE = 20;
    /** Times in a row a unit may fail to make progress before it gives up on an order. */
    private static final int MAX_STALLS = 6;
    private static final double SPEED = 1.0;

    private String teamId = "";
    private UnitKind kind = UnitKind.SOLDIER;
    private int number;
    private int armorColor = -1;
    private Order order = Order.IDLE;
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
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(Config.unitHealth);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.3);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(48);
        getAttributeMap().registerAttribute(SharedMonsterAttributes.attackDamage)
            .setBaseValue(1);
    }

    /** Makes a new unit a member of the team, with its name, armor and tool. */
    void setTeam(Team team, UnitKind kind, int number) {
        this.teamId = team.getId();
        this.kind = kind;
        this.number = number;
        setCurrentItemOrArmor(0, new ItemStack(kind == UnitKind.WORKER ? Items.iron_axe : Items.iron_sword));
        updateTeam(team);
        setHealth(getMaxHealth());
    }

    /** Keeps name and armor in step with the team's name and color. */
    void updateTeam(Team team) {
        String name = team.getColor() + team.getName() + (kind == UnitKind.WORKER ? " worker " : " ") + number;
        if (!name.equals(getCustomNameTag())) {
            setCustomNameTag(name);
            setAlwaysRenderNameTag(true);
        }
        int color = team.getRgb();
        if (color != armorColor) {
            armorColor = color;
            setCurrentItemOrArmor(1, dyed(Items.leather_boots, color));
            setCurrentItemOrArmor(2, dyed(Items.leather_leggings, color));
            setCurrentItemOrArmor(3, dyed(Items.leather_chestplate, color));
        }
    }

    private static ItemStack dyed(Item item, int color) {
        ItemStack stack = new ItemStack(item);
        NBTTagCompound display = new NBTTagCompound();
        display.setInteger("color", color);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("display", display);
        stack.setTagCompound(tag);
        return stack;
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
        return order == Order.WORK && delivering;
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
        if (getCarried() >= Config.carryCapacity) {
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
        Item item = most < 0 ? (isWorker() ? Items.iron_axe : Items.iron_sword) : CARRIED_ITEMS[most];
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
                return building == null || building.distanceTo(posX, posZ) <= Buildings.REACH ? null
                    : building.nearestOutside(posX, posZ);
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
        delivering = order == Order.WORK && getCarried() >= Config.carryCapacity;
        target = null;
        targetId = null;
        defending = false;
        startOrder();
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
                .findEnemyNear(this, GUARD_RANGE);
            if (enemy != null) {
                defend(enemy);
                return;
            }
        }
        if (building.distanceTo(posX, posZ) <= Buildings.REACH) {
            getNavigator().clearPathEntity();
            getLookHelper().setLookPosition(building.getCenterX(), building.groundY + 1, building.getCenterZ(), 30, 30);
            if (attackCooldown <= 0) {
                attackCooldown = 20;
                swingItem();
                Buildings.get()
                    .damage(building, Config.unitBuildingDamage, getCommandSenderName().replaceAll("\u00a7.", ""));
            }
            progressTicks = 0;
            return;
        }
        walkTo(building);
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
            && current.getDistance(postX, current.posY, postZ) > CHASE_RANGE;
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
        double reach = width * 2 * width * 2 + current.width;
        double distanceSq = getDistanceSq(current.posX, current.boundingBox.minY, current.posZ);
        if (distanceSq <= reach) {
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
        if (!isWorker() && --scanTicks <= 0) {
            scanTicks = 10 + rand.nextInt(5);
            EntityLivingBase enemy = Units.get()
                .findEnemyNear(this, GUARD_RANGE);
            if (enemy != null) {
                defend(enemy);
                return;
            }
            Building building = Units.get()
                .findEnemyBuildingNear(this, GUARD_RANGE);
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
        tag.setInteger("ArmorColor", armorColor);
        // A fight picked up while guarding is not worth keeping; the unit is back on guard after a restart.
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
        armorColor = tag.getInteger("ArmorColor");
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
