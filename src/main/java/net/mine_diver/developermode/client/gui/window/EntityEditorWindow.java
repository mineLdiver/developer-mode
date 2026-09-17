package net.mine_diver.developermode.client.gui.window;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.Freezing;
import net.mine_diver.developermode.client.gui.Button;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.EntityPreview;
import net.mine_diver.developermode.client.gui.NbtPanel;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.composer.DevWindow;
import net.mine_diver.developermode.feature.entity.Entities;
import net.mine_diver.developermode.feature.entity.FrozenEntities;
import net.mine_diver.developermode.feature.net.NbtTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

/**
 * An entity's NBT, with the entity beside it.
 *
 * <p>The entity is frozen while it is being edited, since one that walks away
 * mid edit leaves you applying stale coordinates. The window freezes on its own
 * and thaws again when it retargets or closes, so it does not leave a trail of
 * stopped mobs behind it. Pressing the button yourself hands you ownership, and
 * then the window stops managing it.
 */
public final class EntityEditorWindow extends DevWindow {
    private static final int PREVIEW_WIDTH = 58;
    private static final int PREVIEW_HEIGHT = 72;
    private static final int BUTTON_WIDTH = 54;
    private static final int GAP = 4;

    private final Button freezeButton = new Button("Freeze");
    private final Button pickButton = new Button("Pick");
    private final NbtPanel panel = new NbtPanel();

    private Entity entity;
    /** Where the entity was when the NBT was asked for, to notice it drifting. */
    private double dumpedX;
    private double dumpedY;
    private double dumpedZ;

    private float turntable;

    public EntityEditorWindow(Entity entity) {
        super("Entity", 248, 216);
        setTarget(entity);
    }

    public static void open(Entity entity) {
        ComposerScreen composer = ComposerScreen.instance();
        EntityEditorWindow window = composer.find(EntityEditorWindow.class);
        if (window == null) {
            composer.add(new EntityEditorWindow(entity));
        } else {
            window.setTarget(entity);
            composer.focus(window);
        }
        ComposerScreen.open();
    }

    public void setTarget(Entity entity) {
        if (this.entity != null) Freezing.stopEditing(this.entity);
        this.entity = entity;
        if (entity != null && !entity.dead) Freezing.freezeWhileEditing(entity);

        rememberWhere();
        panel.setTarget(entity == null ? null : NbtTarget.entity(entity.id));
    }

    @Override
    public void onClosed() {
        if (entity != null) Freezing.stopEditing(entity);
    }

    @Override
    public void tick() {
        panel.tick();

        // An entity from a world we have left is no more use than a dead one.
        if (entity != null && entity.world != DeveloperModeClient.minecraft().world) {
            Freezing.stopEditing(entity);
            entity = null;
            panel.setTarget(null);
        }
        if (entity == null || entity.dead) return;

        // tick only runs while the composer is the screen, so re-asserting here
        // is this window saying it is still looking. DeveloperUi drops the
        // freeze the moment the UI goes away, and this puts it back when the UI
        // returns. Local only: the world was told once already.
        FrozenEntities.freezeWhileEditing(entity);

        // Between the UI closing and opening again the entity was free to walk
        // off, which would leave Apply writing a stale Pos and teleporting it
        // back. Silently ask again when nothing has been typed; say so when
        // something has, rather than throwing away the edit.
        if (hasDrifted() && !panel.isDirty() && panel.mayRequest()) {
            rememberWhere();
            panel.reload();
        }
    }

    @Override
    protected void renderContent(Minecraft minecraft, int mouseX, int mouseY, float delta, boolean focused) {
        boolean alive = entity != null && !entity.dead;

        int previewX = contentX();
        int previewY = contentY();
        Draw.rect(previewX, previewY, previewX + PREVIEW_WIDTH, previewY + PREVIEW_HEIGHT, Theme.PANEL_SUNKEN);
        Draw.outline(previewX, previewY, PREVIEW_WIDTH, PREVIEW_HEIGHT, Theme.BORDER);

        if (alive) {
            if (mouseX >= previewX && mouseX < previewX + PREVIEW_WIDTH
                    && mouseY >= previewY && mouseY < previewY + PREVIEW_HEIGHT) {
                turntable = (float) Math.atan((mouseX - (previewX + PREVIEW_WIDTH / 2.0)) / 18.0) * 70;
            }
            EntityPreview.render(minecraft, entity,
                    previewX + 1, previewY + 1, PREVIEW_WIDTH - 2, PREVIEW_HEIGHT - 2, turntable);
        } else {
            Draw.textCentered(minecraft, "gone", previewX + PREVIEW_WIDTH / 2,
                    previewY + PREVIEW_HEIGHT / 2 - 4, Theme.TEXT_FAINT);
        }

        int infoX = previewX + PREVIEW_WIDTH + GAP;
        int infoWidth = contentX() + contentWidth() - infoX;
        if (entity != null) {
            Draw.text(minecraft, Draw.ellipsize(minecraft, Entities.name(entity), infoWidth),
                    infoX, previewY + 1, alive ? Theme.TEXT : Theme.TEXT_FAINT);
            Draw.text(minecraft, "id " + entity.id, infoX, previewY + 12, Theme.TEXT_DIM);
            Draw.text(minecraft, position(entity), infoX, previewY + 23, Theme.TEXT_FAINT);
            Draw.text(minecraft, hasDrifted() ? "moved, reload to catch up" : freezeState(),
                    infoX, previewY + 34,
                    hasDrifted() ? Theme.DANGER
                            : FrozenEntities.isFrozen(entity) ? Theme.ACCENT : Theme.TEXT_FAINT);
        } else {
            Draw.text(minecraft, "Nothing targeted", infoX, previewY + 1, Theme.TEXT_DIM);
        }

        int buttonY = previewY + PREVIEW_HEIGHT - Button.HEIGHT;
        freezeButton.bounds(infoX, buttonY, BUTTON_WIDTH);
        freezeButton.enabled = alive;
        freezeButton.toggled = alive && FrozenEntities.isHeld(entity);
        freezeButton.label = freezeButton.toggled ? "Held" : "Hold";
        freezeButton.render(minecraft, mouseX, mouseY);

        pickButton.bounds(infoX + BUTTON_WIDTH + GAP, buttonY, BUTTON_WIDTH);
        pickButton.render(minecraft, mouseX, mouseY);

        int panelY = contentY() + PREVIEW_HEIGHT + GAP;
        panel.render(minecraft, contentX(), panelY, contentWidth(),
                contentHeight() - PREVIEW_HEIGHT - GAP, mouseX, mouseY);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (freezeButton.enabled && freezeButton.contains(mouseX, mouseY)) {
            if (FrozenEntities.isHeld(entity)) {
                // Back to a plain automatic freeze, which lasts only as long as
                // this window is on screen.
                Freezing.release(entity);
                Freezing.freezeWhileEditing(entity);
            } else {
                Freezing.hold(entity);
            }
            return;
        }
        if (pickButton.contains(mouseX, mouseY)) {
            EntityListWindow.open();
            return;
        }
        panel.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        panel.mouseScrolled(direction);
    }

    @Override
    public void keyPressed(char character, int keyCode) {
        panel.keyPressed(character, keyCode);
    }

    @Override
    public boolean clearTypingFocus() {
        return panel.clearTypingFocus();
    }

    private void rememberWhere() {
        if (entity == null) return;
        dumpedX = entity.x;
        dumpedY = entity.y;
        dumpedZ = entity.z;
    }

    /** True once the entity has moved away from where the NBT was read. */
    private boolean hasDrifted() {
        if (entity == null || !panel.hasContent()) return false;
        return Math.abs(entity.x - dumpedX) + Math.abs(entity.y - dumpedY)
                + Math.abs(entity.z - dumpedZ) > 0.01;
    }

    private String freezeState() {
        if (entity == null) return "";
        if (FrozenEntities.isHeld(entity)) return "held";
        return FrozenEntities.isFrozen(entity) ? "frozen while open" : "ticking";
    }

    private static String position(Entity entity) {
        return String.format("%.1f %.1f %.1f", entity.x, entity.y, entity.z);
    }
}
