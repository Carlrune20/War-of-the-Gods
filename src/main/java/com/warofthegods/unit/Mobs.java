package com.warofthegods.unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
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
import com.warofthegods.game.GameMap;
import com.warofthegods.game.Ruin;
import com.warofthegods.game.SafeMode;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Hostile mobs on the game map. Only zombies and skeletons, and only the ones spawned here: now and then, day or night,
 * a small group turns up in the wilderness, most often among trees, never near a town center or on a building. Haunted
 * ruins keep their own: spiders lurk in the ones hung with cobwebs, and zombies and skeletons walk round others, until
 * someone builds nearby. They do not burn in daylight, notice enemies only from close by and chase them further once
 * they have, go after units as readily as after players, and often wear armor; some skeletons carry swords.
 *
 * <p>
 * Forinthry, the Wilderness, has its own creatures instead (see {@link EntityWildMob}): ghosts, hill giants,
 * hellhounds, red spiders, gargoyles and chromatic dragons roam it, in bigger groups and far more often than
 * Misthalin's undead, about eight times as many, anywhere outside towns, and its ruins are haunted by ghosts, giants,
 * bandits or hellhounds by their kind.
 */
public final class Mobs {

    /** Armor by slot, from boots (1) to helmet (4), and by material: leather or iron, never gold or chainmail. */
    private static final Item[][] ARMOR = { { Items.leather_boots, Items.iron_boots },
        { Items.leather_leggings, Items.iron_leggings }, { Items.leather_chestplate, Items.iron_chestplate },
        { Items.leather_helmet, Items.iron_helmet } };
    /** How likely each armor material is, out of the sum. */
    private static final int[] MATERIAL_WEIGHTS = { 65, 35 };
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
        if (entity instanceof EntityWildMob) {
            return;
        }
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
        if (entity.worldObj.isRemote || !ours(entity) || !GameWorld.isGameWorld(entity.worldObj)) {
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
        if (entity.worldObj.isRemote || !ours(entity) || !GameWorld.isGameWorld(entity.worldObj)) {
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

    /** Whether an entity is one of the hostile mobs this class looks after. */
    private static boolean ours(Entity entity) {
        return entity instanceof EntityZombie || entity instanceof EntitySkeleton || entity instanceof EntityWildMob;
    }

    private static boolean wilderness(Game game) {
        return game.getMap() == GameMap.FORINTHRY;
    }

    // ---------------------------------------------------------------- haunted ruins

    /** Entity data key holding the id of the ruin a mob haunts. */
    private static final String RUIN_KEY = "warofthegods.ruin";
    /** Most spiders lurking in a ruin at once. */
    private static final int RUIN_SPIDERS = 3;
    /** Most zombies and skeletons walking round a ruin at once. */
    private static final int RUIN_UNDEAD = 3;
    /** Most bandits in a camp, and hill giants and gargoyles round a fortress, at once. */
    private static final int RUIN_BANDITS = 6;
    private static final int RUIN_GIANTS = 2;
    /** Most of the old fortress's ghosts, undead and spiders about it at once. */
    private static final int RUIN_FORTRESS = 8;
    /** How far a ruin's mobs keep round it; one that wanders further off with nothing to chase is gone. */
    private static final int RUIN_REACH = 16;
    /** A ruin's mobs turn up only while a player or unit is this near, and none nearer than {@link #RUIN_HIDDEN}. */
    private static final int RUIN_NEAR = 96;
    private static final int RUIN_HIDDEN = 14;
    /** A building this near a ruin lays it to rest: its mobs go and no more come. */
    private static final int RUIN_SETTLED = 32;

    /**
     * Keeps the haunted ruins haunted: spiders in the ones with cobwebs, zombies and skeletons round the others,
     * topped up one at a time while someone is about but not in sight of the spot. They keep round their ruin; one
     * that strays off with nothing to chase vanishes, and another comes in time. Once anyone builds near a ruin, its
     * mobs leave as soon as they have nothing to chase, and no more come.
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
            boolean strayed = mob.getDistanceSq(ruin.getCenterX(), mob.posY, ruin.getCenterZ()) > reach * reach;
            if (!chasing && (strayed || settled(game, ruin))) {
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
            if (ruin.haunt == Ruin.QUIET || settled(game, ruin)) {
                continue;
            }
            int most = ruin.haunt == Ruin.SPIDERS ? RUIN_SPIDERS
                : ruin.haunt == Ruin.BANDITS ? RUIN_BANDITS
                    : ruin.haunt == Ruin.GIANTS ? RUIN_GIANTS
                        : ruin.haunt == Ruin.FORTRESS ? RUIN_FORTRESS : RUIN_UNDEAD;
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
                // Bandits keep their camp as a band, all of them there at once; the rest turn up one at a time.
                int count = ruin.haunt == Ruin.BANDITS ? most - counts.getOrDefault(ruin.id, 0) : 1;
                for (int i = 0; i < count; i++) {
                    spawnAtRuin(game, world, ruin);
                }
            }
        }
    }

    /**
     * Whether someone has built near a ruin: a building or building site of any team within {@link #RUIN_SETTLED}
     * blocks of it. Nothing haunts it any more then, so a town can grow up beside old ruins.
     */
    private static boolean settled(Game game, Ruin ruin) {
        double margin = RUIN_SETTLED + Math.max(ruin.sizeX, ruin.sizeZ) / 2.0;
        for (Building building : game.getBuildings()) {
            if (building.covers(ruin.getCenterX(), ruin.getCenterZ(), margin)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The y to stand at in the column x, z within a few blocks of y: on solid ground, not liquid, with room to stand,
     * under a tent's roof or a lookout's deck as well as in the open; -1 if there is none.
     */
    private static int floorNear(WorldServer world, int x, int y, int z) {
        for (int dy = 0; dy <= 3; dy = dy > 0 ? -dy : 1 - dy) {
            int at = y + dy;
            Block below = world.getBlock(x, at - 1, z);
            if (below.getMaterial()
                .isSolid()
                && !below.getMaterial()
                    .isLiquid()
                && world.isAirBlock(x, at, z)
                && world.isAirBlock(x, at + 1, z)) {
                return at;
            }
        }
        return -1;
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

    private void spawnAtRuin(Game game, WorldServer world, Ruin ruin) {
        for (int attempt = 0; attempt < 12; attempt++) {
            // Spiders in among the walls, the undead in and round the ruin.
            int spread = ruin.haunt == Ruin.SPIDERS ? 0 : 3;
            int x = ruin.minX - spread + random.nextInt(ruin.sizeX + 2 * spread);
            int z = ruin.minZ - spread + random.nextInt(ruin.sizeZ + 2 * spread);
            int y = floorNear(world, x, ruin.groundY + 1, z);
            if (y < 0) {
                continue;
            }
            EntityLiving mob = ruinMob(game, world, ruin.haunt);
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
            if (!(mob instanceof EntitySpider || mob instanceof EntityWildMob)) {
                mob.onSpawnWithEgg(null);
                equip(mob);
            }
            world.spawnEntityInWorld(mob);
            return;
        }
    }

    /** A mob of the kind that haunts a ruin: red spiders in Forinthry's cobwebs, its own lot round its ruins. */
    private EntityLiving ruinMob(Game game, WorldServer world, int haunt) {
        switch (haunt) {
            case Ruin.SPIDERS:
                return wilderness(game) ? new EntityWildMob(world, WildKind.RED_SPIDER) : new EntitySpider(world);
            case Ruin.GHOSTS:
                // The odd skeleton walks the graveyards with the ghosts.
                return random.nextInt(4) == 0 ? new EntitySkeleton(world) : new EntityWildMob(world, WildKind.GHOST);
            case Ruin.GIANTS:
                return new EntityWildMob(world, random.nextInt(3) == 0 ? WildKind.GARGOYLE : WildKind.HILL_GIANT);
            case Ruin.BANDITS:
                return new EntityWildMob(world, WildKind.BANDIT);
            case Ruin.HELLHOUNDS:
                return new EntityWildMob(world, WildKind.HELLHOUND);
            case Ruin.FORTRESS: {
                int roll = random.nextInt(20);
                return roll < 6 ? new EntityWildMob(world, WildKind.GHOST)
                    : roll < 11 ? new EntityWildMob(world, WildKind.RED_SPIDER)
                        : roll < 16 ? new EntitySkeleton(world) : new EntityZombie(world);
            }
            default:
                return random.nextInt(3) == 0 ? new EntitySkeleton(world) : new EntityZombie(world);
        }
    }

    // ---------------------------------------------------------------- spawning

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
            || ++ticks % Math.max(5, Config.mobSpawnSeconds * 20 / WILD_TRIES) != 0) {
            return;
        }
        Game game = DimensionManager.getWorld(0) == null ? null : GameManager.getGame();
        WorldServer world = DimensionManager.getWorld(GameWorld.dimensionId());
        if (game == null || world == null || Config.maxMobs <= 0 || SafeMode.isActive()) {
            return;
        }
        // The Wilderness gets WILD_TRIES tries an interval, everywhere else one; with its bigger groups, about eight
        // times as many mobs come, up to eight times as many at once.
        boolean wild = wilderness(game);
        if (!wild && ticks % (Math.max(5, Config.mobSpawnSeconds * 20 / WILD_TRIES) * WILD_TRIES) != 0) {
            return;
        }
        int maxMobs = wild ? Config.maxMobs * WILD_MOBS : Config.maxMobs;
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
        if (hostile.size() >= maxMobs) {
            return;
        }
        // A few tries for a good spot; most of the map is no good at a given moment.
        for (int attempt = 0; attempt < 8; attempt++) {
            int[] spot = pickSpot(game, world, hostile);
            if (spot != null) {
                if (wild) {
                    spawnWildGroup(world, game, spot, maxMobs - hostile.size());
                } else {
                    spawnGroup(world, game, spot, maxMobs - hostile.size());
                }
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
        if (nearby >= (wilderness(game) ? MAX_NEARBY * 4 : MAX_NEARBY)) {
            return null;
        }
        int y = world.getTopSolidOrLiquidBlock(x, z);
        Material ground = world.getBlock(x, y - 1, z)
            .getMaterial();
        if (ground.isLiquid() || ground == Material.leaves || ground == Material.ice) {
            return null;
        }
        if (!wilderness(game) && !amongTrees(world, x, z) && random.nextDouble() >= Config.openGroundChance) {
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

    /** How often each of the Wilderness's creatures turns up, out of the sum, and how many come together. */
    private static final WildKind[] WILD = { WildKind.GHOST, WildKind.RED_SPIDER, WildKind.HELLHOUND,
        WildKind.HILL_GIANT, WildKind.GARGOYLE, null };
    private static final int[] WILD_WEIGHTS = { 24, 22, 20, 14, 10, 10 };
    private static final int[][] WILD_GROUPS = { { 4, 6 }, { 4, 6 }, { 3, 6 }, { 2, 3 }, { 2, 3 }, { 1, 1 } };
    /** Tries at a spawn the Wilderness gets for each one elsewhere, and how many times as many mobs it holds. */
    private static final int WILD_TRIES = 4;
    private static final int WILD_MOBS = 8;
    private static final WildKind[] DRAGONS = { WildKind.GREEN_DRAGON, WildKind.BLUE_DRAGON, WildKind.RED_DRAGON,
        WildKind.BLACK_DRAGON };

    /** A group of one of the Wilderness's creatures; a dragon (the null kind above) comes alone, of any colour. */
    private void spawnWildGroup(WorldServer world, Game game, int[] spot, int room) {
        int total = 0;
        for (int weight : WILD_WEIGHTS) {
            total += weight;
        }
        int roll = random.nextInt(total);
        int pick = 0;
        while (roll >= WILD_WEIGHTS[pick]) {
            roll -= WILD_WEIGHTS[pick++];
        }
        int[] group = WILD_GROUPS[pick];
        int size = Math.min(group[0] + random.nextInt(group[1] - group[0] + 1), room);
        for (int i = 0; i < size; i++) {
            int x = spot[0] + (i == 0 ? 0 : random.nextInt(9) - 4);
            int z = spot[2] + (i == 0 ? 0 : random.nextInt(9) - 4);
            int y = world.getTopSolidOrLiquidBlock(x, z);
            if (Math.abs(y - spot[1]) > 3 || onBuilding(game, x, z, BUILDING_MARGIN)
                || world.getBlock(x, y - 1, z)
                    .getMaterial()
                    .isLiquid()) {
                continue;
            }
            WildKind kind = WILD[pick] != null ? WILD[pick] : DRAGONS[random.nextInt(DRAGONS.length)];
            EntityWildMob mob = new EntityWildMob(world, kind);
            mob.setLocationAndAngles(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
            if (!world.getCollidingBoundingBoxes(mob, mob.boundingBox)
                .isEmpty()) {
                continue;
            }
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
        // Only the armor given here: none of what Minecraft gives a mob as it spawns, gold or chainmail among it.
        for (int slot = 1; slot <= 4; slot++) {
            mob.setCurrentItemOrArmor(slot, null);
        }
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
