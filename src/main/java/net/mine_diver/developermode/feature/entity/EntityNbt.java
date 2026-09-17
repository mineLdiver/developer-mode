package net.mine_diver.developermode.feature.entity;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;

/**
 * Reading an entity out to NBT and writing edited NBT back in.
 */
public final class EntityNbt {
    private EntityNbt() {}

    public static NbtCompound dump(Entity entity) {
        NbtCompound nbt = new NbtCompound();
        entity.write(nbt);
        return nbt;
    }

    /**
     * Writes {@code nbt} back into the entity.
     *
     * <p>Entity.read trusts its input: a missing Pos list or a short Rotation
     * throws straight out of it, halfway through, leaving the entity in a
     * mangled state. So take a snapshot first and put it back if anything goes
     * wrong. Crashing the game over a mistyped field is not acceptable in a
     * tool whose whole job is mistyped fields.
     *
     * @return null on success, or a message describing what went wrong
     */
    public static String apply(Entity entity, NbtCompound nbt) {
        NbtCompound rollback = dump(entity);
        try {
            entity.read(nbt);
            return null;
        } catch (Throwable error) {
            try {
                entity.read(rollback);
            } catch (Throwable ignored) {
                // Nothing better to try; the snapshot came straight off this
                // entity moments ago, so this should not be reachable.
            }
            return describe(error);
        }
    }

    /** How to put an exception in a status line. */
    public static String describe(Throwable error) {
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        return message == null || message.isEmpty() ? type : type + ": " + message;
    }
}
