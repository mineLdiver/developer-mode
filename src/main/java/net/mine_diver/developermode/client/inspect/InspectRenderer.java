package net.mine_diver.developermode.client.inspect;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.WorldDraw;
import net.mine_diver.developermode.feature.entity.Entities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ScreenScaler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * The two halves of what inspect mode looks like: wireframes out in the world,
 * and a readout under the crosshair.
 */
public final class InspectRenderer {
    private static final int FOCUSED_COLOR = 0xFF4EC9B0;
    private static final int CANDIDATE_COLOR = 0x593FA893;
    private static final float GROW = 0.03F;

    private InspectRenderer() {}

    /**
     * Called from inside the world render, with the camera matrices still set,
     * so boxes are given in world space relative to the camera.
     */
    public static void renderWorld(float tickDelta) {
        if (!InspectMode.isActive() || InspectMode.candidates().isEmpty()) return;

        Minecraft minecraft = DeveloperModeClient.minecraft();
        LivingEntity camera = minecraft.camera;
        if (camera == null) return;

        double cameraX = camera.lastTickX + (camera.x - camera.lastTickX) * tickDelta;
        double cameraY = camera.lastTickY + (camera.y - camera.lastTickY) * tickDelta;
        double cameraZ = camera.lastTickZ + (camera.z - camera.lastTickZ) * tickDelta;

        // Through walls on purpose. Half of what you want to inspect is behind
        // something, and an inspector that hides things is not much of one.
        WorldDraw.beginLines(true);

        Entity focused = InspectMode.focused();
        for (Entity entity : InspectMode.candidates()) {
            boolean isFocused = entity == focused;
            GL11.glLineWidth(isFocused ? 2.5F : 1);
            WorldDraw.color(isFocused ? FOCUSED_COLOR : CANDIDATE_COLOR);

            double interpolatedX = entity.lastTickX + (entity.x - entity.lastTickX) * tickDelta - entity.x;
            double interpolatedY = entity.lastTickY + (entity.y - entity.lastTickY) * tickDelta - entity.y;
            double interpolatedZ = entity.lastTickZ + (entity.z - entity.lastTickZ) * tickDelta - entity.z;

            WorldDraw.outline(entity.boundingBox
                    .expand(GROW, GROW, GROW)
                    .offset(interpolatedX - cameraX, interpolatedY - cameraY, interpolatedZ - cameraZ));
        }

        WorldDraw.endLines();
    }

    /**
     * Called after the HUD, in scaled GUI space.
     */
    public static void renderHud(Minecraft minecraft) {
        if (!InspectMode.isActive() || minecraft.currentScreen != null) return;

        ScreenScaler scaler = new ScreenScaler(minecraft.options, minecraft.displayWidth, minecraft.displayHeight);
        int width = scaler.getScaledWidth();
        int height = scaler.getScaledHeight();

        Entity focused = InspectMode.focused();
        String title = focused == null ? "Nothing under the crosshair" : Entities.name(focused);
        String detail = focused == null
                ? InspectMode.candidates().size() + " in range"
                : String.format("%.1fm   id %d", distanceTo(minecraft, focused), focused.id);
        String help = focused == null ? "right click to cancel" : "left click to edit    right click to cancel";

        int panelWidth = Math.max(Math.max(Draw.textWidth(minecraft, title), Draw.textWidth(minecraft, detail)),
                Draw.textWidth(minecraft, help)) + 10;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 + 14;

        Draw.rect(panelX, panelY, panelX + panelWidth, panelY + 36, Theme.PANEL);
        Draw.outline(panelX, panelY, panelWidth, 36, focused == null ? Theme.BORDER : Theme.BORDER_FOCUSED);

        Draw.textCentered(minecraft, title, width / 2, panelY + 4, focused == null ? Theme.TEXT_DIM : Theme.ACCENT);
        Draw.textCentered(minecraft, detail, width / 2, panelY + 14, Theme.TEXT_DIM);
        Draw.textCentered(minecraft, help, width / 2, panelY + 25, Theme.TEXT_FAINT);

        String mode = "inspecting   " + Keyboard.getKeyName(DeveloperModeClient.INSPECT_KEY.code);
        Draw.text(minecraft, mode, 4, 4, Theme.ACCENT);
    }

    private static double distanceTo(Minecraft minecraft, Entity entity) {
        double dx = minecraft.player.x - entity.x;
        double dy = minecraft.player.y - entity.y;
        double dz = minecraft.player.z - entity.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
