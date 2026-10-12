package com.warofthegods.unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import com.warofthegods.WarOfTheGods;

/**
 * Keeps the chunks around units loaded with Forge chunk loading tickets, so units keep walking and fighting with no
 * player near. Tickets are not kept across restarts: the units' last known chunks are saved with the game instead.
 */
final class UnitChunkLoader implements ForgeChunkManager.OrderedLoadingCallback {

    private World world;
    private final List<Ticket> tickets = new ArrayList<>();
    private final Map<ChunkCoordIntPair, Ticket> forced = new HashMap<>();
    private boolean warned;

    /** Forces exactly the wanted chunks of the world. */
    void update(World world, Set<ChunkCoordIntPair> wanted) {
        if (world != this.world) {
            // A new game world: the old world's tickets went with it.
            tickets.clear();
            forced.clear();
            this.world = world;
        }
        for (Iterator<Map.Entry<ChunkCoordIntPair, Ticket>> it = forced.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<ChunkCoordIntPair, Ticket> entry = it.next();
            if (!wanted.contains(entry.getKey())) {
                ForgeChunkManager.unforceChunk(entry.getValue(), entry.getKey());
                it.remove();
            }
        }
        for (ChunkCoordIntPair chunk : wanted) {
            if (forced.containsKey(chunk)) {
                continue;
            }
            Ticket ticket = ticketWithRoom();
            if (ticket == null) {
                if (!warned) {
                    warned = true;
                    WarOfTheGods.LOG.warn(
                        "Out of chunk loading tickets, so some units stop when no player is near. Raise the limits"
                            + " for warofthegods in config/forgeChunkLoading.cfg");
                }
                return;
            }
            ForgeChunkManager.forceChunk(ticket, chunk);
            forced.put(chunk, ticket);
        }
    }

    private Ticket ticketWithRoom() {
        for (Ticket ticket : tickets) {
            if (ticket.getChunkList()
                .size() < ticket.getChunkListDepth()) {
                return ticket;
            }
        }
        Ticket ticket = ForgeChunkManager.requestTicket(WarOfTheGods.instance, world, ForgeChunkManager.Type.NORMAL);
        if (ticket != null) {
            tickets.add(ticket);
        }
        return ticket;
    }

    void releaseAll() {
        // Tickets of a world that has since been unloaded are gone already.
        if (world != null && DimensionManager.getWorld(world.provider.dimensionId) == world) {
            for (Ticket ticket : tickets) {
                ForgeChunkManager.releaseTicket(ticket);
            }
        }
        tickets.clear();
        forced.clear();
        world = null;
    }

    /** Tickets saved before a restart are dropped; the units' chunks are forced again from the game's records. */
    @Override
    public List<Ticket> ticketsLoaded(List<Ticket> tickets, World world, int maxTicketCount) {
        return new ArrayList<>();
    }

    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {}
}
