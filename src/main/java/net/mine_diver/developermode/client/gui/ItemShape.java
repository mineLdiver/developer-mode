package net.mine_diver.developermode.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An item stack, as a stack is written: an id, a count and a damage value.
 *
 * <p>Three keys with three particular types is a strong enough test that a
 * compound matching by accident is not worth designing around.
 *
 * <p>The id and the damage are shown as one thing, because to anyone holding
 * it they are one thing. An identifier of wool and a damage of one is orange
 * wool, and picking orange wool sets both. The count is its own field, since
 * how many of something you have is not part of what it is.
 */
public final class ItemShape implements NbtShape {
    public static final ItemShape INSTANCE = new ItemShape();

    /** What a stack's id is written as once StationAPI has flattened it. */
    private static final String FLATTENED_ID = "stationapi:id";
    private static final String COUNT = "Count";
    private static final String DAMAGE = "Damage";

    /** An item is drawn at sixteen, and a slot is that plus its edges. */
    private static final int SLOT = 18;

    private static List<Choice> choices;

    private ItemShape() {}

    @Override
    public boolean matches(NbtCompound compound) {
        return NbtShape.get(compound, FLATTENED_ID) instanceof NbtString
                && NbtShape.get(compound, COUNT) instanceof NbtByte
                && NbtShape.get(compound, DAMAGE) instanceof NbtShort;
    }

    @Override
    public ItemStack iconFor(NbtCompound compound) {
        Item item = item(compound);
        if (item == null) return null;
        try {
            return new ItemStack(item, 1, compound.getShort(DAMAGE));
        } catch (Exception error) {
            return null;
        }
    }

    @Override
    public String summarize(NbtCompound compound) {
        String name = name(compound);
        return name == null ? null : compound.getByte(COUNT) + "x " + name;
    }

    @Override
    public int cardHeight() {
        return SLOT + 4;
    }

    @Override
    public void renderCard(Minecraft minecraft, NbtCompound compound,
                           int x, int y, int width, boolean hovered) {
        Draw.rect(x, y + 2, x + SLOT, y + 2 + SLOT, Theme.PANEL_SUNKEN);
        Draw.outline(x, y + 2, SLOT, SLOT, hovered ? Theme.BORDER_FOCUSED : Theme.BORDER);

        ItemStack icon = iconFor(compound);
        if (icon != null) ItemDraw.single(minecraft, icon, x + 1, y + 3);

        String name = name(compound);
        int textX = x + SLOT + 4;
        Draw.text(minecraft, Draw.ellipsize(minecraft, name == null ? "Unknown item" : name,
                        x + width - textX), textX, y + 4,
                hovered ? Theme.ACCENT : Theme.TEXT);
        Draw.text(minecraft, Draw.ellipsize(minecraft, "click to change", x + width - textX),
                textX, y + 14, Theme.TEXT_FAINT);
    }

    @Override
    public boolean hides(NbtCompound compound, String key) {
        // The row says which item this is, so the fields that spell that out
        // would only be saying it again.
        if (FLATTENED_ID.equals(key)) return true;
        if (!DAMAGE.equals(key)) return false;

        Item item = item(compound);
        // On a tool the damage is how worn it is, which the row does not say
        // and nothing else will.
        return item == null || item.getMaxDamage() <= 0;
    }

    @Override
    public List<Choice> choicesFor(NbtCompound compound, String key) {
        return key == null ? everyItem() : null;
    }

    @Override
    public long clamp(NbtCompound compound, String key, long value) {
        Item item = item(compound);

        return switch (key) {
            case COUNT -> bound(value, 1, item == null ? 64 : item.getMaxCount());
            case DAMAGE -> item != null && item.getMaxDamage() > 0
                    ? bound(value, 0, item.getMaxDamage())
                    : bound(value, 0, Short.MAX_VALUE);
            default -> value;
        };
    }

    /**
     * Every item there is, one entry per distinct thing rather than per id, so
     * that picking is picking the thing rather than the thing and then a
     * number.
     */
    private static synchronized List<Choice> everyItem() {
        if (choices != null) return choices;

        List<Choice> built = new ArrayList<>();
        for (ItemCatalogue.Variant variant : ItemCatalogue.all()) {
            if (variant.identifier() == null) continue;

            Map<String, String> writes = new LinkedHashMap<>();
            writes.put(FLATTENED_ID, variant.identifier().toString());
            writes.put(DAMAGE, String.valueOf(variant.damage()));

            built.add(new Choice(variant.name(), variant.stack(), writes));
        }

        choices = built;
        return choices;
    }

    /** What the stack is called, variant and all, or null if the id is not one. */
    private static String name(NbtCompound compound) {
        ItemCatalogue.Variant variant =
                ItemCatalogue.variant(compound.getString(FLATTENED_ID), compound.getShort(DAMAGE));
        return variant == null ? null : variant.name();
    }

    private static Item item(NbtCompound compound) {
        String id = compound.getString(FLATTENED_ID);
        return id == null || id.isEmpty() ? null : ItemCatalogue.item(id);
    }

    private static long bound(long value, long low, long high) {
        return Math.max(low, Math.min(high, value));
    }
}
