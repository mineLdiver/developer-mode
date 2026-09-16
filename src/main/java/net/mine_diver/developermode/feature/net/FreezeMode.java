package net.mine_diver.developermode.feature.net;

import net.mine_diver.developermode.feature.entity.FrozenEntities;
import net.minecraft.entity.Entity;

/**
 * The things one side can ask the other to do about freezing.
 *
 * <p>Both directions carry the same set. A client asks for one, and the world
 * that owns the entity carries it out and sends the same one back, so what the
 * client ends up mirroring is what actually happened rather than what it hoped
 * would.
 */
public final class FreezeMode {
    public static final byte EDITING = 0;
    public static final byte STOP_EDITING = 1;
    public static final byte HOLD = 2;
    public static final byte RELEASE = 3;
    public static final byte RELEASE_AUTOMATIC = 4;
    public static final byte RELEASE_ALL = 5;

    private FreezeMode() {}

    /** Whether this mode is about one entity rather than all of somebody's. */
    public static boolean needsEntity(byte mode) {
        return mode == EDITING || mode == STOP_EDITING || mode == HOLD || mode == RELEASE;
    }

    /**
     * @param owner who to record the freeze against, or null to own nothing in
     *              particular and to mean everyone's when releasing
     */
    public static void apply(byte mode, Entity entity, Object owner) {
        if (needsEntity(mode) && entity == null) return;
        switch (mode) {
            case EDITING -> FrozenEntities.freezeWhileEditing(entity, owner);
            case STOP_EDITING -> FrozenEntities.stopEditing(entity);
            case HOLD -> FrozenEntities.hold(entity, owner);
            case RELEASE -> FrozenEntities.release(entity);
            case RELEASE_AUTOMATIC -> FrozenEntities.releaseAutomatic(owner);
            case RELEASE_ALL -> FrozenEntities.releaseAll(owner);
            default -> {}
        }
    }
}
