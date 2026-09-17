package net.mine_diver.developermode.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtString;
import net.modificationstation.stationapi.api.nbt.NbtIntArray;
import net.modificationstation.stationapi.api.nbt.NbtLongArray;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A scrollable, editable view of an NBT compound.
 *
 * <p>Edits mutate the element in place. Every scalar tag in Beta exposes a
 * public {@code value} field, so there is no need to rebuild the compound or
 * the list that holds it, and no need for the list setter that Beta's NbtList
 * does not have.
 *
 * <p>Structure is fixed: values can be changed, but tags cannot be added,
 * removed or retyped.
 *
 * <p>Arrays are shown but not edited: byte arrays from Beta, and int and long
 * arrays from StationAPI, which registers those as tag ids 11 and 12 so they
 * appear in anything it has loaded.
 */
public final class NbtTree {
    public static final int ROW_HEIGHT = 10;

    private static final int INDENT = 8;
    /** An item is drawn at sixteen, and a row is not that tall. */
    private static final int ICON_SIZE = ROW_HEIGHT - 1;
    private static final int CARET_SIZE = 5;
    private static final int KEY_GAP = 6;

    /** One visible line. Rebuilt whenever something is expanded or collapsed. */
    public static final class Row {
        final String path;
        final String key;
        final NbtElement element;
        final int depth;
        final boolean container;
        /** The compound this element sits in, so a shape can speak for it. */
        final NbtCompound owner;

        Row(String path, String key, NbtElement element, int depth, boolean container, NbtCompound owner) {
            this.path = path;
            this.key = key;
            this.element = element;
            this.depth = depth;
            this.container = container;
            this.owner = owner;
        }
    }

    private final ChoiceList choices = new ChoiceList();
    private final Set<String> expanded = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private final TextField editor = new TextField(64);

    private static final NbtShape[] SHAPES = { ItemShape.INSTANCE };

    private NbtCompound root;
    private boolean raw;
    private Row editing;
    private String error = "";
    private boolean dirty;
    private int revision;
    private int scrollRow;

    private int x;
    private int y;
    private int width;
    private int height;

    public void bounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setRoot(NbtCompound root) {
        this.root = root;
        this.editing = null;
        this.error = "";
        this.dirty = false;
        this.revision++;
        rebuild();
    }

    /**
     * Whether to show compounds as themselves rather than as whatever they
     * were recognized as.
     */
    public void setRaw(boolean raw) {
        this.raw = raw;
        // Rows are ordered by what they read as, which raw changes.
        rebuild();
    }

    public boolean isRaw() {
        return raw;
    }

    /** True once a value has been changed and not yet reloaded away. */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Changes with every committed edit, and with every new root.
     *
     * <p>{@link #isDirty} says whether anything has been touched; this says
     * whether anything has been touched <em>since you last looked</em>, which
     * is what a view that has to rebuild itself off the tree actually needs.
     */
    public int revision() {
        return revision;
    }

    public String error() {
        return error;
    }

    public boolean isEditing() {
        return editing != null;
    }

    public boolean cancelEditing() {
        if (choices.isOpen()) {
            choices.close();
            return true;
        }
        if (editing == null) return false;
        editing = null;
        editor.setFocused(false);
        error = "";
        return true;
    }

    public void tick() {
        editor.tick();
        choices.tick();
    }

    /** Whether a field is being picked from a list rather than typed into. */
    public boolean isChoosing() {
        return choices.isOpen();
    }

    /**
     * Drawn after the window, so a list longer than the row it belongs to is
     * not cut off by the panel it opened in.
     */
    public void renderOverlay(Minecraft minecraft, int mouseX, int mouseY) {
        choices.render(minecraft, mouseX, mouseY);
    }

    public void render(Minecraft minecraft, int mouseX, int mouseY) {
        clampScroll();

        int visible = visibleRows();
        for (int i = 0; i < visible; i++) {
            int index = scrollRow + i;
            if (index >= rows.size()) break;

            Row row = rows.get(index);
            int rowY = y + i * ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            int indent = x + row.depth * INDENT;
            if (row.container) {
                Draw.caret(indent + 1, rowY + 2, CARET_SIZE, expanded.contains(row.path), Theme.NBT_CONTAINER);
            }

            int keyX = indent + CARET_SIZE + 3;
            String label = labelOf(row);
            Draw.text(minecraft, label, keyX, rowY + 1, Theme.NBT_KEY);

            int valueX = keyX + Draw.textWidth(minecraft, label) + KEY_GAP;

            ItemStack icon = iconOf(row);
            if (icon != null) {
                ItemDraw.scaled(minecraft, icon, valueX, rowY, ICON_SIZE);
                valueX += ICON_SIZE + 2;
            }

            if (row == editing) {
                editor.bounds(valueX, rowY - 1, Math.max(30, x + width - valueX - 2));
                editor.render(minecraft, "");
            } else {
                Draw.text(minecraft, Draw.ellipsize(minecraft, displayOf(row), x + width - valueX - 2),
                        valueX, rowY + 1, colorOf(row.element));
            }

            // Over the row's own contents. A wash laid down first is painted
            // over by whatever follows it, which leaves the hovered row as the
            // one row whose icon is not tinted.
            if (hovered) Draw.rect(x, rowY, x + width, rowY + ROW_HEIGHT, Theme.HOVER);
        }

        renderScrollbar();
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (choices.mouseClicked(mouseX, mouseY, button)) return;
        if (editing != null && editor.contains(mouseX, mouseY)) return;
        commitEdit();

        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return;

        int index = scrollRow + (mouseY - y) / ROW_HEIGHT;
        if (index < 0 || index >= rows.size()) return;

        Row row = rows.get(index);
        if (row.container) {
            if (!expanded.remove(row.path)) expanded.add(row.path);
            rebuild();
        } else if (isEditable(row.element)) {
            if (offerChoices(row, index)) return;

            editing = row;
            editor.setText(rawValue(row.element));
            editor.setFocused(true);
            error = "";
        } else {
            error = typeName(row.element) + " is not editable here";
        }
    }

    public void mouseScrolled(int direction) {
        if (choices.mouseScrolled(direction)) return;
        scrollRow -= direction * 3;
        clampScroll();
    }

    public void keyPressed(char character, int keyCode) {
        if (choices.keyPressed(character, keyCode)) return;
        if (editing == null) return;

        if (keyCode == Keyboard.KEY_RETURN) {
            commitEdit();
            return;
        }
        editor.keyPressed(character, keyCode);
    }

    /**
     * Opens the list of what a field is allowed to hold, if it has one.
     *
     * @return true if the field is picked from rather than typed into
     */
    private boolean offerChoices(Row row, int index) {
        NbtShape shape = shapeOf(row.owner);
        if (shape == null) return false;

        List<NbtShape.Choice> options = shape.choicesFor(row.owner, row.key);
        if (options == null || options.isEmpty()) return false;

        int rowY = y + (index - scrollRow) * ROW_HEIGHT;
        choices.open(options, x, rowY + ROW_HEIGHT, x, y, width, height, picked -> {
            // Through the same path as typing, so the value is parsed, bounded
            // and marked changed exactly as it would have been by hand.
            editing = row;
            editor.setText(picked);
            commitEdit();
        });
        return true;
    }

    private void commitEdit() {
        if (editing == null) return;

        Row row = editing;
        String text = editor.text();
        editing = null;
        editor.setFocused(false);

        try {
            NbtElement element = row.element;
            if (element instanceof NbtString value) {
                value.value = text;
            } else if (element instanceof NbtFloat value) {
                value.value = Float.parseFloat(text.trim());
            } else if (element instanceof NbtDouble value) {
                value.value = Double.parseDouble(text.trim());
            } else if (!commitWhole(row, Long.parseLong(text.trim()))) {
                error = "\"" + text + "\" does not fit in a " + typeName(row.element);
                return;
            }
            error = "";
            dirty = true;
            revision++;
        } catch (NumberFormatException failure) {
            error = "\"" + text + "\" is not a " + typeName(row.element);
        }
    }

    /**
     * Stores a whole number, after whatever recognized the compound has had a
     * say about what the field will accept.
     *
     * @return false if it will not fit the tag's own type, which no shape can
     *         excuse
     */
    private boolean commitWhole(Row row, long parsed) {
        NbtShape shape = shapeOf(row.owner);
        long value = shape == null ? parsed : shape.clamp(row.owner, row.key, parsed);

        NbtElement element = row.element;
        if (element instanceof NbtByte typed) {
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) return false;
            typed.value = (byte) value;
        } else if (element instanceof NbtShort typed) {
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) return false;
            typed.value = (short) value;
        } else if (element instanceof NbtInt typed) {
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) return false;
            typed.value = (int) value;
        } else if (element instanceof NbtLong typed) {
            typed.value = value;
        }
        return true;
    }

    private void rebuild() {
        rows.clear();
        if (root == null) return;
        appendChildren(root, "", 0);
        clampScroll();
    }

    private void appendChildren(NbtElement parent, String path, int depth) {
        if (parent instanceof NbtCompound compound) {
            List<NbtElement> children = new ArrayList<>();
            for (Object child : compound.values()) children.add((NbtElement) child);
            // HashMap order is arbitrary, and a tree that reshuffles itself is
            // unusable, so impose one. By what the rows read as rather than
            // what they are keyed by, or a renamed field sorts somewhere its
            // name does not explain.
            children.sort(Comparator.comparing(child -> sortKeyOf(compound, child)));

            for (NbtElement child : children) {
                append(child, path + "/" + child.getKey(), child.getKey(), depth, compound);
            }
        } else if (parent instanceof NbtList list) {
            for (int i = 0; i < list.size(); i++) {
                append(list.get(i), path + "/" + i, "[" + i + "]", depth, null);
            }
        }
    }

    private void append(NbtElement element, String path, String key, int depth, NbtCompound owner) {
        boolean container = element instanceof NbtCompound || element instanceof NbtList;
        rows.add(new Row(path, key, element, depth, container, owner));
        if (container && expanded.contains(path)) appendChildren(element, path, depth + 1);
    }

    private String sortKeyOf(NbtCompound owner, NbtElement child) {
        NbtShape shape = shapeOf(owner);
        String label = shape == null ? null : shape.labelFor(owner, child.getKey());
        return label == null ? child.getKey() : label;
    }

    /** The shape a compound was recognized as, or null. */
    private NbtShape shapeOf(NbtCompound compound) {
        if (raw || compound == null) return null;
        for (NbtShape shape : SHAPES) {
            if (shape.matches(compound)) return shape;
        }
        return null;
    }

    private void renderScrollbar() {
        int visible = visibleRows();
        if (rows.size() <= visible) return;

        int trackX = x + width - 3;
        Draw.rect(trackX, y, trackX + 3, y + height, Theme.PANEL_SUNKEN);

        int thumbHeight = Math.max(8, height * visible / rows.size());
        int thumbTop = y + (height - thumbHeight) * scrollRow / (rows.size() - visible);
        Draw.rect(trackX, thumbTop, trackX + 3, thumbTop + thumbHeight, Theme.BORDER_FOCUSED);
    }

    private int visibleRows() {
        return Math.max(1, height / ROW_HEIGHT);
    }

    private void clampScroll() {
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, rows.size() - visibleRows())));
    }

    private static boolean isEditable(NbtElement element) {
        return element instanceof NbtByte || element instanceof NbtShort || element instanceof NbtInt
                || element instanceof NbtLong || element instanceof NbtFloat || element instanceof NbtDouble
                || element instanceof NbtString;
    }

    /** What goes in the editor: the value with no type suffix or quotes. */
    private static String rawValue(NbtElement element) {
        if (element instanceof NbtByte value) return String.valueOf(value.value);
        if (element instanceof NbtShort value) return String.valueOf(value.value);
        if (element instanceof NbtInt value) return String.valueOf(value.value);
        if (element instanceof NbtLong value) return String.valueOf(value.value);
        if (element instanceof NbtFloat value) return String.valueOf(value.value);
        if (element instanceof NbtDouble value) return String.valueOf(value.value);
        if (element instanceof NbtString value) return value.value;
        return "";
    }

    /**
     * The icon for a row that stands for a whole recognized compound.
     *
     * <p>Only on the compound's own row. The fields inside it are already
     * underneath one, and a second copy a line later says nothing new.
     */
    private ItemStack iconOf(Row row) {
        if (raw || !(row.element instanceof NbtCompound compound)) return null;

        NbtShape shape = shapeOf(compound);
        return shape == null ? null : shape.iconFor(compound);
    }

    /** What a field is called here, which is not always what it is keyed by. */
    private String labelOf(Row row) {
        NbtShape shape = shapeOf(row.owner);
        String label = shape == null ? null : shape.labelFor(row.owner, row.key);
        return label == null ? row.key : label;
    }

    /** What a field reads as here, which is not always what it stores. */
    private String displayOf(Row row) {
        NbtShape shape = shapeOf(row.owner);
        String display = shape == null ? null : shape.displayFor(row.owner, row.key, row.element);
        return display == null ? describe(row.element) : display;
    }

    /**
     * What goes on screen.
     *
     * <p>Raw suffixes numbers and quotes strings the way SNBT writes them, so
     * a byte reads as a byte. Otherwise the type is noise between a person and
     * the value, and it is still there in the error when something will not fit.
     */
    private String describe(NbtElement element) {
        if (element instanceof NbtByte value) return raw ? value.value + "b" : String.valueOf(value.value);
        if (element instanceof NbtShort value) return raw ? value.value + "s" : String.valueOf(value.value);
        if (element instanceof NbtInt value) return String.valueOf(value.value);
        if (element instanceof NbtLong value) return raw ? value.value + "L" : String.valueOf(value.value);
        if (element instanceof NbtFloat value) return raw ? value.value + "f" : String.valueOf(value.value);
        if (element instanceof NbtDouble value) return raw ? value.value + "d" : String.valueOf(value.value);
        if (element instanceof NbtString value) return raw ? "\"" + value.value + "\"" : value.value;
        if (element instanceof NbtByteArray value) return "[" + value.value.length + " bytes]";
        if (element instanceof NbtIntArray value) return "[" + value.data.length + " ints]";
        if (element instanceof NbtLongArray value) return "[" + value.data.length + " longs]";
        if (element instanceof NbtCompound value) {
            NbtShape shape = shapeOf(value);
            String summary = shape == null ? null : shape.summarize(value);
            return summary == null ? "{" + value.values().size() + "}" : summary;
        }
        if (element instanceof NbtList value) return "[" + value.size() + "]";
        return "?";
    }

    private static int colorOf(NbtElement element) {
        if (element instanceof NbtString) return Theme.NBT_STRING;
        if (element instanceof NbtCompound || element instanceof NbtList) return Theme.NBT_CONTAINER;
        if (element instanceof NbtByteArray || element instanceof NbtIntArray
                || element instanceof NbtLongArray) return Theme.NBT_OPAQUE;
        return Theme.NBT_NUMBER;
    }

    private static String typeName(NbtElement element) {
        if (element instanceof NbtByte) return "byte";
        if (element instanceof NbtShort) return "short";
        if (element instanceof NbtInt) return "int";
        if (element instanceof NbtLong) return "long";
        if (element instanceof NbtFloat) return "float";
        if (element instanceof NbtDouble) return "double";
        if (element instanceof NbtString) return "string";
        if (element instanceof NbtByteArray) return "byte array";
        if (element instanceof NbtIntArray) return "int array";
        if (element instanceof NbtLongArray) return "long array";
        if (element instanceof NbtCompound) return "compound";
        if (element instanceof NbtList) return "list";
        return "tag";
    }
}
