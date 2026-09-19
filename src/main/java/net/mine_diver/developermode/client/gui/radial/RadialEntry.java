package net.mine_diver.developermode.client.gui.radial;

import net.mine_diver.developermode.client.gui.ItemDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;

/**
 * One slot of the radial menu.
 *
 * <p>Label, hint, icon and availability are methods rather than fields so a
 * slot can describe whatever it is pointed at right now.
 *
 * <p>A slot either does something or leads somewhere. Which one it is decides
 * what a click on it means, so the two are kept apart rather than left to a
 * null check at the call site.
 */
public class RadialEntry {
    private final String label;
    private final String hint;
    private final ItemStack icon;
    private final RadialAction action;
    private final RadialMenu submenu;

    public RadialEntry(String label, String hint, ItemStack icon, RadialAction action) {
        this(label, hint, icon, action, null);
    }

    /** A category: clicking it descends instead of doing anything. */
    public RadialEntry(String label, String hint, ItemStack icon, RadialMenu submenu) {
        this(label, hint, icon, null, submenu);
    }

    private RadialEntry(String label, String hint, ItemStack icon,
                        RadialAction action, RadialMenu submenu) {
        this.label = label;
        this.hint = hint;
        this.icon = icon;
        this.action = action;
        this.submenu = submenu;
    }

    /** The level this leads to, or null if it is something to do rather than somewhere to go. */
    public RadialMenu submenu() {
        return submenu;
    }

    public String label() {
        return label;
    }

    public String hint() {
        return hint;
    }

    /** A disabled slot still shows, and its hint should say why it is off. */
    public boolean enabled() {
        return true;
    }

    public void renderIcon(Minecraft minecraft, int centerX, int centerY) {
        renderIcon(minecraft, centerX, centerY, ItemDraw.SIZE);
    }

    /** Sized, so a slot can grow under the stick without moving off its angle. */
    public void renderIcon(Minecraft minecraft, int centerX, int centerY, int size) {
        ItemDraw.scaled(minecraft, icon, centerX - size / 2, centerY - size / 2, size);
    }

    public void perform(Screen returnTo) {
        action.perform(returnTo);
    }
}
