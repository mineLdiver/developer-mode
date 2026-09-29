package net.mine_diver.developermode.client.gui.radial;

import net.mine_diver.developermode.client.gui.window.PresetWindow;
import net.mine_diver.developermode.client.preset.Preset;
import net.mine_diver.developermode.client.preset.Presets;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The presets level: every preset in the order they are kept, and the editor
 * after them.
 *
 * <p>Nothing else goes on it. Every other level mixes switches, windows and
 * tools; this one reads the way the time and weather levels do, as states you
 * can be in, lit while you are in them.
 *
 * <p>The editor comes last rather than first so that the first preset has the
 * top of the ring, which is the one direction that does not move as presets
 * are added.
 *
 * <p>Built again only when the presets change, so the ring is handed the same
 * slots on every frame in between.
 */
final class PresetSlots implements Supplier<List<RadialEntry>> {
    private final RadialEntry editor = new RadialEntry(
            "gui.developermode.radial.edit_presets",
            new ItemStack(Item.SIGN),
            returnTo -> PresetWindow.open())
            .marked(RadialEntry.Badge.WINDOW);

    private List<RadialEntry> slots;
    private int revision;

    @Override
    public List<RadialEntry> get() {
        if (slots != null && revision == Presets.revision()) return slots;

        List<RadialEntry> built = new ArrayList<>();
        for (Preset preset : Presets.all()) built.add(RequestEntry.preset(preset));
        built.add(editor);

        slots = built;
        revision = Presets.revision();
        return slots;
    }
}
