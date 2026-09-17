package net.mine_diver.developermode.feature.storage;

import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtString;

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

    /**
     * Copies values across where both sides already have the same key with the
     * same type, and changes nothing else.
     *
     * <p>For writing back to something whose compound cannot be swapped out
     * from under it, only changed in place. Nothing is added, removed or
     * retyped, which is the same limit the editor works under, so a round trip
     * through it can never ask for more than this can do.
     *
     * <p>Arrays are left alone. Nothing can edit one, so a difference in one
     * did not come from a person.
     */
    public static void mergeValues(NbtCompound target, NbtCompound source) {
        if (target == null || source == null) return;
        for (Object entry : source.values()) {
            NbtElement from = (NbtElement) entry;
            String key = from.getKey();
            if (!target.contains(key)) continue;
            copyValue(find(target, key), from);
        }
    }

    private static void copyValue(NbtElement to, NbtElement from) {
        if (to == null || from == null || to.getClass() != from.getClass()) return;

        if (to instanceof NbtByte a) a.value = ((NbtByte) from).value;
        else if (to instanceof NbtShort a) a.value = ((NbtShort) from).value;
        else if (to instanceof NbtInt a) a.value = ((NbtInt) from).value;
        else if (to instanceof NbtLong a) a.value = ((NbtLong) from).value;
        else if (to instanceof NbtFloat a) a.value = ((NbtFloat) from).value;
        else if (to instanceof NbtDouble a) a.value = ((NbtDouble) from).value;
        else if (to instanceof NbtString a) a.value = ((NbtString) from).value;
        else if (to instanceof NbtCompound a) mergeValues(a, (NbtCompound) from);
        else if (to instanceof NbtList a) copyList(a, (NbtList) from);
    }

    private static void copyList(NbtList to, NbtList from) {
        // A list that changed length changed shape, and only the entries both
        // lists have are values.
        for (int i = 0; i < Math.min(to.size(), from.size()); i++) copyValue(to.get(i), from.get(i));
    }

    private static NbtElement find(NbtCompound compound, String key) {
        for (Object entry : compound.values()) {
            NbtElement element = (NbtElement) entry;
            if (element.getKey().equals(key)) return element;
        }
        return null;
    }

    /** Every key in a compound, sorted, since Beta backs it with a HashMap. */
    public static List<String> keys(NbtCompound compound) {
        List<String> keys = new ArrayList<>();
        for (Object entry : compound.values()) keys.add(((NbtElement) entry).getKey());
        Collections.sort(keys);
        return keys;
    }
}
