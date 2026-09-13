package net.mine_diver.developermode.client.gui;

import net.minecraft.client.render.Tessellator;
import net.minecraft.util.math.Box;
import org.lwjgl.opengl.GL11;

/**
 * Drawing in world space, from inside the world render.
 *
 * <p>Boxes are expected already offset by the camera position, the way
 * vanilla's block outline does it.
 */
public final class WorldDraw {
    private WorldDraw() {}

    /** Sets up state for wireframes. Pair with {@link #endLines()}. */
    public static void beginLines(boolean throughWalls) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        if (throughWalls) GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    public static void endLines() {
        GL11.glLineWidth(1);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void color(int argb) {
        GL11.glColor4f(
                (argb >> 16 & 0xFF) / 255F,
                (argb >> 8 & 0xFF) / 255F,
                (argb & 0xFF) / 255F,
                (argb >>> 24) / 255F);
    }

    /** Vanilla's block outline wireframe, for any box. */
    public static void outline(Box box) {
        Tessellator tessellator = Tessellator.INSTANCE;

        tessellator.start(GL11.GL_LINE_STRIP);
        tessellator.vertex(box.minX, box.minY, box.minZ);
        tessellator.vertex(box.maxX, box.minY, box.minZ);
        tessellator.vertex(box.maxX, box.minY, box.maxZ);
        tessellator.vertex(box.minX, box.minY, box.maxZ);
        tessellator.vertex(box.minX, box.minY, box.minZ);
        tessellator.draw();

        tessellator.start(GL11.GL_LINE_STRIP);
        tessellator.vertex(box.minX, box.maxY, box.minZ);
        tessellator.vertex(box.maxX, box.maxY, box.minZ);
        tessellator.vertex(box.maxX, box.maxY, box.maxZ);
        tessellator.vertex(box.minX, box.maxY, box.maxZ);
        tessellator.vertex(box.minX, box.maxY, box.minZ);
        tessellator.draw();

        tessellator.start(GL11.GL_LINES);
        tessellator.vertex(box.minX, box.minY, box.minZ);
        tessellator.vertex(box.minX, box.maxY, box.minZ);
        tessellator.vertex(box.maxX, box.minY, box.minZ);
        tessellator.vertex(box.maxX, box.maxY, box.minZ);
        tessellator.vertex(box.maxX, box.minY, box.maxZ);
        tessellator.vertex(box.maxX, box.maxY, box.maxZ);
        tessellator.vertex(box.minX, box.minY, box.maxZ);
        tessellator.vertex(box.minX, box.maxY, box.maxZ);
        tessellator.draw();
    }
}
