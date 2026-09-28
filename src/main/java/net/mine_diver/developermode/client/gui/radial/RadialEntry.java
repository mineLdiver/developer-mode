package net.mine_diver.developermode.client.gui.radial;

import net.mine_diver.developermode.client.Lang;
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
 * <p>Named by a translation key, with its hint under {@code .hint} beneath it,
 * and translated each time it is drawn.
 *
 * <p>A slot either does something or leads somewhere. Which one it is decides
 * what a click on it means, so the two are kept apart rather than left to a
 * null check at the call site.
 */
public class RadialEntry {
    /**
     * What choosing a slot will do, where that is more than doing it.
     *
     * <p>Doing something straight away is what a slot is expected to do, so
     * it has no mark: a mark is only for when something follows the click.
     * A slot that leads to another level says so with the sheets stacked
     * behind it, which is its own mark.
     */
    public enum Badge {
        /** Does it now, and there is nothing more to it. */
        NONE,
        /** Opens a window, where the rest of it happens. */
        WINDOW,
        /** Hands you a tool, which does nothing until you aim it and click. */
        TOOL,
        /** Turns something on or off, and shows which. */
        SWITCH,
        /** Can also be held, to lock the world to it. */
        LOCK
    }

    private final String key;
    private final ItemStack icon;
    private final RadialAction action;
    private final RadialMenu submenu;
    private Badge badge = Badge.NONE;

    public RadialEntry(String key, ItemStack icon, RadialAction action) {
        this(key, icon, action, null);
    }

    /** A category: clicking it descends instead of doing anything. */
    public RadialEntry(String key, ItemStack icon, RadialMenu submenu) {
        this(key, icon, null, submenu);
    }

    private RadialEntry(String key, ItemStack icon, RadialAction action, RadialMenu submenu) {
        this.key = key;
        this.icon = icon;
        this.action = action;
        this.submenu = submenu;
    }

    /** Sets the mark this slot carries, for a slot built as one kind of thing. */
    public RadialEntry marked(Badge badge) {
        this.badge = badge;
        return this;
    }

    /** What choosing this slot will do, drawn on it so it can be read beforehand. */
    public Badge badge() {
        return lockable() ? Badge.LOCK : badge;
    }

    /** The level this leads to, or null if it is something to do rather than somewhere to go. */
    public RadialMenu submenu() {
        return submenu;
    }

    public String label() {
        return Lang.get(key);
    }

    public String hint() {
        return Lang.get(key + ".hint");
    }

    /** A disabled slot still shows, and its hint should say why it is off. */
    public boolean enabled() {
        return true;
    }

    /**
     * Whether what this slot switches is currently on.
     *
     * <p>Not the same question as {@link #enabled()}, which is about whether
     * the slot can be chosen at all. A slot that is on is lit in the ring, so
     * a level of switches can be read without pointing at each one in turn.
     */
    public boolean on() {
        return false;
    }

    /**
     * Whether holding the button down on this slot locks the world to it.
     *
     * <p>A click still does what it always does. Holding is a second, slower
     * answer to the same slot, so a lock is never set by a click that went on
     * a moment too long: it takes a hold long enough to watch it fill.
     */
    public boolean lockable() {
        return false;
    }

    /** Whether the world is locked to what this slot chooses. */
    public boolean locked() {
        return false;
    }

    /** Locks the world to this slot, or unlocks it if it is locked already. */
    public void toggleLock() {}

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
