package net.mine_diver.developermode.client.gui.radial;

import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.window.EntityListWindow;
import net.mine_diver.developermode.client.gui.window.SummonWindow;
import net.mine_diver.developermode.client.gui.window.ItemPickerWindow;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * The ring itself: a fixed number of compass slots, any of which may be empty.
 *
 * <p>Slot 0 points straight up and the rest follow clockwise, so the direction
 * you flick the mouse is the one you get. Bumping {@link #SLOTS} to eight is
 * the only change needed to make room for more.
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

    private static final RadialEntry[] ENTRIES = new RadialEntry[SLOTS];

    private RadialMenu() {}

    public static void bootstrap() {
        put(UP, new RadialEntry(
                "Composer", "Open the window desktop",
                new ItemStack(Block.CRAFTING_TABLE),
                returnTo -> ComposerScreen.open()));

        put(DOWN, new RadialEntry(
                "Items", "Pick something to give yourself",
                new ItemStack(Block.CHEST),
                returnTo -> ComposerScreen.reveal(ItemPickerWindow.class, ItemPickerWindow::new)));

        put(DOWN_LEFT, new RadialEntry(
                "Summon", "Place a new entity in the world",
                new ItemStack(Item.EGG),
                returnTo -> SummonWindow.open()));

        put(RIGHT, new RadialEntry(
                "Entities", "Everything loaded, nearest first",
                new ItemStack(Item.COMPASS),
                returnTo -> EntityListWindow.open()));
    }

    public static void put(int slot, RadialEntry entry) {
        ENTRIES[slot] = entry;
    }

    public static RadialEntry get(int slot) {
        return slot < 0 || slot >= SLOTS ? null : ENTRIES[slot];
    }
}
