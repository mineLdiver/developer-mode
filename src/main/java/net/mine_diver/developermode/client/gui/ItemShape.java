package net.mine_diver.developermode.client.gui;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;
import net.modificationstation.stationapi.api.registry.ItemRegistry;
import net.modificationstation.stationapi.api.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * How far to look for variants. Beta packs a variant into four bits of
     * metadata, so past this an item is answering about something else.
     */
    private static final int VARIANT_PROBE_LIMIT = 16;

    /** Every item there is, built once. A registry does not change after start. */
    private static List<Choice> choices;
    /** Variants per item, found by asking and kept because asking is not free. */
    private static final Map<Item, List<Choice>> variants = new HashMap<>();

    private ItemShape() {}

    @Override
    public ItemStack iconFor(NbtCompound compound) {
        // The damage picks the sprite for anything with variants, which is the
        // difference between wool and orange wool.
        return iconOf(item(compound), compound.getShort(DAMAGE));
    }

    private static ItemStack iconOf(Item item, int damage) {
        if (item == null) return null;
        try {
            return new ItemStack(item, 1, damage);
        } catch (Exception error) {
            return null;
        }
    }

    @Override
    public String labelFor(NbtCompound compound, String key) {
        if (FLATTENED_ID.equals(key) || LEGACY_ID.equals(key)) return "Item";

        // Damage is a durability on a tool and a variant on everything else,
        // and calling both of them damage is how a white wool ends up looking
        // broken.
        if (DAMAGE.equals(key)) {
            Item item = item(compound);
            return item != null && item.getMaxDamage() > 0 ? "Damage" : "Variant";
        }
        return null;
    }

    @Override
    public String displayFor(NbtCompound compound, String key, NbtElement element) {
        Item item = item(compound);
        if (item == null) return null;

        // Each field says what it holds. The identifier is the item, which for
        // wool is wool; the damage is which wool, which is the orange part.
        if (FLATTENED_ID.equals(key) || LEGACY_ID.equals(key)) return baseName(item);
        if (DAMAGE.equals(key) && item.hasSubtypes()) return name(item, compound.getShort(DAMAGE));
        return null;
    }

    @Override
    public List<Choice> choicesFor(NbtCompound compound, String key) {
        if (FLATTENED_ID.equals(key)) return everyItem();
        // Variants live in the damage value, so that is the field to pick one
        // from. On anything else the damage is a durability, which is a number
        // and not a list of anything.
        if (DAMAGE.equals(key)) return variantsOf(item(compound));
        return null;
    }

    /**
     * The distinct things an item turns into as its damage value changes.
     *
     * <p>Found by asking rather than by knowing: an item is handed each damage
     * value in turn and asked what it would be called and what it would look
     * like, and an answer nothing has given before is another variant. Mods do
     * not announce their variants, but they do answer these.
     *
     * @return null when damage on this item is a durability rather than a
     *         variant, which leaves the field a number to type into
     */
    private static synchronized List<Choice> variantsOf(Item item) {
        if (item == null || !item.hasSubtypes()) return null;

        List<Choice> known = variants.get(item);
        if (known != null) return known;

        List<Choice> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int damage = 0; damage < VARIANT_PROBE_LIMIT; damage++) {
            String answer = describes(item, damage);
            if (answer == null || !seen.add(answer)) continue;

            String label = name(item, damage);
            found.add(new Choice(String.valueOf(damage),
                    label == null ? "Variant " + damage : label,
                    iconOf(item, damage)));
        }

        variants.put(item, found);
        return found;
    }

    /**
     * How an item answers for one damage value, as a name and a sprite
     * together. Either alone misses variants that differ only in the other.
     */
    private static String describes(Item item, int damage) {
        try {
            ItemStack stack = new ItemStack(item, 1, damage);
            String key = stack.getTranslationKey();
            return key == null ? null : key + "@" + stack.getTextureId();
        } catch (Exception error) {
            // An item that will not answer for a damage value has no variant
            // there, which is the answer.
            return null;
        }
    }

    private static synchronized List<Choice> everyItem() {
        if (choices != null) return choices;

        List<Choice> built = new ArrayList<>();
        for (Item item : ItemRegistry.INSTANCE) {
            if (item == null) continue;

            Identifier identifier = ItemRegistry.INSTANCE.getId(item);
            if (identifier == null) continue;

            String label = baseName(item);
            built.add(new Choice(identifier.toString(),
                    label == null ? identifier.path : label,
                    iconOf(item, 0)));
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

    /**
     * What the item is called with no variant applied.
     *
     * <p>Asked of the item rather than of a stack, so that wool is wool rather
     * than whichever wool a damage value of zero happens to be.
     */
    private static String baseName(Item item) {
        try {
            String key = item.getTranslationKey();
            if (key != null) {
                String translated = I18n.getTranslation(key + ".name");
                if (translated != null && !translated.isEmpty() && !translated.equals(key + ".name")) {
                    return translated;
                }
            }
        } catch (Exception ignored) {
            // Fall through to the identifier, which is always there.
        }

        // An item whose variants are all named but whose base is not, like a
        // slab or a dye, has only its own id left to be called by.
        Identifier identifier = ItemRegistry.INSTANCE.getId(item);
        return identifier == null ? null : readable(identifier.path);
    }

    /** An id turned into something that reads like a name. */
    private static String readable(String path) {
        String spaced = path.replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");

        StringBuilder out = new StringBuilder(spaced.length());
        for (String word : spaced.split(" ")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.length() == 0 ? path : out.toString();
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
