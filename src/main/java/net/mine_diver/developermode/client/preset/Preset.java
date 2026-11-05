package net.mine_diver.developermode.client.preset;

import net.mine_diver.developermode.api.setting.LockableSetting;
import net.mine_diver.developermode.api.setting.Setting;
import net.mine_diver.developermode.api.setting.SettingRegistry;
import net.mine_diver.developermode.feature.setting.SettingChange;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.modificationstation.stationapi.api.util.Identifier;
import net.modificationstation.stationapi.api.util.StringIdentifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Several settings, set at once.
 *
 * <p>A preset holds a value for any number of {@link Setting}s, each either
 * set or locked, and knows nothing else about them. Anything registered as a
 * setting can be in one.
 *
 * <p>What it puts in place is of two kinds, and which is the setting's to
 * say. Settings that stay, and settings it locks, remain as it left them, so
 * they are what a preset is lit by, and choosing a lit preset lets go of them:
 * what stays goes back to rest, and what was locked is unlocked. A setting
 * that drifts and was only set is done once on the way in and then left to
 * drift; counting it would put the light out by itself, a quarter of a day
 * after choosing a preset that moves the sun.
 *
 * <p>Settings are kept by identifier, and a part for a setting that is not
 * registered, because the mod that adds it is not installed or has renamed
 * it, is kept as it is and otherwise left out: saved again, but never applied,
 * lit by, or shown. So a preset outlives the settings it mentions coming and
 * going.
 *
 * <p>Immutable. A part for a registered setting is kept in the shape the
 * setting allows: a value it does not have is dropped, and a lock it cannot
 * hold is taken off.
 *
 * @param icon  what it is drawn as, or null for the one every preset has
 * @param parts what it sets, by the setting's identifier
 */
public record Preset(String name, ItemStack icon, Map<Identifier, Part> parts) {
    /**
     * What a preset puts one setting at.
     *
     * @param value  the value's name, which is how a preset keeps it, since
     *               that is all there is of a setting that is not registered
     * @param locked whether it holds it there, for a setting that can be held
     */
    public record Part(String value, boolean locked) {
        /** The key its value is named by, as a value of that setting. */
        public <V extends Enum<V> & StringIdentifiable> String valueKey(Setting<V> setting) {
            V known = setting.value(value);
            return known == null ? value : setting.valueKey(known);
        }
    }

    private static final String NAME_KEY = "Name";
    private static final String ICON_KEY = "Icon";
    private static final String SETTINGS_KEY = "Settings";
    private static final String VALUE_KEY = "Value";
    private static final String LOCKED_KEY = "Locked";

    public Preset {
        name = name == null ? "" : name;

        Map<Identifier, Part> kept = new LinkedHashMap<>();
        parts.forEach((id, part) -> {
            Setting<?> setting = SettingRegistry.INSTANCE.get(id);
            if (setting == null) {
                kept.put(id, part);
            } else if (setting.value(part.value) != null) {
                kept.put(id, new Part(part.value, part.locked && setting instanceof LockableSetting));
            }
        });
        parts = Collections.unmodifiableMap(kept);
    }

    /** Nothing in it at all, which a new one starts out as. */
    public static Preset empty(String name) {
        return new Preset(name, null, Map.of());
    }

    /** What it puts a setting at, or null if the setting is not part of it. */
    public @Nullable Part part(Setting<?> setting) {
        return parts.get(setting.id());
    }

    /** Whether anything in it stays in place, which is what it is lit by. */
    public boolean lights() {
        return known().stream().anyMatch(entry -> stays(entry.setting, entry.part));
    }

    /** Whether choosing it again has anything to let go of. */
    public boolean holds() {
        return known().stream().anyMatch(entry -> letGo(entry.setting, entry.part) != null);
    }

    public boolean isEmpty() {
        return known().isEmpty();
    }

    /** Whether everything in it that stays is in place for this player. */
    public boolean inEffect(PlayerEntity player) {
        if (player == null || !lights()) return false;
        for (Known entry : known()) {
            Setting<?> setting = entry.setting;
            Part part = entry.part;
            if (setting.stays() && !part.value.equals(nameOf(setting.current(player)))) return false;
            if (part.locked && setting instanceof LockableSetting<?> lockable
                    && !part.value.equals(nameOf(lockable.locked(player)))) return false;
        }
        return true;
    }

    /** Everything it asks for when it is chosen and not in effect. */
    public List<SettingChange> changes() {
        List<SettingChange> changes = new ArrayList<>();
        for (Known entry : known()) {
            SettingChange.Kind kind = entry.part.locked ? SettingChange.Kind.LOCK : SettingChange.Kind.SET;
            changes.add(new SettingChange(entry.setting, kind, entry.part.value));
        }
        return changes;
    }

    /**
     * Everything it asks for when it is chosen in effect: what stays goes back
     * to rest and what it locked is unlocked. A setting it puts at rest
     * already, such as a power it switches off, has nothing to go back to.
     */
    public List<SettingChange> release() {
        List<SettingChange> changes = new ArrayList<>();
        for (Known entry : known()) {
            SettingChange change = letGo(entry.setting, entry.part);
            if (change != null) changes.add(change);
        }
        return changes;
    }

    public Preset withName(String name) {
        return new Preset(name, icon, parts);
    }

    public Preset withIcon(ItemStack icon) {
        return new Preset(name, icon, parts);
    }

    /** With a setting put at a value, or left out of it for a null part. */
    public Preset with(Setting<?> setting, @Nullable Part part) {
        Map<Identifier, Part> changed = new LinkedHashMap<>(parts);
        if (part == null) changed.remove(setting.id());
        else changed.put(setting.id(), part);
        return new Preset(name, icon, changed);
    }

    public NbtCompound write() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString(NAME_KEY, name);
        if (icon != null) nbt.put(ICON_KEY, icon.writeNbt(new NbtCompound()));

        NbtCompound settings = new NbtCompound();
        parts.forEach((id, part) -> {
            NbtCompound written = new NbtCompound();
            written.putString(VALUE_KEY, part.value);
            written.putBoolean(LOCKED_KEY, part.locked);
            settings.put(id.toString(), written);
        });
        nbt.put(SETTINGS_KEY, settings);
        return nbt;
    }

    /**
     * Read back as whatever still makes sense of it. A hand edit or a later
     * version could have put anything there: a key that is not an identifier
     * is dropped, and the constructor drops what a registered setting would
     * not take.
     */
    public static Preset read(NbtCompound nbt) {
        ItemStack icon = null;
        if (nbt.contains(ICON_KEY)) {
            icon = new ItemStack(nbt.getCompound(ICON_KEY));
            // An item that is no longer registered reads back as nothing.
            if (icon.getItem() == null) icon = null;
        }

        Map<Identifier, Part> parts = new LinkedHashMap<>();
        for (Object entry : nbt.getCompound(SETTINGS_KEY).values()) {
            if (!(entry instanceof NbtCompound written)) continue;
            Identifier id = Identifier.tryParse(((NbtElement) entry).getKey());
            if (id != null) parts.put(id, new Part(written.getString(VALUE_KEY), written.getBoolean(LOCKED_KEY)));
        }
        return new Preset(nbt.getString(NAME_KEY), icon, parts);
    }

    /** A part whose setting is registered, with the setting it is for. */
    private record Known(Setting<?> setting, Part part) {}

    /** The parts that mean something here, in the order settings were registered. */
    private List<Known> known() {
        List<Known> known = new ArrayList<>();
        for (Setting<?> setting : SettingRegistry.INSTANCE) {
            Part part = parts.get(setting.id());
            if (part != null) known.add(new Known(setting, part));
        }
        return known;
    }

    private static boolean stays(Setting<?> setting, Part part) {
        return setting.stays() || part.locked;
    }

    /** What letting go of a part asks for, or null if there is nothing to let go of. */
    private static @Nullable SettingChange letGo(Setting<?> setting, Part part) {
        if (part.locked && setting instanceof LockableSetting<?> lockable) return SettingChange.unlock(lockable);
        if (setting.stays() && !part.value.equals(setting.rest().asString())) return toRest(setting);
        return null;
    }

    private static <V extends Enum<V> & StringIdentifiable> SettingChange toRest(Setting<V> setting) {
        return SettingChange.set(setting, setting.rest());
    }

    private static @Nullable String nameOf(@Nullable StringIdentifiable value) {
        return value == null ? null : value.asString();
    }
}
