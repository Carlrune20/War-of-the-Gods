package com.warofthegods.unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

import com.warofthegods.Config;
import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.Ruin;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Hostile mobs on the game map. Only zombies and skeletons, and only the ones spawned here: now and then, day or
 * night, a small group turns up in the wilderness, most often among trees, never near a town center or on a
 * building. Haunted ruins keep their own: spiders lurk in the ones hung with cobwebs, and zombies and skeletons walk
 * round others. They do not burn in daylight, notice enemies only from close by and chase them further once they have,
 * go after units as readily as after players, and often wear armor; some skeletons carry swords.
 */
public final class Mobs {

    /** Armor by slot, from boots (1) to helmet (4), and by material from leather to iron. */
    private static final Item[][] ARMOR = {
        { Items.leather_boots, Items.chainmail_boots, Items.golden_boots, Items.iron_boots },
        { Items.leather_leggings, Items.chainmail_leggings, Items.golden_leggings, Items.iron_leggings },
        { Items.leather_chestplate, Items.chainmail_chestplate, Items.golden_chestplate, Items.iron_chestplate },
        { Items.leather_helmet, Items.chainmail_helmet, Items.golden_helmet, Items.iron_helmet } };
    /** How likely each armor material is, out of the sum. */
    private static final int[] MATERIAL_WEIGHTS = { 40, 30, 10, 20 };
    /** Spawn spots are this far from a player, unit or building at least, so groups do not appear in plain view. */
    private static final int MIN_DISTANCE = 24;
    /** And at most this far from a player or unit, where the chunks are loaded. */
    private static final int MAX_DISTANCE = 64;
    /** Most hostile mobs near a spawn spot already for another group to come there. */
    private static final int MAX_NEARBY = 4;
    /** Blocks around a building where nothing spawns. */
    private static final int BUILDING_MARGIN = 6;

    private final Random random = new Random();
    private int ticks;

    /** Nothing hostile spawns in the game world the vanilla way; groups come from this class instead. */
    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.world.isRemote || !GameWorld.isGameWorld(event.world)) {
            return;
        }
        if (event.entityLiving instanceof IMob) {
            event.setResult(Event.Result.DENY);
            return;
        }
        // Animals do not spawn on buildings either.
        Game game = GameManager.getGame();
        if (game != null && onBuilding(game, event.x, event.z, 1)) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** No spawner, reinforcement call or jockey brings anything hostile but zombies and skeletons in. */
    @SubscribeEvent
    public void onJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isRemote || !GameWorld.isGameWorld(event.world) || !(event.entity instanceof IMob)) {
            return;
        }
        Entity entity = event.entity;
        if (entity.getClass() == EntitySpider.class && entity.getEntityData()
            .hasKey(RUIN_KEY)) {
            // A spider that lurks in a ruin; it comes alone.
            if (entity.riddenByEntity != null) {
                entity.riddenByEntity.setDead();
            }
            return;
        }
        if (!(entity instanceof EntityZombie || entity instanceof EntitySkeleton)
            || entity instanceof EntitySkeleton && ((EntitySkeleton) entity).getSkeletonType() != 0) {
            event.setCanceled(true);
            return;
        }
        EntityMob mob = (EntityMob) entity;
        if (mob instanceof EntityZombie) {
            EntityZombie zombie = (EntityZombie) mob;
            zombie.setVillager(false);
            zombie.setChild(false);
            // Hurt zombies call no friends.
            IAttributeInstance reinforcements = zombie.getAttributeMap()
                .getAttributeInstanceByName("zombie.spawnReinforcements");
            if (reinforcements != null) {
                reinforcements.setBaseValue(0);
            }
        }
        if (mob.riddenByEntity != null || mob.ridingEntity != null) {
            mob.mountEntity(null);
        }
        mob.getEntityAttribute(SharedMonsterAttributes.followRange)
            .setBaseValue(Config.mobDetectRange);
        // Their own fighting only goes after players, so they get some for units too.
        mob.targetTasks.addTask(2, new EntityAINearestAttackableTarget(mob, EntityUnit.class, 0, true));
        if (mob instanceof EntityZombie) {
            mob.tasks.addTask(3, new EntityAIAttackOnCollide(mob, EntityUnit.class, 1.0D, true));
        } else if (mob.getHeldItem() != null && !(mob.getHeldItem()
            .getItem() instanceof ItemBow)) {
                // A skeleton with a sword, loaded again. One just spawning holds nothing yet; equip() sees to it.
                addMelee((EntitySkeleton) mob);
            }
    }

    /**
     * Zombies and skeletons do not burn in daylight, and notice enemies only close by; once they have one, they
     * follow it further.
     */
    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        Entity entity = event.entity;
        if (entity.worldObj.isRemote || !(entity instanceof EntityZombie || entity instanceof EntitySkeleton)
            || !GameWorld.isGameWorld(entity.worldObj)) {
            return;
        }
        EntityMob mob = (EntityMob) entity;
        // The sun sets them alight after this event, and this puts it out again before it can do harm. Fire from
        // anything else, such as lava, still burns.
        if (mob.isBurning() && mob.worldObj.isDaytime()
            && !mob.handleLavaMovement()
            && mob.worldObj.canBlockSeeTheSky(
                MathHelper.floor_double(mob.posX),
                MathHelper.floor_double(mob.posY),
                MathHelper.floor_double(mob.posZ))) {
            mob.extinguish();
        }
        if (mob.ticksExisted % 10 == 0) {
            double range = mob.getAttackTarget() != null ? Config.mobChaseRange : Config.mobDetectRange;
            IAttributeInstance follow = mob.getEntityAttribute(SharedMonsterAttributes.followRange);
            if (follow.getBaseValue() != range) {
                follow.setBaseValue(range);
            }
        }
    }

    /**
     * A zombie or skeleton a player or a fighting unit hurts goes after them, from as far as it would chase them, so
     * archers and bows cannot pick it off from beyond where it notices enemies.
     */
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        Entity entity = event.entity;
        if (entity.worldObj.isRemote || !(entity instanceof EntityZombie || entity instanceof EntitySkeleton)
            || !GameWorld.isGameWorld(entity.worldObj)) {
            return;
        }
        Entity attacker = event.source.getEntity();
        boolean fighter = attacker instanceof EntityPlayer
            || attacker instanceof EntityUnit && !((EntityUnit) attacker).isWorker();
        if (!fighter || !attacker.isEntityAlive() || attacker == entity) {
            return;
        }
        EntityMob mob = (EntityMob) entity;
        mob.getEntityAttribute(SharedMonsterAttributes.followRange)
            .setBaseValue(Config.mobChaseRange);
        mob.setRevengeTarget((EntityLivingBase) attacker);
        mob.setAttackTarget((EntityLivingBase) attacker);
    }

    // ---------------------------------------------------------------- haunted ruins

    /** Entity data key holding the id of the ruin a mob haunts. */
    private static final String RUIN_KEY = "warofthegods.ruin";
    /** Most spiders lurking in a ruin at once. */
    private static final int RUIN_SPIDERS = 3;
    /** Most zombies and skeletons walking round a ruin at once. */
    private static final int RUIN_UNDEAD = 3;
    /** How far a ruin's mobs keep round it; one that wanders further off with nothing to chase is gone. */
    private static final int RUIN_REACH = 16;
    /** A ruin's mobs turn up only while a player or unit is this near, and none nearer than {@link #RUIN_HIDDEN}. */
    private static final int RUIN_NEAR = 96;
    private static final int RUIN_HIDDEN = 14;

    /**
     * Keeps the haunted ruins haunted: spiders in the ones with cobwebs, zombies and skeletons round the others,
     * topped up one at a time while someone is about but not in sight of the spot. They keep round their ruin; one
     * that strays off with nothing to chase vanishes, and another comes in time.
     */
    private void haunt(Game game, WorldServer world, List<EntityLiving> haunting) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (EntityLiving mob : haunting) {
            Ruin ruin = ruinOf(game, mob);
            if (ruin == null) {
                mob.setDead();
                continue;
            }
            boolean chasing = mob.getAttackTarget() != null;
            if (mob instanceof EntityCreature) {
                chasing |= ((EntityCreature) mob).getEntityToAttack() != null;
            }
            double reach = RUIN_REACH + Math.max(ruin.sizeX, ruin.sizeZ);
            if (!chasing && mob.getDistanceSq(ruin.getCenterX(), mob.posY, ruin.getCenterZ()) > reach * reach) {
                mob.setDead();
                continue;
            }
            counts.merge(ruin.id, 1, Integer::sum);
        }
        List<Entity> anchors = new ArrayList<>(
            Units.get()
                .getLoaded());
        for (Object player : world.playerEntities) {
            anchors.add((Entity) player);
        }
        for (Ruin ruin : game.getRuins()) {
            if (ruin.haunt == Ruin.QUIET) {
                continue;
            }
            int most = ruin.haunt == Ruin.SPIDERS ? RUIN_SPIDERS : RUIN_UNDEAD;
            if (counts.getOrDefault(ruin.id, 0) >= most) {
                continue;
            }
            double cx = ruin.getCenterX();
            double cz = ruin.getCenterZ();
            if (!world.blockExists((int) cx, ruin.groundY, (int) cz)) {
                continue;
            }
            boolean near = false;
            boolean seen = false;
            for (Entity anchor : anchors) {
                double distance = anchor.getDistanceSq(cx, anchor.posY, cz);
                near |= distance < RUIN_NEAR * RUIN_NEAR;
                seen |= distance < RUIN_HIDDEN * RUIN_HIDDEN;
            }
            if (near && !seen) {
                spawnAtRuin(world, ruin);
            }
        }
    }

    /** The ruin a mob haunts, or null if it is gone. */
    private static Ruin ruinOf(Game game, EntityLiving mob) {
        int id = mob.getEntityData()
            .getInteger(RUIN_KEY);
        for (Ruin ruin : game.getRuins()) {
            if (ruin.id == id) {
                return ruin;
            }
        }
        return null;
    }

    private void spawnAtRuin(WorldServer world, Ruin ruin) {
        for (int attempt = 0; attempt < 6; attempt++) {
            // Spiders in among the walls, the undead in and round the ruin.
            int spread = ruin.haunt == Ruin.SPIDERS ? 0 : 3;
            int x = ruin.minX - spread + random.nextInt(ruin.sizeX + 2 * spread);
            int z = ruin.minZ - spread + random.nextInt(ruin.sizeZ + 2 * spread);
            int y = world.getTopSolidOrLiquidBlock(x, z);
            if (Math.abs(y - ruin.groundY - 1) > 2 || world.getBlock(x, y - 1, z)
                .getMaterial()
                .isLiquid()) {
                continue;
            }
            EntityLiving mob;
            if (ruin.haunt == Ruin.SPIDERS) {
                mob = new EntitySpider(world);
            } else {
                mob = random.nextInt(3) == 0 ? new EntitySkeleton(world) : new EntityZombie(world);
            }
            mob.setLocationAndAngles(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
            if (!world.getCollidingBoundingBoxes(mob, mob.boundingBox)
                .isEmpty()) {
                continue;
            }
            mob.getEntityData()
                .setInteger(RUIN_KEY, ruin.id);
            if (mob instanceof EntityCreature) {
                ((EntityCreature) mob).setHomeArea(
                    (int) ruin.getCenterX(),
                    ruin.groundY + 1,
                    (int) ruin.getCenterZ(),
                    Math.max(ruin.sizeX, ruin.sizeZ) / 2 + 6);
            }
            if (!(mob instanceof EntitySpider)) {
                mob.onSpawnWithEgg(null);
                equip(mob);
            }
            world.spawnEntityInWorld(mob);
            return;
        }
    }

    // ---------------------------------------------------------------- spawning

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % Math.max(20, Config.mobSpawnSeconds * 20) != 0) {
            return;
        }
        Game game = DimensionManager.getWorld(0) == null ? null : GameManager.getGame();
        WorldServer world = DimensionManager.getWorld(GameWorld.dimensionId());
        if (game == null || world == null || Config.maxMobs <= 0) {
            return;
        }
        List<EntityLiving> hostile = new ArrayList<>();
        List<EntityLiving> haunting = new ArrayList<>();
        for (EntityLiving mob : Units.get()
            .getMobs(game, world)) {
            if (mob.getEntityData()
                .hasKey(RUIN_KEY)) {
                haunting.add(mob);
            } else if (mob instanceof IMob) {
                hostile.add(mob);
            }
        }
        haunt(game, world, haunting);
        if (hostile.size() >= Config.maxMobs) {
            return;
        }
        // A few tries for a good spot; most of the map is no good at a given moment.
        for (int attempt = 0; attempt < 8; attempt++) {
            int[] spot = pickSpot(game, world, hostile);
            if (spot != null) {
                spawnGroup(world, game, spot, Config.maxMobs - hostile.size());
                return;
            }
        }
    }

    /** A spot somewhere around a player or unit, out of sight and away from towns, or null if this try found none. */
    private int[] pickSpot(Game game, WorldServer world, List<EntityLiving> hostile) {
        List<Entity> anchors = new ArrayList<>(
            Units.get()
                .getLoaded());
        for (Object player : world.playerEntities) {
            anchors.add((Entity) player);
        }
        if (anchors.isEmpty()) {
            return null;
        }
        Entity anchor = anchors.get(random.nextInt(anchors.size()));
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        int x = MathHelper.floor_double(anchor.posX + Math.cos(angle) * distance);
        int z = MathHelper.floor_double(anchor.posZ + Math.sin(angle) * distance);
        if (!game.isInside(x, z) || !world.blockExists(x, 64, z)
            || random.nextDouble() >= townChance(game, x, z)
            || onBuilding(game, x, z, BUILDING_MARGIN)) {
            return null;
        }
        for (Object object : world.playerEntities) {
            if (((Entity) object).getDistanceSq(x, ((Entity) object).posY, z) < MIN_DISTANCE * MIN_DISTANCE) {
                return null;
            }
        }
        for (EntityUnit unit : Units.get()
            .getLoaded()) {
            if (unit.getDistanceSq(x, unit.posY, z) < MIN_DISTANCE * MIN_DISTANCE) {
                return null;
            }
        }
        int nearby = 0;
        for (EntityLiving mob : hostile) {
            nearby += mob.getDistanceSq(x, mob.posY, z) < 48 * 48 ? 1 : 0;
        }
        if (nearby >= MAX_NEARBY) {
            return null;
        }
        int y = world.getTopSolidOrLiquidBlock(x, z);
        Material ground = world.getBlock(x, y - 1, z)
            .getMaterial();
        if (ground.isLiquid() || ground == Material.leaves || ground == Material.ice) {
            return null;
        }
        if (!amongTrees(world, x, z) && random.nextDouble() >= Config.openGroundChance) {
            return null;
        }
        return new int[] { x, y, z };
    }

    /** Whether there are trees around x, z: leaves on top in a good few spots nearby. */
    private static boolean amongTrees(World world, int x, int z) {
        int leaves = 0;
        for (int dx = -8; dx <= 8; dx += 4) {
            for (int dz = -8; dz <= 8; dz += 4) {
                int top = world.getHeightValue(x + dx, z + dz) - 1;
                Material material = world.getBlock(x + dx, top, z + dz)
                    .getMaterial();
                if (material == Material.leaves || material == Material.wood) {
                    leaves++;
                }
            }
        }
        return leaves >= 4;
    }

    private void spawnGroup(WorldServer world, Game game, int[] spot, int room) {
        int size = Config.mobGroupMin + random.nextInt(Math.max(1, Config.mobGroupMax - Config.mobGroupMin + 1));
        size = Math.min(size, room);
        boolean skeletons = random.nextInt(3) == 0;
        for (int i = 0; i < size; i++) {
            int x = spot[0] + (i == 0 ? 0 : random.nextInt(5) - 2);
            int z = spot[2] + (i == 0 ? 0 : random.nextInt(5) - 2);
            int y = world.getTopSolidOrLiquidBlock(x, z);
            if (Math.abs(y - spot[1]) > 3 || onBuilding(game, x, z, BUILDING_MARGIN)
                || world.getBlock(x, y - 1, z)
                    .getMaterial()
                    .isLiquid()) {
                continue;
            }
            EntityMob mob = skeletons && random.nextInt(4) > 0 || !skeletons && random.nextInt(4) == 0
                ? new EntitySkeleton(world)
                : new EntityZombie(world);
            mob.setLocationAndAngles(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
            if (!world.getCollidingBoundingBoxes(mob, mob.boundingBox)
                .isEmpty()) {
                continue;
            }
            mob.onSpawnWithEgg(null);
            if (mob instanceof EntityZombie) {
                ((EntityZombie) mob).setVillager(false);
                ((EntityZombie) mob).setChild(false);
            }
            equip(mob);
            world.spawnEntityInWorld(mob);
        }
    }

    /**
     * How likely a hostile mob may spawn at x, z: never within {@link Config#townSafeRadius} of a town center, rising
     * to always at {@link Config#townRadius} and beyond.
     */
    static double townChance(Game game, double x, double z) {
        double nearest = nearestTownCenter(game, x, z);
        if (nearest < Config.townSafeRadius) {
            return 0;
        }
        if (nearest >= Config.townRadius) {
            return 1;
        }
        return (nearest - Config.townSafeRadius) / Math.max(1, Config.townRadius - Config.townSafeRadius);
    }

    private static double nearestTownCenter(Game game, double x, double z) {
        double nearest = Double.MAX_VALUE;
        for (Building building : game.getBuildings()) {
            if (building.type == BuildingType.TOWN_HALL) {
                nearest = Math.min(nearest, building.distanceTo(x, z));
            }
        }
        return nearest;
    }

    /** Whether x, z is on a building or building site, or within margin blocks of one. */
    static boolean onBuilding(Game game, double x, double z, double margin) {
        for (Building building : game.getBuildings()) {
            if (building.covers(x + 0.5, z + 0.5, margin)) {
                return true;
            }
        }
        return false;
    }

    private void equip(EntityLiving mob) {
        if (random.nextDouble() < Config.mobArmorChance) {
            int material = pickMaterial();
            for (int slot = 1; slot <= 4; slot++) {
                // Mostly one material; now and then a piece of another.
                if (random.nextInt(3) > 0) {
                    int piece = random.nextInt(4) == 0 ? pickMaterial() : material;
                    mob.setCurrentItemOrArmor(slot, new ItemStack(ARMOR[slot - 1][piece]));
                    mob.setEquipmentDropChance(slot, 0.05F);
                }
            }
        }
        if (mob instanceof EntitySkeleton && random.nextDouble() < Config.skeletonSwordChance) {
            mob.setCurrentItemOrArmor(0, new ItemStack(random.nextInt(3) == 0 ? Items.iron_sword : Items.stone_sword));
            mob.setEquipmentDropChance(0, 0.05F);
            ((EntitySkeleton) mob).setCombatTask();
            addMelee((EntitySkeleton) mob);
        }
    }

    private int pickMaterial() {
        int total = 0;
        for (int weight : MATERIAL_WEIGHTS) {
            total += weight;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < MATERIAL_WEIGHTS.length; i++) {
            roll -= MATERIAL_WEIGHTS[i];
            if (roll < 0) {
                return i;
            }
        }
        return 0;
    }

    private static void addMelee(EntityCreature skeleton) {
        skeleton.tasks.addTask(4, new EntityAIAttackOnCollide(skeleton, EntityUnit.class, 1.2D, false));
    }
}
