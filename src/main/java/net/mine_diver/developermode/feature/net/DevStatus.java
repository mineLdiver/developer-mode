package net.mine_diver.developermode.feature.net;

import java.util.HashMap;
import java.util.Map;

/**
 * The last thing the server said about each kind of request.
 *
 * <p>Requests are answered by a packet rather than a return value, so the
 * window that asked is no longer on the stack when the answer arrives. It
 * leaves the answer here, and whoever is showing a status for that kind picks
 * it up.
 *
 * <p>Kept per kind so the item picker does not report what the summon picker
 * asked for. {@link #sequence} changes with every message, including a repeat
 * of the one before it, so a window can tell "said again" from "still saying".
 */
public final class DevStatus {
    public static final String GIVE = "give";
    public static final String SUMMON = "summon";
    public static final String ENTITY = "entity";

    private static final Map<String, String> MESSAGES = new HashMap<>();
    private static final Map<String, Integer> SEQUENCES = new HashMap<>();
    private static final Map<String, Boolean> OUTCOMES = new HashMap<>();

    private DevStatus() {}

    public static void set(String kind, String text, boolean ok) {
        MESSAGES.put(kind, text);
        OUTCOMES.put(kind, ok);
        SEQUENCES.merge(kind, 1, Integer::sum);
    }

    /** Whether the last message of this kind reported success. */
    public static boolean ok(String kind) {
        return OUTCOMES.getOrDefault(kind, false);
    }

    public static String message(String kind) {
        return MESSAGES.getOrDefault(kind, "");
    }

    public static int sequence(String kind) {
        return SEQUENCES.getOrDefault(kind, 0);
    }
}
