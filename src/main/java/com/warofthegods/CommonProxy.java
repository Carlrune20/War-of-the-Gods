package com.warofthegods;

/** Side-specific setup; the dedicated server needs none. See {@link com.warofthegods.client.ClientProxy}. */
public class CommonProxy {

    public void init() {}

    /** A message for the in-game RTS view arrived from the server; called on a Netty thread. */
    public void receiveRts(String json) {}
}
