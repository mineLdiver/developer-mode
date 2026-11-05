package net.mine_diver.developermode.client.preset;

import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.api.setting.Setting;
import net.mine_diver.developermode.api.setting.SettingRegistry;
import net.mine_diver.developermode.api.setting.Switch;
import net.mine_diver.developermode.api.setting.SwitchSetting;
import net.mine_diver.developermode.client.Lang;
import net.mine_diver.developermode.client.gui.radial.RadialMenu;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.setting.DeveloperSettings;
import net.mine_diver.developermode.feature.setting.SettingChange;
import net.mine_diver.developermode.feature.setting.TimeSetting.TimeOfDay;
import net.mine_diver.developermode.feature.setting.WeatherSetting.WeatherKind;
import net.mine_diver.developermode.feature.storage.DevStorage;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The presets this client keeps, in the order the ring lays them out, and
 * choosing one.
 *
 * <p>Kept here rather than with a player or a world, because what a preset
 * means is a preference of whoever made it, and it should mean the same thing
 * in the next world they open and on the next server they join. Choosing one
 * is still only a request, which the world is as entitled to turn down as any
 * other.
 *
 * <p>Held as one {@link DevStorage} document, read once and written on every
 * change, like summon presets. Until there is one, every client starts with
 * the same two: Creative, which is the powers of a creative mode, and
 * Daylight, which is noon and a clear sky, kept. Nothing is written until
 * something is changed, so a client that never opens the editor never has the
 * file.
 */
public final class Presets {
    /** One slot of the level is the editor, and every other one can be a preset. */
    public static final int MAX = RadialMenu.MAX_SLOTS - 1;

    private static final String DOCUMENT = "presets";
    private static final String LIST_KEY = "Presets";

    private static final int NBT_COMPOUND = 10;

    /** Null until first touched. */
    private static List<Preset> presets;
    private static int revision;

    private Presets() {}

    /** Every preset, in order. */
    public static List<Preset> all() {
        return Collections.unmodifiableList(presets());
    }

    /**
     * Changes every time the presets do, so something built from them can
     * tell when to build again without comparing them all.
     */
    public static int revision() {
        return revision;
    }

    /**
     * Keeps a preset, in place of the one at an index, or after the last one
     * when the index is one past it.
     *
     * @return null on success, or a message describing what went wrong
     */
    public static Message put(int index, Preset preset) {
        if (preset.name().isEmpty()) return Message.of("message.developermode.name_it_first");

        List<Preset> presets = presets();
        if (index < 0 || index > presets.size()) return Message.of("message.developermode.nothing_to_write");
        if (index == presets.size()) {
            if (presets.size() >= MAX) return Message.of("message.developermode.presets_full");
            presets.add(preset);
        } else {
            presets.set(index, preset);
        }
        return write();
    }

    /** @return null on success, or a message describing what went wrong */
    public static Message delete(int index) {
        List<Preset> presets = presets();
        if (index < 0 || index >= presets.size()) return null;
        presets.remove(index);
        return write();
    }

    /**
     * Moves a preset one place earlier or later, which moves where it sits on
     * the ring.
     *
     * @return null on success, or a message describing what went wrong
     */
    public static Message move(int index, int by) {
        List<Preset> presets = presets();
        int target = index + by;
        if (index < 0 || index >= presets.size() || target < 0 || target >= presets.size()) return null;
        Collections.swap(presets, index, target);
        return write();
    }

    /**
     * Puts a preset in place, or lets go of what it keeps if it is in place
     * already.
     *
     * <p>A preset with nothing to let go of is put in place again, which for
     * one that only moves the sun is the point of choosing it.
     */
    public static void choose(Preset preset, PlayerEntity player) {
        if (player == null || preset.isEmpty()) return;

        boolean release = preset.holds() && preset.inEffect(player);
        SettingChange.request(DevStatus.PRESET, release ? preset.release() : preset.changes());
    }

    /**
     * What a preset does, in the words of the settings it is made of, so it
     * can be read before it is chosen: "God mode, Flight, Time: Noon locked".
     *
     * <p>A switch is said by its name alone when it is switched on, since that
     * is what a switch being in a preset usually means.
     */
    public static String describe(Preset preset) {
        List<String> parts = new ArrayList<>();
        for (Setting<?> setting : SettingRegistry.INSTANCE) {
            Preset.Part part = preset.part(setting);
            if (part == null) continue;

            String name = Lang.get(setting.translationKey());
            String value = Lang.get(part.valueKey(setting));
            if (setting instanceof SwitchSetting && Switch.ON.asString().equals(part.value())) {
                parts.add(name);
            } else {
                parts.add(Lang.get(part.locked() ? "gui.developermode.presets.part.locked" : "gui.developermode.presets.part",
                        name, value));
            }
        }
        return parts.isEmpty() ? Lang.get("gui.developermode.presets.nothing") : String.join(", ", parts);
    }

    private static List<Preset> presets() {
        if (presets != null) return presets;

        NbtCompound stored = DevStorage.read(DOCUMENT);
        if (stored == null || !stored.contains(LIST_KEY)) {
            presets = defaults();
            return presets;
        }

        presets = new ArrayList<>();
        NbtList list = stored.getList(LIST_KEY);
        for (int i = 0; i < list.size() && presets.size() < MAX; i++) {
            if (list.get(i).getType() == NBT_COMPOUND) presets.add(Preset.read((NbtCompound) list.get(i)));
        }
        return presets;
    }

    private static List<Preset> defaults() {
        Preset.Part on = new Preset.Part(Switch.ON.asString(), false);

        List<Preset> defaults = new ArrayList<>();
        defaults.add(Preset.empty(Lang.get("gui.developermode.presets.creative"))
                .withIcon(new ItemStack(Block.BRICKS))
                .with(DeveloperSettings.GOD, on)
                .with(DeveloperSettings.FLIGHT, on)
                .with(DeveloperSettings.INSTANT_BREAK, on)
                .with(DeveloperSettings.ENDLESS, on));
        defaults.add(Preset.empty(Lang.get("gui.developermode.presets.daylight"))
                .withIcon(new ItemStack(Block.GLOWSTONE))
                .with(DeveloperSettings.TIME, new Preset.Part(TimeOfDay.NOON.asString(), true))
                .with(DeveloperSettings.WEATHER, new Preset.Part(WeatherKind.CLEAR.asString(), true)));
        return defaults;
    }

    private static Message write() {
        revision++;

        NbtList list = new NbtList();
        for (Preset preset : presets) list.add(preset.write());
        NbtCompound root = new NbtCompound();
        root.put(LIST_KEY, list);

        return DevStorage.write(DOCUMENT, root) ? null : Message.of("message.developermode.could_not_write");
    }
}
