package com.warofthegods.world;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.commons.io.FileUtils;

import com.warofthegods.Config;
import com.warofthegods.WarOfTheGods;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;

/** The dimension games are played in: registering it, rebuilding it for each new game and moving players into it. */
public final class GameWorld {

    /** Folder under the world save that holds one sub folder per game world. */
    private static final String SAVE_ROOT = "warofthegods";
    /** Player NBT key holding the number of the game whose world the player was last put in. */
    private static final String PLAYER_GAME_KEY = "warofthegods.game";

    private GameWorld() {}

    public static int dimensionId() {
        return Config.gameDimensionId;
    }

    public static boolean isGameWorld(World world) {
        return world.provider.dimensionId == dimensionId();
    }

    /** Registers the game dimension, reusing its id as the id of its provider type. */
    public static void register() {
        int id = dimensionId();
        if (!DimensionManager.registerProviderType(id, WorldProviderRts.class, true)) {
            throw new IllegalStateException(
                "War of the Gods: dimension provider id " + id + " is taken, change gameDimensionId in the config");
        }
        if (DimensionManager.isDimensionRegistered(id)) {
            throw new IllegalStateException(
                "War of the Gods: dimension " + id + " is taken, change gameDimensionId in the config");
        }
        DimensionManager.registerDimension(id, id);
    }

    /** The running game, or null when there is none or the overworld is not loaded yet. */
    static Game currentGame() {
        return DimensionManager.getWorld(0) == null ? null : GameManager.getGame();
    }

    static MapArea mapArea(Game game) {
        if (game == null) {
            int half = Config.defaultMapSize / 2;
            return new MapArea(-half, -half, Config.defaultMapSize - half - 1, Config.defaultMapSize - half - 1, 0);
        }
        return MapArea.of(game, Config.mapMargin);
    }

    static long seedOf(Game game, long worldSeed) {
        return game == null ? worldSeed : game.getSeed();
    }

    static String saveFolder(Game game) {
        return SAVE_ROOT + "/" + (game == null ? "no-game" : "game-" + game.getNumber());
    }

    public static WorldServer getWorld() {
        return MinecraftServer.getServer()
            .worldServerForDimension(dimensionId());
    }

    /**
     * Throws away the old game world and loads a fresh one for the running game. Players in the old world are sent
     * to the overworld first, since a world cannot be unloaded with players in it.
     */
    public static void rebuild() {
        MinecraftServer server = MinecraftServer.getServer();
        int id = dimensionId();
        WorldServer old = DimensionManager.getWorld(id);
        if (old != null) {
            WorldServer overworld = server.worldServerForDimension(0);
            ChunkCoordinates spawn = overworld.getSpawnPoint();
            for (Object player : new ArrayList<Object>(old.playerEntities)) {
                teleport(
                    (EntityPlayerMP) player,
                    overworld,
                    spawn.posX + 0.5,
                    overworld.getTopSolidOrLiquidBlock(spawn.posX, spawn.posZ),
                    spawn.posZ + 0.5);
            }
            MinecraftForge.EVENT_BUS.post(new WorldEvent.Unload(old));
            // Waits for chunk writes and closes region files, so the old folder can be deleted.
            old.flush();
            DimensionManager.setWorld(id, null);
        }
        deleteOldGameFolders(saveFolder(GameManager.getGame()));
        DimensionManager.initDimension(id);
        WarOfTheGods.LOG.info("Game world ready in dimension {}", id);
    }

    private static void deleteOldGameFolders(String keep) {
        File root = new File(DimensionManager.getCurrentSaveRootDirectory(), SAVE_ROOT);
        File[] folders = root.listFiles();
        if (folders == null) {
            return;
        }
        File kept = new File(DimensionManager.getCurrentSaveRootDirectory(), keep);
        for (File folder : folders) {
            if (folder.isDirectory() && !folder.equals(kept)) {
                try {
                    FileUtils.deleteDirectory(folder);
                } catch (IOException e) {
                    WarOfTheGods.LOG.warn("Could not delete old game world {}", folder, e);
                }
            }
        }
    }

    /** A spot on dry ground near the middle of the map, as x, y, z of the block to stand in. */
    public static int[] findSpawn(WorldServer world, Game game) {
        MapArea area = MapArea.of(game, Config.mapMargin);
        int centerX = area.getCenterX();
        int centerZ = area.getCenterZ();
        int[] fallback = null;
        for (int ring = 0; ring <= 96; ring += 8) {
            for (int dx = -ring; dx <= ring; dx += 8) {
                for (int dz = -ring; dz <= ring; dz += 8) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    int y = world.getTopSolidOrLiquidBlock(x, z);
                    if (fallback == null) {
                        fallback = new int[] { x, y, z };
                    }
                    Block ground = world.getBlock(x, y - 1, z);
                    Material material = ground.getMaterial();
                    if (!material.isLiquid() && material != Material.leaves && material != Material.ice) {
                        return new int[] { x, y, z };
                    }
                }
            }
        }
        return fallback;
    }

    /** Puts the player at the map spawn of the running game. */
    public static void sendToGame(EntityPlayerMP player, Game game) {
        WorldServer world = getWorld();
        int[] spawn = findSpawn(world, game);
        teleport(player, world, spawn[0] + 0.5, spawn[1], spawn[2] + 0.5);
        persistedData(player).setInteger(PLAYER_GAME_KEY, game.getNumber());
    }

    /** Whether the player is somewhere other than the running game's world, such as an older game's world. */
    public static boolean isOutsideGame(EntityPlayerMP player, Game game) {
        return player.dimension != dimensionId()
            || persistedData(player).getInteger(PLAYER_GAME_KEY) != game.getNumber();
    }

    /** Player NBT that Forge keeps across deaths. */
    private static NBTTagCompound persistedData(EntityPlayer player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static void sendAllToGame(Game game) {
        List<EntityPlayerMP> players = new ArrayList<>();
        for (Object player : MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList) {
            players.add((EntityPlayerMP) player);
        }
        for (EntityPlayerMP player : players) {
            sendToGame(player, game);
        }
    }

    public static void teleport(EntityPlayerMP player, WorldServer target, double x, double y, double z) {
        if (player.ridingEntity != null) {
            player.mountEntity(null);
        }
        if (player.dimension != target.provider.dimensionId) {
            MinecraftServer.getServer()
                .getConfigurationManager()
                .transferPlayerToDimension(player, target.provider.dimensionId, new DirectTeleporter(target, x, y, z));
        }
        player.playerNetServerHandler.setPlayerLocation(x, y, z, player.rotationYaw, player.rotationPitch);
        player.fallDistance = 0;
    }
}
