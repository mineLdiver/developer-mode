package net.mine_diver.developermode.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.List;
import java.util.Map;

/**
 * A compound that is recognizably something, so it can be shown as that thing.
 *
 * <p>Recognition is a guess and is allowed to be wrong. A shape that does not
 * match costs nothing, and the tree shows the compound the way it shows any
 * other. Raw switches all of it off, which is the answer both to a guess that
 * missed and to a field whose stored form is the thing in question.
 */
public interface NbtShape {
    /** Whether this compound looks like the thing this shape knows about. */
    boolean matches(NbtCompound compound);

    /**
     * An icon standing for the whole compound, drawn on its own row.
     *
     * @return null for a compound that does not look like anything
     */
    default ItemStack iconFor(NbtCompound compound) {
        return null;
    }

    /**
     * What to show on the compound's own row instead of a count of its keys.
     *
     * @return null to leave the default alone
     */
    String summarize(NbtCompound compound);

    /**
     * How tall a view of its own this compound wants, under its header row.
     *
     * <p>A recognized compound is a thing rather than a bag of fields, and a
     * thing can be worth showing as itself: an item at the size items are
     * drawn, rather than an identifier and a number on two lines.
     *
     * @return zero for a compound that has nothing to show but its fields
     */
    default int cardHeight() {
        return 0;
    }

    /** Draws that view. Only called when {@link #cardHeight} is positive. */
    default void renderCard(Minecraft minecraft, NbtCompound compound,
                            int x, int y, int width, boolean hovered) {}

    /**
     * Whether a field is already said by the row above it.
     *
     * <p>A hidden field is still there and still written; it is only that
     * showing it again would be saying the same thing twice. Raw shows
     * everything.
     */
    default boolean hides(NbtCompound compound, String key) {
        return false;
    }

    /**
     * The things this compound, or one field of it, is expected to be.
     *
     * <p>Something with choices is picked from rather than typed into. A choice
     * may set more than one field, because what a person picks and what the
     * game stores are not always the same shape: one orange wool is an
     * identifier and a number.
     *
     * @param key the field being picked for, or null for the compound itself
     * @return null when there is nothing to choose from, which is most fields
     */
    default List<Choice> choicesFor(NbtCompound compound, String key) {
        return null;
    }

    /**
     * Brings a number typed into one of this compound's fields into range.
     *
     * @param key the field that was typed into
     * @return the value to actually store
     */
    long clamp(NbtCompound compound, String key, long value);

    /**
     * One option, as it reads and as it is stored.
     *
     * @param label  what a person recognizes it by
     * @param icon   something to look at alongside the label, or null
     * @param writes the fields it sets, by key, as they would have been typed
     */
    record Choice(String label, ItemStack icon, Map<String, String> writes) {}

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
