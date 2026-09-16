package net.mine_diver.developermode.client;

import net.mine_diver.developermode.feature.net.FreezeMode;
import net.mine_diver.developermode.feature.net.packet.FreezeC2SPacket;
import net.minecraft.entity.Entity;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;

/**
 * Asking the world that owns an entity to stop ticking it.
 *
 * <p>Sent on changes of mind rather than continuously. A window re-asserts its
 * interest locally every tick, which costs nothing, but saying so over a wire
 * twenty times a second would.
 */
public final class Freezing {
    private static final int NOTHING = -1;

    private Freezing() {}

    public static void freezeWhileEditing(Entity entity) {
        send(FreezeMode.EDITING, entity);
    }

    public static void stopEditing(Entity entity) {
        send(FreezeMode.STOP_EDITING, entity);
    }

    public static void hold(Entity entity) {
        send(FreezeMode.HOLD, entity);
    }

    public static void release(Entity entity) {
        send(FreezeMode.RELEASE, entity);
    }

    public static void releaseAutomatic() {
        send(FreezeMode.RELEASE_AUTOMATIC, null);
    }

    public static void releaseAll() {
        send(FreezeMode.RELEASE_ALL, null);
    }

    private static void send(byte mode, Entity entity) {
        if (FreezeMode.needsEntity(mode) && entity == null) return;
        PacketHelper.send(new FreezeC2SPacket(entity == null ? NOTHING : entity.id, mode));
    }
}
