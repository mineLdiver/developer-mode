package net.mine_diver.developermode.client.gui.window;

import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.api.setting.LockableSetting;
import net.mine_diver.developermode.api.setting.Setting;
import net.mine_diver.developermode.api.setting.SettingRegistry;
import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.Lang;
import net.mine_diver.developermode.client.gui.Button;
import net.mine_diver.developermode.client.gui.ChoiceList;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.ItemCatalogue;
import net.mine_diver.developermode.client.gui.ItemDraw;
import net.mine_diver.developermode.client.gui.NbtShape;
import net.mine_diver.developermode.client.gui.TextField;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.composer.DevWindow;
import net.mine_diver.developermode.client.preset.Preset;
import net.mine_diver.developermode.client.preset.Presets;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.modificationstation.stationapi.api.util.StringIdentifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Where presets are made, changed, put in order and thrown away.
 *
 * <p>The ring only ever chooses them. Making one needs a name typed, and a
 * choice for every setting there is, which is a form rather than a flick, so
 * it lives here.
 *
 * <p>Every registered setting has a row: a button that steps through its
 * values, backwards on a right click, and a lock beside it for one that can be
 * locked. Left out is one of the steps, and is the one that matters most: it
 * is what lets Creative leave the weather alone. Capture fills the form from
 * where everything is now, so a preset can be made by setting things up in
 * the world first and then taking out what does not belong.
 *
 * <p>Edits are made to a copy and kept by Save. Choosing another preset in the
 * list drops them, the same as summon presets, since a half made preset that
 * saved itself would appear in the ring before it was finished.
 */
public final class PresetWindow extends DevWindow {
    private static final int LIST_WIDTH = 100;
    private static final int ROW_HEIGHT = 12;
    private static final int ICON_SIZE = ROW_HEIGHT - 3;
    private static final int GAP = 4;
    private static final int FIELD_ROW = 14;
    private static final int ICON_BOX = 18;
    private static final int LABEL_WIDTH = 62;
    private static final int VALUE_WIDTH = 48;
    private static final int LOCK_WIDTH = 38;
    private static final int NEW_WIDTH = 32;
    private static final int DESCRIPTION_LINES = 2;
    private static final int LINE_HEIGHT = 10;
    private static final int STATUS_DURATION_TICKS = 80;

    /** One setting's row of the form. */
    private record Row(Setting<?> setting, Button value, Button lock) {}

    private final TextField nameField = new TextField(24);
    private final ChoiceList icons = new ChoiceList();

    private final Button newButton = new Button("gui.developermode.presets.new");
    private final Button earlierButton = new Button("gui.developermode.presets.earlier");
    private final Button laterButton = new Button("gui.developermode.presets.later");
    private final Button captureButton = new Button("gui.developermode.presets.capture");
    private final Button saveButton = new Button("gui.developermode.presets.save");

    /** In the order settings were registered, which is the order they are described in. */
    private final List<Row> rows = new ArrayList<>();

    /** The preset being edited, as an index, which is one past the last for one not saved yet. */
    private int selected;
    private Preset working;
    private int listScroll;
    private int rowScroll;

    private Message status;
    private boolean statusIsError;
    private int statusTicks;

    public PresetWindow() {
        super("gui.developermode.presets.title", 264, 196);
        for (Setting<?> setting : SettingRegistry.INSTANCE) {
            rows.add(new Row(setting,
                    new Button("gui.developermode.presets.left_out"),
                    setting instanceof LockableSetting ? new Button("gui.developermode.presets.lock") : null));
        }
        select(0);
    }

    public static void open() {
        ComposerScreen.reveal(PresetWindow.class, PresetWindow::new);
    }

    @Override
    public void tick() {
        nameField.tick();
        icons.tick();
        if (statusTicks > 0 && --statusTicks == 0) status = null;
    }

    @Override
    protected void renderContent(Minecraft minecraft, int mouseX, int mouseY, float delta, boolean focused) {
        // Hovering is for the list that is up, not for what it is covering.
        int pointerX = icons.isOpen() ? POINTER_AWAY : mouseX;
        int pointerY = icons.isOpen() ? POINTER_AWAY : mouseY;

        renderList(minecraft, pointerX, pointerY);
        renderEditor(minecraft, pointerX, pointerY);
        renderFooter(minecraft, pointerX, pointerY);
    }

    @Override
    public void renderOverlay(Minecraft minecraft, int mouseX, int mouseY) {
        icons.render(minecraft, mouseX, mouseY);
    }

    private void renderList(Minecraft minecraft, int mouseX, int mouseY) {
        List<Preset> presets = Presets.all();
        int listX = contentX();
        int listY = contentY();
        int visible = visibleListRows();
        Draw.rect(listX, listY, listX + LIST_WIDTH, listY + visible * ROW_HEIGHT, Theme.PANEL_SUNKEN);

        // One row past the last for a preset that is being made, so there is
        // something in the list that says where it will go.
        int count = presets.size() + (selected == presets.size() ? 1 : 0);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, count - visible)));

        for (int row = 0; row < visible; row++) {
            int index = listScroll + row;
            if (index >= count) break;

            int rowY = listY + row * ROW_HEIGHT;
            boolean isNew = index == presets.size();
            boolean hovered = !isNew && mouseX >= listX && mouseX < listX + LIST_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (index == selected) Draw.rect(listX, rowY, listX + LIST_WIDTH, rowY + ROW_HEIGHT, Theme.PANEL_LIT);
            if (hovered) Draw.rect(listX, rowY, listX + LIST_WIDTH, rowY + ROW_HEIGHT, Theme.HOVER);

            Preset preset = isNew ? working : presets.get(index);
            ItemDraw.scaled(minecraft, iconOf(preset), listX + 2, rowY + 1, ICON_SIZE);

            String name = isNew ? Lang.get("gui.developermode.presets.unsaved") : preset.name();
            int ink = isNew ? Theme.TEXT_FAINT : index == selected || hovered ? Theme.ACCENT : Theme.TEXT;
            int textX = listX + 2 + ICON_SIZE + 3;
            Draw.text(minecraft, Draw.ellipsize(minecraft, name, listX + LIST_WIDTH - textX - 2), textX, rowY + 2, ink);
        }
    }

    private void renderEditor(Minecraft minecraft, int mouseX, int mouseY) {
        int x = editorX();
        int width = editorWidth();
        int y = contentY();

        boolean iconHovered = overIcon(mouseX, mouseY);
        Draw.rect(x, y, x + ICON_BOX, y + ICON_BOX, iconHovered ? Theme.PANEL_RAISED : Theme.PANEL_SUNKEN);
        Draw.outline(x, y, ICON_BOX, ICON_BOX, iconHovered || icons.isOpen() ? Theme.BORDER_FOCUSED : Theme.BORDER);
        ItemDraw.scaled(minecraft, iconOf(working), x + 1, y + 1, ItemDraw.SIZE);

        nameField.bounds(x + ICON_BOX + GAP, y + 2, width - ICON_BOX - GAP);
        nameField.render(minecraft, Lang.get("gui.developermode.presets.name"));

        int visible = visibleFieldRows();
        rowScroll = Math.max(0, Math.min(rowScroll, Math.max(0, rows.size() - visible)));
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int line = i - rowScroll;
            if (line < 0 || line >= visible) {
                // Out of view, and so out of reach of the pointer as well.
                row.value.bounds(POINTER_AWAY, POINTER_AWAY, 0);
                if (row.lock != null) row.lock.bounds(POINTER_AWAY, POINTER_AWAY, 0);
                continue;
            }
            renderRow(minecraft, row, x, fieldsTop() + line * FIELD_ROW, mouseX, mouseY);
        }
        if (rows.size() > visible) {
            Draw.text(minecraft, Lang.get("gui.developermode.presets.more", rowScroll + 1, rowScroll + visible, rows.size()),
                    x, fieldsTop() + visible * FIELD_ROW + 2, Theme.TEXT_FAINT);
        }

        // What it will do, in the words the ring will use for it.
        int descriptionY = descriptionTop();
        for (String line : Draw.wrap(minecraft, Presets.describe(working), width, DESCRIPTION_LINES)) {
            Draw.text(minecraft, line, x, descriptionY, Theme.TEXT_FAINT);
            descriptionY += LINE_HEIGHT;
        }
    }

    private void renderRow(Minecraft minecraft, Row row, int x, int y, int mouseX, int mouseY) {
        Preset.Part part = working.part(row.setting);

        Draw.text(minecraft, Draw.ellipsize(minecraft, Lang.get(row.setting.translationKey()), LABEL_WIDTH - 2),
                x, y + 2, part == null ? Theme.TEXT_DIM : Theme.TEXT);

        row.value.key = part == null ? "gui.developermode.presets.left_out" : part.valueKey(row.setting);
        row.value.toggled = part != null;
        row.value.bounds(x + LABEL_WIDTH, y, VALUE_WIDTH);
        row.value.render(minecraft, mouseX, mouseY);

        if (row.lock != null) {
            row.lock.enabled = part != null;
            row.lock.toggled = part != null && part.locked();
            row.lock.bounds(x + editorWidth() - LOCK_WIDTH, y, LOCK_WIDTH);
            row.lock.render(minecraft, mouseX, mouseY);
        }
    }

    private void renderFooter(Minecraft minecraft, int mouseX, int mouseY) {
        int y = footerY();
        int x = contentX();
        int moveWidth = (LIST_WIDTH - NEW_WIDTH - GAP * 2) / 2;

        newButton.bounds(x, y, NEW_WIDTH);
        newButton.enabled = Presets.all().size() < Presets.MAX;
        newButton.render(minecraft, mouseX, mouseY);

        boolean saved = selected < Presets.all().size();
        earlierButton.bounds(x + NEW_WIDTH + GAP, y, moveWidth);
        earlierButton.enabled = saved && selected > 0;
        earlierButton.render(minecraft, mouseX, mouseY);

        laterButton.bounds(x + NEW_WIDTH + GAP + moveWidth + GAP, y, moveWidth);
        laterButton.enabled = saved && selected < Presets.all().size() - 1;
        laterButton.render(minecraft, mouseX, mouseY);

        int right = contentX() + contentWidth();
        int buttonWidth = (editorWidth() - GAP) / 2;
        captureButton.bounds(editorX(), y, buttonWidth);
        captureButton.enabled = player() != null;
        captureButton.render(minecraft, mouseX, mouseY);

        saveButton.bounds(right - buttonWidth, y, buttonWidth);
        saveButton.render(minecraft, mouseX, mouseY);

        String message = status != null ? Lang.of(status) : Lang.get("gui.developermode.presets.hint");
        int ink = status == null ? Theme.TEXT_FAINT : statusIsError ? Theme.DANGER : Theme.ACCENT;
        Draw.text(minecraft, Draw.ellipsize(minecraft, message, contentWidth()), contentX(), y + Button.HEIGHT + 3, ink);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (icons.mouseClicked(mouseX, mouseY, button)) return;

        if (nameField.contains(mouseX, mouseY)) {
            nameField.setFocused(true);
            return;
        }
        nameField.setFocused(false);

        if (overIcon(mouseX, mouseY)) {
            openIcons();
            return;
        }

        for (Row row : rows) {
            if (row.value.contains(mouseX, mouseY)) {
                step(row.setting, button == 1 ? -1 : 1);
                return;
            }
            if (row.lock != null && row.lock.enabled && row.lock.contains(mouseX, mouseY)) {
                Preset.Part part = working.part(row.setting);
                working = working.with(row.setting, new Preset.Part(part.value(), !part.locked()));
                return;
            }
        }

        if (newButton.enabled && newButton.contains(mouseX, mouseY)) {
            select(Presets.all().size());
            nameField.setFocused(true);
            return;
        }
        if (earlierButton.enabled && earlierButton.contains(mouseX, mouseY)) {
            move(-1);
            return;
        }
        if (laterButton.enabled && laterButton.contains(mouseX, mouseY)) {
            move(1);
            return;
        }
        if (captureButton.enabled && captureButton.contains(mouseX, mouseY)) {
            capture();
            return;
        }
        if (saveButton.contains(mouseX, mouseY)) {
            save();
            return;
        }

        int clicked = presetAt(mouseX, mouseY);
        if (clicked < 0) return;
        if (button == 1) {
            delete(clicked);
        } else {
            select(clicked);
        }
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        if (icons.mouseScrolled(direction)) return;
        if (mouseX < contentX() + LIST_WIDTH) {
            listScroll -= direction;
        } else {
            rowScroll -= direction;
        }
    }

    @Override
    public void keyPressed(char character, int keyCode) {
        if (icons.keyPressed(character, keyCode)) return;
        nameField.keyPressed(character, keyCode);
    }

    @Override
    public boolean clearTypingFocus() {
        if (icons.isOpen()) {
            icons.close();
            return true;
        }
        if (nameField.isFocused()) {
            nameField.setFocused(false);
            return true;
        }
        return false;
    }

    /** Starts editing a copy of a saved preset, or a new one at one past the last. */
    private void select(int index) {
        List<Preset> presets = Presets.all();
        selected = Math.max(0, Math.min(index, presets.size()));
        working = selected < presets.size() ? presets.get(selected) : Preset.empty("");
        nameField.setText(working.name());
        nameField.setFocused(false);
        icons.close();
        scrollToSelected();
    }

    /** Brings the preset being edited into view, since it may have been made or moved off the end. */
    private void scrollToSelected() {
        int visible = visibleListRows();
        if (selected < listScroll) listScroll = selected;
        if (selected >= listScroll + visible) listScroll = selected - visible + 1;
    }

    private void save() {
        String name = nameField.text().trim();
        Preset named = working.withName(name);
        Message failure = Presets.put(selected, named);
        if (failure != null) {
            setStatus(failure, true);
            return;
        }
        working = named;
        setStatus(Message.of("gui.developermode.presets.saved", name), false);
    }

    private void delete(int index) {
        String name = Presets.all().get(index).name();
        Message failure = Presets.delete(index);
        if (failure != null) {
            setStatus(failure, true);
            return;
        }
        // Whatever was being edited stays what is being edited, wherever it
        // has moved to, unless it was the one that went.
        if (index == selected) {
            select(Math.min(index, Math.max(0, Presets.all().size() - 1)));
        } else if (index < selected) {
            selected--;
        }
        setStatus(Message.of("gui.developermode.presets.deleted", name), false);
    }

    /** Moves the preset being edited, and keeps editing it, unsaved changes and all. */
    private void move(int by) {
        Message failure = Presets.move(selected, by);
        if (failure != null) {
            setStatus(failure, true);
            return;
        }
        selected += by;
        scrollToSelected();
    }

    /**
     * Fills the form from where everything is now: every setting that stays
     * and is away from rest, and every setting that drifts, locked if it is
     * locked.
     *
     * <p>A setting at rest is left out rather than put in at rest, since a
     * preset made this way is almost always about what it turns on. Putting
     * one in at rest is a click on its button afterwards.
     */
    private void capture() {
        PlayerEntity player = player();
        if (player == null) return;

        Preset captured = Preset.empty(working.name()).withIcon(working.icon());
        for (Row row : rows) {
            Setting<?> setting = row.setting;
            StringIdentifiable current = setting.current(player);
            if (current == null || setting.stays() && current.equals(setting.rest())) continue;

            StringIdentifiable locked = setting instanceof LockableSetting<?> lockable ? lockable.locked(player) : null;
            captured = captured.with(setting, locked == null
                    ? new Preset.Part(current.asString(), false)
                    : new Preset.Part(locked.asString(), true));
        }

        working = captured;
        setStatus(Message.of("gui.developermode.presets.captured"), false);
    }

    /**
     * Moves a setting on to its next value, with left out as the step before
     * the first. A lock stays with the setting as its value changes.
     */
    private void step(Setting<?> setting, int direction) {
        List<String> values = setting.values().stream().map(StringIdentifiable::asString).toList();
        Preset.Part part = working.part(setting);
        int now = part == null ? -1 : values.indexOf(part.value());
        int next = Math.floorMod(now + 1 + direction, values.size() + 1) - 1;
        working = working.with(setting, next < 0 ? null
                : new Preset.Part(values.get(next), part != null && part.locked()));
    }

    private void openIcons() {
        List<NbtShape.Choice> choices = new ArrayList<>();
        for (ItemCatalogue.Variant variant : ItemCatalogue.all()) {
            choices.add(new NbtShape.Choice(variant.name(), variant.stack(), Map.of("id", variant.displayId())));
        }
        icons.open(choices, editorX(), contentY() + ICON_BOX + 1,
                contentX(), contentY(), contentWidth(), contentHeight(),
                choice -> working = working.withIcon(choice.icon().copy()));
    }

    /** The saved preset under the pointer, or -1. */
    private int presetAt(int pointX, int pointY) {
        if (pointX < contentX() || pointX >= contentX() + LIST_WIDTH || pointY < contentY()) return -1;
        int row = (pointY - contentY()) / ROW_HEIGHT;
        if (row >= visibleListRows()) return -1;
        int index = listScroll + row;
        return index < Presets.all().size() ? index : -1;
    }

    private boolean overIcon(int pointX, int pointY) {
        return pointX >= editorX() && pointX < editorX() + ICON_BOX
                && pointY >= contentY() && pointY < contentY() + ICON_BOX;
    }

    private static ItemStack iconOf(Preset preset) {
        return preset.icon() == null ? new ItemStack(Item.BOOK) : preset.icon();
    }

    private int editorX() {
        return contentX() + LIST_WIDTH + GAP;
    }

    private int editorWidth() {
        return contentWidth() - LIST_WIDTH - GAP;
    }

    private int fieldsTop() {
        return contentY() + ICON_BOX + GAP + 2;
    }

    private int descriptionTop() {
        return footerY() - GAP - DESCRIPTION_LINES * LINE_HEIGHT;
    }

    private int footerY() {
        return contentY() + contentHeight() - Button.HEIGHT - 11;
    }

    private int visibleListRows() {
        return (footerY() - GAP - contentY()) / ROW_HEIGHT;
    }

    /**
     * How many settings fit between the name and the description, less one
     * when they do not all fit, to leave room for saying which are showing.
     */
    private int visibleFieldRows() {
        int room = (descriptionTop() - GAP - fieldsTop()) / FIELD_ROW;
        return Math.max(1, rows.size() > room ? room - 1 : room);
    }

    private static PlayerEntity player() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        return minecraft == null ? null : minecraft.player;
    }

    private void setStatus(Message message, boolean isError) {
        status = message;
        statusIsError = isError;
        statusTicks = STATUS_DURATION_TICKS;
    }
}
