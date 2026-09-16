package net.mine_diver.developermode.client.gui.window;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.Button;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.EntityPreview;
import net.mine_diver.developermode.client.gui.NbtTree;
import net.mine_diver.developermode.client.gui.TextField;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.composer.DevWindow;
import net.mine_diver.developermode.client.summon.SummonMode;
import net.mine_diver.developermode.feature.entity.EntityNbt;
import net.mine_diver.developermode.feature.entity.EntitySummoning;
import net.mine_diver.developermode.feature.entity.SummonPresets;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The NBT an entity will be born with, set up before there is an entity.
 *
 * <p>A prompt rather than a tool. It opens on a fresh dump every time, never on
 * whatever was left here last, and Summon hands that compound to the arming and
 * dismisses the window. Nothing about a type changes because you looked at it:
 * left clicking it in the picker still summons a plain one.
 *
 * <p>Saving is the deliberate half. A named preset goes to disk through
 * {@link SummonPresets} and comes back in the list above the fields, which is
 * the only state here that outlives the window.
 *
 * <p>Simpler than {@link EntityEditorWindow} because the subject is simpler:
 * nothing is ticking, nothing can drift, so there is no Freeze and no Apply.
 * Edits land in the compound directly, which is what lets the model beside them
 * change shape as you type.
 */
public final class SummonPresetWindow extends DevWindow {
    private static final int PREVIEW_WIDTH = 58;
    private static final int PREVIEW_HEIGHT = 72;
    private static final int FOOTER_HEIGHT = 30;
    private static final int BUTTON_WIDTH = 54;
    private static final int NAME_WIDTH = 132;
    private static final int GAP = 4;
    private static final int SAVED_ROWS = 5;
    private static final int SAVED_ROW_HEIGHT = 10;
    private static final int STATUS_DURATION_TICKS = 80;

    private final Button summonButton = new Button("Summon");
    private final Button saveButton = new Button("Save");
    private final TextField nameField = new TextField(24);
    private final NbtTree tree = new NbtTree();
    /** Names saved for this type, refreshed when they change rather than per frame. */
    private final List<String> saved = new ArrayList<>();

    private String type;
    /** What Summon would arm and Save would store. */
    private NbtCompound working;
    private int lastRevision;
    /** Whether this is still just a dump of a plain one. */
    private boolean customised;
    private int savedScroll;

    private Entity preview;
    private World previewWorld;
    private boolean previewBroken;

    private float turntable;
    private String status = "";
    private boolean statusIsError;
    private int statusTicks;

    public SummonPresetWindow(String type) {
        super("Preset", 256, 236);
        setType(type);
    }

    public static void open(String type) {
        ComposerScreen composer = ComposerScreen.instance();
        SummonPresetWindow window = composer.find(SummonPresetWindow.class);
        if (window == null) {
            composer.add(new SummonPresetWindow(type));
        } else {
            window.setType(type);
            composer.focus(window);
        }
        ComposerScreen.open();
    }

    /** Retargets at a type, on a fresh dump. Any unsaved edit is gone. */
    public void setType(String type) {
        this.type = type;
        setTitle("Preset: " + type);
        refreshSaved();
        reload();
    }

    @Override
    public void tick() {
        tree.tick();
        nameField.tick();
        if (statusTicks > 0 && --statusTicks == 0) status = "";
        if (tree.revision() != lastRevision) {
            lastRevision = tree.revision();
            customised = true;
            rebuildPreview();
        }
    }

    @Override
    protected void renderContent(Minecraft minecraft, int mouseX, int mouseY, float delta, boolean focused) {
        // A new world invalidates more than the model: without one there was
        // nothing to dump defaults off, so the fields may never have been
        // filled in at all.
        if (previewWorld != minecraft.world) reload();

        int previewX = contentX();
        int previewY = contentY();
        Draw.rect(previewX, previewY, previewX + PREVIEW_WIDTH, previewY + PREVIEW_HEIGHT, Theme.PANEL_SUNKEN);
        Draw.outline(previewX, previewY, PREVIEW_WIDTH, PREVIEW_HEIGHT, Theme.BORDER);

        if (preview != null && !previewBroken) {
            if (mouseX >= previewX && mouseX < previewX + PREVIEW_WIDTH
                    && mouseY >= previewY && mouseY < previewY + PREVIEW_HEIGHT) {
                turntable = (float) Math.atan((mouseX - (previewX + PREVIEW_WIDTH / 2.0)) / 18.0) * 70;
            }
            if (!EntityPreview.render(minecraft, preview,
                    previewX + 1, previewY + 1, PREVIEW_WIDTH - 2, PREVIEW_HEIGHT - 2, turntable)) {
                previewBroken = true;
            }
        } else {
            Draw.textCentered(minecraft, previewBroken ? "no model" : "no world",
                    previewX + PREVIEW_WIDTH / 2, previewY + PREVIEW_HEIGHT / 2 - 4, Theme.TEXT_FAINT);
        }

        renderSaved(minecraft, mouseX, mouseY);

        int treeY = contentY() + PREVIEW_HEIGHT + GAP;
        int treeHeight = contentHeight() - PREVIEW_HEIGHT - GAP - FOOTER_HEIGHT;
        tree.bounds(contentX(), treeY, contentWidth(), treeHeight);
        tree.render(minecraft, mouseX, mouseY);

        if (working == null) {
            Draw.text(minecraft, "Nothing to edit without a world", contentX(), treeY + 1, Theme.TEXT_DIM);
        }

        int footerY = contentY() + contentHeight() - FOOTER_HEIGHT + 1;
        nameField.bounds(contentX(), footerY, NAME_WIDTH);
        nameField.render(minecraft, "Name to save as");

        saveButton.bounds(contentX() + NAME_WIDTH + GAP, footerY + 1, BUTTON_WIDTH);
        saveButton.enabled = working != null;
        saveButton.render(minecraft, mouseX, mouseY);

        summonButton.bounds(contentX() + NAME_WIDTH + GAP + BUTTON_WIDTH + GAP, footerY + 1, BUTTON_WIDTH);
        summonButton.enabled = working != null && preview != null && !previewBroken;
        summonButton.render(minecraft, mouseX, mouseY);

        String message = !tree.error().isEmpty() ? tree.error()
                : previewBroken ? "nothing here can draw this, it would crash the world render"
                : !status.isEmpty() ? status
                : "Pos and Rotation come from where you click";
        int ink = !tree.error().isEmpty() || previewBroken || (statusIsError && !status.isEmpty())
                ? Theme.DANGER
                : status.isEmpty() ? Theme.TEXT_FAINT : Theme.ACCENT;
        Draw.text(minecraft, Draw.ellipsize(minecraft, message, contentWidth()),
                contentX(), footerY + 17, ink);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (nameField.contains(mouseX, mouseY)) {
            tree.cancelEditing();
            nameField.setFocused(true);
            return;
        }
        nameField.setFocused(false);

        if (saveButton.enabled && saveButton.contains(mouseX, mouseY)) {
            String failure = SummonPresets.save(type, nameField.text().trim(), working);
            if (failure == null) {
                setStatus("Saved as " + nameField.text().trim(), false);
                refreshSaved();
            } else {
                setStatus(failure, true);
            }
            return;
        }
        if (summonButton.enabled && summonButton.contains(mouseX, mouseY)) {
            // An untouched dump is handed over as nothing at all, so opening
            // this window and changing your mind does not leave the readout
            // claiming a preset that would summon a perfectly ordinary one.
            SummonMode.arm(type, customised ? working : null);
            // A prompt that stayed open would sit there afterwards showing NBT
            // that is no longer connected to anything.
            ComposerScreen.instance().close(this);
            DeveloperModeClient.minecraft().setScreen(null);
            return;
        }

        String clicked = savedAt(mouseX, mouseY);
        if (clicked != null) {
            if (button == 1) {
                SummonPresets.delete(type, clicked);
                refreshSaved();
                setStatus("Deleted " + clicked, false);
            } else {
                loadSaved(clicked);
            }
            return;
        }

        tree.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        if (mouseY < contentY() + PREVIEW_HEIGHT && mouseX >= contentX() + PREVIEW_WIDTH + GAP) {
            savedScroll = Math.max(0, Math.min(savedScroll - direction, Math.max(0, saved.size() - SAVED_ROWS)));
            return;
        }
        tree.mouseScrolled(direction);
    }

    @Override
    public void keyPressed(char character, int keyCode) {
        if (nameField.isFocused()) {
            nameField.keyPressed(character, keyCode);
            return;
        }
        tree.keyPressed(character, keyCode);
    }

    @Override
    public boolean clearTypingFocus() {
        if (nameField.isFocused()) {
            nameField.setFocused(false);
            return true;
        }
        return tree.cancelEditing();
    }

    private void renderSaved(Minecraft minecraft, int mouseX, int mouseY) {
        int listX = contentX() + PREVIEW_WIDTH + GAP;
        int listWidth = contentX() + contentWidth() - listX;
        int previewY = contentY();

        Draw.text(minecraft, Draw.ellipsize(minecraft, type, listWidth), listX, previewY + 1,
                saved.isEmpty() ? Theme.TEXT : Theme.ACCENT);

        if (saved.isEmpty()) {
            Draw.text(minecraft, "nothing saved yet", listX, previewY + 14, Theme.TEXT_FAINT);
            Draw.text(minecraft, "edit below, name it,", listX, previewY + 28, Theme.TEXT_FAINT);
            Draw.text(minecraft, "and Save keeps it", listX, previewY + 38, Theme.TEXT_FAINT);
            return;
        }

        int listTop = savedTop();
        for (int i = 0; i < SAVED_ROWS; i++) {
            int index = savedScroll + i;
            if (index >= saved.size()) break;

            int rowY = listTop + i * SAVED_ROW_HEIGHT;
            boolean hovered = mouseX >= listX && mouseX < listX + listWidth
                    && mouseY >= rowY && mouseY < rowY + SAVED_ROW_HEIGHT;
            if (hovered) Draw.rect(listX, rowY, listX + listWidth, rowY + SAVED_ROW_HEIGHT, Theme.HOVER);

            Draw.text(minecraft, Draw.ellipsize(minecraft, saved.get(index), listWidth),
                    listX, rowY + 1, hovered ? Theme.ACCENT : Theme.TEXT);
        }

        Draw.text(minecraft, "click loads    right click deletes",
                listX, previewY + PREVIEW_HEIGHT - 9, Theme.TEXT_FAINT);
    }

    /** The saved name under the cursor, or null. */
    private String savedAt(int pointX, int pointY) {
        if (saved.isEmpty()) return null;

        int listX = contentX() + PREVIEW_WIDTH + GAP;
        if (pointX < listX || pointX >= contentX() + contentWidth()) return null;

        int row = (pointY - savedTop()) / SAVED_ROW_HEIGHT;
        if (pointY < savedTop() || row >= SAVED_ROWS) return null;

        int index = savedScroll + row;
        return index < saved.size() ? saved.get(index) : null;
    }

    private int savedTop() {
        return contentY() + 13;
    }

    private void loadSaved(String name) {
        NbtCompound loaded = SummonPresets.load(type, name);
        if (loaded == null) {
            setStatus("Could not load " + name, true);
            return;
        }
        working = loaded;
        tree.setRoot(working);
        lastRevision = tree.revision();
        customised = true;
        nameField.setText(name);
        rebuildPreview();
        setStatus("Loaded " + name, false);
    }

    private void refreshSaved() {
        saved.clear();
        saved.addAll(SummonPresets.namesFor(type));
        savedScroll = Math.max(0, Math.min(savedScroll, Math.max(0, saved.size() - SAVED_ROWS)));
    }

    /** A fresh dump of an unmodified one, which is where every visit starts. */
    private void reload() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        World world = minecraft == null ? null : minecraft.world;

        Entity fresh = world == null ? null : EntitySummoning.create(type, world);
        working = fresh == null ? null : EntityNbt.dump(fresh);

        tree.setRoot(working);
        lastRevision = tree.revision();
        customised = false;
        rebuildPreview();
    }

    /**
     * Rebuilds the model beside the fields, since a value that changes what an
     * entity looks like has to be visible to be worth editing.
     *
     * <p>Built the way the ghost and the real summon are: construct, then load.
     * Anything the renderer chokes on is caught and refused here rather than in
     * the world render, where a throw once a frame leaves no way out but
     * quitting.
     */
    private void rebuildPreview() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        previewWorld = minecraft == null ? null : minecraft.world;
        previewBroken = false;
        preview = previewWorld == null ? null : EntitySummoning.create(type, previewWorld);
        if (preview == null) return;

        if (working != null) {
            String failure = EntitySummoning.applyPreset(preview, working);
            if (failure != null) setStatus(failure, true);
        }
        EntitySummoning.place(preview, 0, 0, 0, 0);
    }

    private void setStatus(String message, boolean isError) {
        status = message;
        statusIsError = isError;
        statusTicks = STATUS_DURATION_TICKS;
    }
}
