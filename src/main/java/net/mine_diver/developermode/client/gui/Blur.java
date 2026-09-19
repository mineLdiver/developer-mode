package net.mine_diver.developermode.client.gui;

import net.mine_diver.developermode.DeveloperMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;

/**
 * Blurs whatever is already in the back buffer, in place.
 *
 * <p>Beta runs on a fixed function context, so there are no shaders and no
 * framebuffer objects to lean on. Instead the frame is copied into a texture
 * and repeatedly halved with bilinear filtering, then smeared a few times at
 * that low resolution and stretched back over the screen. Each step samples
 * the texture and draws into the back buffer, then copies the result back into
 * the texture, so only one texture is ever needed.
 *
 * <p>Call this at the very start of a screen's render, before anything the
 * screen wants to stay sharp.
 *
 * <p>A screen that wants a sharp window through the blur asks for the frame to
 * be kept, draws whatever it dims the background with, and then punches the
 * window. The hole is stamped last because it has to come back over the dimming
 * as well as over the blur, which is the whole point of it.
 */
public final class Blur {
    /** Halvings before smearing. Each one doubles the effective radius. */
    private static final int DOWNSAMPLES = 4;
    /** Half texel smears at the smallest size. Keep even, so they cancel out. */
    private static final int SMEAR_PASSES = 4;
    /** Never shrink below this, or large radii turn into visible blockiness. */
    private static final int MIN_SIZE = 16;

    /** Steps across the soft edge of a punched hole. */
    private static final int FEATHER_STEPS = 6;

    private static int texture = -1;
    private static int sharpTexture = -1;
    private static int textureWidth;
    private static int textureHeight;
    private static boolean unsupported;
    private static boolean sharpHeld;

    private Blur() {}

    public static void render(Minecraft minecraft) {
        render(minecraft, false);
    }

    /**
     * @param keepSharp take a copy of the frame first, so {@link #punch} can
     *                  put part of it back afterwards
     */
    public static void render(Minecraft minecraft, boolean keepSharp) {
        if (unsupported) return;
        sharpHeld = false;

        int frameWidth = minecraft.displayWidth;
        int frameHeight = minecraft.displayHeight;
        if (frameWidth <= 0 || frameHeight <= 0) return;

        try {
            allocate(frameWidth, frameHeight);
        } catch (Throwable error) {
            DeveloperMode.LOGGER.warn("Background blur unavailable, falling back to a flat scrim.", error);
            unsupported = true;
            return;
        }

        // Taken before the passes below, which draw their working copies into
        // the frame and would otherwise be what got kept.
        if (keepSharp) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sharpTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, frameWidth, frameHeight);
            sharpHeld = true;
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, frameWidth, frameHeight);

        beginFramePass(frameWidth, frameHeight);

        int width = frameWidth;
        int height = frameHeight;
        for (int i = 0; i < DOWNSAMPLES; i++) {
            int nextWidth = Math.max(MIN_SIZE, width / 2);
            int nextHeight = Math.max(MIN_SIZE, height / 2);
            if (nextWidth == width && nextHeight == height) break;
            blit(0, 0, width, height, 0, 0, nextWidth, nextHeight);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, nextWidth, nextHeight);
            width = nextWidth;
            height = nextHeight;
        }

        // Redrawing at the same size but sampling half a texel off makes the
        // bilinear filter average neighbors, which widens the blur without
        // shrinking the image further. The direction alternates so the passes
        // cancel out instead of walking the picture across the screen.
        for (int i = 0; i < SMEAR_PASSES; i++) {
            float offset = (0.5F + i / 2) * (i % 2 == 0 ? 1 : -1);
            blit(offset, offset, width, height, 0, 0, width, height);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        }

        blit(0, 0, width, height, 0, 0, frameWidth, frameHeight);

        endFramePass();
    }

    /**
     * Puts the middle of the kept frame back, as a disc with a soft edge, over
     * everything drawn since.
     *
     * <p>Radii are in GUI pixels, like the screen that asks for them.
     */
    public static void punch(Minecraft minecraft, float radius, float feather) {
        if (unsupported || !sharpHeld || radius <= 0) return;

        int frameWidth = minecraft.displayWidth;
        int frameHeight = minecraft.displayHeight;
        float scale = Draw.scaleFactor(minecraft);
        float centerX = frameWidth / 2F;
        float centerY = frameHeight / 2F;

        beginFramePass(frameWidth, frameHeight);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sharpTexture);

        float inner = radius * scale;
        float outer = inner + feather * scale;

        GL11.glColor4f(1, 1, 1, 1);
        fan(centerX, centerY, inner);

        // Flat shaded rings rather than one fan with interpolated alpha: same
        // gradient, and it stays on the one draw path this class already uses.
        for (int step = 0; step < FEATHER_STEPS; step++) {
            float from = inner + (outer - inner) * step / FEATHER_STEPS;
            float to = inner + (outer - inner) * (step + 1) / FEATHER_STEPS;
            GL11.glColor4f(1, 1, 1, 1 - (step + 0.5F) / FEATHER_STEPS);
            band(centerX, centerY, from, to);
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
        endFramePass();
    }

    /**
     * Drops the capture textures. Worth doing when the UI closes so an idle
     * session is not sitting on screen sized textures.
     */
    public static void release() {
        if (texture != -1) {
            GL11.glDeleteTextures(texture);
            texture = -1;
        }
        if (sharpTexture != -1) {
            GL11.glDeleteTextures(sharpTexture);
            sharpTexture = -1;
        }
        sharpHeld = false;
        textureWidth = 0;
        textureHeight = 0;
    }

    private static void fan(float centerX, float centerY, float radius) {
        int steps = 48;
        Tessellator tessellator = Tessellator.INSTANCE;
        tessellator.start(GL11.GL_TRIANGLE_FAN);
        vertexAt(tessellator, centerX, centerY);
        for (int i = 0; i <= steps; i++) {
            double radians = Math.PI * 2 * i / steps;
            vertexAt(tessellator,
                    centerX + (float) Math.sin(radians) * radius,
                    centerY + (float) Math.cos(radians) * radius);
        }
        tessellator.draw();
    }

    private static void band(float centerX, float centerY, float from, float to) {
        int steps = 48;
        Tessellator tessellator = Tessellator.INSTANCE;
        tessellator.start(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= steps; i++) {
            double radians = Math.PI * 2 * i / steps;
            float sin = (float) Math.sin(radians);
            float cos = (float) Math.cos(radians);
            vertexAt(tessellator, centerX + sin * to, centerY + cos * to);
            vertexAt(tessellator, centerX + sin * from, centerY + cos * from);
        }
        tessellator.draw();
    }

    /** The kept frame is the same size as the frame, so the pixel is the texel. */
    private static void vertexAt(Tessellator tessellator, float x, float y) {
        tessellator.vertex(x, y, 0, x / textureWidth, y / textureHeight);
    }

    private static void allocate(int frameWidth, int frameHeight) {
        if (texture != -1 && textureWidth >= frameWidth && textureHeight >= frameHeight) return;

        release();

        boolean npot = GLContext.getCapabilities().GL_ARB_texture_non_power_of_two;
        textureWidth = npot ? frameWidth : ceilPowerOfTwo(frameWidth);
        textureHeight = npot ? frameHeight : ceilPowerOfTwo(frameHeight);

        texture = blankTexture();
        sharpTexture = blankTexture();
    }

    private static int blankTexture() {
        int name = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, name);
        ByteBuffer blank = BufferUtils.createByteBuffer(textureWidth * textureHeight * 4);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, textureWidth, textureHeight, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, blank);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        return name;
    }

    /**
     * Swaps the scaled GUI projection for one in raw frame buffer pixels, so
     * the passes below can address exact texels.
     */
    private static void beginFramePass(int frameWidth, int frameHeight) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, frameWidth, 0, frameHeight, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void endFramePass() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();

        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
    }

    /** Source is in texels, destination in frame buffer pixels. Both are y up. */
    private static void blit(float sourceX, float sourceY, float sourceWidth, float sourceHeight,
                             float x, float y, float width, float height) {
        float u0 = sourceX / textureWidth;
        float v0 = sourceY / textureHeight;
        float u1 = (sourceX + sourceWidth) / textureWidth;
        float v1 = (sourceY + sourceHeight) / textureHeight;

        Tessellator tessellator = Tessellator.INSTANCE;
        tessellator.startQuads();
        tessellator.vertex(x, y, 0, u0, v0);
        tessellator.vertex(x + width, y, 0, u1, v0);
        tessellator.vertex(x + width, y + height, 0, u1, v1);
        tessellator.vertex(x, y + height, 0, u0, v1);
        tessellator.draw();
    }

    private static int ceilPowerOfTwo(int value) {
        int result = 1;
        while (result < value) result <<= 1;
        return result;
    }
}
