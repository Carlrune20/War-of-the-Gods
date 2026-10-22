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
import com.warofthegods.game.Building;
import com.warofthegods.game.Game;
import com.warofthegods.game.GameManager;
import com.warofthegods.game.GameMap;
import com.warofthegods.game.Team;
import com.warofthegods.unit.Units;

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

    /** Whether the world is the game world of a game on Forinthry, the Wilderness, where nothing green grows. */
    public static boolean isWilderness(World world) {
        return !world.isRemote && isGameWorld(world) && mapOf(currentGame()) == GameMap.FORINTHRY;
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
    public static Game currentGame() {
        return DimensionManager.getWorld(0) == null ? null : GameManager.getGame();
    }

    static MapArea mapArea(Game game) {
        if (game == null) {
            int half = Config.defaultMapSize / 2;
            return new MapArea(-half, -half, Config.defaultMapSize - half - 1, Config.defaultMapSize - half - 1, 0);
        }
        return MapArea.of(game, Config.mapMargin);
    }

    /** How many teams the map is laid out for. */
    static int mapTeams(Game game) {
        if (game == null) {
            return 2;
        }
        return Math.max(
            2,
            game.getMapTeams() > 0 ? game.getMapTeams()
                : game.getTeams()
                    .size());
    }

    private static Misthalin layout;
    private static int layoutGame = -1;

    /**
     * The river and bridges of the running game's map; null for no game, a map from before there were any, or a map
     * other than Misthalin, which has none.
     */
    public static Misthalin layout(Game game) {
        if (game == null || game.getMapTeams() == 0 || game.getMap() != GameMap.MISTHALIN) {
            return null;
        }
        if (layout == null || layoutGame != game.getNumber()) {
            layout = new Misthalin(mapArea(game), game.getSeed(), mapTeams(game));
            layoutGame = game.getNumber();
        }
        return layout;
    }

    /** The map the game is played on; Misthalin with no game. */
    static GameMap mapOf(Game game) {
        return game == null ? GameMap.MISTHALIN : game.getMap();
    }

    /** Where the teams' bases go on the running game's map, as x, z; the same spots on every map. */
    static double[][] basesOf(MapArea area, long seed, int teams) {
        int size = Math.max(area.getMaxX() - area.getMinX(), area.getMaxZ() - area.getMinZ()) + 1;
        int n = Math.max(2, teams);
        double[][] bases = new double[Math.max(1, teams)][];
        for (int i = 0; i < bases.length; i++) {
            bases[i] = Misthalin.baseSpot(area.getCenterX(), area.getCenterZ(), size, seed, i, n);
        }
        return bases;
    }

    private static Forinthry wilderness;
    private static int wildernessGame = -1;

    /** The layout of the running game's map if it is Forinthry; null for no game or another map. */
    public static Forinthry forinthry(Game game) {
        if (game == null || game.getMap() != GameMap.FORINTHRY) {
            return null;
        }
        if (wilderness == null || wildernessGame != game.getNumber()) {
            wilderness = new Forinthry(mapArea(game), game.getSeed(), mapTeams(game));
            wildernessGame = game.getNumber();
        }
        return wilderness;
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

    /**
     * A spot on dry ground in front of the team's town center, or at its base before the town center is up, as x, y,
     * z of the block to stand in; the middle of the map for no team.
     */
    public static int[] findTeamSpawn(WorldServer world, Game game, Team team) {
        if (team == null) {
            return findSpawn(world, game);
        }
        Building center = game.getTownHall(team);
        if (center != null) {
            double[] door = center.getDoorstep();
            return findDryGround(world, (int) Math.floor(door[0]), (int) Math.floor(door[1]), 16);
        }
        Units.assignBase(game, world, team);
        int[] base = team.getBase();
        return base == null ? findSpawn(world, game) : findDryGround(world, base[0], base[1], 16);
    }

    /** A spot on dry ground near the middle of the map, as x, y, z of the block to stand in. */
    public static int[] findSpawn(WorldServer world, Game game) {
        MapArea area = MapArea.of(game, Config.mapMargin);
        return findDryGround(world, area.getCenterX(), area.getCenterZ(), 96);
    }

    /**
     * A spot on dry ground near x, z, searched in growing squares up to the given distance, as x, y, z of the block
     * to stand in. If there is none it gives x, z itself.
     */
    public static int[] findDryGround(WorldServer world, int x, int z, int maxDistance) {
        int[] fallback = null;
        for (int ring = 0; ring <= maxDistance; ring += 8) {
            for (int dx = -ring; dx <= ring; dx += 8) {
                for (int dz = -ring; dz <= ring; dz += 8) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int y = world.getTopSolidOrLiquidBlock(x + dx, z + dz);
                    if (fallback == null) {
                        fallback = new int[] { x + dx, y, z + dz };
                    }
                    Block ground = world.getBlock(x + dx, y - 1, z + dz);
                    Material material = ground.getMaterial();
                    if (!material.isLiquid() && material != Material.leaves && material != Material.ice) {
                        return new int[] { x + dx, y, z + dz };
                    }
                }
            }
        }
        return fallback;
    }

    /**
     * Puts the player in the running game: in front of their team's town center, or where it is about to go up, and
     * players on no team in the middle of the map.
     */
    public static void sendToGame(EntityPlayerMP player, Game game) {
        if (persistedData(player).getInteger(PLAYER_GAME_KEY) != game.getNumber()) {
            // Everyone starts each new game empty handed, whatever they carried out of the last one.
            clearInventory(player);
        }
        WorldServer world = getWorld();
        int[] spawn = findTeamSpawn(world, game, game.getTeamOf(player.getUniqueID()));
        teleport(player, world, spawn[0] + 0.5, spawn[1], spawn[2] + 0.5);
        player.setSpawnChunk(new ChunkCoordinates(spawn[0], spawn[1], spawn[2]), true, dimensionId());
        persistedData(player).setInteger(PLAYER_GAME_KEY, game.getNumber());
    }

    /**
     * Moves the player's spawn point in the game world to in front of their team's main town center, or the middle of
     * the map on no team, so they come back there after dying. Called whenever a player changes team.
     */
    public static void setTeamSpawn(EntityPlayerMP player, Game game) {
        WorldServer world = DimensionManager.getWorld(dimensionId());
        if (world == null || game != GameManager.getGame()) {
            return;
        }
        int[] spawn = findTeamSpawn(world, game, game.getTeamOf(player.getUniqueID()));
        player.setSpawnChunk(new ChunkCoordinates(spawn[0], spawn[1], spawn[2]), true, dimensionId());
    }

    /** Whether the player is somewhere other than the running game's world, such as an older game's world. */
    public static boolean isOutsideGame(EntityPlayerMP player, Game game) {
        return player.dimension != dimensionId()
            || persistedData(player).getInteger(PLAYER_GAME_KEY) != game.getNumber();
    }

    /** Empties the player's inventory, armor and what they hold on the cursor. */
    private static void clearInventory(EntityPlayerMP player) {
        player.inventory.clearInventory(null, -1);
        player.inventory.setItemStack(null);
        player.inventoryContainer.detectAndSendChanges();
        player.updateHeldItem();
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
