package net.mine_diver.developermode.client.gui;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtShort;

/**
 * An item stack, as Beta writes one: an id, a count and a damage value.
 *
 * <p>Three keys with three particular types is a strong enough test that a
 * compound matching by accident is not worth worrying about, and the raw
 * switch is there for when one does.
 *
 * <p>What this buys is mostly the id. A short reading 264 says nothing about
 * what it is, and the whole point of looking at an item's NBT is usually to
 * find out.
 */
public final class ItemShape implements NbtShape {
    public static final ItemShape INSTANCE = new ItemShape();

    private static final String ID = "id";
    private static final String COUNT = "Count";
    private static final String DAMAGE = "Damage";

    private ItemShape() {}

    @Override
    public boolean matches(NbtCompound compound) {
        return NbtShape.get(compound, ID) instanceof NbtShort
                && NbtShape.get(compound, COUNT) instanceof NbtByte
                && NbtShape.get(compound, DAMAGE) instanceof NbtShort;
    }

    @Override
    public String summarize(NbtCompound compound) {
        int id = compound.getShort(ID);
        int count = compound.getByte(COUNT);
        int damage = compound.getShort(DAMAGE);

        String name = name(id, damage);
        return name == null ? null : count + "x " + name;
    }

    @Override
    public long clamp(NbtCompound compound, String key, long value) {
        Item item = item(compound.getShort(ID));

        return switch (key) {
            case ID -> bound(value, 0, Item.ITEMS.length - 1);
            case COUNT -> bound(value, 1, item == null ? 64 : item.getMaxCount());
            // A damage value is a durability on a tool and a variant on
            // everything else, so only the tools have a ceiling worth applying.
            case DAMAGE -> item != null && item.getMaxDamage() > 0
                    ? bound(value, 0, item.getMaxDamage())
                    : bound(value, 0, Short.MAX_VALUE);
            default -> value;
        };
    }

    /** What the item is called, or null when the id is not one. */
    private static String name(int id, int damage) {
        Item item = item(id);
        if (item == null) return null;

        try {
            String key = new ItemStack(id, 1, damage).getTranslationKey();
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

    private static Item item(int id) {
        return id < 0 || id >= Item.ITEMS.length ? null : Item.ITEMS[id];
    }

    private static long bound(long value, long low, long high) {
        return Math.max(low, Math.min(high, value));
    }
}
