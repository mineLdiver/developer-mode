package net.mine_diver.developermode.client.tool;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.Lang;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.WorldDraw;
import net.mine_diver.developermode.feature.player.Warp;
import net.mine_diver.developermode.feature.world.Blast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ScreenScaler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

/**
 * What an armed tool would do, drawn where it would do it, and a readout that
 * stays on screen for as long as it is armed.
 *
 * <p>Each tool has a color of its own, on its mark in the world and on its
 * name in the readout, so a glance tells you which one is in hand.
 */
public final class ToolRenderer {
    private static final int WARP_COLOR = Theme.ACCENT;
    private static final int GROW_COLOR = Theme.NBT_STRING;
    private static final int BLAST_COLOR = Theme.DANGER;
    private static final int SMITE_COLOR = Theme.NBT_NUMBER;

    /** How far above the strike the bolt is drawn, which is far enough to read as coming down from the sky. */
    private static final double BOLT_HEIGHT = 24;
    /** Half the width of the mark lightning leaves where it lands. */
    private static final double SCORCH = 0.6;
    /** Clear of the surface, so a mark lying on it does not flicker into it. */
    private static final double LIFT = 0.02;

    private ToolRenderer() {}

    public static void renderWorld(float tickDelta) {
        Tool tool = ToolMode.armed();
        HitResult target = ToolMode.target();
        Minecraft minecraft = DeveloperModeClient.minecraft();
        if (tool == null || target == null || minecraft == null || minecraft.currentScreen != null) return;

        LivingEntity camera = minecraft.camera;
        if (camera == null) return;
        double cameraX = camera.lastTickX + (camera.x - camera.lastTickX) * tickDelta;
        double cameraY = camera.lastTickY + (camera.y - camera.lastTickY) * tickDelta;
        double cameraZ = camera.lastTickZ + (camera.z - camera.lastTickZ) * tickDelta;

        WorldDraw.beginLines(true);
        GL11.glLineWidth(2);
        WorldDraw.color(color(tool));

        Box block = Box.create(target.blockX, target.blockY, target.blockZ,
                target.blockX + 1, target.blockY + 1, target.blockZ + 1)
                .expand(0.01, 0.01, 0.01)
                .offset(-cameraX, -cameraY, -cameraZ);
        double x = target.pos.x - cameraX;
        double y = target.pos.y - cameraY;
        double z = target.pos.z - cameraZ;

        switch (tool) {
            case WARP -> {
                // A ghost of you, standing exactly where you would.
                Box you = minecraft.player.boundingBox;
                Vec3d feet = Warp.landing(you, target);
                double halfWidth = (you.maxX - you.minX) / 2;
                WorldDraw.outline(Box.create(
                        feet.x - halfWidth, feet.y, feet.z - halfWidth,
                        feet.x + halfWidth, feet.y + (you.maxY - you.minY), feet.z + halfWidth)
                        .offset(-cameraX, -cameraY, -cameraZ));
            }
            case GROW -> WorldDraw.outline(block);
            case BLAST -> {
                WorldDraw.outline(block);
                WorldDraw.sphere(x, y, z, Blast.REACH);
            }
            case SMITE -> {
                WorldDraw.line(x, y, z, x, y + BOLT_HEIGHT, z);
                WorldDraw.outline(Box.create(
                        x - SCORCH, y + LIFT, z - SCORCH,
                        x + SCORCH, y + LIFT, z + SCORCH));
            }
        }

        WorldDraw.endLines();
    }

    public static void renderHud(Minecraft minecraft) {
        Tool tool = ToolMode.armed();
        if (tool == null || minecraft.currentScreen != null) return;

        ScreenScaler scaler = new ScreenScaler(minecraft.options, minecraft.displayWidth, minecraft.displayHeight);
        int width = scaler.getScaledWidth();
        int height = scaler.getScaledHeight();

        HitResult target = ToolMode.target();
        String title = Lang.get("gui.developermode.armed", tool.label());
        String detail = !ToolMode.error().isEmpty()
                ? ToolMode.error()
                : target == null
                        ? Lang.get("gui.developermode.armed.no_target")
                        : Lang.get("gui.developermode.armed.target", target.blockX, target.blockY, target.blockZ);
        String help = ToolMode.used() > 0
                ? Lang.get("gui.developermode.armed.help.used", tool.verb(), tool.count(ToolMode.used()))
                : Lang.get("gui.developermode.armed.help", tool.verb());

        int panelWidth = Math.max(Math.max(Draw.textWidth(minecraft, title), Draw.textWidth(minecraft, detail)),
                Draw.textWidth(minecraft, help)) + 10;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 + 14;

        Draw.rect(panelX, panelY, panelX + panelWidth, panelY + 36, Theme.PANEL);
        Draw.outline(panelX, panelY, panelWidth, 36, color(tool));

        Draw.textCentered(minecraft, title, width / 2, panelY + 4, color(tool));
        Draw.textCentered(minecraft, detail, width / 2, panelY + 14,
                ToolMode.error().isEmpty() ? Theme.TEXT_DIM : Theme.DANGER);
        Draw.textCentered(minecraft, help, width / 2, panelY + 25, Theme.TEXT_FAINT);
    }

    private static int color(Tool tool) {
        return switch (tool) {
            case WARP -> WARP_COLOR;
            case GROW -> GROW_COLOR;
            case BLAST -> BLAST_COLOR;
            case SMITE -> SMITE_COLOR;
        };
    }
}
