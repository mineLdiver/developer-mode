package net.mine_diver.developermode.client.census;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSource;
import net.modificationstation.stationapi.api.block.BlockState;
import net.modificationstation.stationapi.impl.world.chunk.ChunkSection;
import net.modificationstation.stationapi.impl.world.chunk.FlattenedChunk;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Every block of one kind near you, outlined through the walls, for seeing
 * how an ore actually sits in the ground rather than reading about it.
 *
 * <p>A toggle, like a power: it stays on until it is switched off, and follows
 * you. The chunks around you are looked through one a frame, round and round,
 * so it keeps up as you move and as the blocks change without a pause.
 */
public final class OreLens {
    /** How far around you it looks, in chunks. */
    private static final int RADIUS = 4;
    /** Past this many outlines the picture is a blur, and drawing them would cost the frame. */
    private static final int MOST = 4096;

    private static BlockState watched;
    /** The blocks found in each chunk, by chunk, as packed positions within it. */
    private static final Map<Long, int[]> found = new HashMap<>();
    private static World foundIn;
    private static int cursor;

    private OreLens() {}

    /** Outlines this kind of block from now on, in place of any other. */
    public static void watch(BlockState state) {
        if (state == watched) return;
        watched = state;
        found.clear();
    }

    public static void stop() {
        watched = null;
        found.clear();
    }

    /** The kind of block outlined, or null when it is off. */
    public static BlockState watched() {
        return watched;
    }

    /** Polled once a frame from the world render. Looks through one chunk. */
    public static void update() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        if (watched == null || minecraft == null || minecraft.world == null || minecraft.player == null) return;
        if (minecraft.world != foundIn) {
            found.clear();
            foundIn = minecraft.world;
        }

        int playerX = (int) Math.floor(minecraft.player.x) >> 4;
        int playerZ = (int) Math.floor(minecraft.player.z) >> 4;
        found.keySet().removeIf(key -> Math.abs((int) (key >> 32) - playerX) > RADIUS || Math.abs((int) (long) key - playerZ) > RADIUS);

        int across = RADIUS * 2 + 1;
        int index = cursor++ % (across * across);
        int chunkX = playerX - RADIUS + index % across;
        int chunkZ = playerZ - RADIUS + index / across;
        long key = (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;

        ChunkSource source = minecraft.world.getChunkSource();
        Chunk chunk = source.isChunkLoaded(chunkX, chunkZ) ? source.getChunk(chunkX, chunkZ) : null;
        if (!(chunk instanceof FlattenedChunk flattened) || chunk.isEmpty()) {
            found.remove(key);
            return;
        }
        found.put(key, scan(flattened));
    }

    private static int[] scan(FlattenedChunk chunk) {
        int[] positions = new int[64];
        int count = 0;
        for (ChunkSection section : chunk.sections) {
            if (section == null || section.isEmpty()) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z) != watched) continue;
                        if (count == positions.length) positions = Arrays.copyOf(positions, count * 2);
                        positions[count++] = (section.getYOffset() + y) << 8 | z << 4 | x;
                    }
                }
            }
        }
        return Arrays.copyOf(positions, count);
    }

    public static void renderWorld(float tickDelta) {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        if (watched == null || found.isEmpty() || minecraft == null || minecraft.camera == null) return;
        LivingEntity camera = minecraft.camera;
        double cameraX = camera.lastTickX + (camera.x - camera.lastTickX) * tickDelta;
        double cameraY = camera.lastTickY + (camera.y - camera.lastTickY) * tickDelta;
        double cameraZ = camera.lastTickZ + (camera.z - camera.lastTickZ) * tickDelta;

        WorldDraw.beginLines(true);
        GL11.glLineWidth(1.5F);
        WorldDraw.color(Theme.LOCKED);
        int drawn = 0;
        for (Map.Entry<Long, int[]> entry : found.entrySet()) {
            int originX = (int) (entry.getKey() >> 32) << 4;
            int originZ = (int) (long) entry.getKey() << 4;
            for (int packed : entry.getValue()) {
                if (drawn++ >= MOST) break;
                double x = originX + (packed & 15) - cameraX;
                double y = (packed >> 8) - cameraY;
                double z = originZ + (packed >> 4 & 15) - cameraZ;
                WorldDraw.outline(Box.create(x, y, z, x + 1, y + 1, z + 1));
            }
        }
        WorldDraw.endLines();
    }
}
