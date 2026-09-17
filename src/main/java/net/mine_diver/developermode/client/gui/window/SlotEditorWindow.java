package net.mine_diver.developermode.client.gui.window;

import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.ItemDraw;
import net.mine_diver.developermode.client.gui.NbtPanel;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.composer.DevWindow;
import net.mine_diver.developermode.feature.net.NbtTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.ItemStack;

/**
 * The NBT on a stack in an open container.
 *
 * <p>The whole stack: the id, count and damage Beta gives every stack, and
 * whatever a mod has added to this one through StationAPI. A stack nothing has
 * added to shows the three and no more, which is not a fault.
 *
 * <p>Held by container and slot number rather than by stack, because a stack is
 * a copy on this side and the slot is what the server can be asked about.
 */
public final class SlotEditorWindow extends DevWindow {
    private static final int HEADER_HEIGHT = 26;
    private static final int GAP = 4;

    private final NbtPanel panel = new NbtPanel();

    private int syncId;
    private int slotId;
    private String name = "";
    private ItemStack icon;

    public SlotEditorWindow(int syncId, int slotId, ItemStack stack) {
        super("Item", 248, 190);
        setTarget(syncId, slotId, stack);
    }

    public static void open(int syncId, int slotId, ItemStack stack) {
        ComposerScreen composer = ComposerScreen.instance();
        SlotEditorWindow window = composer.find(SlotEditorWindow.class);
        if (window == null) {
            composer.add(new SlotEditorWindow(syncId, slotId, stack));
        } else {
            window.setTarget(syncId, slotId, stack);
            composer.focus(window);
        }
        ComposerScreen.open();
    }

    public void setTarget(int syncId, int slotId, ItemStack stack) {
        this.syncId = syncId;
        this.slotId = slotId;
        this.name = nameOf(stack);
        // A copy, so that what the heading shows stays what was clicked even
        // once the slot holds something else.
        this.icon = stack == null ? null : stack.copy();

        setTitle("Item: " + name);
        panel.setTarget(NbtTarget.slot(syncId, slotId));
    }

    @Override
    public void tick() {
        panel.tick();
    }

    @Override
    protected void renderContent(Minecraft minecraft, int mouseX, int mouseY, float delta, boolean focused) {
        // The title bar already carries the name, so this says the part it
        // cannot: which slot of which container is being written to.
        int textX = contentX();
        if (icon != null) {
            ItemDraw.single(minecraft, icon, contentX(), contentY());
            textX = contentX() + ItemDraw.SIZE + GAP;
        }

        Draw.text(minecraft, "slot " + slotId + "   container " + syncId,
                textX, contentY() + 5, Theme.TEXT_FAINT);

        panel.render(minecraft, contentX(), contentY() + HEADER_HEIGHT, contentWidth(),
                contentHeight() - HEADER_HEIGHT, mouseX, mouseY);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        panel.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderOverlay(Minecraft minecraft, int mouseX, int mouseY) {
        panel.renderOverlay(minecraft, mouseX, mouseY);
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

    /** The stack's display name, or its translation key when it has none. */
    private static String nameOf(ItemStack stack) {
        if (stack == null) return "Empty";

        String key;
        try {
            key = stack.getTranslationKey();
        } catch (Exception error) {
            return "Item";
        }
        if (key == null) return "Item";

        String translated = I18n.getTranslation(key + ".name");
        return translated == null || translated.isEmpty() || translated.equals(key + ".name")
                ? key
                : translated;
    }
}
