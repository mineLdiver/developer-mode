package net.mine_diver.developermode.feature.entity;

import net.minecraft.entity.Entity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * Entities whose ticking is suppressed, so they hold still while you edit them.
 *
 * <p>There are two ways in, and the difference is who is responsible for
 * undoing it. An <em>automatic</em> freeze belongs to whatever window is
 * looking at the entity and lasts exactly as long as the developer UI is on
 * screen; nothing has to remember to undo it, because
 * {@link net.mine_diver.developermode.client.DeveloperUi} drops them all every
 * frame the UI is not up. A <em>held</em> freeze is one you asked for by
 * pressing the button: it survives closing everything, and is announced on the
 * HUD so it cannot be forgotten about.
 *
 * <p>Held by identity rather than by entity id: ids are recycled between
 * worlds, and a stale entry must never freeze some unrelated entity later.
 *
 * @see net.mine_diver.developermode.mixin.WorldMixin
 */
public final class FrozenEntities {
    private static final Set<Entity> held = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Entity> automatic = Collections.newSetFromMap(new IdentityHashMap<>());

    private FrozenEntities() {}

    public static boolean isFrozen(Entity entity) {
        return (!held.isEmpty() && held.contains(entity))
                || (!automatic.isEmpty() && automatic.contains(entity));
    }

    /** True only for a freeze you asked for, which is the kind that outlives the UI. */
    public static boolean isHeld(Entity entity) {
        return !held.isEmpty() && held.contains(entity);
    }

    /** Deliberate and sticky. Survives closing every screen. */
    public static void hold(Entity entity) {
        automatic.remove(entity);
        if (held.add(entity)) snap(entity);
    }

    /** Drops both kinds for one entity. */
    public static void release(Entity entity) {
        held.remove(entity);
        automatic.remove(entity);
    }

    /**
     * Freezes for as long as a window is looking at it. Re-assert this freely:
     * it is idempotent, and re-asserting every tick is how a window says it is
     * still interested.
     */
    public static void freezeWhileEditing(Entity entity) {
        if (held.contains(entity)) return;
        if (automatic.add(entity)) snap(entity);
    }

    public static void stopEditing(Entity entity) {
        automatic.remove(entity);
    }

    /**
     * Drops every automatic freeze. Called once a frame while the developer UI
     * is closed, which is what keeps an editor left open on the desktop from
     * quietly holding a mob still for the rest of the session.
     */
    public static void releaseAutomatic() {
        automatic.clear();
    }

    public static void releaseAll() {
        held.clear();
        automatic.clear();
    }

    public static int heldCount() {
        prune();
        return held.size();
    }

    public static int frozenCount() {
        prune();
        int automaticOnly = 0;
        for (Entity entity : automatic) {
            if (!held.contains(entity)) automaticOnly++;
        }
        return held.size() + automaticOnly;
    }

    /** Drops entities that died or left while frozen. */
    public static void prune() {
        removeDead(held);
        removeDead(automatic);
    }

    private static void removeDead(Set<Entity> entities) {
        for (Iterator<Entity> iterator = entities.iterator(); iterator.hasNext(); ) {
            if (iterator.next().dead) iterator.remove();
        }
    }

    private static void snap(Entity entity) {
        // Ticking is what normally copies the current position into the
        // previous one. With it suppressed, the renderer would interpolate
        // between two stale values forever, so collapse them here.
        entity.lastTickX = entity.prevX = entity.x;
        entity.lastTickY = entity.prevY = entity.y;
        entity.lastTickZ = entity.prevZ = entity.z;
        entity.prevYaw = entity.yaw;
        entity.prevPitch = entity.pitch;

        // Entity.write adds cameraOffset to the saved Y but Entity.read never
        // takes it back off, so a non zero one makes every dump and reapply
        // nudge the entity upwards. It is only ever mid decay step smoothing,
        // and decay needs ticking, which is exactly what we are stopping.
        entity.cameraOffset = 0;
    }
}
