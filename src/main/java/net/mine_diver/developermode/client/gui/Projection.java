package net.mine_diver.developermode.client.gui;

import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Turns a pixel on the screen back into a direction through the world.
 *
 * <p>Needed because a pointer picks things, and a pointer is somewhere other
 * than the middle of the screen. The camera's own look vector only answers for
 * the crosshair.
 *
 * <p>The matrices are read from the world render rather than worked out from
 * the field of view, since the projection the world was actually drawn with is
 * the only one whose answers line up with what is on screen.
 *
 * <p>Points come back relative to the camera, which is the space the world is
 * drawn in: add the camera's own position to get somewhere in the world.
 */
public final class Projection {
    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer UNPROJECTED = BufferUtils.createFloatBuffer(3);

    private static boolean captured;

    private Projection() {}

    /** Called from inside the world render, while the matrices are the world's. */
    public static void capture() {
        MODELVIEW.clear();
        PROJECTION.clear();
        VIEWPORT.clear();

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
        captured = true;
    }

    /**
     * A point on the ray through a window pixel.
     *
     * @param windowX window pixels from the left, as the mouse reports them
     * @param windowY window pixels from the bottom, as the mouse reports them
     * @param depth   zero at the near plane, one at the far plane
     * @return the point relative to the camera, or null before the world has
     *         been drawn even once
     */
    public static Vec3d at(int windowX, int windowY, float depth) {
        if (!captured) return null;

        UNPROJECTED.clear();
        if (!GLU.gluUnProject(windowX, windowY, depth, MODELVIEW, PROJECTION, VIEWPORT, UNPROJECTED)) {
            return null;
        }
        return Vec3d.create(UNPROJECTED.get(0), UNPROJECTED.get(1), UNPROJECTED.get(2));
    }
}
