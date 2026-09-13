package net.mine_diver.developermode.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Draws item stacks the way vanilla container screens do. Wrap a run of
 * {@link #stack} calls in {@link #begin()} and {@link #end()}, since the
 * lighting setup is the expensive part.
 */
public final class ItemDraw {
    public static final int SIZE = 16;

    private static final ItemRenderer RENDERER = new ItemRenderer();

    private ItemDraw() {}

    public static void begin() {
        GL11.glPushMatrix();
        GL11.glRotatef(120, 1, 0, 0);
        Lighting.turnOn();
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
    }

    public static void stack(Minecraft minecraft, ItemStack stack, int x, int y, boolean decorations) {
        if (stack == null) return;
        RENDERER.renderGuiItem(minecraft.textRenderer, minecraft.textureManager, stack, x, y);
        if (decorations) {
            RENDERER.renderGuiItemDecoration(minecraft.textRenderer, minecraft.textureManager, stack, x, y);
        }
    }

    public static void end() {
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        Lighting.turnOff();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** Convenience for one off icons. */
    public static void single(Minecraft minecraft, ItemStack stack, int x, int y) {
        begin();
        stack(minecraft, stack, x, y, false);
        end();
    }
}
