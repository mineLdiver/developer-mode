package net.mine_diver.developermode.client;

import net.mine_diver.developermode.feature.Message;
import net.minecraft.client.resource.language.I18n;

/**
 * Turns keys into the words the player reads.
 *
 * <p>Asked at the moment something is drawn rather than when it is built, so
 * nothing built before the language files are loaded is stuck showing its key.
 * A key with no translation comes out as itself, which is at least a name to
 * look for in the language file.
 */
public final class Lang {
    private Lang() {}

    /** @param args filled into the translation's {@code %s} placeholders in order */
    public static String get(String key, Object... args) {
        return args.length == 0 ? I18n.getTranslation(key) : I18n.getTranslation(key, args);
    }

    /**
     * The message in words, with any messages among its arguments translated
     * as well. No message is no words, rather than a null to check for.
     */
    public static String of(Message message) {
        if (message == null) return "";

        Object[] args = message.args();
        if (message.isLiteral()) return (String) args[0];

        for (int i = 0; i < args.length; i++)
            if (args[i] instanceof Message inner) args[i] = of(inner);
        return get(message.key(), args);
    }
}
