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
import java.util.Map;
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
        final int height;
        /** Set on a row that is a shape's own view rather than a field. */
        final NbtShape card;

        Row(String path, String key, NbtElement element, int depth, boolean container,
            NbtCompound owner, int height, NbtShape card) {
            this.path = path;
            this.key = key;
            this.element = element;
            this.depth = depth;
            this.container = container;
            this.owner = owner;
            this.height = height;
            this.card = card;
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

        int rowY = y;
        for (int index = scrollRow; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (rowY + row.height > y + height) break;

            boolean hovered = mouseX >= x && mouseX < x + width
                    && mouseY >= rowY && mouseY < rowY + row.height;

            int indent = x + row.depth * INDENT;
            // Where a row's own content starts, past the space a caret needs.
            // A view of a thing lines up with the fields beside it.
            int contentX = indent + CARET_SIZE + 3;

            if (row.card != null) {
                row.card.renderCard(minecraft, (NbtCompound) row.element,
                        contentX, rowY, x + width - contentX, hovered);
                rowY += row.height;
                continue;
            }

            if (row.container) {
                Draw.caret(indent + 1, rowY + 2, CARET_SIZE, expanded.contains(row.path), Theme.NBT_CONTAINER);
            }

            String label = labelOf(row);
            Draw.text(minecraft, label, contentX, rowY + 1, Theme.NBT_KEY);

            int valueX = contentX + Draw.textWidth(minecraft, label) + KEY_GAP;

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
            if (hovered) Draw.rect(x, rowY, x + width, rowY + row.height, Theme.HOVER);
            rowY += row.height;
        }

        renderScrollbar();
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (choices.mouseClicked(mouseX, mouseY, button)) return;
        if (editing != null && editor.contains(mouseX, mouseY)) return;
        commitEdit();

        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return;

        int index = rowAt(mouseY);
        if (index < 0) return;

        Row row = rows.get(index);
        if (row.card != null) {
            // The view is the thing itself, so choosing here chooses what it is.
            offerChoices((NbtCompound) row.element, null, index);
        } else if (row.container) {
            if (!expanded.remove(row.path)) expanded.add(row.path);
            rebuild();
        } else if (isEditable(row.element)) {
            if (offerChoices(row.owner, row.key, index)) return;

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
    /**
     * Opens what a compound, or one field of it, is allowed to be.
     *
     * @param key the field being picked for, or null for the compound itself
     * @return true if there was something to choose from
     */
    private boolean offerChoices(NbtCompound owner, String key, int index) {
        NbtShape shape = shapeOf(owner);
        if (shape == null) return false;

        List<NbtShape.Choice> options = shape.choicesFor(owner, key);
        if (options == null || options.isEmpty()) return false;

        choices.open(options, x, topOf(index) + rows.get(index).height, x, y, width, height,
                picked -> applyChoice(owner, picked));
        return true;
    }

    /**
     * Writes everything a choice stands for.
     *
     * <p>Through the same path as typing, so each value is parsed, bounded and
     * marked changed exactly as it would have been by hand. The rows are built
     * again afterwards because what a compound is can decide which of its
     * fields are worth showing.
     */
    private void applyChoice(NbtCompound owner, NbtShape.Choice choice) {
        for (Map.Entry<String, String> write : choice.writes().entrySet()) {
            NbtElement element = NbtShape.get(owner, write.getKey());
            if (element != null) store(owner, element, write.getKey(), write.getValue());
        }
        rebuild();
    }

    private void commitEdit() {
        if (editing == null) return;

        Row row = editing;
        String text = editor.text();
        editing = null;
        editor.setFocused(false);

        store(row.owner, row.element, row.key, text);
    }

    /**
     * Writes text into a tag, as the tag's own type and within whatever bounds
     * the compound it sits in asks for.
     */
    private void store(NbtCompound owner, NbtElement element, String key, String text) {
        try {
            if (element instanceof NbtString value) {
                value.value = text;
            } else if (element instanceof NbtFloat value) {
                value.value = Float.parseFloat(text.trim());
            } else if (element instanceof NbtDouble value) {
                value.value = Double.parseDouble(text.trim());
            } else if (!storeWhole(owner, element, key, Long.parseLong(text.trim()))) {
                error = "\"" + text + "\" does not fit in a " + typeName(element);
                return;
            }
            error = "";
            dirty = true;
            revision++;
        } catch (NumberFormatException failure) {
            error = "\"" + text + "\" is not a " + typeName(element);
        }
    }

    /**
     * Stores a whole number, after whatever recognized the compound has had a
     * say about what the field will accept.
     *
     * @return false if it will not fit the tag's own type, which no shape can
     *         excuse
     */
    private boolean storeWhole(NbtCompound owner, NbtElement element, String key, long parsed) {
        NbtShape shape = shapeOf(owner);
        long value = shape == null ? parsed : shape.clamp(owner, key, parsed);

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
                if (hidden(compound, child.getKey())) continue;
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
        rows.add(new Row(path, key, element, depth, container, owner, ROW_HEIGHT, null));
        if (!container || !expanded.contains(path)) return;

        // Opening something recognized opens onto it rather than straight into
        // its fields.
        if (element instanceof NbtCompound compound) {
            NbtShape shape = shapeOf(compound);
            if (shape != null && shape.cardHeight() > 0) {
                rows.add(new Row(path + "/view", "", compound, depth + 1, false,
                        compound, shape.cardHeight(), shape));
            }
        }
        appendChildren(element, path, depth + 1);
    }

    /** Screen y of a row, accumulated because rows are not all one height. */
    private int topOf(int index) {
        int top = y;
        for (int i = scrollRow; i < index && i < rows.size(); i++) top += rows.get(i).height;
        return top;
    }

    /** The row under a point, or -1 for none. */
    private int rowAt(int pointY) {
        int top = y;
        for (int i = scrollRow; i < rows.size() && top < y + height; i++) {
            int next = top + rows.get(i).height;
            if (pointY >= top && pointY < next) return i;
            top = next;
        }
        return -1;
    }

    /** Whether the row above already says this, so showing it would repeat. */
    private boolean hidden(NbtCompound owner, String key) {
        NbtShape shape = shapeOf(owner);
        return shape != null && shape.hides(owner, key);
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
        int used = 0;
        int count = 0;
        for (int i = scrollRow; i < rows.size(); i++) {
            used += rows.get(i).height;
            if (used > height) break;
            count++;
        }
        return Math.max(1, count);
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
