package net.mine_diver.developermode.feature.net;

import net.minecraft.nbt.NbtCompound;

/**
 * The last dump the world sent back, and what it was of.
 *
 * <p>A client's copy of an entity is kept roughly in step by position updates
 * rather than being the entity, and a client may not have a block entity at all
 * until something tells it to. So the editor asks instead of reading, and the
 * answer lands here.
 *
 * <p>The target comes back with it, because by the time an answer arrives the
 * editor may have been pointed at something else.
 */
public final class NbtInbox {
    private static NbtTarget target;
    private static NbtCompound nbt;
    private static int sequence;

    private NbtInbox() {}

    public static void set(NbtTarget of, NbtCompound compound) {
        target = of;
        nbt = compound;
        sequence++;
    }

    public static NbtTarget target() {
        return target;
    }

    public static NbtCompound nbt() {
        return nbt;
    }

    public static int sequence() {
        return sequence;
    }
}
