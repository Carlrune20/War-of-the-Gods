package com.warofthegods;

import com.warofthegods.rts.TerrainMessage;

/** Side-specific setup; the dedicated server needs none. See {@link com.warofthegods.client.ClientProxy}. */
public class CommonProxy {

    public void init() {}

    /** All mods have loaded; Forge reloads resources right after this. */
    public void loadComplete() {}

    /** A message for the in-game RTS view arrived from the server; called on a Netty thread. */
    public void receiveRts(String json) {}

    /** Some of the map's surface for the in-game RTS view arrived from the server; called on a Netty thread. */
    public void receiveTerrain(TerrainMessage message) {}
}
