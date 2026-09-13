package net.mine_diver.developermode.client.gui.radial;

import net.mine_diver.developermode.client.gui.ItemDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;

/**
 * One slot of the radial menu.
 *
 * <p>Label, hint, icon and availability are methods rather than fields so a
 * slot can describe whatever it is pointed at right now. See {@link EntityEntry}.
 */
public class RadialEntry {
    private final String label;
    private final String hint;
    private final ItemStack icon;
    private final RadialAction action;

    public RadialEntry(String label, String hint, ItemStack icon, RadialAction action) {
        this.label = label;
        this.hint = hint;
        this.icon = icon;
        this.action = action;
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
        ItemDraw.single(minecraft, icon, centerX - ItemDraw.SIZE / 2, centerY - ItemDraw.SIZE / 2);
    }

    public void perform(Screen returnTo) {
        action.perform(returnTo);
    }
}
