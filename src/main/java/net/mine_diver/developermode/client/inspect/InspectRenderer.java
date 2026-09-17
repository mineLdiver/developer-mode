package net.mine_diver.developermode.client.inspect;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.WorldDraw;
import net.mine_diver.developermode.feature.entity.Entities;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
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
        if (!InspectMode.isActive()) return;
        if (InspectMode.candidates().isEmpty() && !InspectMode.isBlockFocused()) return;

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

        if (InspectMode.isBlockFocused()) {
            GL11.glLineWidth(2.5F);
            WorldDraw.color(FOCUSED_COLOR);
            WorldDraw.outline(Box.create(
                            InspectMode.blockX(), InspectMode.blockY(), InspectMode.blockZ(),
                            InspectMode.blockX() + 1, InspectMode.blockY() + 1, InspectMode.blockZ() + 1)
                    .expand(GROW, GROW, GROW)
                    .offset(-cameraX, -cameraY, -cameraZ));
        }

        WorldDraw.endLines();
    }

    /**
     * The readout under the pointer, drawn by the screen that owns it.
     */
    public static void renderReadout(Minecraft minecraft, int width, int height) {
        if (!InspectMode.isActive()) return;

        Entity focused = InspectMode.focused();
        boolean block = InspectMode.isBlockFocused();
        boolean anything = block || focused != null;

        String title = block ? blockName(minecraft)
                : focused == null ? "Nothing under the crosshair" : Entities.name(focused);
        String detail = block
                ? InspectMode.blockX() + " " + InspectMode.blockY() + " " + InspectMode.blockZ()
                : focused == null
                        ? InspectMode.candidates().size() + " in range"
                        : String.format("%.1fm   id %d", distanceTo(minecraft, focused), focused.id);
        String help = anything ? "left click to edit    right click to cancel" : "right click to cancel";

        int panelWidth = Math.max(Math.max(Draw.textWidth(minecraft, title), Draw.textWidth(minecraft, detail)),
                Draw.textWidth(minecraft, help)) + 10;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 + 14;

        Draw.rect(panelX, panelY, panelX + panelWidth, panelY + 36, Theme.PANEL);
        Draw.outline(panelX, panelY, panelWidth, 36, anything ? Theme.BORDER_FOCUSED : Theme.BORDER);

        Draw.textCentered(minecraft, title, width / 2, panelY + 4, anything ? Theme.ACCENT : Theme.TEXT_DIM);
        Draw.textCentered(minecraft, detail, width / 2, panelY + 14, Theme.TEXT_DIM);
        Draw.textCentered(minecraft, help, width / 2, panelY + 25, Theme.TEXT_FAINT);

        String mode = "inspecting   " + Keyboard.getKeyName(DeveloperModeClient.INSPECT_KEY.code);
        Draw.text(minecraft, mode, 4, 4, Theme.ACCENT);
    }

    /** What the client calls the block entity it is looking at, if it has one. */
    private static String blockName(Minecraft minecraft) {
        if (minecraft.world == null) return "Block entity";

        var blockEntity = minecraft.world.getBlockEntity(
                InspectMode.blockX(), InspectMode.blockY(), InspectMode.blockZ());
        return blockEntity == null ? "Block entity" : blockEntity.getClass().getSimpleName();
    }

    private static double distanceTo(Minecraft minecraft, Entity entity) {
        double dx = minecraft.player.x - entity.x;
        double dy = minecraft.player.y - entity.y;
        double dz = minecraft.player.z - entity.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
