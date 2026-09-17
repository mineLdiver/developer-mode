package net.mine_diver.developermode.feature.net;

import net.mine_diver.developermode.feature.entity.Entities;
import net.mine_diver.developermode.feature.entity.EntityNbt;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

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
 * @param x    entity id, block x, or the sync id of an open container
 * @param y    block y, or a slot's index in that container
 * @param z    block z
 */
public record NbtTarget(byte kind, int x, int y, int z) {
    public static final byte ENTITY = 0;
    public static final byte BLOCK = 1;
    public static final byte SLOT = 2;

    /** Bytes on the wire: the kind and three numbers. */
    public static final int SIZE = 1 + Integer.BYTES * 3;

    public static NbtTarget entity(int entityId) {
        return new NbtTarget(ENTITY, entityId, 0, 0);
    }

    public static NbtTarget block(int x, int y, int z) {
        return new NbtTarget(BLOCK, x, y, z);
    }

    /**
     * A slot in whatever container the sender has open.
     *
     * <p>The sync id comes along so that a slot number means something. It
     * identifies which container the client was looking at, and a client that
     * has since opened a different one is talking about a slot that no longer
     * exists.
     *
     * <p>A player's own inventory is sync id zero and is open for as long as
     * they are, so a slot in it can be read and written whenever. Any other
     * container closes when the editor takes the screen, which is after the
     * read has been asked for and before a write could arrive: those slots
     * read, and a write to one says the container is closed.
     */
    public static NbtTarget slot(int syncId, int slotId) {
        return new NbtTarget(SLOT, syncId, slotId, 0);
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
            case SLOT -> {
                ItemStack stack = stackIn(player);
                return stack == null ? null : stack.writeNbt(new NbtCompound());
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
            case SLOT -> {
                ScreenHandler handler = player.currentScreenHandler;
                if (handler == null || handler.syncId != x) return "That container is closed";

                ItemStack stack = stackIn(player);
                if (stack == null) return "That slot is empty";

                String failure = applyTo(stack, nbt);
                if (failure != null) return failure;

                // Comparing against a copy taken earlier is how a container
                // notices a change, and everything written here counts towards
                // that, so an edit made now is a change it will send on.
                handler.sendContentUpdates();
                return null;
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

    /**
     * Writes NBT into a stack, and puts it back if that goes wrong.
     *
     * <p>The whole stack, not just what a mod added to it: the id, the count
     * and the damage are as much a part of what a stack is as the rest, and
     * reading them back is how the id can be changed at all.
     */
    private static String applyTo(ItemStack stack, NbtCompound nbt) {
        NbtCompound rollback = stack.writeNbt(new NbtCompound());
        try {
            stack.readNbt(nbt);
            return null;
        } catch (Throwable error) {
            try {
                stack.readNbt(rollback);
            } catch (Throwable ignored) {
                // Nothing better to try; the snapshot came off this stack a
                // moment ago.
            }
            return EntityNbt.describe(error);
        }
    }

    /**
     * The stack this points at, if the sender still has that container open.
     */
    private ItemStack stackIn(PlayerEntity player) {
        ScreenHandler handler = player.currentScreenHandler;
        if (handler == null || handler.syncId != x) return null;
        if (y < 0 || y >= handler.slots.size()) return null;

        Slot slot = handler.getSlot(y);
        return slot == null ? null : slot.getStack();
    }

    /** How to say what this points at, in a status message. */
    public String describe() {
        return switch (kind) {
            case ENTITY -> "entity " + x;
            case BLOCK -> "block entity at " + x + " " + y + " " + z;
            case SLOT -> "slot " + y;
            default -> "nothing";
        };
    }
}
