package net.mine_diver.developermode.client.gui;

import net.mine_diver.developermode.feature.world.Preview;
import net.mine_diver.developermode.feature.world.transform.Stack;
import net.mine_diver.developermode.feature.world.transform.Transform;
import net.minecraft.client.Minecraft;

/**
 * A regular checkerboard, bent the way the transforms bend the world, drawn on
 * the level of the cut-away like a twisted rubber sheet.
 *
 * <p>Each block is colored by the square it takes its density from rather than
 * the one it is in, so where nothing is warped the board is straight, and
 * around a vortex its squares wind in toward the center. That shows the reach,
 * the twist and the falloff with no terrain in the way.
 */
public final class WarpGridView {
    /** Blocks to a square. */
    private static final int SQUARE = 4;
    private static final int LIGHT = 0x5C6878;
    private static final int DARK = 0x2E3540;
    private static final int GAP = 4;

    private final PixelTexture picture = new PixelTexture();
    private long builtKey;

    public void release() {
        picture.release();
    }

    public void render(Minecraft minecraft, int x, int y, int width, int height, Preview preview, Stack stack, int cut) {
        int side = preview.across() * 16;
        int size = Math.max(16, Math.min(width, height) - GAP * 2);
        picture.place(x + (width - size) / 2, y + (height - size) / 2, size, size);

        long key = stack.fingerprint() * 31 + cut;
        key = key * 31 + preview.fromX();
        key = key * 31 + preview.fromZ() * 7L + preview.across();
        if (key != builtKey) {
            builtKey = key;
            Transform.Lookup lookup = stack.lookup(preview.world().getSeed());
            double[] source = new double[2];
            int left = preview.fromX() * 16;
            int top = preview.fromZ() * 16;
            int[] pixels = new int[side * side];
            for (int row = 0; row < side; row++) {
                for (int column = 0; column < side; column++) {
                    lookup.source(left + column + 0.5, cut, top + row + 0.5, source);
                    long squareX = (long) Math.floor(source[0] / SQUARE);
                    long squareZ = (long) Math.floor(source[1] / SQUARE);
                    pixels[row * side + column] = ((squareX + squareZ) & 1) == 0 ? LIGHT : DARK;
                }
            }
            picture.upload(pixels, side, side);
        }
        picture.draw();
    }

    /** @return the world x and z under a point of the board, or null when it is off it */
    public int[] pick(int pointX, int pointY, Preview preview) {
        if (!picture.contains(pointX, pointY)) return null;
        int side = preview.across() * 16;
        return new int[] {
                preview.fromX() * 16 + (int) ((pointX - picture.x) / (double) picture.width * side),
                preview.fromZ() * 16 + (int) ((pointY - picture.y) / (double) picture.height * side)
        };
    }
}
