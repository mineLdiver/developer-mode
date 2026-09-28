package net.mine_diver.developermode.client.gui.radial;

import net.mine_diver.developermode.api.setting.Setting;
import net.mine_diver.developermode.client.Lang;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.window.EntityListWindow;
import net.mine_diver.developermode.client.gui.window.SummonWindow;
import net.mine_diver.developermode.client.gui.window.ItemPickerWindow;
import net.mine_diver.developermode.client.Sight;
import net.mine_diver.developermode.client.tool.Tool;
import net.mine_diver.developermode.client.tool.ToolMode;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.player.Heal;
import net.mine_diver.developermode.feature.setting.DeveloperSettings;
import net.mine_diver.developermode.feature.setting.TimeSetting;
import net.mine_diver.developermode.feature.setting.WeatherSetting;
import net.mine_diver.developermode.feature.world.Clearing;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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

    /** The dye whose damage value makes it bone meal. */
    private static final int BONE_MEAL = 15;

    private static RadialMenu root;

    private final Supplier<String> key;
    private final List<RadialEntry> entries = new ArrayList<>();

    /** @param key the translation key of its name, usually the same as the slot that leads to it */
    public RadialMenu(String key) {
        this(() -> key);
    }

    /** @param key asked for the translation key of its name each time it is drawn */
    public RadialMenu(Supplier<String> key) {
        this.key = key;
    }

    /** Named so that a level can say where you are once you are below the root. */
    public String title() {
        return Lang.get(key.get());
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
        // Laid out as the sky's own dial: noon at the top, midnight at the
        // bottom, and the day running clockwise between them.
        Setting timeSetting = DeveloperSettings.TIME;
        RadialMenu time = new RadialMenu(timeSetting::translationKey)
                .add(RequestEntry.choice(timeSetting, TimeSetting.NOON, new ItemStack(Block.GLOWSTONE)))
                .add(RequestEntry.choice(timeSetting, TimeSetting.DUSK, new ItemStack(Block.JACK_O_LANTERN)))
                .add(RequestEntry.choice(timeSetting, TimeSetting.MIDNIGHT, new ItemStack(Block.OBSIDIAN)))
                .add(RequestEntry.choice(timeSetting, TimeSetting.DAWN, new ItemStack(Block.DANDELION)));

        Setting weatherSetting = DeveloperSettings.WEATHER;
        RadialMenu weather = new RadialMenu(weatherSetting::translationKey)
                .add(RequestEntry.choice(weatherSetting, WeatherSetting.CLEAR, new ItemStack(Block.GLASS)))
                .add(RequestEntry.choice(weatherSetting, WeatherSetting.RAIN, new ItemStack(Item.WATER_BUCKET)))
                .add(RequestEntry.choice(weatherSetting, WeatherSetting.STORM, new ItemStack(Item.GUNPOWDER)));

        RadialMenu world = new RadialMenu("gui.developermode.radial.world")
                .add(new RadialEntry(
                        "gui.developermode.radial.entities",
                        new ItemStack(Item.COMPASS),
                        returnTo -> EntityListWindow.open())
                        .marked(RadialEntry.Badge.WINDOW))
                .add(RequestEntry.action(
                        DevStatus.SWEEP, "gui.developermode.radial.sweep",
                        new ItemStack(Block.CACTUS),
                        returnTo -> Clearing.request(Clearing.LITTER, Sight.reach())))
                .add(RequestEntry.action(
                        DevStatus.PURGE, "gui.developermode.radial.purge",
                        new ItemStack(Item.DIAMOND_SWORD),
                        returnTo -> Clearing.request(Clearing.MOBS, Sight.reach())))
                .add(new RadialEntry(
                        weatherSetting::translationKey,
                        new ItemStack(Item.SNOWBALL),
                        weather))
                .add(new RadialEntry(
                        timeSetting::translationKey,
                        new ItemStack(Item.CLOCK),
                        time));

        // Grouped by what choosing one does rather than by what it acts on,
        // which is sometimes the world and sometimes you: each one closes the
        // ring on something to point with, and does nothing until you do.
        RadialMenu tools = new RadialMenu("gui.developermode.radial.tools")
                .add(new RadialEntry(
                        "gui.developermode.radial.summon",
                        new ItemStack(Item.EGG),
                        returnTo -> SummonWindow.open())
                        .marked(RadialEntry.Badge.WINDOW))
                .add(tool(Tool.WARP, new ItemStack(Item.MAP)))
                .add(tool(Tool.GROW, new ItemStack(Item.DYE, 1, BONE_MEAL)))
                .add(tool(Tool.BLAST, new ItemStack(Block.TNT)))
                .add(tool(Tool.SMITE, new ItemStack(Item.GLOWSTONE_DUST)));

        RadialMenu player = new RadialMenu("gui.developermode.radial.player")
                .add(RequestEntry.toggle(DeveloperSettings.GOD, new ItemStack(Item.GOLDEN_APPLE)))
                .add(RequestEntry.toggle(DeveloperSettings.FLIGHT, new ItemStack(Item.FEATHER)))
                .add(RequestEntry.toggle(DeveloperSettings.NOCLIP, new ItemStack(Block.GLASS)))
                .add(RequestEntry.toggle(DeveloperSettings.INSTANT_BREAK, new ItemStack(Item.DIAMOND_PICKAXE)))
                .add(RequestEntry.toggle(DeveloperSettings.ENDLESS, new ItemStack(Block.DISPENSER)))
                .add(RequestEntry.action(
                        DevStatus.HEAL, "gui.developermode.radial.heal",
                        new ItemStack(Item.COOKED_PORKCHOP),
                        returnTo -> Heal.request()));

        root = new RadialMenu("gui.developermode.radial.root")
                .add(new RadialEntry(
                        "gui.developermode.radial.composer",
                        new ItemStack(Block.CRAFTING_TABLE),
                        returnTo -> ComposerScreen.open())
                        .marked(RadialEntry.Badge.WINDOW))
                .add(new RadialEntry(
                        "gui.developermode.radial.world",
                        new ItemStack(Block.GRASS_BLOCK),
                        world))
                .add(new RadialEntry(
                        "gui.developermode.radial.player",
                        new ItemStack(Item.GOLDEN_BOOTS),
                        player))
                .add(new RadialEntry(
                        "gui.developermode.radial.tools",
                        new ItemStack(Item.STICK),
                        tools))
                .add(new RadialEntry(
                        "gui.developermode.radial.items",
                        new ItemStack(Block.CHEST),
                        returnTo -> ComposerScreen.reveal(ItemPickerWindow.class, ItemPickerWindow::new))
                        .marked(RadialEntry.Badge.WINDOW));
    }

    /** A tool, armed rather than used, and lit while it is the one in hand. */
    private static RadialEntry tool(Tool tool, ItemStack icon) {
        return new RadialEntry(tool.key(), icon, returnTo -> ToolMode.arm(tool)) {
            @Override
            public boolean on() {
                return ToolMode.armed() == tool;
            }
        }.marked(RadialEntry.Badge.TOOL);
    }
}
