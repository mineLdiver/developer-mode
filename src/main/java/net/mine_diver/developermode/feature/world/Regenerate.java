package net.mine_diver.developermode.feature.world;

import net.mine_diver.developermode.DeveloperMode;
import net.mine_diver.developermode.feature.Aim;
import net.mine_diver.developermode.feature.Message;
import net.mine_diver.developermode.feature.net.packet.RegenerateC2SPacket;
import net.mine_diver.developermode.feature.world.transform.Stack;
import net.mine_diver.developermode.feature.world.transform.Substitution;
import net.mine_diver.developermode.mixin.ChunkCacheAccessor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSource;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;
import net.modificationstation.stationapi.api.util.SideUtil;
import net.modificationstation.stationapi.impl.world.chunk.FlattenedChunk;

import java.util.HashMap;
import java.util.List;

/**
 * Building the chunks you point at again, from the world's own generator, so
 * a change to the generator shows up without making a new world.
 *
 * <p>Each chunk in the area keeps its object, so nothing holding one breaks,
 * and takes on the sections of a chunk freshly generated in its place. What
 * lived in it goes: its entities other than players and what they ride, its
 * block entities, and every block anyone put there. The area is generated in
 * full before any of it is touched, so a generator that throws partway leaves
 * the world as it was.
 *
 * <p>The area is then decorated again through the chunk cache rather than the
 * generator. The cache is where StationAPI posts its decoration event, so
 * every mod's listeners run again too. Vanilla's decoration and StationAPI's
 * are both seeded from the world seed and the chunk, so the same code makes
 * the same blocks, and any difference is the change.
 *
 * <p>Decorating a chunk writes into a square shifted half a chunk toward the
 * positive corner, across four chunks, so a chunk gets its decoration from
 * itself and the three behind it. Inside the area all four are regenerated
 * and it comes out exactly as a new world would have it. The edges do not:
 * the low ones miss what the neighbors outside spilled into them, and the high
 * ones spill over the neighbors outside. A radius of one is the smallest area
 * whose middle chunk is exact.
 */
public final class Regenerate {
    /** How far out from the chunk pointed at the area reaches, in chunks: 81 of them at most. */
    public static final int MAX_RADIUS = 4;
    /** The smallest area whose middle chunk comes out exact. */
    public static final int DEFAULT_RADIUS = 1;

    /**
     * Ten blocks that span a section from corner to corner.
     *
     * <p>A server sends what changed in a section block by block, up to ten
     * blocks, and once ten have changed it sends the whole section instead,
     * block entities and all. Marking these is how a regenerated section gets
     * to the players watching it: the same way a big explosion gets there.
     */
    private static final int[][] SECTION_MARKS = {
            { 0, 0, 0 }, { 15, 15, 15 },
            { 15, 0, 0 }, { 0, 15, 0 }, { 0, 0, 15 },
            { 15, 15, 0 }, { 15, 0, 15 }, { 0, 15, 15 },
            { 7, 7, 7 }, { 8, 8, 8 }
    };

    private Regenerate() {}

    /**
     * @param reach  how far the sender can see, in blocks
     * @param radius how far out from the chunk pointed at, in chunks
     */
    public static void request(double reach, int radius) {
        PacketHelper.send(new RegenerateC2SPacket(reach, radius));
    }

    public static boolean legalRadius(int radius) {
        return radius >= 0 && radius <= MAX_RADIUS;
    }

    /** How many chunks across an area of this radius is. */
    public static int across(int radius) {
        return radius * 2 + 1;
    }

    /** @return what went wrong, or null if the whole area was regenerated */
    public static Message regenerate(PlayerEntity player, double reach, int radius) {
        HitResult hit = Aim.block(player, reach);
        if (hit == null) return Message.of("message.developermode.nothing_in_reach");
        return regenerate(player.world, hit.blockX >> 4, hit.blockZ >> 4, radius);
    }

    /**
     * Regenerates the square of chunks around one, wherever it came from.
     *
     * @return what went wrong, or null if the whole area was regenerated
     */
    public static Message regenerate(World world, int chunkX, int chunkZ, int radius) {
        return regenerate(world, chunkX, chunkZ, radius, null);
    }

    /**
     * Regenerates the square of chunks around one with transforms laid over
     * the generator's noise, the way a preview of it was built, so what was
     * tuned in a preview can be walked around in. The transformed noise
     * stands in for the generator's own while it builds each chunk, the way it
     * does for the preview's generator.
     *
     * @param stack null, or one with nothing switched on, for the generator as it is
     * @return what went wrong, or null if the whole area was regenerated
     */
    public static Message regenerate(World world, int chunkX, int chunkZ, int radius, Stack stack) {
        ChunkSource cache = world.getChunkSource();
        ChunkSource generator = generatorOf(cache);
        if (generator == null) return Message.of("message.developermode.cannot_regenerate");
        boolean transforming = stack != null && stack.active();
        if (transforming && !Noise.knows(generator)) return Message.of("message.developermode.no_transforms");
        TransformedNoise noise = transforming ? new TransformedNoise(generator, world, stack, new HashMap<>()) : null;

        int across = across(radius);
        int fromX = chunkX - radius;
        int fromZ = chunkZ - radius;
        FlattenedChunk[] live = new FlattenedChunk[across * across];
        for (int i = 0; i < live.length; i++) {
            int x = fromX + i % across;
            int z = fromZ + i / across;
            // Asking the cache for a chunk it does not have would load or
            // generate it, and a chunk nobody can see is not one being looked at.
            if (!cache.isChunkLoaded(x, z)) return Message.of("message.developermode.area_not_loaded");
            if (!(cache.getChunk(x, z) instanceof FlattenedChunk chunk))
                return Message.of("message.developermode.cannot_regenerate");
            live[i] = chunk;
        }

        FlattenedChunk[] fresh = new FlattenedChunk[live.length];
        for (int i = 0; i < live.length; i++) {
            int x = fromX + i % across;
            int z = fromZ + i / across;
            Chunk made;
            try {
                made = noise == null
                        ? generator.getChunk(x, z)
                        : Substitution.during(generator, noise.transformed(x, z), () -> generator.getChunk(x, z));
            } catch (RuntimeException | LinkageError error) {
                DeveloperMode.LOGGER.error("Generating chunk {}, {} threw, so nothing was regenerated", x, z, error);
                return Message.of("message.developermode.generator_threw", error.getClass().getSimpleName(), x, z);
            }
            if (!(made instanceof FlattenedChunk chunk)) return Message.of("message.developermode.cannot_regenerate");
            fresh[i] = chunk;
        }

        boolean[][] had = new boolean[live.length][];
        for (int i = 0; i < live.length; i++) {
            had[i] = occupied(live[i]);
            empty(world, live[i]);
            replace(live[i], fresh[i]);
        }

        Message failure = decorate(cache, live);
        relight(world, live);
        resend(world, live, had);
        return failure;
    }

    /**
     * The generator the world's chunks come from, or null for a chunk cache
     * this does not know.
     */
    private static ChunkSource generatorOf(ChunkSource cache) {
        if (cache instanceof ChunkCacheAccessor local) return local.getGenerator();
        // A lambda rather than a method reference, so the server only class is
        // not loaded on the side that never calls it.
        return SideUtil.get(() -> null, () -> ServerGenerator.of(cache));
    }

    /** Which of a chunk's sections hold anything at all. */
    private static boolean[] occupied(FlattenedChunk chunk) {
        boolean[] occupied = new boolean[chunk.sections.length];
        for (int i = 0; i < occupied.length; i++) occupied[i] = chunk.sections[i] != null;
        return occupied;
    }

    private static void empty(World world, Chunk chunk) {
        for (List<?> slice : chunk.entities) {
            for (Object loaded : slice) {
                Entity entity = (Entity) loaded;
                // A player stays, and so does what one is riding, or they
                // would be thrown out of it.
                if (entity instanceof PlayerEntity || entity.passenger instanceof PlayerEntity) continue;
                entity.markDead();
            }
        }

        for (Object loaded : chunk.blockEntities.values()) {
            BlockEntity blockEntity = (BlockEntity) loaded;
            blockEntity.markRemoved();
            // Out of the world's list now rather than on its next tick. The
            // tick would also take away whatever block entity stands at the
            // same spot by then, which, since decoration is deterministic, is
            // often a new one just like it.
            world.blockEntities.remove(blockEntity);
        }
        chunk.blockEntities.clear();
    }

    private static void replace(FlattenedChunk live, FlattenedChunk fresh) {
        System.arraycopy(fresh.sections, 0, live.sections, 0, live.sections.length);
        // The heightmap and the sky light straight down each column belong to
        // the blocks, so they are worked out again from the new ones.
        live.populateHeightMap();
        live.markDirty();
    }

    /** @return what went wrong, or null if every chunk was decorated */
    private static Message decorate(ChunkSource cache, FlattenedChunk[] chunks) {
        for (FlattenedChunk chunk : chunks) {
            // Cleared one at a time, right before its turn, so a failure
            // leaves the rest marked decorated, and the cache does not set
            // about decorating them later on its own.
            chunk.terrainPopulated = false;
            try {
                cache.decorate(cache, chunk.x, chunk.z);
            } catch (RuntimeException | LinkageError error) {
                DeveloperMode.LOGGER.error("Decorating chunk {}, {} threw", chunk.x, chunk.z, error);
                return Message.of("message.developermode.decoration_threw", chunk.x, chunk.z, error.getClass().getSimpleName());
            }
        }
        return null;
    }

    /**
     * Light worked out again across the whole area, a section at a time.
     *
     * <p>The new blocks come with sky light straight down each column and no
     * block light at all, so light that spreads sideways, under an overhang or
     * from lava, would be missing, and so would the light the neighbors
     * outside get from the area. Beta's light update spreads on its own from
     * wherever it changes something, but it refuses a box over 32768 blocks,
     * which a section stays under.
     *
     * <p>It is worked through here rather than over the next ticks, so the
     * sections sent to players carry their light. A client takes the light in
     * a section it is sent as it comes, and nothing tells it about a change of
     * light alone.
     */
    private static void relight(World world, FlattenedChunk[] chunks) {
        // Sections above the highest block are empty air, lit by default.
        // One more above it holds whatever spreads up out of the top.
        int top = 0;
        for (FlattenedChunk chunk : chunks)
            for (int i = chunk.sections.length - 1; i > top; i--)
                if (chunk.sections[i] != null) top = i;
        top = Math.min(top + 1, world.countVerticalSections() - 1);

        for (FlattenedChunk chunk : chunks) {
            int x = chunk.x << 4;
            int z = chunk.z << 4;
            for (int i = 0; i <= top; i++) {
                int y = world.sectionIndexToCoord(i) << 4;
                world.queueLightUpdate(LightType.SKY, x, y, z, x + 15, y + 15, z + 15);
                world.queueLightUpdate(LightType.BLOCK, x, y, z, x + 15, y + 15, z + 15);
            }
        }
        while (world.doLightingUpdates()) {
            // Each call works through a few hundred and says whether any are left.
        }
    }

    private static void resend(World world, FlattenedChunk[] chunks, boolean[][] had) {
        for (int c = 0; c < chunks.length; c++) {
            FlattenedChunk chunk = chunks[c];
            int x = chunk.x << 4;
            int z = chunk.z << 4;

            // For whoever draws this world, which on a client is this one.
            world.setBlocksDirty(x, world.getBottomY(), z, x + 15, world.getTopY() - 1, z + 15);

            // For whoever is sent it. A section that is empty now still goes
            // if it held anything before, or the players watching keep it.
            for (int i = 0; i < chunk.sections.length; i++) {
                if (!had[c][i] && chunk.sections[i] == null) continue;
                int y = world.sectionIndexToCoord(i) << 4;
                for (int[] mark : SECTION_MARKS) world.blockUpdateEvent(x + mark[0], y + mark[1], z + mark[2]);
            }
        }
    }
}
