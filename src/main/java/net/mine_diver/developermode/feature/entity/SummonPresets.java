package net.mine_diver.developermode.feature.entity;

import net.mine_diver.developermode.feature.storage.DevStorage;
import net.mine_diver.developermode.feature.storage.Nbt;
import net.minecraft.nbt.NbtCompound;

import java.util.List;

/**
 * Saved spawn templates: named NBT, per entity type, on disk.
 *
 * <p>Saving is deliberate. The NBT you edit on the way to summoning something
 * does not come through here at all: that compound belongs to the arming and is
 * gone when you disarm. Only a preset you named and saved comes back.
 *
 * <p>Held as one {@link DevStorage} document, shaped entity id to name to
 * preset. Read once and written on every change, since presets are small and
 * are saved by hand, so there is nothing worth batching.
 */
public final class SummonPresets {
    private static final String DOCUMENT = "summon-presets";

    /** Null until first touched, never null afterwards. */
    private static NbtCompound root;

    private SummonPresets() {}

    /** Whether a type has anything saved, for marking it in the picker. */
    public static boolean any(String type) {
        return root().contains(type);
    }

    /** Saved names for a type, sorted. Empty if it has none. */
    public static List<String> namesFor(String type) {
        return Nbt.keys(root().getCompound(type));
    }

    /**
     * @return a copy of the saved preset, or null if there is no such thing
     */
    public static NbtCompound load(String type, String name) {
        NbtCompound forType = root().getCompound(type);
        return forType.contains(name) ? forType.getCompound(name).copy() : null;
    }

    /**
     * Saves a copy, so the caller can go on editing what it handed over.
     *
     * @return null on success, or a message describing what went wrong
     */
    public static String save(String type, String name, NbtCompound nbt) {
        if (name.isEmpty()) return "name it first";

        NbtCompound forType = root().getCompound(type);
        forType.put(name, nbt.copy());
        root().put(type, forType);

        return DevStorage.write(DOCUMENT, root()) ? null : "could not write the file, see the log";
    }

    public static void delete(String type, String name) {
        NbtCompound forType = root().getCompound(type);
        if (!forType.contains(name)) return;

        forType = Nbt.without(forType, name);
        // A type with nothing left under it would keep its marker in the
        // picker and promise saved presets it no longer has.
        if (forType.values().isEmpty()) {
            root = Nbt.without(root(), type);
        } else {
            root().put(type, forType);
        }

        DevStorage.write(DOCUMENT, root);
    }

    private static NbtCompound root() {
        if (root == null) {
            NbtCompound stored = DevStorage.read(DOCUMENT);
            root = stored == null ? new NbtCompound() : stored;
        }
        return root;
    }
}
