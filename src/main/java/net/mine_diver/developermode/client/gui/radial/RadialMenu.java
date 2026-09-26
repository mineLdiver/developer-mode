package net.mine_diver.developermode.client.gui.radial;

import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.window.EntityListWindow;
import net.mine_diver.developermode.client.gui.window.SummonWindow;
import net.mine_diver.developermode.client.gui.window.ItemPickerWindow;
import net.mine_diver.developermode.client.Sight;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.player.Heal;
import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.developermode.feature.player.Warp;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One level of the ring: as many slots as it has entries, and no more.
 *
 * <p>The ring divides itself by what is on it, so a level of two is two halves
 * and a level of five is five fifths. A fixed number of compass points would
 * mean seats that are there to be aimed at and do nothing, and a wedge that
 * cannot be chosen is a wedge that has to be learned around.
 *
 * <p>Slot 0 points straight up and the rest follow clockwise. Angles belong to
 * a level rather than to the ring, so they do not move once a level is built,
 * and a path down the tree stays the same movement every time even though two
 * levels need not agree on what points where.
 *
 * <p>A slot holding another menu is a category, and opening it replaces this
 * level rather than drawing beside it.
 */
public final class RadialMenu {
    /** Enough for any level, and what the screen sizes its per slot state to. */
    public static final int MAX_SLOTS = 16;

    private static RadialMenu root;

    private final String title;
    private final List<RadialEntry> entries = new ArrayList<>();

    public RadialMenu(String title) {
        this.title = title;
    }

    /** Named so that a level can say where you are once you are below the root. */
    public String title() {
        return title;
    }

    public RadialMenu add(RadialEntry entry) {
        if (entries.size() < MAX_SLOTS) entries.add(entry);
        return this;
    }

    public int size() {
        return entries.size();
    }

    public RadialEntry get(int slot) {
        return slot < 0 || slot >= entries.size() ? null : entries.get(slot);
    }

    /** Degrees each slot is given. A level with nothing on it still has a width. */
    public double slice() {
        return 360.0 / Math.max(1, entries.size());
    }

    /** Degrees clockwise from straight up to the middle of a slot. */
    public double angleOf(int slot) {
        return slot * slice();
    }

    public static RadialMenu root() {
        return root;
    }

    public static void bootstrap() {
        RadialMenu world = new RadialMenu("World")
                .add(new RadialEntry(
                        "Summon", "Place a new entity in the world",
                        new ItemStack(Item.EGG),
                        returnTo -> SummonWindow.open()))
                .add(new RadialEntry(
                        "Entities", "Everything loaded, nearest first",
                        new ItemStack(Item.COMPASS),
                        returnTo -> EntityListWindow.open()));

        RadialMenu player = new RadialMenu("Player")
                .add(PlayerEntry.power(
                        Power.GOD, "God mode", "Nothing in the world hurts you",
                        new ItemStack(Item.GOLDEN_APPLE)))
                .add(PlayerEntry.power(
                        Power.FLIGHT, "Flight", "Jump to rise, sneak to sink",
                        new ItemStack(Item.FEATHER)))
                .add(PlayerEntry.power(
                        Power.NOCLIP, "Noclip", "Through blocks, flying with it",
                        new ItemStack(Block.GLASS)))
                .add(PlayerEntry.power(
                        Power.INSTANT_BREAK, "Insta break", "One hit, anything, bedrock too",
                        new ItemStack(Item.DIAMOND_PICKAXE)))
                .add(PlayerEntry.action(
                        DevStatus.HEAL, "Heal", "Full health, no fire, full air",
                        new ItemStack(Item.COOKED_PORKCHOP),
                        returnTo -> Heal.request()))
                .add(PlayerEntry.action(
                        DevStatus.WARP, "Warp", "Stand where you are looking",
                        new ItemStack(Item.MAP),
                        returnTo -> Warp.request(Sight.reach())));

        root = new RadialMenu("Developer")
                .add(new RadialEntry(
                        "Composer", "Open the window desktop",
                        new ItemStack(Block.CRAFTING_TABLE),
                        returnTo -> ComposerScreen.open()))
                .add(new RadialEntry(
                        "World", "What is out there",
                        new ItemStack(Block.GRASS_BLOCK),
                        world))
                .add(new RadialEntry(
                        "Player", "What you are while you test",
                        new ItemStack(Item.GOLDEN_BOOTS),
                        player))
                .add(new RadialEntry(
                        "Items", "Pick something to give yourself",
                        new ItemStack(Block.CHEST),
                        returnTo -> ComposerScreen.reveal(ItemPickerWindow.class, ItemPickerWindow::new)));
    }
}
