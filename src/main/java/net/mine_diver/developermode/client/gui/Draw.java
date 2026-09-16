package net.mine_diver.developermode.client.gui;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.util.ScreenScaler;
import org.lwjgl.opengl.GL11;

/**
 * Immediate mode drawing helpers, in GUI (scaled) coordinates.
 *
 * <p>Everything here leaves GL in the state vanilla screens expect between
 * draws: texturing on, blending off, alpha test on.
 */
public final class Draw {
    private static final char AMI_RARITY_PREFIX = '\u00D7';

    private static final int SCISSOR_DEPTH = 8;
    private static final int[] SCISSOR_STACK = new int[SCISSOR_DEPTH * 4];
    private static int scissorDepth;

    private Draw() {}

    public static void rect(double x1, double y1, double x2, double y2, int argb) {
        if (x1 > x2) { double t = x1; x1 = x2; x2 = t; }
        if (y1 > y2) { double t = y1; y1 = y2; y2 = t; }

        beginShapes(argb);
        Tessellator tessellator = Tessellator.INSTANCE;
        tessellator.startQuads();
        tessellator.vertex(x1, y2, 0);
        tessellator.vertex(x2, y2, 0);
        tessellator.vertex(x2, y1, 0);
        tessellator.vertex(x1, y1, 0);
        tessellator.draw();
        endShapes();
    }

    /**
     * A one pixel outline drawn just inside the given bounds.
     */
    public static void outline(double x, double y, double width, double height, int argb) {
        rect(x, y, x + width, y + 1, argb);
        rect(x, y + height - 1, x + width, y + height, argb);
        rect(x, y + 1, x + 1, y + height - 1, argb);
        rect(x + width - 1, y + 1, x + width, y + height - 1, argb);
    }

    /**
     * An annular sector. Angles are degrees clockwise from straight up, which
     * is how the radial menu thinks about directions.
     */
    public static void ring(double centerX, double centerY, double innerRadius, double outerRadius,
                            double fromDegrees, double toDegrees, int argb) {
        int steps = Math.max(2, (int) Math.ceil(Math.abs(toDegrees - fromDegrees) / 3));

        beginShapes(argb);
        Tessellator tessellator = Tessellator.INSTANCE;
        tessellator.start(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= steps; i++) {
            double radians = Math.toRadians(fromDegrees + (toDegrees - fromDegrees) * i / steps);
            double sin = Math.sin(radians);
            double cos = Math.cos(radians);
            tessellator.vertex(centerX + sin * outerRadius, centerY - cos * outerRadius, 0);
            tessellator.vertex(centerX + sin * innerRadius, centerY - cos * innerRadius, 0);
        }
        tessellator.draw();
        endShapes();
    }

    /**
     * A thick line between two points, as a rotated quad.
     */
    public static void line(double x1, double y1, double x2, double y2, double thickness, int argb) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length == 0) return;

        double offsetX = -dy / length * thickness / 2;
        double offsetY = dx / length * thickness / 2;

        beginShapes(argb);
        Tessellator tessellator = Tessellator.INSTANCE;
        tessellator.startQuads();
        tessellator.vertex(x1 - offsetX, y1 - offsetY, 0);
        tessellator.vertex(x2 - offsetX, y2 - offsetY, 0);
        tessellator.vertex(x2 + offsetX, y2 + offsetY, 0);
        tessellator.vertex(x1 + offsetX, y1 + offsetY, 0);
        tessellator.draw();
        endShapes();
    }

    /**
     * A close cross. Drawn rather than typed: AlwaysMoreItems claims the
     * multiplication sign as a control character and chokes on a string that
     * starts with one.
     */
    public static void cross(double centerX, double centerY, double radius, int argb) {
        line(centerX - radius, centerY - radius, centerX + radius, centerY + radius, 1.4, argb);
        line(centerX - radius, centerY + radius, centerX + radius, centerY - radius, 1.4, argb);
    }

    /**
     * A soft disc of light, brightest in the middle and fading out at the rim.
     *
     * <p>Built from concentric fans of one flat colour rather than a single fan
     * with interpolated vertex colours: same shape, but it only uses the draw
     * path everything else here already uses. Blended additively, so it reads
     * as light rather than paint. The alpha of {@code argb} is the brightness
     * at the very centre.
     */
    public static void glow(double centerX, double centerY, double radius, int argb) {
        int layers = 10;
        int steps = 24;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        float red = (argb >> 16 & 0xFF) / 255F;
        float green = (argb >> 8 & 0xFF) / 255F;
        float blue = (argb & 0xFF) / 255F;
        float layerAlpha = (argb >>> 24) / 255F / layers;

        Tessellator tessellator = Tessellator.INSTANCE;
        for (int layer = 0; layer < layers; layer++) {
            double layerRadius = radius * (1 - layer / (double) layers);
            GL11.glColor4f(red, green, blue, layerAlpha);
            tessellator.start(GL11.GL_TRIANGLE_FAN);
            tessellator.vertex(centerX, centerY, 0);
            for (int i = 0; i <= steps; i++) {
                double radians = Math.PI * 2 * i / steps;
                tessellator.vertex(
                        centerX + Math.sin(radians) * layerRadius,
                        centerY - Math.cos(radians) * layerRadius, 0);
            }
            tessellator.draw();
        }

        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /**
     * A solid triangle, used for the expand carets in the NBT tree.
     */
    public static void triangle(double x1, double y1, double x2, double y2, double x3, double y3, int argb) {
        beginShapes(argb);
        Tessellator tessellator = Tessellator.INSTANCE;
        tessellator.start(GL11.GL_TRIANGLES);
        tessellator.vertex(x1, y1, 0);
        tessellator.vertex(x2, y2, 0);
        tessellator.vertex(x3, y3, 0);
        tessellator.draw();
        endShapes();
    }

    /** A disclosure caret, pointing right when collapsed and down when open. */
    public static void caret(double x, double y, double size, boolean open, int argb) {
        if (open) {
            triangle(x, y, x + size, y, x + size / 2, y + size * 0.8, argb);
        } else {
            triangle(x, y, x, y + size, x + size * 0.8, y + size / 2, argb);
        }
    }

    public static void text(Minecraft minecraft, String text, int x, int y, int argb) {
        minecraft.textRenderer.drawWithShadow(sanitize(text), x, y, argb);
    }

    public static void textCentered(Minecraft minecraft, String text, int centerX, int y, int argb) {
        String safe = sanitize(text);
        minecraft.textRenderer.drawWithShadow(safe, centerX - minecraft.textRenderer.getWidth(safe) / 2, y, argb);
    }

    public static int textWidth(Minecraft minecraft, String text) {
        return minecraft.textRenderer.getWidth(sanitize(text));
    }

    /**
     * AlwaysMoreItems claims the multiplication sign as a rarity marker and,
     * on any string that starts with one, runs an unguarded substring(2) that
     * throws on short strings. Item names come from the translation table, so
     * drop a leading one rather than trust every mod's naming.
     */
    private static String sanitize(String text) {
        return text != null && !text.isEmpty() && text.charAt(0) == AMI_RARITY_PREFIX ? text.substring(1) : text;
    }

    /**
     * Truncates with an ellipsis so the result fits in {@code maxWidth}.
     */
    public static String ellipsize(Minecraft minecraft, String text, int maxWidth) {
        text = sanitize(text);
        if (minecraft.textRenderer.getWidth(text) <= maxWidth) return text;
        String truncated = text;
        while (!truncated.isEmpty() && minecraft.textRenderer.getWidth(truncated + "...") > maxWidth) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + "...";
    }

    /**
     * Clips drawing to the given GUI space rectangle until the matching
     * {@link #popScissor()}. Nested pushes intersect with the enclosing clip.
     */
    public static void pushScissor(Minecraft minecraft, int x, int y, int width, int height) {
        if (scissorDepth > 0) {
            int base = (scissorDepth - 1) * 4;
            int parentX = SCISSOR_STACK[base];
            int parentY = SCISSOR_STACK[base + 1];
            int parentRight = parentX + SCISSOR_STACK[base + 2];
            int parentBottom = parentY + SCISSOR_STACK[base + 3];
            int right = Math.min(x + width, parentRight);
            int bottom = Math.min(y + height, parentBottom);
            x = Math.max(x, parentX);
            y = Math.max(y, parentY);
            width = right - x;
            height = bottom - y;
        }
        width = Math.max(0, width);
        height = Math.max(0, height);

        if (scissorDepth < SCISSOR_DEPTH) {
            int base = scissorDepth * 4;
            SCISSOR_STACK[base] = x;
            SCISSOR_STACK[base + 1] = y;
            SCISSOR_STACK[base + 2] = width;
            SCISSOR_STACK[base + 3] = height;
        }
        scissorDepth++;

        applyScissor(minecraft, x, y, width, height);
    }

    /** Drops any clip still on the stack. Called once per frame as a safety net. */
    public static void resetScissor() {
        if (scissorDepth == 0) return;
        scissorDepth = 0;
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static void popScissor() {
        scissorDepth = Math.max(0, scissorDepth - 1);
        if (scissorDepth == 0) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            int base = (scissorDepth - 1) * 4;
            applyScissor(DeveloperModeClient.minecraft(), SCISSOR_STACK[base], SCISSOR_STACK[base + 1],
                    SCISSOR_STACK[base + 2], SCISSOR_STACK[base + 3]);
        }
    }

    private static void applyScissor(Minecraft minecraft, int x, int y, int width, int height) {
        int scale = scaleFactor(minecraft);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, minecraft.displayHeight - (y + height) * scale, width * scale, height * scale);
    }

    public static int scaleFactor(Minecraft minecraft) {
        return new ScreenScaler(minecraft.options, minecraft.displayWidth, minecraft.displayHeight).scaleFactor;
    }

    private static void beginShapes(int argb) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        // Culling is left on by the world render, and a triangle strip's
        // winding depends on which way round the arc is swept.
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(
                (argb >> 16 & 0xFF) / 255F,
                (argb >> 8 & 0xFF) / 255F,
                (argb & 0xFF) / 255F,
                (argb >>> 24) / 255F);
    }

    private static void endShapes() {
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
