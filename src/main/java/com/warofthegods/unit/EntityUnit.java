package com.warofthegods.unit;

import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.warofthegods.Config;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Team;

/**
 * A soldier of a team, with the player model, its team's color on dyed leather armor and an iron sword. Units follow
 * orders from the web map: walk somewhere, attack something, or stand guard, in which case they fight enemies that
 * come close and walk back to their post afterwards.
 *
 * <p>
 * Walking uses vanilla pathfinding in legs of at most {@link #LEG} blocks toward the goal, since vanilla paths only
 * reach a few dozen blocks. Long-range planning comes later.
 */
public class EntityUnit extends EntityCreature {

    public enum Order {
        IDLE,
        MOVE,
        ATTACK
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

    /** Makes a new unit a member of the team, with its name, armor and sword. */
    void setTeam(Team team, int number) {
        this.teamId = team.getId();
        this.number = number;
        setCurrentItemOrArmor(0, new ItemStack(Items.iron_sword));
        updateTeam(team);
        setHealth(getMaxHealth());
    }

    /** Keeps name and armor in step with the team's name and color. */
    void updateTeam(Team team) {
        String name = team.getColor() + team.getName() + " " + number;
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
        if (--scanTicks <= 0) {
            scanTicks = 10 + rand.nextInt(5);
            EntityLivingBase enemy = Units.get()
                .findEnemyNear(this, GUARD_RANGE);
            if (enemy != null) {
                defend(enemy);
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
        if (hurt && attacker instanceof EntityLivingBase
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
