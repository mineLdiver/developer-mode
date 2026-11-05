package net.mine_diver.developermode.client.gui;

import net.mine_diver.developermode.feature.world.Preview;
import net.mine_diver.developermode.feature.world.transform.Stack;
import net.mine_diver.developermode.feature.world.transform.Transform;
import net.mine_diver.developermode.feature.world.transform.VortexWarp;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the vortexes fall, from above and thousands of blocks across: each
 * one's reach as a disk shaded by how hard it turns, its center as a dot, and
 * the cells they are placed in drawn faintly.
 *
 * <p>Placing them only takes hashing, with no terrain, so this follows every
 * change at once. The preview's own square is outlined in the middle, to show
 * how much of the pattern it covers.
 */
public final class PlacementView {
    private static final int BACKGROUND = 0x12161B;
    private static final int GRID = 0x232A33;
    private static final int DISK = Theme.ACCENT & 0xFFFFFF;
    private static final int CENTER = 0xFFFFFF;
    private static final int AREA = Theme.LOCKED & 0xFFFFFF;
    private static final int MIN_SPAN = 128;
    private static final int MAX_SPAN = 16384;
    private static final int GAP = 4;

    private final PixelTexture picture = new PixelTexture();
    /** How many blocks across the map is. */
    private int span = 2048;
    private long builtKey;

    public void zoom(int notches) {
        span = Math.max(MIN_SPAN, Math.min(MAX_SPAN, notches > 0 ? span / 2 : span * 2));
    }

    public void release() {
        picture.release();
    }

    /** @return false when there is no vortex warp to show */
    public boolean render(Minecraft minecraft, int x, int y, int width, int height, Preview preview, Stack stack) {
        List<VortexWarp.Placement> placements = placements(stack, preview.world().getSeed());
        if (placements.isEmpty()) return false;

        int size = Math.max(16, Math.min(width, height) - GAP * 2);
        picture.place(x + (width - size) / 2, y + (height - size) / 2, size, size);

        long key = stack.fingerprint() * 31 + span;
        key = key * 31 + preview.fromX();
        key = key * 31 + preview.fromZ() * 7L + preview.across();
        key = key * 31 + size;
        if (key != builtKey) {
            builtKey = key;
            draw(preview, placements, size);
        }
        picture.draw();
        return true;
    }

    /** @return the world x and z under a point of the map, or null when it is off it */
    public int[] pick(int pointX, int pointY, Preview preview) {
        if (!picture.contains(pointX, pointY)) return null;
        double perPixel = span / (double) picture.width;
        double centerX = (preview.fromX() + preview.across() / 2.0) * 16;
        double centerZ = (preview.fromZ() + preview.across() / 2.0) * 16;
        return new int[] {
                (int) Math.floor(centerX + (pointX - picture.x - picture.width / 2.0) * perPixel),
                (int) Math.floor(centerZ + (pointY - picture.y - picture.height / 2.0) * perPixel)
        };
    }

    private void draw(Preview preview, List<VortexWarp.Placement> placements, int size) {
        double perPixel = span / (double) size;
        double centerX = (preview.fromX() + preview.across() / 2.0) * 16;
        double centerZ = (preview.fromZ() + preview.across() / 2.0) * 16;
        double left = centerX - span / 2.0;
        double top = centerZ - span / 2.0;
        int areaLeft = preview.fromX() * 16;
        int areaTop = preview.fromZ() * 16;
        int areaSide = preview.across() * 16;
        VortexWarp.Placement first = placements.get(0);

        int[] pixels = new int[size * size];
        for (int row = 0; row < size; row++) {
            double worldZ = top + (row + 0.5) * perPixel;
            for (int column = 0; column < size; column++) {
                double worldX = left + (column + 0.5) * perPixel;
                int color = BACKGROUND;
                if (crossesCell(worldX, perPixel, first.cell()) || crossesCell(worldZ, perPixel, first.cell())) color = GRID;

                for (VortexWarp.Placement placement : placements) {
                    int cellX = (int) Math.floor(worldX / placement.cell());
                    int cellZ = (int) Math.floor(worldZ / placement.cell());
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            double[] center = placement.center(cellX + dx, cellZ + dz);
                            if (center == null) continue;
                            double distance = Math.hypot(worldX - center[0], worldZ - center[1]);
                            // A dot of about a pixel, however far out the map is.
                            if (distance < Math.max(perPixel * 0.6, 1.5)) {
                                color = CENTER;
                            } else if (distance < placement.radius() && color != CENTER) {
                                float strength = (float) Math.pow(1 - distance / placement.radius(), 0.7);
                                color = Shades.blend(color, DISK, 0.25F + 0.55F * strength);
                            }
                        }
                    }
                }

                boolean onAreaEdge = onEdge(worldX, perPixel, areaLeft, areaSide, worldZ, areaTop)
                        || onEdge(worldZ, perPixel, areaTop, areaSide, worldX, areaLeft);
                if (onAreaEdge) color = AREA;
                pixels[row * size + column] = color;
            }
        }
        picture.upload(pixels, size, size);
    }

    /** Whether a cell boundary falls inside this pixel. */
    private static boolean crossesCell(double at, double perPixel, int cell) {
        return Math.floor((at - perPixel / 2) / cell) != Math.floor((at + perPixel / 2) / cell);
    }

    /** Whether this pixel holds one of the two sides of the square across this axis. */
    private static boolean onEdge(double at, double perPixel, int from, int side, double other, int otherFrom) {
        if (other < otherFrom || other > otherFrom + side) return false;
        return Math.abs(at - from) <= perPixel / 2 || Math.abs(at - (from + side)) <= perPixel / 2;
    }

    private static List<VortexWarp.Placement> placements(Stack stack, long seed) {
        List<VortexWarp.Placement> placements = new ArrayList<>();
        for (Transform transform : stack.transforms())
            if (transform.enabled() && transform instanceof VortexWarp vortex) placements.add(vortex.placement(seed));
        return placements;
    }
}
