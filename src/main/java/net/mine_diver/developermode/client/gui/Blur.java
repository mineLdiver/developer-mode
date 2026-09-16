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
 */
public final class Blur {
    /** Halvings before smearing. Each one doubles the effective radius. */
    private static final int DOWNSAMPLES = 4;
    /** Half texel smears at the smallest size. Keep even, so they cancel out. */
    private static final int SMEAR_PASSES = 4;
    /** Never shrink below this, or large radii turn into visible blockiness. */
    private static final int MIN_SIZE = 16;

    private static int texture = -1;
    private static int textureWidth;
    private static int textureHeight;
    private static boolean unsupported;

    private Blur() {}

    public static void render(Minecraft minecraft) {
        if (unsupported) return;

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
     * Drops the capture texture. Worth doing when the UI closes so an idle
     * session is not sitting on a screen sized texture.
     */
    public static void release() {
        if (texture != -1) {
            GL11.glDeleteTextures(texture);
            texture = -1;
            textureWidth = 0;
            textureHeight = 0;
        }
    }

    private static void allocate(int frameWidth, int frameHeight) {
        if (texture != -1 && textureWidth >= frameWidth && textureHeight >= frameHeight) return;

        release();

        boolean npot = GLContext.getCapabilities().GL_ARB_texture_non_power_of_two;
        textureWidth = npot ? frameWidth : ceilPowerOfTwo(frameWidth);
        textureHeight = npot ? frameHeight : ceilPowerOfTwo(frameHeight);

        texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        ByteBuffer blank = BufferUtils.createByteBuffer(textureWidth * textureHeight * 4);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, textureWidth, textureHeight, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, blank);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
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
