package net.mine_diver.developermode.client.gui;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

/**
 * A compound that is recognizably something, so it can be shown as that thing.
 *
 * <p>Recognition is a guess and is allowed to be wrong. A shape that does not
 * match costs nothing, and the tree shows the compound the way it shows any
 * other. Nothing here can refuse an edit either: the most a shape does is
 * bring a number back inside the range the game would accept.
 */
public interface NbtShape {
    /** Whether this compound looks like the thing this shape knows about. */
    boolean matches(NbtCompound compound);

    /**
     * What to show on the compound's own row instead of a count of its keys.
     *
     * @return null to leave the default alone
     */
    String summarize(NbtCompound compound);

    /**
     * Brings a number typed into one of this compound's fields into range.
     *
     * @param key the field that was typed into
     * @return the value to actually store
     */
    long clamp(NbtCompound compound, String key, long value);

    /** The element under a key, or null. Beta's getters cannot say "absent". */
    static NbtElement get(NbtCompound compound, String key) {
        if (!compound.contains(key)) return null;
        for (Object entry : compound.values()) {
            NbtElement element = (NbtElement) entry;
            if (element.getKey().equals(key)) return element;
        }
        return null;
    }
}
