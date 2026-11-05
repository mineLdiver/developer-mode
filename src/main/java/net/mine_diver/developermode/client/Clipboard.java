package net.mine_diver.developermode.client;

import net.mine_diver.developermode.DeveloperMode;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * Putting text where the system pastes from.
 *
 * <p>Beta only ever reads the clipboard, through the same toolkit, so writing
 * it goes the same way.
 */
public final class Clipboard {
    private Clipboard() {}

    /** @return whether it got there, which it does not where there is no system clipboard */
    public static boolean copy(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            return true;
        } catch (Exception | Error error) {
            DeveloperMode.LOGGER.warn("Could not copy to the clipboard", error);
            return false;
        }
    }
}
