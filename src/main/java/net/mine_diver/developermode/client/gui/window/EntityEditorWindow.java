package net.mine_diver.developermode.client.gui.window;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.Freezing;
import net.mine_diver.developermode.client.gui.Button;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.EntityPreview;
import net.mine_diver.developermode.client.gui.NbtTree;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.composer.DevWindow;
import net.mine_diver.developermode.feature.entity.Entities;
import net.mine_diver.developermode.feature.entity.FrozenEntities;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.EntityNbtInbox;
import net.mine_diver.developermode.feature.net.packet.ApplyEntityNbtC2SPacket;
import net.mine_diver.developermode.feature.net.packet.RequestEntityNbtC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;

/**
 * Dumps an entity to NBT, lets you edit the values, and writes them back.
 *
 * <p>The entity is frozen while it is being edited, since an entity that walks
 * away mid edit leaves you applying stale coordinates. The window freezes on
 * its own and thaws again when it retargets or closes, so it does not leave a
 * trail of stopped mobs behind it. Pressing the button yourself hands you
 * ownership, and then the window stops managing it.
 */
public final class EntityEditorWindow extends DevWindow {
    private static final int PREVIEW_WIDTH = 58;
    private static final int PREVIEW_HEIGHT = 72;
    private static final int FOOTER_HEIGHT = 26;
    private static final int BUTTON_WIDTH = 54;
    private static final int GAP = 4;
    private static final int STATUS_DURATION_TICKS = 80;
    /** A drifting entity would otherwise ask for a fresh dump every tick. */
    private static final int REQUEST_INTERVAL_TICKS = 10;

    private final Button freezeButton = new Button("Freeze");
    private final Button pickButton = new Button("Pick");
    private final Button applyButton = new Button("Apply");
    private final Button reloadButton = new Button("Reload");
    private final NbtTree tree = new NbtTree();

    private Entity entity;
    private NbtCompound working;
    /** Where the entity was when the NBT was dumped, to notice it drifting. */
    private double dumpedX;
    private double dumpedY;
    private double dumpedZ;

    private int nbtSequence = EntityNbtInbox.sequence();
    private int statusSequence = DevStatus.sequence(DevStatus.ENTITY);
    private int requestCooldown;

    private float turntable;
    private String status = "";
    private boolean statusIsError;
    private int statusTicks;

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
        reloadNbt();
    }

    @Override
    public void onClosed() {
        if (entity != null) Freezing.stopEditing(entity);
    }

    @Override
    public void tick() {
        tree.tick();
        if (statusTicks > 0 && --statusTicks == 0) status = "";
        if (requestCooldown > 0) requestCooldown--;
        takeAnswers();

        // An entity from a world we have left is no more use than a dead one.
        if (entity != null && entity.world != DeveloperModeClient.minecraft().world) {
            Freezing.stopEditing(entity);
            entity = null;
            reloadNbt();
        }
        if (entity == null || entity.dead) return;

        // tick only runs while the composer is the screen, so re-asserting here
        // is this window saying it is still looking. DeveloperUi drops the
        // freeze again the moment the UI goes away, and this puts it back when
        // the UI returns. Local only: the world was told once already.
        FrozenEntities.freezeWhileEditing(entity);

        // Between the UI closing and opening again the entity was free to walk
        // off, which would leave Apply writing a stale Pos and teleporting it
        // back. Silently re-read when nothing has been typed; say so when
        // something has, rather than throwing away the edit.
        if (hasDrifted() && !tree.isDirty() && requestCooldown == 0) reloadNbt();
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
            Draw.text(minecraft, freezeState(), infoX, previewY + 34,
                    FrozenEntities.isFrozen(entity) ? Theme.ACCENT : Theme.TEXT_FAINT);
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

        int treeY = contentY() + PREVIEW_HEIGHT + GAP;
        int treeHeight = contentHeight() - PREVIEW_HEIGHT - GAP - FOOTER_HEIGHT;
        tree.bounds(contentX(), treeY, contentWidth(), treeHeight);
        tree.render(minecraft, mouseX, mouseY);

        int footerY = contentY() + contentHeight() - FOOTER_HEIGHT + 1;
        applyButton.bounds(contentX(), footerY, BUTTON_WIDTH);
        applyButton.enabled = alive && working != null;
        applyButton.render(minecraft, mouseX, mouseY);

        reloadButton.bounds(contentX() + BUTTON_WIDTH + GAP, footerY, BUTTON_WIDTH);
        reloadButton.enabled = alive;
        reloadButton.render(minecraft, mouseX, mouseY);

        String message = !tree.error().isEmpty() ? tree.error()
                : hasDrifted() ? "moved since loaded, reload to catch up"
                : status;
        int ink = !tree.error().isEmpty() || statusIsError ? Theme.DANGER
                : hasDrifted() ? Theme.DANGER : Theme.ACCENT;
        if (!message.isEmpty()) {
            int messageX = contentX() + (BUTTON_WIDTH + GAP) * 2;
            Draw.text(minecraft, Draw.ellipsize(minecraft, message, contentX() + contentWidth() - messageX),
                    messageX, footerY + 2, ink);
        }
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
        if (applyButton.enabled && applyButton.contains(mouseX, mouseY)) {
            applyNbt();
            return;
        }
        if (reloadButton.enabled && reloadButton.contains(mouseX, mouseY)) {
            reloadNbt();
            setStatus("Reloaded", false);
            return;
        }
        tree.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseScrolled(int mouseX, int mouseY, int direction) {
        tree.mouseScrolled(direction);
    }

    @Override
    public void keyPressed(char character, int keyCode) {
        tree.keyPressed(character, keyCode);
    }

    @Override
    public boolean clearTypingFocus() {
        return tree.cancelEditing();
    }

    private void applyNbt() {
        if (entity == null || working == null) return;
        PacketHelper.send(new ApplyEntityNbtC2SPacket(entity.id, working));
        // The answer carries a fresh dump with it, so the tree ends up showing
        // what the entity accepted rather than what was typed at it.
        takeAnswers();
    }

    /**
     * Asks the world that owns the entity for its NBT.
     *
     * <p>Asks even in a local world, where the answer is already waiting by the
     * time this returns. A client's copy of an entity is something kept roughly
     * in step by position updates rather than the entity itself, so reading it
     * here would show values the world does not agree with.
     */
    private void reloadNbt() {
        working = null;
        tree.setRoot(null);

        if (entity != null) {
            dumpedX = entity.x;
            dumpedY = entity.y;
            dumpedZ = entity.z;
        }
        if (entity == null || entity.dead) return;

        requestCooldown = REQUEST_INTERVAL_TICKS;
        PacketHelper.send(new RequestEntityNbtC2SPacket(entity.id));
        takeAnswers();
    }

    /** Picks up whatever has come back for the entity being edited. */
    private void takeAnswers() {
        if (EntityNbtInbox.sequence() != nbtSequence) {
            nbtSequence = EntityNbtInbox.sequence();
            // An answer can arrive after the window has been pointed somewhere
            // else, which is why the id comes back with it.
            if (entity != null && EntityNbtInbox.entityId() == entity.id) {
                working = EntityNbtInbox.nbt();
                tree.setRoot(working);
            }
        }
        if (DevStatus.sequence(DevStatus.ENTITY) != statusSequence) {
            statusSequence = DevStatus.sequence(DevStatus.ENTITY);
            setStatus(DevStatus.message(DevStatus.ENTITY), !DevStatus.ok(DevStatus.ENTITY));
        }
    }

    /** True once the entity has moved away from where the NBT was read. */
    private boolean hasDrifted() {
        if (entity == null || working == null) return false;
        return Math.abs(entity.x - dumpedX) + Math.abs(entity.y - dumpedY)
                + Math.abs(entity.z - dumpedZ) > 0.01;
    }

    private String freezeState() {
        if (entity == null) return "";
        if (FrozenEntities.isHeld(entity)) return "held";
        return FrozenEntities.isFrozen(entity) ? "frozen while open" : "ticking";
    }

    private void setStatus(String message, boolean isError) {
        status = message;
        statusIsError = isError;
        statusTicks = STATUS_DURATION_TICKS;
    }

    private static String position(Entity entity) {
        return String.format("%.1f %.1f %.1f", entity.x, entity.y, entity.z);
    }
}
