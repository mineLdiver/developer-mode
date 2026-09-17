package net.mine_diver.developermode.client.gui;

import net.minecraft.block.Block;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.modificationstation.stationapi.api.registry.ItemRegistry;
import net.modificationstation.stationapi.api.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every item as it can actually turn up, one entry per distinct thing.
 *
 * <p>An item with subtypes is several things wearing one id, and a flat list of
 * all of them is what anyone picking an item is looking for: nobody wants wool
 * and then a number, they want orange wool.
 *
 * <p>Variants are found by asking rather than by knowing. An item is handed
 * each damage value in turn and asked what it would be called and what it would
 * look like, and an answer nothing has given before is another variant. Mods do
 * not announce their variants, but they answer these.
 */
public final class ItemCatalogue {
    /**
     * How far to look for variants. Beta packs one into four bits of metadata,
     * so past this an item is answering about something else.
     */
    private static final int PROBE_LIMIT = 16;

    private static List<Variant> all;
    /** Looked up per row per frame, so looked up rather than searched for. */
    private static final Map<String, Item> BY_ID = new HashMap<>();
    private static final Map<String, Variant> BY_ID_AND_DAMAGE = new HashMap<>();

    private ItemCatalogue() {}

    /**
     * One thing that can be in a slot.
     *
     * @param damage the value that selects it, which is a variant on an item
     *               with subtypes and zero on everything else
     */
    public record Variant(Item item, int damage, String name, Identifier identifier, ItemStack stack) {
        /** The id as a person would write it down, variant and all. */
        public String displayId() {
            String base = identifier == null ? "item " + item.id : identifier.toString();
            return damage == 0 ? base : base + " #" + damage;
        }
    }

    /** Built once. A registry does not change after the game has started. */
    public static synchronized List<Variant> all() {
        if (all != null) return all;

        List<Variant> built = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Item item : ItemRegistry.INSTANCE) {
            if (item == null) continue;
            // The vanilla item renderer reads Block.BLOCKS[id] for anything
            // below 256 without a null check, so skip ids with no block.
            if (item.id < Block.BLOCKS.length && Block.BLOCKS[item.id] == null) continue;

            int limit = item.hasSubtypes() ? PROBE_LIMIT : 1;
            for (int damage = 0; damage < limit; damage++) offer(built, seen, item, damage);
        }

        all = Collections.unmodifiableList(built);
        return all;
    }

    /** The item an identifier names, or null. */
    public static Item item(String identifier) {
        all();
        return BY_ID.get(identifier);
    }

    /**
     * The variant an identifier and a damage value name.
     *
     * @return the item's first variant when that damage is not one of its own,
     *         which is what a worn tool is, or null if the identifier is not an
     *         item at all
     */
    public static Variant variant(String identifier, int damage) {
        all();
        Variant exact = BY_ID_AND_DAMAGE.get(identifier + "#" + damage);
        return exact == null ? BY_ID_AND_DAMAGE.get(identifier + "#0") : exact;
    }

    private static void offer(List<Variant> into, Set<String> seen, Item item, int damage) {
        ItemStack stack = new ItemStack(item, 1, damage);

        String translationKey;
        int texture;
        try {
            translationKey = stack.getTranslationKey();
            texture = stack.getTextureId();
        } catch (Exception error) {
            // An item that will not answer for a damage value has nothing there.
            return;
        }
        if (translationKey == null) return;

        // Two variants can share a name and differ by sprite, or the reverse,
        // and either test alone quietly loses one of them.
        if (!seen.add(translationKey + "@" + texture)) return;

        Identifier identifier = ItemRegistry.INSTANCE.getId(item);
        String name = I18n.getTranslation(translationKey + ".name");
        if (name == null || name.isEmpty() || name.equals(translationKey + ".name")) {
            name = identifier == null ? translationKey : identifier.path;
        }

        Variant variant = new Variant(item, damage, name, identifier, stack);
        into.add(variant);

        if (identifier != null) {
            BY_ID.putIfAbsent(identifier.toString(), item);
            BY_ID_AND_DAMAGE.putIfAbsent(identifier + "#" + damage, variant);
        }
    }
}
