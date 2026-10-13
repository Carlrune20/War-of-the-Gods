package com.warofthegods.unit;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import com.warofthegods.Config;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Hunting: workers kill cows, pigs, chickens and sheep, and the carcass holds food they carve off and carry home,
 * the way they chop wood off a tree. Animals units kill drop nothing and lie where they fell, as a dead pig and so on,
 * until the carcass is eaten up, or until the server stops.
 */
public final class Hunting {

    /** What is left of an animal units killed. */
    public static final class Carcass {

        public final int id;
        public final double x;
        public final double y;
        public final double z;
        /** What workers carry off it, for show. */
        public final Item meat;
        public final String name;
        /** What it lies there as, one of {@link EntityCarcass#KINDS}. */
        public final int kind;
        /** The carcass entity drawn for it, or 0. */
        public int entityId;
        private int food;

        Carcass(int id, double x, double y, double z, Item meat, String name, int kind, int food) {
            this.id = id;
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.meat = meat;
            this.name = name;
            this.food = food;
        }

        public int getFood() {
            return food;
        }

        /** What it is called while it lies there, such as Dead pig. */
        public String displayName() {
            return "Dead " + EntityCarcass.KINDS[kind];
        }

        /** Takes up to amount of food off it; returns how much it gave. */
        int take(int amount) {
            int taken = Math.min(food, amount);
            food -= taken;
            return taken;
        }
    }

    private static final Map<Integer, Carcass> carcasses = new LinkedHashMap<>();
    private static int lastId;

    /** Food an animal holds once killed; 0 for anything that cannot be hunted. */
    public static int foodOf(Entity entity) {
        if (entity instanceof EntityCow) {
            return Config.cowFood;
        }
        if (entity instanceof EntityPig) {
            return Config.pigFood;
        }
        if (entity instanceof EntitySheep) {
            return Config.sheepFood;
        }
        if (entity instanceof EntityChicken) {
            return Config.chickenFood;
        }
        return 0;
    }

    private static Item meatOf(Entity entity) {
        if (entity instanceof EntityPig) {
            return Items.porkchop;
        }
        if (entity instanceof EntityChicken) {
            return Items.chicken;
        }
        // Mutton came after 1.7.10; sheep give beef here.
        return Items.beef;
    }

    public static Collection<Carcass> getCarcasses() {
        return Collections.unmodifiableCollection(carcasses.values());
    }

    public static Carcass get(int id) {
        return carcasses.get(id);
    }

    /** The carcass nearest x, z within range that still holds food, or null. */
    public static Carcass nearest(double x, double z, double range) {
        Carcass best = null;
        double bestDistance = range * range;
        for (Carcass carcass : carcasses.values()) {
            double dx = carcass.x - x;
            double dz = carcass.z - z;
            if (carcass.food > 0 && dx * dx + dz * dz <= bestDistance) {
                best = carcass;
                bestDistance = dx * dx + dz * dz;
            }
        }
        return best;
    }

    /** Takes food off a carcass, dropping it once it is eaten up; returns how much it gave. */
    static int carve(Carcass carcass, int amount) {
        int taken = carcass.take(amount);
        if (carcass.food <= 0) {
            carcasses.remove(carcass.id);
        }
        return taken;
    }

    /** Forgets every carcass, as when a new game starts. */
    public static void clear() {
        carcasses.clear();
    }

    /** Whether units killed the animal, which then leaves a carcass instead of what it would drop. */
    private static boolean huntedDown(EntityLivingBase animal, DamageSource source) {
        return !animal.worldObj.isRemote && GameWorld.isGameWorld(animal.worldObj)
            && animal instanceof EntityLiving
            && foodOf(animal) > 0
            && (source.getEntity() instanceof EntityUnit || source.getSourceOfDamage() instanceof EntityTeamArrow);
    }

    /** What the animal is drawn as once dead, one of {@link EntityCarcass#KINDS}. */
    private static int kindOf(Entity entity) {
        if (entity instanceof EntityPig) {
            return 1;
        }
        if (entity instanceof EntitySheep) {
            return 2;
        }
        if (entity instanceof EntityChicken) {
            return 3;
        }
        return 0;
    }

    /** An animal units killed drops nothing: the food is in its carcass. */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (huntedDown(event.entityLiving, event.source)) {
            event.setCanceled(true);
        }
    }

    /**
     * An animal killed by a unit or its arrow leaves a carcass that workers can carve food off, lying on its side
     * where it fell.
     */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!huntedDown(event.entityLiving, event.source)) {
            return;
        }
        int food = foodOf(event.entityLiving);
        // Old carcasses nobody came for give way, so they cannot pile up without end.
        if (carcasses.size() >= 200) {
            Iterator<Carcass> oldest = carcasses.values()
                .iterator();
            oldest.next();
            oldest.remove();
        }
        lastId++;
        EntityLiving animal = (EntityLiving) event.entityLiving;
        carcasses.put(
            lastId,
            new Carcass(
                lastId,
                animal.posX,
                animal.posY,
                animal.posZ,
                meatOf(animal),
                animal.getCommandSenderName(),
                kindOf(animal),
                food));
        // The carcass takes its place at once, rather than after the animal's own fall and puff of smoke.
        animal.setDead();
        EntityCarcass body = new EntityCarcass(
            animal.worldObj,
            lastId,
            kindOf(animal),
            animal.posX,
            animal.posY,
            animal.posZ,
            animal.renderYawOffset);
        animal.worldObj.spawnEntityInWorld(body);
        carcasses.get(lastId).entityId = body.getEntityId();
    }
}
