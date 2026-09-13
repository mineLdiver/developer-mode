package net.mine_diver.developermode.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Renders a live entity inside a rectangle of the GUI.
 *
 * <p>The transform sequence is vanilla's, from the inventory screen's player
 * render, with the fixed scale swapped for one derived from the entity's own
 * height so a chicken and a ghast both end up framed. {@link #ANCHOR} and
 * {@link #FILL} are the two numbers to reach for if the framing is off.
 */
public final class EntityPreview {
    /** Where the entity's feet sit in the box, as a fraction of its height. */
    private static final float ANCHOR = 0.86F;
    /** How much of the box height the entity should fill. */
    private static final float FILL = 0.62F;
    /** Stops very small entities being blown up to fill the frame. */
    private static final float MAX_SCALE = 45;

    private EntityPreview() {}

    /**
     * @return false if the entity's renderer threw, which means this type
     *         cannot be drawn from a bare construction and should not be
     *         offered for summoning either
     */
    public static boolean render(Minecraft minecraft, Entity entity, int x, int y, int width, int height, float turntable) {
        if (entity == null) return false;

        // Own the clip: the depth clear below would otherwise wipe the whole
        // buffer when this is called outside a window, as the radial does.
        Draw.pushScissor(minecraft, x, y, width, height);

        float scale = Math.min(MAX_SCALE, height * FILL / Math.max(0.5F, entity.height));
        int anchorX = x + width / 2;
        int anchorY = y + Math.round(height * ANCHOR);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);

        GL11.glPushMatrix();
        GL11.glTranslatef(anchorX, anchorY, 50);
        GL11.glScalef(-scale, scale, scale);
        GL11.glRotatef(180, 0, 0, 1);
        GL11.glRotatef(135, 0, 1, 0);
        Lighting.turnOn();
        GL11.glRotatef(-135, 0, 1, 0);
        GL11.glRotatef(turntable, 0, 1, 0);
        GL11.glTranslatef(0, entity.standingEyeHeight, 0);

        // Light the model fully rather than at whatever the world is doing,
        // so an entity in a cave is still legible. Restored immediately.
        float brightness = entity.minBrightness;
        entity.minBrightness = 1;
        EntityRenderDispatcher.INSTANCE.yaw = 180;

        // This draws whatever the registry holds, including modded entities
        // built without the state their renderer assumes. One of those must not
        // take the game down over a preview icon.
        boolean rendered = true;
        int stackDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        try {
            EntityRenderDispatcher.INSTANCE.render(entity, 0, 0, 0, entity.yaw, 1);
        } catch (Throwable error) {
            rendered = false;
            // It may have died between its own push and pop, which would leave
            // every later draw transformed by whatever it had set up.
            for (int depth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH); depth > stackDepth; depth--) {
                GL11.glPopMatrix();
            }
        }

        entity.minBrightness = brightness;

        GL11.glPopMatrix();

        Lighting.turnOff();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);
        GL11.glDisable(GL11.GL_LIGHTING);
        // The model wrote depth in the middle of a 2D screen. Clearing is safe
        // because the scissor pushed above keeps it inside this box.
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1, 1, 1, 1);

        Draw.popScissor();
        return rendered;
    }
}
