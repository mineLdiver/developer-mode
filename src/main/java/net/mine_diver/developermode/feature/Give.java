package net.mine_diver.developermode.feature;

import net.mine_diver.developermode.feature.net.packet.GiveC2SPacket;
import net.minecraft.item.ItemStack;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;

/**
 * Puts a stack in the player's inventory.
 *
 * <p>Beta has no creative mode and no packet that lets a client conjure items,
 * so the stack is asked for rather than taken: the request goes to whoever has
 * the authority to grant it, which on a server is the server and in a local
 * world is this same process a moment later.
 *
 * <p>The answer arrives as a packet rather than a return value, so it lands in
 * {@link net.mine_diver.developermode.feature.net.DevStatus} instead of coming
 * back from here.
 */
public final class Give {
    private Give() {}

    public static void give(ItemStack stack) {
        PacketHelper.send(new GiveC2SPacket(stack));
    }
}
