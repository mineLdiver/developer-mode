package net.mine_diver.developermode.feature.net;

import net.mine_diver.developermode.feature.entity.Entities;
import net.mine_diver.developermode.feature.entity.EntityNbt;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Something with NBT worth looking at, named in a way both sides agree on.
 *
 * <p>Handles rather than references. An entity id and a block position mean the
 * same thing on a client and on a server, where an object does not: a client
 * holds copies, and the copy is not what the world is about to save.
 *
 * @param kind which of the things below this points at
 * @param x    entity id, or block x
 * @param y    block y
 * @param z    block z
 */
public record NbtTarget(byte kind, int x, int y, int z) {
    public static final byte ENTITY = 0;
    public static final byte BLOCK = 1;

    /** Bytes on the wire: the kind and three numbers. */
    public static final int SIZE = 1 + Integer.BYTES * 3;

    public static NbtTarget entity(int entityId) {
        return new NbtTarget(ENTITY, entityId, 0, 0);
    }

    public static NbtTarget block(int x, int y, int z) {
        return new NbtTarget(BLOCK, x, y, z);
    }

    public static NbtTarget read(DataInputStream in) throws IOException {
        return new NbtTarget(in.readByte(), in.readInt(), in.readInt(), in.readInt());
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeByte(kind);
        out.writeInt(x);
        out.writeInt(y);
        out.writeInt(z);
    }

    /** @return what the world is holding, or null if this points at nothing */
    public NbtCompound dump(PlayerEntity player) {
        switch (kind) {
            case ENTITY -> {
                Entity entity = Entities.byId(player.world, x);
                return entity == null ? null : EntityNbt.dump(entity);
            }
            case BLOCK -> {
                BlockEntity blockEntity = player.world.getBlockEntity(x, y, z);
                if (blockEntity == null) return null;

                NbtCompound nbt = new NbtCompound();
                blockEntity.writeNbt(nbt);
                return nbt;
            }
            default -> {
                return null;
            }
        }
    }

    /** @return null on success, or a message describing what went wrong */
    public String apply(PlayerEntity player, NbtCompound nbt) {
        switch (kind) {
            case ENTITY -> {
                Entity entity = Entities.byId(player.world, x);
                return entity == null ? "That entity is gone" : EntityNbt.apply(entity, nbt);
            }
            case BLOCK -> {
                BlockEntity blockEntity = player.world.getBlockEntity(x, y, z);
                return blockEntity == null ? "Nothing there" : applyTo(blockEntity, nbt);
            }
            default -> {
                return "Nothing to write to";
            }
        }
    }

    /**
     * Writes NBT into a block entity, and puts it back if that goes wrong.
     *
     * <p>{@code readNbt} takes the block entity's position out of the NBT it is
     * given, so a dump edited anywhere near those three fields would move it
     * off the block it belongs to and leave the world with one it can no longer
     * find. The position it actually sits at wins.
     */
    private String applyTo(BlockEntity blockEntity, NbtCompound nbt) {
        NbtCompound rollback = new NbtCompound();
        blockEntity.writeNbt(rollback);

        try {
            blockEntity.readNbt(nbt);
        } catch (Throwable error) {
            try {
                blockEntity.readNbt(rollback);
            } catch (Throwable ignored) {
                // Nothing better to try; the snapshot came off this block
                // entity a moment ago.
            }
            return EntityNbt.describe(error);
        }

        blockEntity.x = x;
        blockEntity.y = y;
        blockEntity.z = z;
        blockEntity.markDirty();
        return null;
    }

    /** How to say what this points at, in a status message. */
    public String describe() {
        return switch (kind) {
            case ENTITY -> "entity " + x;
            case BLOCK -> "block entity at " + x + " " + y + " " + z;
            default -> "nothing";
        };
    }
}
