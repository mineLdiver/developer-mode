package net.mine_diver.developermode.client.gui;

import net.minecraft.client.render.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/**
 * A picture made a pixel at a time, kept as a texture and drawn into a
 * rectangle of the GUI with its pixels left sharp.
 *
 * <p>For views that are cheap to work out but too many pixels to draw as
 * rectangles every frame: worked out and handed over once, then drawn as one
 * quad until it changes.
 */
public final class PixelTexture {
    public int x;
    public int y;
    public int width;
    public int height;
    private int texture;

    public void place(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(int pointX, int pointY) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
    }

    /** @param pixels RGB, a row at a time from the top */
    public void upload(int[] pixels, int pixelsWide, int pixelsTall) {
        if (texture == 0) texture = GL11.glGenTextures();
        ByteBuffer buffer = BufferUtils.createByteBuffer(pixels.length * 4);
        for (int pixel : pixels) {
            buffer.put((byte) (pixel >> 16)).put((byte) (pixel >> 8)).put((byte) pixel).put((byte) 0xFF);
        }
        buffer.flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, pixelsWide, pixelsTall, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
    }

    public void draw() {
        if (texture == 0) return;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glColor4f(1, 1, 1, 1);
        Tessellator tessellator = Tessellator.INSTANCE;
        tessellator.startQuads();
        tessellator.vertex(x, y + height, 0, 0, 1);
        tessellator.vertex(x + width, y + height, 0, 1, 1);
        tessellator.vertex(x + width, y, 0, 1, 0);
        tessellator.vertex(x, y, 0, 0, 0);
        tessellator.draw();
        Draw.outline(x - 1, y - 1, width + 2, height + 2, Theme.BORDER);
    }

    /** Hands the texture back. Safe to call more than once. */
    public void release() {
        if (texture != 0) GL11.glDeleteTextures(texture);
        texture = 0;
    }
}
