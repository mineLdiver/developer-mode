package net.mine_diver.developermode.client.gui.radial;

import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.window.EntityListWindow;
import net.mine_diver.developermode.client.gui.window.SummonWindow;
import net.mine_diver.developermode.client.gui.window.ItemPickerWindow;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * One level of the ring: a fixed number of compass slots, any of which may be
 * empty.
 *
 * <p>Slot 0 points straight up and the rest follow clockwise, so the direction
 * you flick the mouse is the one you get. A slot holding another menu is a
 * category, and opening it replaces this level rather than drawing beside it,
 * which is what keeps every level the same shape.
 *
 * <p>Angles belong to slots and never move. A path down the tree is therefore a
 * sequence of directions, and the same path is the same wrist movement every
 * time.
 */
public final class RadialMenu {
    public static final int SLOTS = 8;

    public static final int UP = 0;
    public static final int UP_RIGHT = 1;
    public static final int RIGHT = 2;
    public static final int DOWN_RIGHT = 3;
    public static final int DOWN = 4;
    public static final int DOWN_LEFT = 5;
    public static final int LEFT = 6;
    public static final int UP_LEFT = 7;

    private static RadialMenu root;

    private final String title;
    private final RadialEntry[] entries = new RadialEntry[SLOTS];

    public RadialMenu(String title) {
        this.title = title;
    }

    /** Named so that a level can say where you are once you are below the root. */
    public String title() {
        return title;
    }

    public RadialMenu put(int slot, RadialEntry entry) {
        entries[slot] = entry;
        return this;
    }

    public RadialEntry get(int slot) {
        return slot < 0 || slot >= SLOTS ? null : entries[slot];
    }

    /** How many slots are taken, which is what a category has to advertise. */
    public int filled() {
        int count = 0;
        for (RadialEntry entry : entries) {
            if (entry != null) count++;
        }
        return count;
    }

    public static RadialMenu root() {
        return root;
    }

    public static void bootstrap() {
        RadialMenu world = new RadialMenu("World")
                .put(UP, new RadialEntry(
                        "Summon", "Place a new entity in the world",
                        new ItemStack(Item.EGG),
                        returnTo -> SummonWindow.open()))
                .put(RIGHT, new RadialEntry(
                        "Entities", "Everything loaded, nearest first",
                        new ItemStack(Item.COMPASS),
                        returnTo -> EntityListWindow.open()));

        root = new RadialMenu("Developer")
                .put(UP, new RadialEntry(
                        "Composer", "Open the window desktop",
                        new ItemStack(Block.CRAFTING_TABLE),
                        returnTo -> ComposerScreen.open()))
                .put(RIGHT, new RadialEntry(
                        "World", "What is out there",
                        new ItemStack(Block.GRASS_BLOCK),
                        world))
                .put(DOWN, new RadialEntry(
                        "Items", "Pick something to give yourself",
                        new ItemStack(Block.CHEST),
                        returnTo -> ComposerScreen.reveal(ItemPickerWindow.class, ItemPickerWindow::new)));
    }
}
