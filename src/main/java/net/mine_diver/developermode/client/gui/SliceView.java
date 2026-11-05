package net.mine_diver.developermode.client.gui;

import net.mine_diver.developermode.feature.world.Preview;
import net.minecraft.client.Minecraft;
import net.modificationstation.stationapi.api.block.BlockState;
import net.modificationstation.stationapi.impl.world.chunk.FlattenedChunk;

/**
 * Two flat cuts through a preview: one level, seen from above at the height of
 * the cut-away, and one upright, seen from the side along a line across it.
 *
 * <p>At the Noise step each block is shaded by its density, warm where it is
 * solid and cool where it is not, with the line where it crosses zero drawn
 * between them, since that line is where the surface will fall. At the
 * Terrain step they show the blocks themselves.
 *
 * <p>A horizontal cut shows a shape from above, such as a spiral; a vertical
 * one shows its profile, such as a funnel. Clicking the level cut moves the
 * upright one to the line clicked.
 *
 * <p>Each cut is a texture of a pixel per block, drawn again only when
 * something it shows has changed.
 */
public final class SliceView {
    private static final int BACKGROUND = 0x101418;
    private static final int AIR = 0x1A2028;
    private static final int ZERO = 0xFFFFFF;
    private static final int MARK = Theme.ACCENT & 0xFFFFFF;
    private static final int GAP = 4;

    /** A point of one of the cuts, in world coordinates, and the density there if there is one. */
    public record Hit(int x, int y, int z, float density, BlockState state) {}

    private Preview preview;
    /** The upright cut's line, as a z across the preview from its edge. */
    private int line;

    private final PixelTexture level = new PixelTexture();
    private final PixelTexture upright = new PixelTexture();
    private Preview builtFor;
    private int builtMade = -1;
    private int builtCut;
    private int builtLine;

    /** Shows another preview, keeping the upright cut's line if it covers the same ground. */
    public void show(Preview preview) {
        boolean sameGround = this.preview != null && this.preview.fromX() == preview.fromX()
                && this.preview.fromZ() == preview.fromZ() && this.preview.across() == preview.across();
        this.preview = preview;
        if (!sameGround) this.line = preview.across() * 8;
        this.builtFor = null;
    }

    public void release() {
        level.release();
        upright.release();
    }

    public void render(Minecraft minecraft, int x, int y, int width, int height, int cut) {
        if (preview == null) return;
        refresh(cut);
        layout(x, y, width, height);
        level.draw();
        upright.draw();
    }

    /** Moves the upright cut if the point is on the level one. @return whether it was */
    public boolean click(int pointX, int pointY) {
        if (preview == null || !level.contains(pointX, pointY)) return false;
        line = (int) ((pointY - level.y) / (double) level.height * side());
        return true;
    }

    /** @return the block under a point of either cut, or null */
    public Hit pick(int pointX, int pointY, int cut) {
        if (preview == null) return null;
        int side = side();
        if (level.contains(pointX, pointY)) {
            int px = (int) ((pointX - level.x) / (double) level.width * side);
            int pz = (int) ((pointY - level.y) / (double) level.height * side);
            return hit(px, cut, pz);
        }
        if (upright.contains(pointX, pointY)) {
            int px = (int) ((pointX - upright.x) / (double) upright.width * side);
            int py = preview.bottomY() + preview.height() - 1 - (int) ((pointY - upright.y) / (double) upright.height * preview.height());
            return hit(px, py, line);
        }
        return null;
    }

    private Hit hit(int px, int worldY, int pz) {
        int x = preview.fromX() * 16 + px;
        int z = preview.fromZ() * 16 + pz;
        return new Hit(x, worldY, z, preview.density(x, worldY, z), stateAt(px, worldY, pz));
    }

    /**
     * The cuts side by side, as large as they fit with blocks kept square,
     * and a whole number of pixels to a block once there is room for two.
     */
    private void layout(int x, int y, int width, int height) {
        int side = side();
        int tall = preview.height();
        int available = width - GAP * 3;
        float scale = Math.min(available / (float) (side * 2), (height - GAP * 2) / (float) Math.max(side, tall));
        if (scale >= 2) scale = (float) Math.floor(scale);
        level.place(x + GAP, y + GAP, Math.round(side * scale), Math.round(side * scale));
        upright.place(level.x + level.width + GAP, y + GAP, Math.round(side * scale), Math.round(tall * scale));
    }

    private void refresh(int cut) {
        line = Math.max(0, Math.min(line, side() - 1));
        if (builtFor == preview && builtMade == preview.made() && builtCut == cut && builtLine == line) return;
        builtFor = preview;
        builtMade = preview.made();
        builtCut = cut;
        builtLine = line;

        int side = side();
        int tall = preview.height();
        int top = preview.bottomY() + tall - 1;

        int[] above = new int[side * side];
        for (int pz = 0; pz < side; pz++) {
            for (int px = 0; px < side; px++) {
                int color = color(px, cut, pz);
                if (pz == line) color = Shades.blend(color, MARK, 0.6F);
                above[pz * side + px] = color;
            }
        }
        if (preview.step() == Preview.Step.NOISE) zeroLine(above, side, side, (px, row) -> density(px, cut, row));
        level.upload(above, side, side);

        int[] across = new int[side * tall];
        for (int row = 0; row < tall; row++) {
            int worldY = top - row;
            for (int px = 0; px < side; px++) {
                int color = color(px, worldY, line);
                if (worldY == cut) color = Shades.blend(color, MARK, 0.6F);
                across[row * side + px] = color;
            }
        }
        if (preview.step() == Preview.Step.NOISE) zeroLine(across, side, tall, (px, row) -> density(px, top - row, line));
        upright.upload(across, side, tall);
    }

    private interface Sampler {
        float at(int column, int row);
    }

    /** Lights every pixel whose density has the other sign from the one after it, across or down. */
    private static void zeroLine(int[] pixels, int width, int height, Sampler density) {
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                float here = density.at(column, row);
                if (Float.isNaN(here)) continue;
                boolean edge = false;
                if (column + 1 < width) edge = crosses(here, density.at(column + 1, row));
                if (!edge && row + 1 < height) edge = crosses(here, density.at(column, row + 1));
                if (edge) pixels[row * width + column] = ZERO;
            }
        }
    }

    private static boolean crosses(float here, float next) {
        return !Float.isNaN(next) && (here > 0) != (next > 0);
    }

    private int color(int px, int worldY, int pz) {
        if (preview.step() == Preview.Step.NOISE) {
            float density = density(px, worldY, pz);
            return Float.isNaN(density) ? BACKGROUND : Shades.density(density);
        }
        FlattenedChunk chunk = preview.chunk(preview.fromX() + (px >> 4), preview.fromZ() + (pz >> 4));
        if (chunk == null) return BACKGROUND;
        BlockState state = chunk.getBlockState(px & 15, worldY, pz & 15);
        if (state.isAir()) return AIR;
        int color = state.getBlock().material.mapColor.color;
        return color == 0 ? 0x8C8C8C : color;
    }

    private float density(int px, int worldY, int pz) {
        return preview.density(preview.fromX() * 16 + px, worldY, preview.fromZ() * 16 + pz);
    }

    private BlockState stateAt(int px, int worldY, int pz) {
        FlattenedChunk chunk = preview.chunk(preview.fromX() + (px >> 4), preview.fromZ() + (pz >> 4));
        if (chunk == null || worldY < preview.bottomY() || worldY >= preview.bottomY() + preview.height()) return null;
        BlockState state = chunk.getBlockState(px & 15, worldY, pz & 15);
        return state.isAir() ? null : state;
    }

    private int side() {
        return preview.across() * 16;
    }
}
