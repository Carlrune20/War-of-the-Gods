package com.warofthegods.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.warofthegods.Config;

class SafeModeTest {

    @Test
    void comesOnOnlyOnceTicksHaveRunLongForAWhile() {
        SafeMode.Tracker tracker = new SafeMode.Tracker();
        int checks = Config.safeModeEnterSeconds;
        for (int i = 1; i < checks; i++) {
            assertFalse(tracker.update(Config.safeModeEnterMs + 10, 20));
        }
        assertTrue(tracker.update(Config.safeModeEnterMs + 10, 20));
    }

    @Test
    void aQuickTickStartsTheCountAgain() {
        SafeMode.Tracker tracker = new SafeMode.Tracker();
        for (int i = 1; i < Config.safeModeEnterSeconds; i++) {
            tracker.update(Config.safeModeEnterMs + 10, 20);
        }
        assertFalse(tracker.update(Config.safeModeExitMs - 10, 20));
        assertFalse(tracker.update(Config.safeModeEnterMs + 10, 20));
    }

    @Test
    void goesOffOnlyOnceTicksHaveRunQuickForLonger() {
        SafeMode.Tracker tracker = new SafeMode.Tracker();
        for (int i = 0; i < Config.safeModeEnterSeconds; i++) {
            tracker.update(Config.safeModeEnterMs + 10, 20);
        }
        // Between the marks it stays as it is.
        assertTrue(tracker.update((Config.safeModeEnterMs + Config.safeModeExitMs) / 2.0, 20));
        for (int i = 1; i < Config.safeModeExitSeconds; i++) {
            assertTrue(tracker.update(Config.safeModeExitMs - 10, 20));
        }
        assertFalse(tracker.update(Config.safeModeExitMs - 10, 20));
    }

    @Test
    void averagesTickTimesInMilliseconds() {
        assertEquals(30.0, SafeMode.averageMs(new long[] { 20_000_000L, 40_000_000L }), 1e-9);
        assertEquals(0.0, SafeMode.averageMs(new long[0]), 1e-9);
    }
}
