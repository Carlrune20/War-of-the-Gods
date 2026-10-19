package com.warofthegods.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class WebSessionsTest {

    private final WebSessions sessions = new WebSessions();
    private final Viewer alice = Viewer.player(UUID.randomUUID(), "Alice");

    @Test
    void linkTokenTradesForASession() {
        String token = sessions.createLinkToken(alice);

        String session = sessions.login(token);

        assertNotNull(session);
        assertSame(alice, sessions.get(session));
    }

    @Test
    void linkTokenWorksOnlyOnce() {
        String token = sessions.createLinkToken(alice);
        sessions.login(token);

        assertNull(sessions.login(token));
    }

    @Test
    void unknownOrMissingTokensGiveNothing() {
        assertNull(sessions.login("not-a-token"));
        assertNull(sessions.login(null));
    }

    @Test
    void tokensAreUniqueAndLongEnoughToGuess() {
        String first = sessions.createLinkToken(alice);
        String second = sessions.createLinkToken(alice);

        assertFalse(first.equals(second));
        assertTrue(first.length() >= 32);
    }

    @Test
    void observerSessionsAreObservers() {
        String session = sessions.login(sessions.createLinkToken(Viewer.observer()));

        assertTrue(
            sessions.get(session)
                .isObserver());
    }

    @Test
    void logoutEndsTheSession() {
        String session = sessions.login(sessions.createLinkToken(alice));

        sessions.logout(session);

        assertNull(sessions.get(session));
    }

    @Test
    void unknownSessionHasNoViewer() {
        assertNull(sessions.get("nope"));
        assertNull(sessions.get(null));
    }
}
