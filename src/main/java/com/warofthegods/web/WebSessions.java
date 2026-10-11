package com.warofthegods.web;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.warofthegods.Config;

/**
 * Login links and browser sessions. A link from /rts web carries a one-time token; opening it trades the token for a
 * session cookie. Both live in memory only, so a server restart logs everyone out.
 */
public final class WebSessions {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Entry> linkTokens = new ConcurrentHashMap<>();
    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    private static final class Entry {

        final Viewer viewer;
        final long expiresAt;

        Entry(Viewer viewer, long expiresAt) {
            this.viewer = viewer;
            this.expiresAt = expiresAt;
        }

        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /** A new one-time link token for the viewer. */
    public String createLinkToken(Viewer viewer) {
        purge();
        String token = randomToken();
        linkTokens.put(token, new Entry(viewer, expiry(TimeUnit.MINUTES.toMillis(Config.webLinkMinutes))));
        return token;
    }

    /** Uses up a link token and returns the new session id, or null if the token is unknown, used or expired. */
    public String login(String linkToken) {
        if (linkToken == null) {
            return null;
        }
        Entry link = linkTokens.remove(linkToken);
        if (link == null || link.expired()) {
            return null;
        }
        String session = randomToken();
        sessions.put(session, new Entry(link.viewer, expiry(sessionMillis())));
        return session;
    }

    /** The viewer of a live session, or null. */
    public Viewer get(String session) {
        if (session == null) {
            return null;
        }
        Entry entry = sessions.get(session);
        if (entry == null || entry.expired()) {
            return null;
        }
        return entry.viewer;
    }

    public void logout(String session) {
        if (session != null) {
            sessions.remove(session);
        }
    }

    public static long sessionMillis() {
        return TimeUnit.HOURS.toMillis(Config.webSessionHours);
    }

    private static long expiry(long millis) {
        return System.currentTimeMillis() + millis;
    }

    private void purge() {
        purge(linkTokens);
        purge(sessions);
    }

    private static void purge(Map<String, Entry> map) {
        for (Iterator<Entry> it = map.values()
            .iterator(); it.hasNext();) {
            if (it.next()
                .expired()) {
                it.remove();
            }
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        StringBuilder token = new StringBuilder();
        for (byte b : bytes) {
            token.append(String.format("%02x", b));
        }
        return token.toString();
    }
}
