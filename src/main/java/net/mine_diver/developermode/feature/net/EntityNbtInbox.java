package net.mine_diver.developermode.feature.net;

import net.minecraft.nbt.NbtCompound;

/**
 * The last entity dump the world sent back.
 *
 * <p>An entity on a client is a copy kept roughly in step by position updates,
 * not the entity itself, so its NBT is whatever the client happened to build
 * rather than what the world is holding. The editor asks instead of reading,
 * and the answer lands here.
 *
 * <p>The id comes back with it, because by the time an answer arrives the
 * editor may have been pointed at something else.
 */
public final class EntityNbtInbox {
    private static int entityId = -1;
    private static NbtCompound nbt;
    private static int sequence;

    private EntityNbtInbox() {}

    public static void set(int id, NbtCompound compound) {
        entityId = id;
        nbt = compound;
        sequence++;
    }

    public static int entityId() {
        return entityId;
    }

    public static NbtCompound nbt() {
        return nbt;
    }

    public static int sequence() {
        return sequence;
    }
}
