package net.mine_diver.developermode.feature.storage;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The bits of NBT handling neither Beta nor StationAPI hands you.
 */
public final class Nbt {
    private Nbt() {}

    /**
     * The same compound without one key.
     *
     * <p>Beta's NbtCompound can put and get but not remove, so taking a key out
     * means building the rest again. The elements themselves are shared with
     * the original rather than copied: the caller is replacing it, not keeping
     * both.
     */
    public static NbtCompound without(NbtCompound source, String key) {
        NbtCompound result = new NbtCompound();
        for (Object entry : source.values()) {
            NbtElement element = (NbtElement) entry;
            if (!element.getKey().equals(key)) result.put(element.getKey(), element);
        }
        return result;
    }

    /** Every key in a compound, sorted, since Beta backs it with a HashMap. */
    public static List<String> keys(NbtCompound compound) {
        List<String> keys = new ArrayList<>();
        for (Object entry : compound.values()) keys.add(((NbtElement) entry).getKey());
        Collections.sort(keys);
        return keys;
    }
}
