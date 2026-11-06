package io.github.mpdairy.monopaint;

import java.util.Collection;

/**
 * Who is on a live page, and its status in words (the owner, 2026-11-01: "also add connection status?"), the Manta's and
 * a guest's alike. The relays (scripts/live-probe/relay.py, {@link LiveHost}) list every listener's id to each one that
 * asks ({@code /listen?here=1}) whenever one comes or goes, in an event of its own ({@link #EVENT}) that a listener
 * reading only plain data lines (an older one, a probe) never sees. A listener gives its id with {@code from=}, as a
 * sender does: {@link #ID}, random and new each session or page load (the owner, 2026-11-03: the relay learns no one's name), a
 * Manta's marked with the link's key ({@link LiveCrypt#hostId}) so only those with the key tell the host. Pure, checked on
 * the desktop ({@code LiveHostChecks}).
 */
final class LivePresence {
    /** The event's name; a listener's or sender's id, as the relays take it (anything else they show as "-"). */
    static final String EVENT = "here", ID = "[0-9a-f]{16}";
    /** A guest reconnecting this long, ms, has lost the connection. */
    static final long LOST_MS = 15000;
    static final String CONNECTED = "Connected", RECONNECTING = "Reconnecting…", LOST = "Connection lost", HOST_GONE = "Host gone";

    /**
     * The relays' heartbeat: an event of its own every {@link #STILL_MS} while nothing else goes, its data empty (a bare
     * "data" line: a listener reading only "data: " lines never sees it; a browser's EventSource never shows a comment
     * line, so a comment wouldn't do, and dispatches an event only with a data field). A
     * guest that hears nothing for {@link #SILENT_MS} (two missed) starts again: a Wi-Fi gone without a word, or a Manta
     * asleep mid-line, leaves its connection open and silent, never closed.
     */
    static final String STILL = "still";
    static final long STILL_MS = 10000, SILENT_MS = 25000;
    static final String STILL_LINE = "event: " + STILL + "\ndata\n\n";

    /** A guest's status that stops its drawing until it changes: no host, or the connection lost. */
    static boolean cut(String said) { return HOST_GONE.equals(said) || LOST.equals(said); }
    /**
     * What a guest's cut-off window says under its status, or null when it isn't cut off. Lost: the page keeps trying,
     * so it promises nothing; asleep is the Manta's usual way to go (the owner, 2026-11-02).
     */
    static String cutNote(String said) { return HOST_GONE.equals(said) ? CUT_NOTE : LOST.equals(said) ? LOST_NOTE : null; }
    static final String CUT_NOTE = "Drawing waits here until the host is back.", LOST_NOTE = "Waiting for the Manta, maybe asleep. Back within a minute or two of waking.";

    /** The event a relay sends: the listeners' ids, space apart. */
    static String line(Collection<String> names) { return "event: " + EVENT + "\ndata: " + String.join(" ", names) + "\n\n"; }
    /** The ids in an event's data. */
    static String[] names(String data) { String d = data.trim(); return d.isEmpty() ? new String[0] : d.split(" "); }

    /**
     * The Manta's words: "Connected, 2 guests", "Connected, no guests", "Connection lost"; {@code names} null while the
     * relay hasn't said who is there. Every listener but this tablet ({@code me}) is a guest.
     */
    static String host(boolean connected, String[] names, String me) {
        if (!connected) return LOST;
        if (names == null) return CONNECTED;
        int guests = guests(names, me);
        return CONNECTED + ", " + (guests == 0 ? "no guests" : guests + (guests == 1 ? " guest" : " guests"));
    }

    /** How many listen but this tablet ({@code me}); none while the relay hasn't said ({@code names} null). */
    static int guests(String[] names, String me) {
        int guests = 0;
        if (names != null) for (String n : names) if (!n.equals(me)) guests++;
        return guests;
    }

    /**
     * A guest's words: "Connected", "Connected, 2 other guests", "Host gone" (the relay there, no Manta on it: no id
     * marked with {@code crypt}'s key), "Reconnecting…" for {@link #LOST_MS}, then "Connection lost"; {@code names}
     * null while the relay hasn't said.
     */
    static String guest(boolean connected, long lostMs, String[] names, String me, LiveCrypt crypt) {
        if (!connected) return lostMs < LOST_MS ? RECONNECTING : LOST;
        if (names == null) return CONNECTED;
        int hosts = 0, others = 0;
        for (String n : names) if (crypt.isHost(n)) hosts++; else if (!n.equals(me)) others++;
        if (hosts == 0) return HOST_GONE;
        return others == 0 ? CONNECTED : CONNECTED + ", " + others + (others == 1 ? " other guest" : " other guests");
    }
}
