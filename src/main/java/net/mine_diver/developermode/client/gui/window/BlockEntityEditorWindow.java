package net.mine_diver.developermode.client.gui.window;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.NbtPanel;
import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.client.gui.composer.DevWindow;
import net.mine_diver.developermode.feature.net.NbtTarget;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;

/**
 * A block entity's NBT.
 *
 * <p>Plainer than the entity editor, because a block entity gives it less to
 * work with: it does not move, so there is nothing to freeze and nothing to
 * drift, and there is no model to turn. What is left is where it is and what it
 * is holding.
 */
public final class BlockEntityEditorWindow extends DevWindow {
    private final NbtPanel panel = new NbtPanel();

    private int blockX;
    private int blockY;
    private int blockZ;

    public BlockEntityEditorWindow(int x, int y, int z) {
        super("Block entity", 248, 190);
        setTarget(x, y, z);
    }

    public static void open(int x, int y, int z) {
        ComposerScreen composer = ComposerScreen.instance();
        BlockEntityEditorWindow window = composer.find(BlockEntityEditorWindow.class);
        if (window == null) {
            composer.add(new BlockEntityEditorWindow(x, y, z));
        } else {
            window.setTarget(x, y, z);
            composer.focus(window);
        }
        ComposerScreen.open();
    }

    public void setTarget(int x, int y, int z) {
        blockX = x;
        blockY = y;
        blockZ = z;
        setTitle(name() + " " + x + " " + y + " " + z);
        panel.setTarget(NbtTarget.block(x, y, z));
    }

    @Override
    public void tick() {
        panel.tick();
    }

    @Override
    protected void renderContent(Minecraft minecraft, int mouseX, int mouseY, float delta, boolean focused) {
        // The title bar already says what and where. Everything below it is
        // the NBT, which is what the window is for.
        panel.render(minecraft, contentX(), contentY(), contentWidth(), contentHeight(), mouseX, mouseY);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
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

    /**
     * What to call it.
     *
     * <p>Read off the client's own copy, which exists for the block entities a
     * server bothers to send and not for the rest. The NBT in the panel came
     * from the world and is the authority on what this is; this is only a
     * heading.
     */
    private String name() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        if (minecraft == null || minecraft.world == null) return "Block entity";

        BlockEntity blockEntity = minecraft.world.getBlockEntity(blockX, blockY, blockZ);
        return blockEntity == null ? "Block entity" : blockEntity.getClass().getSimpleName();
    }
}
