package net.mine_diver.developermode.client.summon;

import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.util.ScreenScaler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * The ghost standing where the next summon will land, and its readout.
 */
public final class SummonRenderer {
    private static final int GROUNDED_COLOR = 0xFF4EC9B0;
    private static final int FLOATING_COLOR = 0xFFD8A657;

    private SummonRenderer() {}

    public static void renderWorld(float tickDelta) {
        if (!SummonMode.isActive()) return;

        Minecraft minecraft = Minecraft.INSTANCE;
        Entity preview = SummonMode.preview();
        LivingEntity camera = minecraft.camera;
        if (preview == null || camera == null) return;

        double cameraX = camera.lastTickX + (camera.x - camera.lastTickX) * tickDelta;
        double cameraY = camera.lastTickY + (camera.y - camera.lastTickY) * tickDelta;
        double cameraZ = camera.lastTickZ + (camera.z - camera.lastTickZ) * tickDelta;

        // The model, lit like the world's own entities. It is never ticked, so
        // it stands perfectly still, which is most of what makes it read as a
        // placeholder rather than something already spawned.
        Lighting.turnOn();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);

        // The picker already refuses types whose icon would not draw, so this
        // should never fire. It is guarded anyway because a throw here is in
        // the world render, once a frame, with no way out but quitting.
        int stackDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        try {
            EntityRenderDispatcher.INSTANCE.render(preview,
                    preview.x - cameraX, preview.y - cameraY, preview.z - cameraZ,
                    preview.yaw, tickDelta);
        } catch (Throwable error) {
            for (int depth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH); depth > stackDepth; depth--) {
                GL11.glPopMatrix();
            }
            SummonMode.exit();
        }

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        Lighting.turnOff();

        // The rest of what makes it read as a placeholder.
        WorldDraw.beginLines(true);
        GL11.glLineWidth(2);
        WorldDraw.color(SummonMode.isGrounded() ? GROUNDED_COLOR : FLOATING_COLOR);
        WorldDraw.outline(preview.boundingBox.expand(0.03, 0.03, 0.03)
                .offset(-cameraX, -cameraY, -cameraZ));
        WorldDraw.endLines();
    }

    public static void renderHud(Minecraft minecraft) {
        if (!SummonMode.isActive() || minecraft.currentScreen != null) return;

        ScreenScaler scaler = new ScreenScaler(minecraft.options, minecraft.displayWidth, minecraft.displayHeight);
        int width = scaler.getScaledWidth();
        int height = scaler.getScaledHeight();

        String title = "Summoning " + SummonMode.type();
        String detail = !SummonMode.error().isEmpty()
                ? SummonMode.error()
                : SummonMode.isGrounded() ? "on a surface" : "in mid air";
        String help = "left click to place    right click to stop"
                + (SummonMode.placed() > 0 ? "    " + SummonMode.placed() + " placed" : "");

        int panelWidth = Math.max(Math.max(Draw.textWidth(minecraft, title), Draw.textWidth(minecraft, detail)),
                Draw.textWidth(minecraft, help)) + 10;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 + 14;

        Draw.rect(panelX, panelY, panelX + panelWidth, panelY + 36, Theme.PANEL);
        Draw.outline(panelX, panelY, panelWidth, 36, Theme.BORDER_FOCUSED);

        Draw.textCentered(minecraft, title, width / 2, panelY + 4, Theme.ACCENT);
        Draw.textCentered(minecraft, detail, width / 2, panelY + 14,
                SummonMode.error().isEmpty() ? Theme.TEXT_DIM : Theme.DANGER);
        Draw.textCentered(minecraft, help, width / 2, panelY + 25, Theme.TEXT_FAINT);
    }
}
