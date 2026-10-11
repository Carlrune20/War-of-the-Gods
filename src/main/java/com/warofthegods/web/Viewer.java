package com.warofthegods.web;

import java.util.UUID;

/**
 * Who is looking at the web map: a player, or an observer (a link made from the server console or by an op with
 * /rts web observer) who belongs to no team.
 */
public final class Viewer {

    private final UUID player;
    private final String name;

    private Viewer(UUID player, String name) {
        this.player = player;
        this.name = name;
    }

    public static Viewer player(UUID player, String name) {
        return new Viewer(player, name);
    }

    public static Viewer observer() {
        return new Viewer(null, "Observer");
    }

    /** The player's UUID, or null for an observer. */
    public UUID getPlayer() {
        return player;
    }

    public String getName() {
        return name;
    }

    public boolean isObserver() {
        return player == null;
    }
}
