package net.mine_diver.developermode.feature.net;

/**
 * The last thing the server said about a request.
 *
 * <p>Requests are answered by a packet rather than a return value, so the
 * window that asked is no longer on the stack when the answer arrives. It
 * leaves the answer here, and whoever is showing a status picks it up.
 *
 * <p>{@link #sequence()} changes with every message, including a repeat of the
 * one before it, so a window can tell "said again" from "still saying".
 */
public final class DevStatus {
    private static String message = "";
    private static int sequence;

    private DevStatus() {}

    public static void set(String text) {
        message = text;
        sequence++;
    }

    public static String message() {
        return message;
    }

    public static int sequence() {
        return sequence;
    }
}
