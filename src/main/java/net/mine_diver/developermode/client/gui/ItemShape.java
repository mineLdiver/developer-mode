package net.mine_diver.developermode.client.gui;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtString;
import net.modificationstation.stationapi.api.registry.ItemRegistry;
import net.modificationstation.stationapi.api.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * An item stack, as one is actually written down.
 *
 * <p>Two spellings, because Station Flattening replaces the numeric id with an
 * identifier on the way out. What a running game writes carries
 * {@code stationapi:id} as a string; what a world written before flattening
 * carries is a short under {@code id}, and the data fixer has not necessarily
 * reached it yet. Both are item stacks and both are worth naming.
 *
 * <p>What this buys is mostly the id. Neither {@code 264} nor
 * {@code minecraft:wool} alongside a damage of 1 tells you it is orange wool,
 * and finding that out is usually why the NBT was opened.
 */
public final class ItemShape implements NbtShape {
    public static final ItemShape INSTANCE = new ItemShape();

    /** What Station Flattening writes in place of the numeric id. */
    private static final String FLATTENED_ID = "stationapi:id";
    private static final String LEGACY_ID = "id";
    private static final String COUNT = "Count";
    private static final String DAMAGE = "Damage";

    /** Every item there is, built once. A registry does not change after start. */
    private static List<Choice> choices;

    private ItemShape() {}

    @Override
    public List<Choice> choicesFor(NbtCompound compound, String key) {
        return FLATTENED_ID.equals(key) ? everyItem() : null;
    }

    private static synchronized List<Choice> everyItem() {
        if (choices != null) return choices;

        List<Choice> built = new ArrayList<>();
        for (Item item : ItemRegistry.INSTANCE) {
            if (item == null) continue;

            Identifier identifier = ItemRegistry.INSTANCE.getId(item);
            if (identifier == null) continue;

            String label = name(item, 0);
            built.add(new Choice(identifier.toString(), label == null ? identifier.path : label));
        }
        built.sort(Comparator.comparing(Choice::label));

        choices = built;
        return choices;
    }

    @Override
    public boolean matches(NbtCompound compound) {
        if (!(NbtShape.get(compound, COUNT) instanceof NbtByte)) return false;
        if (!(NbtShape.get(compound, DAMAGE) instanceof NbtShort)) return false;

        return NbtShape.get(compound, FLATTENED_ID) instanceof NbtString
                || NbtShape.get(compound, LEGACY_ID) instanceof NbtShort;
    }

    @Override
    public String summarize(NbtCompound compound) {
        int count = compound.getByte(COUNT);
        int damage = compound.getShort(DAMAGE);

        Item item = item(compound);
        if (item == null) return null;

        String name = name(item, damage);
        return name == null ? null : count + "x " + name;
    }

    @Override
    public long clamp(NbtCompound compound, String key, long value) {
        Item item = item(compound);

        return switch (key) {
            // Only the old spelling is a number. The new one is a string, and
            // nothing numeric is ever typed into it.
            case LEGACY_ID -> bound(value, 0, Item.ITEMS.length - 1);
            case COUNT -> bound(value, 1, item == null ? 64 : item.getMaxCount());
            // A damage value is durability on a tool and a variant on
            // everything else, so only tools have a ceiling worth applying.
            case DAMAGE -> item != null && item.getMaxDamage() > 0
                    ? bound(value, 0, item.getMaxDamage())
                    : bound(value, 0, Short.MAX_VALUE);
            default -> value;
        };
    }

    /** The item this compound names, whichever way it names it. */
    private static Item item(NbtCompound compound) {
        if (NbtShape.get(compound, FLATTENED_ID) instanceof NbtString) {
            try {
                return ItemRegistry.INSTANCE.get(Identifier.of(compound.getString(FLATTENED_ID)));
            } catch (Exception error) {
                // An identifier that does not parse names no item, which is a
                // compound this cannot speak for rather than a problem.
                return null;
            }
        }

        int id = compound.getShort(LEGACY_ID);
        return id < 0 || id >= Item.ITEMS.length ? null : Item.ITEMS[id];
    }

    private static String name(Item item, int damage) {
        try {
            String key = new ItemStack(item, 1, damage).getTranslationKey();
            if (key == null) return null;

            String translated = I18n.getTranslation(key + ".name");
            return translated == null || translated.isEmpty() || translated.equals(key + ".name")
                    ? key
                    : translated;
        } catch (Exception error) {
            // A stack an item did not expect can throw on the way to its name,
            // and a row that cannot be summarized is just a row.
            return null;
        }
    }

    private static long bound(long value, long low, long high) {
        return Math.max(low, Math.min(high, value));
    }
}
