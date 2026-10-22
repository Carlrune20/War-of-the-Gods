package com.warofthegods.client;

import java.util.Random;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;

import com.warofthegods.world.BiomeWilderness;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Bubbles welling up in the Wilderness's stagnant ponds, as in a swamp: now and then a bubble rises from under the
 * surface of water near the player and bursts with a little splash.
 */
public final class MurkyWater {

    /** Columns round the player looked at each tick, how far out, and how far up and down for the water's surface. */
    private static final int TRIES = 40;
    private static final int RANGE = 20;
    private static final int REACH_UP = 8, REACH_DOWN = 16;

    private final Random random = new Random();

    private MurkyWater() {}

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new MurkyWater());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.theWorld;
        EntityPlayer player = mc.thePlayer;
        if (event.phase != TickEvent.Phase.END || world == null
            || player == null
            || mc.isGamePaused()
            || BiomeWilderness.get() == null) {
            return;
        }
        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(player.posY);
        int pz = (int) Math.floor(player.posZ);
        if (world.getBiomeGenForCoords(px, pz) != BiomeWilderness.get()) {
            return;
        }
        for (int i = 0; i < TRIES; i++) {
            int x = px + random.nextInt(2 * RANGE + 1) - RANGE;
            int z = pz + random.nextInt(2 * RANGE + 1) - RANGE;
            int surface = surface(world, x, py, z);
            if (surface < 0 || random.nextInt(3) > 0) {
                continue;
            }
            double bx = x + 0.2 + random.nextDouble() * 0.6;
            double bz = z + 0.2 + random.nextDouble() * 0.6;
            // Rising from a little under the surface; it bursts as it leaves the water.
            world.spawnParticle("bubble", bx, surface + 0.2 + random.nextDouble() * 0.5, bz, 0, 0.05, 0);
            if (random.nextInt(4) == 0) {
                world.spawnParticle("splash", bx, surface + 1.0, bz, 0, 0.02, 0);
            }
        }
    }

    /** The y of the top water block in the column under open air, near y; -1 if the column has none. */
    private static int surface(WorldClient world, int x, int y, int z) {
        for (int at = y + REACH_UP; at > y - REACH_DOWN && at > 0; at--) {
            Material material = world.getBlock(x, at, z)
                .getMaterial();
            if (material == Material.air) {
                continue;
            }
            return material == Material.water ? at : -1;
        }
        return -1;
    }
}
