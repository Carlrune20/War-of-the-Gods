package com.warofthegods.unit;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import com.warofthegods.Config;
import com.warofthegods.world.GameWorld;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Hunting: workers kill cows, pigs, chickens and sheep, and the carcass holds food they carve off and carry home,
 * the way they chop wood off a tree. A carcass lasts until it is eaten up, or until the server stops.
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
        private int food;

        Carcass(int id, double x, double y, double z, Item meat, String name, int food) {
            this.id = id;
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

    /** An animal killed by a unit or its arrow leaves a carcass that workers can carve food off. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.entity.worldObj.isRemote || !GameWorld.isGameWorld(event.entity.worldObj)
            || !(event.entityLiving instanceof EntityLiving)) {
            return;
        }
        int food = foodOf(event.entityLiving);
        Entity killer = event.source.getEntity();
        if (food <= 0
            || !(killer instanceof EntityUnit || event.source.getSourceOfDamage() instanceof EntityTeamArrow)) {
            return;
        }
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
                food));
    }
}
