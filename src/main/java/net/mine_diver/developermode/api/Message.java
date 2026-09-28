package net.mine_diver.developermode.api;

/**
 * Something to tell the player, kept as a translation key until it is shown.
 *
 * <p>Built wherever the reason is known, which is often the server, and
 * translated only by the client that draws it. A server has no language files
 * to translate with, and the player reading it need not share its language.
 *
 * <p>Arguments are either text, dropped into the translation as it is, or
 * messages of their own, translated along with the one that holds them, for a
 * reason that is part of a bigger one. A literal carries text that has no key,
 * such as an exception's own message.
 */
public final class Message {
    private final String key;
    private final Object[] args;

    private Message(String key, Object[] args) {
        this.key = key;
        this.args = args;
    }

    /**
     * @param args each one a {@link Message}, or anything else, which is kept
     *             as its string form
     */
    public static Message of(String key, Object... args) {
        Object[] kept = new Object[args.length];
        for (int i = 0; i < args.length; i++)
            kept[i] = args[i] instanceof Message ? args[i] : String.valueOf(args[i]);
        return new Message(key, kept);
    }

    /** Text shown as it is, for what nobody could have written a key for. */
    public static Message literal(String text) {
        return new Message(null, new Object[] { text });
    }

    public boolean isLiteral() {
        return key == null;
    }

    /** The translation key, or null for a literal. */
    public String key() {
        return key;
    }

    /** Each one a {@link String} or a {@link Message}. A literal's only one is its text. */
    public Object[] args() {
        return args.clone();
    }
}
