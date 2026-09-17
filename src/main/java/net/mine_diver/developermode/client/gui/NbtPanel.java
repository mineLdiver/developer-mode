package net.mine_diver.developermode.client.gui;

import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.NbtInbox;
import net.mine_diver.developermode.feature.net.NbtTarget;
import net.mine_diver.developermode.feature.net.packet.ApplyNbtC2SPacket;
import net.mine_diver.developermode.feature.net.packet.RequestNbtC2SPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtCompound;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;

/**
 * A tree of NBT with the buttons that move it, for whatever a target points at.
 *
 * <p>Owns the conversation rather than the subject: it asks the world for a
 * dump, waits for one, and sends edits back. A window supplies the target and
 * whatever heading makes sense for it, and this handles the rest, which is
 * most of it and is the same for an entity as for a block entity.
 *
 * <p>Raw turns off the guessing. A compound that was recognized as something
 * goes back to being a count of keys, and a number typed into one stops being
 * brought into range, which is what you want when the guess is wrong or when
 * the range is the thing you are testing.
 */
public final class NbtPanel {
    public static final int FOOTER_HEIGHT = 26;

    private static final int BUTTON_WIDTH = 54;
    private static final int TOGGLE_WIDTH = 34;
    private static final int GAP = 4;
    private static final int STATUS_DURATION_TICKS = 80;
    /** A subject that keeps moving would otherwise ask again every tick. */
    private static final int REQUEST_INTERVAL_TICKS = 10;

    private final NbtTree tree = new NbtTree();
    private final Button applyButton = new Button("Apply");
    private final Button reloadButton = new Button("Reload");
    private final Button rawButton = new Button("Raw");

    private NbtTarget target;
    private NbtCompound working;

    private int nbtSequence = NbtInbox.sequence();
    private int statusSequence = DevStatus.sequence(DevStatus.ENTITY);
    private int requestCooldown;

    private String status = "";
    private boolean statusIsError;
    private int statusTicks;

    public NbtTarget target() {
        return target;
    }

    /** Points at something else, and asks about it. */
    public void setTarget(NbtTarget target) {
        this.target = target;
        reload();
    }

    public boolean isDirty() {
        return tree.isDirty();
    }

    /** Whether an answer has arrived and there is something to look at. */
    public boolean hasContent() {
        return working != null;
    }

    /** Whether asking again right now would be reasonable rather than spam. */
    public boolean mayRequest() {
        return requestCooldown == 0;
    }

    public void tick() {
        tree.tick();
        if (statusTicks > 0 && --statusTicks == 0) status = "";
        if (requestCooldown > 0) requestCooldown--;
        takeAnswers();
    }

    /**
     * Asks the world that owns the target for its NBT.
     *
     * <p>Asks even in a local world, where the answer is already waiting by the
     * time this returns. A client's copy of a thing is not the thing, and a
     * client may not have been told about a block entity at all.
     */
    public void reload() {
        working = null;
        tree.setRoot(null);
        if (target == null) return;

        requestCooldown = REQUEST_INTERVAL_TICKS;
        PacketHelper.send(new RequestNbtC2SPacket(target));
        takeAnswers();
    }

    public void apply() {
        if (target == null || working == null) return;
        PacketHelper.send(new ApplyNbtC2SPacket(target, working));
        // The answer carries a fresh dump, so the tree ends up showing what was
        // accepted rather than what was typed.
        takeAnswers();
    }

    public void render(Minecraft minecraft, int x, int y, int width, int height, int mouseX, int mouseY) {
        int treeHeight = height - FOOTER_HEIGHT;
        tree.bounds(x, y, width, treeHeight);
        tree.render(minecraft, mouseX, mouseY);

        int footerY = y + height - FOOTER_HEIGHT + 1;
        applyButton.bounds(x, footerY, BUTTON_WIDTH);
        applyButton.enabled = working != null;
        applyButton.render(minecraft, mouseX, mouseY);

        reloadButton.bounds(x + BUTTON_WIDTH + GAP, footerY, BUTTON_WIDTH);
        reloadButton.enabled = target != null;
        reloadButton.render(minecraft, mouseX, mouseY);

        rawButton.bounds(x + (BUTTON_WIDTH + GAP) * 2, footerY, TOGGLE_WIDTH);
        rawButton.toggled = tree.isRaw();
        rawButton.render(minecraft, mouseX, mouseY);

        String message = tree.error().isEmpty() ? status : tree.error();
        int ink = !tree.error().isEmpty() || statusIsError ? Theme.DANGER : Theme.ACCENT;
        if (!message.isEmpty()) {
            int messageX = x + (BUTTON_WIDTH + GAP) * 2 + TOGGLE_WIDTH + GAP;
            Draw.text(minecraft, Draw.ellipsize(minecraft, message, x + width - messageX),
                    messageX, footerY + 2, ink);
        }
    }

    /** @return true if the click was the panel's rather than the window's */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (applyButton.enabled && applyButton.contains(mouseX, mouseY)) {
            apply();
            return true;
        }
        if (reloadButton.enabled && reloadButton.contains(mouseX, mouseY)) {
            reload();
            setStatus("Reloaded", false);
            return true;
        }
        if (rawButton.contains(mouseX, mouseY)) {
            tree.setRaw(!tree.isRaw());
            return true;
        }
        tree.mouseClicked(mouseX, mouseY, button);
        return false;
    }

    public void mouseScrolled(int direction) {
        tree.mouseScrolled(direction);
    }

    public void keyPressed(char character, int keyCode) {
        tree.keyPressed(character, keyCode);
    }

    public boolean clearTypingFocus() {
        return tree.cancelEditing();
    }

    public void setStatus(String message, boolean isError) {
        status = message;
        statusIsError = isError;
        statusTicks = STATUS_DURATION_TICKS;
    }

    /** Picks up anything that has come back for what this is pointed at. */
    private void takeAnswers() {
        if (NbtInbox.sequence() != nbtSequence) {
            nbtSequence = NbtInbox.sequence();
            // An answer can arrive after this has been pointed somewhere else,
            // which is why the target comes back with it.
            if (target != null && target.equals(NbtInbox.target())) {
                working = NbtInbox.nbt();
                tree.setRoot(working);
            }
        }
        if (DevStatus.sequence(DevStatus.ENTITY) != statusSequence) {
            statusSequence = DevStatus.sequence(DevStatus.ENTITY);
            setStatus(DevStatus.message(DevStatus.ENTITY), !DevStatus.ok(DevStatus.ENTITY));
        }
    }
}
