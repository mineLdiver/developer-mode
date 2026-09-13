package net.mine_diver.developermode.client.gui.radial;

import net.minecraft.client.gui.screen.Screen;

/**
 * What a radial slot does when the menu key is released over it.
 */
@FunctionalInterface
public interface RadialAction {
    /**
     * @param returnTo the screen the radial was summoned from, or null if it
     *                 was summoned straight from the world. An action that has
     *                 nothing to show should hand control back to it.
     */
    void perform(Screen returnTo);
}
