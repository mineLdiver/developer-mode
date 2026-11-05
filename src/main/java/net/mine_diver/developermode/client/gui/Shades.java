package net.mine_diver.developermode.client.gui;

/**
 * Colors for numbers drawn as pictures: noise density, and height.
 *
 * <p>Shared by every view of a preview, so a value is the same color in a
 * slice as on a block.
 */
public final class Shades {
    /** Low to high. */
    private static final int[] HEIGHT = { 0x1E2A4A, 0x2F5F8A, 0x4FA38A, 0xA8C66C, 0xE8D38B, 0xFFFFFF };
    /** Just above zero to deep inside: the surface light, the depths dark. */
    private static final int[] SOLID = { 0xF2D38A, 0xC98A4B, 0x7A4A2A, 0x3A2416 };
    /** Just below zero to far from any land. */
    private static final int[] EMPTY = { 0xA9D3F0, 0x5B8FC4, 0x2C4A78, 0x141E33 };
    /**
     * The density at which a shade is halfway along its ramp. Generators keep
     * their densities in no particular units, so the ramp eases toward its
     * end rather than stopping at a maximum, and every value keeps a shade.
     */
    private static final float HALF = 40;

    private Shades() {}

    /** @param along how far from the bottom to the top, from 0 to 1 */
    public static int height(float along) {
        return ramp(HEIGHT, along);
    }

    public static int density(float density) {
        float magnitude = Math.abs(density);
        float along = magnitude / (magnitude + HALF);
        return ramp(density > 0 ? SOLID : EMPTY, along);
    }

    private static int ramp(int[] stops, float along) {
        float scaled = Math.max(0, Math.min(1, along)) * (stops.length - 1);
        int low = Math.min((int) scaled, stops.length - 2);
        return blend(stops[low], stops[low + 1], scaled - low);
    }

    public static int blend(int from, int to, float amount) {
        int r = (int) ((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * amount);
        int g = (int) ((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * amount);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return r << 16 | g << 8 | b;
    }
}
