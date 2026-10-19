package com.warofthegods.rts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.warofthegods.Config;
import com.warofthegods.web.MapTiles;

/**
 * Sends players in the RTS view the map's surface, a tile's changed chunks at a time, nearest tiles first and within a
 * byte budget per player, so they can draw the whole map beyond the chunks their client has. Each chunk goes once, and
 * again only when it changes. What each player was sent is kept while they are online, so leaving the view and coming
 * back sends nothing again; a client that has lost what it was sent says so when it enters, see {@link #entered}.
 */
final class TerrainSender {

    private static final int CHUNKS = MapTiles.CHUNKS_PER_TILE * MapTiles.CHUNKS_PER_TILE;
    /** Server ticks between sends. */
    private static final int INTERVAL = 2;

    private static final class Sent {

        String mapId;
        /** Per tile, the version each of its chunks was sent at. */
        final Map<Long, int[]> chunks = new HashMap<>();
        /** Per tile, the version it was at when last sent in full. */
        final Map<Long, Integer> tiles = new HashMap<>();
        /** Bytes that may still go out, topped up every send. */
        double allowance;
    }

    private final Map<UUID, Sent> sent = new HashMap<>();
    private final int[] indexes = new int[CHUNKS];
    private final byte[][] data = new byte[CHUNKS][];
    private int ticks;

    /** A player entered the RTS view holding the map surface of mapId, or null if they hold none. */
    void entered(EntityPlayerMP player, String mapId) {
        Sent had = sent.get(player.getUniqueID());
        if (had != null && (mapId == null || !mapId.equals(had.mapId))) {
            sent.remove(player.getUniqueID());
        }
    }

    /** Sends the commanding players what they lack of the map; tiles is null when there is no map to send. */
    void tick(MapTiles tiles, List<EntityPlayerMP> players) {
        if (tiles == null || ++ticks % INTERVAL != 0) {
            return;
        }
        List<UUID> online = new ArrayList<>();
        for (EntityPlayerMP player : players) {
            online.add(player.getUniqueID());
        }
        // Players who logged out lose what their client held.
        for (Iterator<UUID> it = sent.keySet()
            .iterator(); it.hasNext();) {
            UUID id = it.next();
            if (!online.contains(id) && Commanders.find(id) == null) {
                it.remove();
            }
        }
        Map<Long, Integer> versions = null;
        for (EntityPlayerMP player : players) {
            Sent mine = sent.computeIfAbsent(player.getUniqueID(), k -> new Sent());
            if (!tiles.getId()
                .equals(mine.mapId)) {
                mine.mapId = tiles.getId();
                mine.chunks.clear();
                mine.tiles.clear();
            }
            double perSend = Config.rtsFarTerrainKbPerSecond * 1024.0 * INTERVAL / 20;
            mine.allowance = Math.min(perSend * 4, mine.allowance + perSend);
            if (mine.allowance <= 0) {
                continue;
            }
            if (versions == null) {
                versions = tiles.versionsByKey();
            }
            List<Long> due = new ArrayList<>();
            for (Map.Entry<Long, Integer> entry : versions.entrySet()) {
                Integer had = mine.tiles.get(entry.getKey());
                if (had == null || had < entry.getValue()) {
                    due.add(entry.getKey());
                }
            }
            if (due.isEmpty()) {
                continue;
            }
            double px = player.posX / MapTiles.TILE_SIZE - 0.5;
            double pz = player.posZ / MapTiles.TILE_SIZE - 0.5;
            due.sort((a, b) -> Double.compare(distanceSq(a, px, pz), distanceSq(b, px, pz)));
            for (long key : due) {
                if (mine.allowance <= 0) {
                    break;
                }
                int[] chunkVersions = mine.chunks.computeIfAbsent(key, k -> new int[CHUNKS]);
                int count = tiles.newerChunks(key, chunkVersions, indexes, data);
                mine.tiles.put(key, versions.get(key));
                if (count == 0) {
                    continue;
                }
                TerrainMessage message = new TerrainMessage(
                    tiles.getId(),
                    MapTiles.tileX(key),
                    MapTiles.tileZ(key),
                    indexes,
                    data,
                    count);
                RtsNetwork.sendTerrain(player, message);
                mine.allowance -= message.size();
            }
        }
        for (int i = 0; i < CHUNKS; i++) {
            data[i] = null;
        }
    }

    private static double distanceSq(long key, double x, double z) {
        double dx = MapTiles.tileX(key) - x;
        double dz = MapTiles.tileZ(key) - z;
        return dx * dx + dz * dz;
    }
}
