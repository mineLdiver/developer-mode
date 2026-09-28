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
    /** Enough that a ball a few blocks across still looks round. */
    private static final int SPHERE_SEGMENTS = 48;

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

    public static void line(double x1, double y1, double z1, double x2, double y2, double z2) {
        Tessellator tessellator = Tessellator.INSTANCE;
        tessellator.start(GL11.GL_LINES);
        tessellator.vertex(x1, y1, z1);
        tessellator.vertex(x2, y2, z2);
        tessellator.draw();
    }

    /**
     * Three great circles, one around each axis, which reads as a ball from
     * any side without hiding what is inside it.
     */
    public static void sphere(double x, double y, double z, double radius) {
        Tessellator tessellator = Tessellator.INSTANCE;
        for (int axis = 0; axis < 3; axis++) {
            tessellator.start(GL11.GL_LINE_LOOP);
            for (int step = 0; step < SPHERE_SEGMENTS; step++) {
                double angle = step * 2 * Math.PI / SPHERE_SEGMENTS;
                double around = Math.cos(angle) * radius;
                double across = Math.sin(angle) * radius;
                switch (axis) {
                    case 0 -> tessellator.vertex(x, y + around, z + across);
                    case 1 -> tessellator.vertex(x + around, y, z + across);
                    default -> tessellator.vertex(x + around, y + across, z);
                }
            }
            tessellator.draw();
        }
    }
}
