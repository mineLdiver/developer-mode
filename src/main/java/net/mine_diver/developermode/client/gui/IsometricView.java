package net.mine_diver.developermode.client.gui;

import net.mine_diver.developermode.client.gui.composer.ComposerScreen;
import net.mine_diver.developermode.feature.world.Preview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Tessellator;
import net.modificationstation.stationapi.api.block.BlockState;
import net.modificationstation.stationapi.impl.world.chunk.ChunkSection;
import net.modificationstation.stationapi.impl.world.chunk.FlattenedChunk;
import org.lwjgl.opengl.GL11;

/**
 * A preview drawn from a fixed angle with no perspective, inside a rectangle
 * of the GUI.
 *
 * <p>Each block is a flat colored box, by what it is made of or by its
 * height, with its faces shaded by which way they point so the shape reads
 * without lighting. The edges of the preview are drawn as cut faces, so the
 * layers under the surface show along every side, and a cut-away hides
 * everything above a height to show what is inside.
 *
 * <p>Faces are worked out once and kept in a display list per chunk, so a
 * frame only replays them. Working them out is done a chunk at a time with
 * {@link #step}, like the generating, and a chunk is shown as soon as it has
 * been. Only faces that can be seen are kept: a camera above never sees a
 * face pointing down, and of the four sides only the two turned toward it, so
 * turning the view works them out again.
 */
public final class IsometricView {
    /** Degrees down from level, enough to see the tops and the sides about equally. */
    private static final float PITCH = 30;
    private static final float MIN_ZOOM = 0.25F;
    private static final float MAX_ZOOM = 24;
    /** The share of the rectangle a preview fills before it is zoomed. */
    private static final float FIT = 0.8F;
    private static final float SHADE_TOP = 1;
    private static final float SHADE_X = 0.62F;
    private static final float SHADE_Z = 0.8F;
    /** For a block whose material has no color of its own. */
    private static final int UNCOLORED = 0x8C8C8C;
    /** How finely a click is followed into the preview, in blocks. */
    private static final double PICK_STEP = 0.05;

    public enum Coloring {
        BLOCKS("gui.developermode.preview.color.blocks"),
        HEIGHT("gui.developermode.preview.color.height"),
        /** How far above zero the noise is, which is how deep inside the land a block sits. */
        DENSITY("gui.developermode.preview.color.density");

        public final String key;

        Coloring(String key) {
            this.key = key;
        }
    }

    /** A block of the preview, in world coordinates. */
    public record Hit(int x, int y, int z, BlockState state) {}

    private Preview preview;
    /** A display list per chunk, by the preview's index, or 0 for none yet. */
    private int[] lists = new int[0];
    /** How many chunks, in the order they were made, have faces for the current settings. */
    private int meshed;

    private int rotation;
    private float zoom = -1;
    private float panX;
    private float panY;
    /** The highest height shown, in world coordinates. */
    private int cut = Integer.MAX_VALUE;
    private Coloring coloring = Coloring.BLOCKS;

    /** Shows another preview, letting go of everything kept for the last one. */
    public void show(Preview preview) {
        release();
        this.preview = preview;
        this.lists = new int[preview.count()];
        this.meshed = 0;
        this.zoom = -1;
        this.panX = 0;
        this.panY = 0;
        this.cut = Math.min(cut, top());
    }

    public Preview preview() {
        return preview;
    }

    /** Hands the display lists back. Safe to call more than once. */
    public void release() {
        for (int i = 0; i < lists.length; i++) {
            if (lists[i] != 0) GL11.glDeleteLists(lists[i], 1);
            lists[i] = 0;
        }
    }

    /** Works out faces for about as long as it is given, never ahead of the generating. */
    public void step(long budgetNanos) {
        if (preview == null) return;
        long until = System.nanoTime() + budgetNanos;
        while (meshed < preview.made() && System.nanoTime() < until) {
            mesh(preview.orderAt(meshed));
            meshed++;
        }
    }

    /** Whether every chunk made so far is drawn as the settings now say. */
    public boolean settled() {
        return preview == null || meshed >= preview.made();
    }

    public void turn(int quarters) {
        rotation = Math.floorMod(rotation + quarters, 4);
        meshed = 0;
    }

    public void zoom(int notches) {
        if (zoom > 0) zoom = clamp(zoom * (float) Math.pow(1.25, notches), MIN_ZOOM, MAX_ZOOM);
    }

    public void pan(float dx, float dy) {
        panX += dx;
        panY += dy;
    }

    public int cut() {
        return Math.min(cut, top());
    }

    public void setCut(int cut) {
        int clamped = preview == null ? cut : Math.max(preview.bottomY(), Math.min(cut, top()));
        if (clamped == cut()) return;
        this.cut = clamped;
        meshed = 0;
    }

    public Coloring coloring() {
        return coloring;
    }

    public void setColoring(Coloring coloring) {
        if (this.coloring == coloring) return;
        this.coloring = coloring;
        meshed = 0;
    }

    public void render(Minecraft minecraft, int x, int y, int width, int height) {
        if (preview == null) return;
        if (zoom < 0) zoom = clamp(Math.min(width, height) * FIT / (preview.across() * 16 * 1.4F), MIN_ZOOM, MAX_ZOOM);

        Draw.pushScissor(minecraft, x, y, width, height);

        // A projection of its own, since the GUI's keeps only a thin slab of
        // depth and a zoomed preview is far deeper than that.
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        ComposerScreen screen = ComposerScreen.instance();
        GL11.glOrtho(0, screen.width, screen.height, 0, -100000, 100000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        transform(x, y, width, height);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glColor4f(1, 1, 1, 1);

        for (int list : lists) if (list != 0) GL11.glCallList(list);

        // The depth written here is in the middle of a 2D screen. Clearing is
        // safe because the scissor keeps it inside this rectangle.
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        Draw.popScissor();
    }

    /**
     * The block under a point of the rectangle, followed in along the line
     * of sight to the first one that is drawn.
     *
     * @return null when the point is over nothing
     */
    public Hit pick(int pointX, int pointY, int x, int y, int width, int height) {
        if (preview == null || zoom < 0) return null;

        double pitch = Math.toRadians(PITCH);
        double yaw = Math.toRadians(yaw());
        double reach = preview.across() * 16 + preview.height();

        // In front of everything, in the space after the turning, then turned
        // back into the preview's own coordinates.
        double ux = (pointX - (x + width / 2F + panX)) / zoom;
        double uy = -(pointY - (y + height / 2F + panY)) / zoom;
        double[] start = unturn(ux, uy, reach, pitch, yaw);
        double[] along = unturn(0, 0, -1, pitch, yaw);
        double[] center = center();

        double px = start[0] + center[0];
        double py = start[1] + center[1];
        double pz = start[2] + center[2];
        for (double travelled = 0; travelled < reach * 2; travelled += PICK_STEP) {
            int bx = (int) Math.floor(px + along[0] * travelled);
            int by = (int) Math.floor(py + along[1] * travelled);
            int bz = (int) Math.floor(pz + along[2] * travelled);
            BlockState state = solidAt(bx, by, bz);
            if (state != null) {
                return new Hit(preview.fromX() * 16 + bx, preview.bottomY() + by, preview.fromZ() * 16 + bz, state);
            }
        }
        return null;
    }

    private void transform(int x, int y, int width, int height) {
        double[] center = center();
        GL11.glTranslatef(x + width / 2F + panX, y + height / 2F + panY, 0);
        GL11.glScalef(zoom, -zoom, zoom);
        GL11.glRotatef(PITCH, 1, 0, 0);
        GL11.glRotatef(yaw(), 0, 1, 0);
        GL11.glTranslated(-center[0], -center[1], -center[2]);
    }

    /** What the view turns around, in the preview's own coordinates: the middle of what is shown. */
    private double[] center() {
        double side = preview.across() * 16;
        return new double[] { side / 2, (cut() - preview.bottomY() + 1) / 2.0, side / 2 };
    }

    private float yaw() {
        return 45 + rotation * 90;
    }

    /** Undoes the turning of the view: the pitch about x, then the yaw about y. */
    private static double[] unturn(double x, double y, double z, double pitch, double yaw) {
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double y1 = y * cosPitch + z * sinPitch;
        double z1 = -y * sinPitch + z * cosPitch;
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        return new double[] { x * cosYaw - z1 * sinYaw, y1, x * sinYaw + z1 * cosYaw };
    }

    private void mesh(int index) {
        int across = preview.across();
        int chunkX = preview.fromX() + index % across;
        int chunkZ = preview.fromZ() + index / across;
        FlattenedChunk chunk = preview.chunk(chunkX, chunkZ);
        if (chunk == null) return;

        // Which two sides are turned toward the camera: the normal of a face
        // seen from where the view looks from has a positive share of it.
        double yaw = Math.toRadians(yaw());
        int sideX = -Math.sin(yaw) > 0 ? 1 : -1;
        int sideZ = Math.cos(yaw) > 0 ? 1 : -1;

        int originX = (index % across) * 16;
        int originZ = (index / across) * 16;
        int bottom = preview.bottomY();
        int cut = cut();

        if (lists[index] == 0) lists[index] = GL11.glGenLists(1);
        GL11.glNewList(lists[index], GL11.GL_COMPILE);
        Tessellator tessellator = Tessellator.INSTANCE;
        tessellator.startQuads();
        for (ChunkSection section : chunk.sections) {
            if (section == null || section.isEmpty()) continue;
            for (int sy = 0; sy < 16; sy++) {
                int worldY = section.getYOffset() + sy;
                if (worldY > cut) break;
                int y = worldY - bottom;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        BlockState state = section.getBlockState(lx, sy, lz);
                        if (state.isAir()) continue;
                        int x = originX + lx;
                        int z = originZ + lz;
                        int color = color(state, preview.fromX() * 16 + x, worldY, preview.fromZ() * 16 + z);

                        if (solidAt(x, y + 1, z) == null) face(tessellator, color, SHADE_TOP, x, y + 1, z, 0);
                        if (solidAt(x + sideX, y, z) == null)
                            face(tessellator, color, SHADE_X, sideX > 0 ? x + 1 : x, y, z, 1);
                        if (solidAt(x, y, z + sideZ) == null)
                            face(tessellator, color, SHADE_Z, x, y, sideZ > 0 ? z + 1 : z, 2);
                    }
                }
            }
        }
        tessellator.draw();
        GL11.glEndList();
    }

    /**
     * One face of a block.
     *
     * @param axis 0 for a face lying flat at height {@code y}, 1 for one
     *             standing across x at {@code x}, 2 for one across z at {@code z}
     */
    private static void face(Tessellator tessellator, int color, float shade, int x, int y, int z, int axis) {
        tessellator.color((int) ((color >> 16 & 0xFF) * shade), (int) ((color >> 8 & 0xFF) * shade), (int) ((color & 0xFF) * shade));
        switch (axis) {
            case 0 -> {
                tessellator.vertex(x, y, z);
                tessellator.vertex(x, y, z + 1);
                tessellator.vertex(x + 1, y, z + 1);
                tessellator.vertex(x + 1, y, z);
            }
            case 1 -> {
                tessellator.vertex(x, y, z);
                tessellator.vertex(x, y + 1, z);
                tessellator.vertex(x, y + 1, z + 1);
                tessellator.vertex(x, y, z + 1);
            }
            default -> {
                tessellator.vertex(x, y, z);
                tessellator.vertex(x + 1, y, z);
                tessellator.vertex(x + 1, y + 1, z);
                tessellator.vertex(x, y + 1, z);
            }
        }
    }

    /**
     * The block drawn at a position in the preview's own coordinates, or null
     * where nothing is: outside it, above the cut, in a chunk not made yet, or
     * air. Anything else hides the faces of whatever is next to it.
     */
    private BlockState solidAt(int x, int y, int z) {
        int side = preview.across() * 16;
        if (x < 0 || z < 0 || x >= side || z >= side || y < 0) return null;
        int worldY = preview.bottomY() + y;
        if (worldY > cut() || y >= preview.height()) return null;
        FlattenedChunk chunk = preview.chunk(preview.fromX() + (x >> 4), preview.fromZ() + (z >> 4));
        if (chunk == null) return null;
        BlockState state = chunk.getBlockState(x & 15, worldY, z & 15);
        return state.isAir() ? null : state;
    }

    private int color(BlockState state, int worldX, int worldY, int worldZ) {
        if (coloring == Coloring.DENSITY) {
            float density = preview.density(worldX, worldY, worldZ);
            if (!Float.isNaN(density)) return Shades.density(density);
        }
        if (coloring == Coloring.HEIGHT || coloring == Coloring.DENSITY) {
            return Shades.height((worldY - preview.bottomY()) / (float) Math.max(1, preview.height() - 1));
        }
        int color = state.getBlock().material.mapColor.color;
        return color == 0 ? UNCOLORED : color;
    }

    private int top() {
        return preview == null ? Integer.MAX_VALUE : preview.bottomY() + preview.height() - 1;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
