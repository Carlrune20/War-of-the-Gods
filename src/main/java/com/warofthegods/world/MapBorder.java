package com.warofthegods.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The invisible wall at the edge of the playable area. Players and mobs that cross it are put back inside, nothing
 * spawns outside it and blocks outside it cannot be changed. Registered on both the FML and the Forge event bus.
 */
public class MapBorder {

    private static final long WARNING_INTERVAL_TICKS = 60;

    private final Map<UUID, Long> lastWarning = new HashMap<>();

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || !GameWorld.isGameWorld(event.world)) {
            return;
        }
        Game game = GameManager.getGame();
        if (game == null) {
            return;
        }
        for (Object object : event.world.loadedEntityList) {
            Entity entity = (Entity) object;
            if (entity.ridingEntity == null && !entity.isDead) {
                keepInside(entity, game);
            }
        }
    }

    private void keepInside(Entity entity, Game game) {
        double half = Math.min(entity.width / 2, 0.5);
        double minX = game.getMinX() + half;
        double maxX = game.getMaxX() + 1 - half;
        double minZ = game.getMinZ() + half;
        double maxZ = game.getMaxZ() + 1 - half;
        if (entity.posX >= minX && entity.posX <= maxX && entity.posZ >= minZ && entity.posZ <= maxZ) {
            return;
        }
        if (entity instanceof IProjectile) {
            entity.setDead();
            return;
        }
        double x = Math.max(minX, Math.min(maxX, entity.posX));
        double z = Math.max(minZ, Math.min(maxZ, entity.posZ));
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            player.playerNetServerHandler
                .setPlayerLocation(x, player.posY, z, player.rotationYaw, player.rotationPitch);
            warn(player);
        } else {
            entity.setPosition(x, entity.posY, z);
            entity.motionX = 0;
            entity.motionZ = 0;
        }
    }

    private void warn(EntityPlayerMP player) {
        long now = player.worldObj.getTotalWorldTime();
        Long last = lastWarning.get(player.getUniqueID());
        if (last == null || now - last >= WARNING_INTERVAL_TICKS || now < last) {
            lastWarning.put(player.getUniqueID(), now);
            player.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "You have reached the edge of the map."));
        }
    }

    private static boolean isOutside(World world, int x, int z) {
        if (world.isRemote || !GameWorld.isGameWorld(world)) {
            return false;
        }
        Game game = GameManager.getGame();
        return game != null && !game.isInside(x, z);
    }

    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (isOutside(event.world, (int) Math.floor(event.x), (int) Math.floor(event.z))) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (isOutside(event.world, event.x, event.z)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (isOutside(event.world, event.x, event.z)) {
            event.setCanceled(true);
        }
    }
}
