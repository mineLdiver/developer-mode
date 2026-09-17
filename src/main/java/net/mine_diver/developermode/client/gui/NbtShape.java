package net.mine_diver.developermode.client.gui;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.List;

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
     * What to call a field, when its key is an implementation detail rather
     * than a name.
     *
     * @return null to show the key itself
     */
    default String labelFor(NbtCompound compound, String key) {
        return null;
    }

    /**
     * How to show a field's value, when the stored form is not the readable
     * one.
     *
     * @return null to show the value itself
     */
    default String displayFor(NbtCompound compound, String key, NbtElement element) {
        return null;
    }

    /**
     * The values a field is expected to hold, for fields that draw from
     * somewhere countable like a registry.
     *
     * <p>A field with choices is picked from rather than typed into. That is
     * the whole of the constraint: raw switches it off and hands the field
     * back, for a value the registry does not have or does not have yet.
     *
     * @return null when the field is free text, which is most of them
     */
    default List<Choice> choicesFor(NbtCompound compound, String key) {
        return null;
    }

    /**
     * One option, as it is stored and as it reads.
     *
     * @param value what goes in the tag
     * @param label what a person recognizes it by
     */
    record Choice(String value, String label) {}

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
