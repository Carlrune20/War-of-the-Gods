package com.warofthegods.unit;

import java.util.Random;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.IMob;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

import com.warofthegods.Config;
import com.warofthegods.game.Building;
import com.warofthegods.game.BuildingType;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Hostile mobs on the game map. Few spawn near town centers and none right by them; out in the wilderness zombies and
 * skeletons come in groups, often in armor, and some skeletons carry swords. Creepers are rare and endermen stay away.
 * Zombies and skeletons go after units as readily as after players.
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

    private final Random random = new Random();
    /** Set while the companions of a mob are being spawned, so they bring no companions of their own. */
    private boolean spawningGroup;

    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.world.isRemote || !GameWorld.isGameWorld(event.world) || !(event.entityLiving instanceof IMob)) {
            return;
        }
        if (event.entityLiving instanceof EntityEnderman && !Config.endermen
            || event.entityLiving instanceof EntityCreeper && random.nextDouble() >= Config.creeperChance) {
            event.setResult(Event.Result.DENY);
            return;
        }
        Game game = GameManager.getGame();
        if (game != null && random.nextDouble() >= townChance(game, event.x, event.z)) {
            event.setResult(Event.Result.DENY);
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

    /**
     * A zombie or skeleton spawning naturally gets its usual gear, then more: maybe armor, maybe a sword instead of a
     * bow. In the wilderness a few more come along.
     */
    @SubscribeEvent
    public void onSpecialSpawn(LivingSpawnEvent.SpecialSpawn event) {
        if (event.world.isRemote || !GameWorld.isGameWorld(event.world)
            || !(event.entityLiving instanceof EntityZombie || event.entityLiving instanceof EntitySkeleton)) {
            return;
        }
        EntityLiving mob = (EntityLiving) event.entityLiving;
        // Cancelling stops the spawner from equipping it, so that is done here, before the extras.
        event.setCanceled(true);
        mob.onSpawnWithEgg(null);
        equip(mob);
        Game game = GameManager.getGame();
        if (spawningGroup || game == null || nearestTownCenter(game, mob.posX, mob.posZ) < Config.townRadius) {
            return;
        }
        int extra = Config.mobGroupExtra <= 0 ? 0 : 1 + random.nextInt(Config.mobGroupExtra);
        spawningGroup = true;
        try {
            for (int i = 0; i < extra; i++) {
                spawnCompanion(event.world, mob);
            }
        } finally {
            spawningGroup = false;
        }
    }

    private void spawnCompanion(World world, EntityLiving leader) {
        int x = MathHelper.floor_double(leader.posX) + random.nextInt(7) - 3;
        int z = MathHelper.floor_double(leader.posZ) + random.nextInt(7) - 3;
        int y = world.getTopSolidOrLiquidBlock(x, z);
        if (Math.abs(y - leader.posY) > 4 || world.getBlock(x, y - 1, z)
            .getMaterial()
            .isLiquid()) {
            return;
        }
        EntityLiving companion = leader instanceof EntitySkeleton ? new EntitySkeleton(world) : new EntityZombie(world);
        companion.setLocationAndAngles(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
        if (!companion.getCanSpawnHere()) {
            return;
        }
        world.spawnEntityInWorld(companion);
        companion.onSpawnWithEgg(null);
        equip(companion);
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
        if (mob instanceof EntitySkeleton && ((EntitySkeleton) mob).getSkeletonType() == 0
            && random.nextDouble() < Config.skeletonSwordChance) {
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

    /** Zombies and skeletons hunt units too. Their own fighting only goes after players, so they get one for units. */
    @SubscribeEvent
    public void onJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isRemote || !GameWorld.isGameWorld(event.world)
            || !(event.entity instanceof EntityZombie || event.entity instanceof EntitySkeleton)) {
            return;
        }
        EntityMob mob = (EntityMob) event.entity;
        mob.targetTasks.addTask(2, new EntityAINearestAttackableTarget(mob, EntityUnit.class, 0, true));
        if (mob instanceof EntityZombie) {
            mob.tasks.addTask(3, new EntityAIAttackOnCollide(mob, EntityUnit.class, 1.0D, true));
        } else if (mob.getHeldItem() != null && !(mob.getHeldItem()
            .getItem() instanceof ItemBow)) {
                // A skeleton with a sword, loaded again. One just spawning holds nothing yet; equip() sees to it.
                addMelee((EntitySkeleton) mob);
            }
    }

    private static void addMelee(EntityCreature skeleton) {
        skeleton.tasks.addTask(4, new EntityAIAttackOnCollide(skeleton, EntityUnit.class, 1.2D, false));
    }
}
