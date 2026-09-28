package net.mine_diver.developermode.client.gui.radial;

import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.window.EntityListWindow;
import net.mine_diver.developermode.client.gui.window.SummonWindow;
import net.mine_diver.developermode.client.gui.window.ItemPickerWindow;
import net.mine_diver.developermode.client.Sight;
import net.mine_diver.developermode.client.tool.Tool;
import net.mine_diver.developermode.client.tool.ToolMode;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.player.Heal;
import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.developermode.feature.world.Clearing;
import net.mine_diver.developermode.feature.world.Locks;
import net.mine_diver.developermode.feature.world.Time;
import net.mine_diver.developermode.feature.world.Weather;
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

    /** The dye whose damage value makes it bone meal. */
    private static final int BONE_MEAL = 15;

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
        // Laid out as the sky's own dial: noon at the top, midnight at the
        // bottom, and the day running clockwise between them.
        RadialMenu time = new RadialMenu("Time")
                .add(timeOfDay(Time.NOON, "Noon", "The sun straight overhead",
                        new ItemStack(Block.GLOWSTONE)))
                .add(timeOfDay(Time.DUSK, "Dusk", "The sun going down",
                        new ItemStack(Block.JACK_O_LANTERN)))
                .add(timeOfDay(Time.MIDNIGHT, "Midnight", "The middle of the night",
                        new ItemStack(Block.OBSIDIAN)))
                .add(timeOfDay(Time.DAWN, "Dawn", "The sun coming up",
                        new ItemStack(Block.DANDELION)));

        RadialMenu weather = new RadialMenu("Weather")
                .add(weatherKind(Weather.CLEAR, "Clear", "Blue sky, for as long as it lasts",
                        new ItemStack(Block.GLASS)))
                .add(weatherKind(Weather.RAIN, "Rain", "Rain, and snow where it is cold",
                        new ItemStack(Item.WATER_BUCKET)))
                .add(weatherKind(Weather.STORM, "Storm", "Rain, thunder and lightning",
                        new ItemStack(Item.GUNPOWDER)));

        RadialMenu world = new RadialMenu("World")
                .add(new RadialEntry(
                        "Entities", "Everything loaded, nearest first",
                        new ItemStack(Item.COMPASS),
                        returnTo -> EntityListWindow.open()))
                .add(RequestEntry.action(
                        DevStatus.SWEEP, "Sweep", "Every dropped item and arrow in sight",
                        new ItemStack(Block.CACTUS),
                        returnTo -> Clearing.request(Clearing.LITTER, Sight.reach())))
                .add(RequestEntry.action(
                        DevStatus.PURGE, "Purge", "Every mob in sight, gone without a drop",
                        new ItemStack(Item.DIAMOND_SWORD),
                        returnTo -> Clearing.request(Clearing.MOBS, Sight.reach())))
                .add(new RadialEntry(
                        "Weather", "Rain or shine",
                        new ItemStack(Item.SNOWBALL),
                        weather))
                .add(new RadialEntry(
                        "Time", "Move the sun, only ever forward",
                        new ItemStack(Item.CLOCK),
                        time));

        // Grouped by what choosing one does rather than by what it acts on,
        // which is sometimes the world and sometimes you: each one closes the
        // ring on something to point with, and does nothing until you do.
        RadialMenu tools = new RadialMenu("Tools")
                .add(new RadialEntry(
                        "Summon", "Place new entities wherever you point",
                        new ItemStack(Item.EGG),
                        returnTo -> SummonWindow.open()))
                .add(tool(Tool.WARP, "Go wherever you point",
                        new ItemStack(Item.MAP)))
                .add(tool(Tool.GROW, "Bone meal whatever you point at",
                        new ItemStack(Item.DYE, 1, BONE_MEAL)))
                .add(tool(Tool.BLAST, "TNT wherever you point, and none of it for you",
                        new ItemStack(Block.TNT)))
                .add(tool(Tool.SMITE, "Lightning wherever you point",
                        new ItemStack(Item.GLOWSTONE_DUST)));

        RadialMenu player = new RadialMenu("Player")
                .add(RequestEntry.power(
                        Power.GOD, "God mode", "Nothing in the world hurts you",
                        new ItemStack(Item.GOLDEN_APPLE)))
                .add(RequestEntry.power(
                        Power.FLIGHT, "Flight", "Jump to rise, sneak to sink",
                        new ItemStack(Item.FEATHER)))
                .add(RequestEntry.power(
                        Power.NOCLIP, "Noclip", "Through blocks, flying with it",
                        new ItemStack(Block.GLASS)))
                .add(RequestEntry.power(
                        Power.INSTANT_BREAK, "Insta break", "One hit, anything, bedrock too",
                        new ItemStack(Item.DIAMOND_PICKAXE)))
                .add(RequestEntry.action(
                        DevStatus.HEAL, "Heal", "Full health, no fire, full air",
                        new ItemStack(Item.COOKED_PORKCHOP),
                        returnTo -> Heal.request()));

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
                        "Tools", "Something to point at the world",
                        new ItemStack(Item.STICK),
                        tools))
                .add(new RadialEntry(
                        "Items", "Pick something to give yourself",
                        new ItemStack(Block.CHEST),
                        returnTo -> ComposerScreen.reveal(ItemPickerWindow.class, ItemPickerWindow::new)));
    }

    /** A tool, armed rather than used, and lit while it is the one in hand. */
    private static RadialEntry tool(Tool tool, String hint, ItemStack icon) {
        return new RadialEntry(tool.label(), hint, icon, returnTo -> ToolMode.arm(tool)) {
            @Override
            public boolean on() {
                return ToolMode.armed() == tool;
            }
        };
    }

    /** A time of day, lit for the quarter of the day nearest it, and one the sun can be locked at. */
    private static RadialEntry timeOfDay(int timeOfDay, String label, String hint, ItemStack icon) {
        return RequestEntry.state(DevStatus.TIME, label, hint, icon,
                world -> Time.around(world, timeOfDay),
                returnTo -> Time.request(timeOfDay),
                world -> Locks.lockedTime(world) == timeOfDay,
                lock -> Locks.request(Locks.TIME, lock, timeOfDay));
    }

    /** A kind of weather, lit while the sky is doing it, and one the sky can be locked to. */
    private static RadialEntry weatherKind(byte weather, String label, String hint, ItemStack icon) {
        return RequestEntry.state(DevStatus.WEATHER, label, hint, icon,
                world -> Weather.of(world) == weather,
                returnTo -> Weather.request(weather),
                world -> Locks.lockedWeather(world) == weather,
                lock -> Locks.request(Locks.WEATHER, lock, weather));
    }
}
