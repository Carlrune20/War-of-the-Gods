package com.warofthegods.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.warofthegods.game.GameMap;

class GameTimeTest {

    /** The map as the shader pack reads it from the day count (shaderpack/shaders/lib/map.glsl), or -1. */
    private static int shaderMap(long time) {
        int day = (int) (time / 24000L) - 65536;
        return day < 0 || day >= 256 ? -1 : day / 16;
    }

    @Test
    void theShaderPackReadsEachMapFromTheDay() {
        for (GameMap map : GameMap.values()) {
            for (long time : new long[] { 0, 6000, 23999, 24000 * 7 + 100, 24000L * 12345 + 13000 }) {
                assertEquals(map.ordinal(), shaderMap(WorldProviderRts.gameTime(time, map)));
            }
        }
    }

    @Test
    void theMapHoldsWhenTheClientCountsPastMidnight() {
        for (GameMap map : GameMap.values()) {
            long sent = WorldProviderRts.gameTime(24000L * 7 + 23999, map);
            assertEquals(map.ordinal(), shaderMap(sent + 40));
        }
    }

    @Test
    void misthalinKeepsTheTimeOfDayAndTheMoonsPhase() {
        long time = 24000L * 13 + 5000;
        long game = WorldProviderRts.gameTime(time, GameMap.MISTHALIN);
        assertEquals(5000, game % 24000);
        assertEquals(13 % 8, game / 24000 % 8);
        assertTrue(game <= Integer.MAX_VALUE);
    }

    @Test
    void forinthryIsAlwaysAtMidnight() {
        assertEquals(18000, WorldProviderRts.gameTime(24000L * 3 + 6000, GameMap.FORINTHRY) % 24000);
    }

    @Test
    void theOverworldsOwnDaysAreNoMap() {
        assertEquals(-1, shaderMap(24000L * 400 + 100));
        assertEquals(24000L * 400, WorldProviderRts.gameTime(24000L * 400, null));
    }
}
