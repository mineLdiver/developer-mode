package net.mine_diver.developermode.client.gui;

import net.mine_diver.developermode.feature.world.Preview;
import net.minecraft.client.Minecraft;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;

/**
 * The biomes around a preview, from above and thousands of blocks across.
 *
 * <p>A biome is worked out from the seed and the position alone, so a map of
 * them costs no chunk generation at all. Each biome is drawn in the color it
 * names for itself, and the preview's own square is outlined in the middle.
 */
public final class BiomeMapView {
    private static final int AREA = Theme.ACCENT & 0xFFFFFF;
    private static final int UNCOLORED = 0x5A6169;
    private static final int MIN_SPAN = 128;
    private static final int MAX_SPAN = 16384;
    private static final int GAP = 4;

    private final PixelTexture picture = new PixelTexture();
    private int span = 2048;
    private long builtKey;

    public void zoom(int notches) {
        span = Math.max(MIN_SPAN, Math.min(MAX_SPAN, notches > 0 ? span / 2 : span * 2));
    }

    public void release() {
        picture.release();
    }

    public void render(Minecraft minecraft, int x, int y, int width, int height, Preview preview) {
        int size = Math.max(16, Math.min(width, height) - GAP * 2);
        picture.place(x + (width - size) / 2, y + (height - size) / 2, size, size);

        long key = (long) span * 31 + size;
        key = key * 31 + preview.fromX();
        key = key * 31 + preview.fromZ() * 7L + preview.across();
        key = key * 31 + System.identityHashCode(preview.world());
        if (key != builtKey) {
            builtKey = key;
            draw(preview, size);
        }
        picture.draw();
    }

    /** @return the biome under a point of the map with its world x and z, or null when it is off it */
    public Hit pick(int pointX, int pointY, Preview preview) {
        if (!picture.contains(pointX, pointY)) return null;
        double perPixel = span / (double) picture.width;
        int worldX = (int) Math.floor(centerX(preview) + (pointX - picture.x - picture.width / 2.0) * perPixel);
        int worldZ = (int) Math.floor(centerZ(preview) + (pointY - picture.y - picture.height / 2.0) * perPixel);
        return new Hit(worldX, worldZ, preview.world().method_1781().getBiome(worldX, worldZ));
    }

    public record Hit(int x, int z, Biome biome) {}

    private void draw(Preview preview, int size) {
        BiomeSource biomes = preview.world().method_1781();
        double perPixel = span / (double) size;
        double left = centerX(preview) - span / 2.0;
        double top = centerZ(preview) - span / 2.0;
        int areaLeft = preview.fromX() * 16;
        int areaTop = preview.fromZ() * 16;
        int areaSide = preview.across() * 16;

        int[] pixels = new int[size * size];
        for (int row = 0; row < size; row++) {
            double worldZ = top + (row + 0.5) * perPixel;
            for (int column = 0; column < size; column++) {
                double worldX = left + (column + 0.5) * perPixel;
                Biome biome = biomes.getBiome((int) Math.floor(worldX), (int) Math.floor(worldZ));
                int color = biome == null || biome.grassColor == 0 ? UNCOLORED : biome.grassColor & 0xFFFFFF;
                boolean insideX = worldX >= areaLeft && worldX <= areaLeft + areaSide;
                boolean insideZ = worldZ >= areaTop && worldZ <= areaTop + areaSide;
                boolean edgeX = Math.abs(worldX - areaLeft) <= perPixel / 2 || Math.abs(worldX - (areaLeft + areaSide)) <= perPixel / 2;
                boolean edgeZ = Math.abs(worldZ - areaTop) <= perPixel / 2 || Math.abs(worldZ - (areaTop + areaSide)) <= perPixel / 2;
                if (edgeX && insideZ || edgeZ && insideX) color = AREA;
                pixels[row * size + column] = color;
            }
        }
        picture.upload(pixels, size, size);
    }

    private static double centerX(Preview preview) {
        return (preview.fromX() + preview.across() / 2.0) * 16;
    }

    private static double centerZ(Preview preview) {
        return (preview.fromZ() + preview.across() / 2.0) * 16;
    }
}
