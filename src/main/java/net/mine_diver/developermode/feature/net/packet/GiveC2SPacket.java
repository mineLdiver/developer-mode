package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.net.Ops;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkHandler;
import net.minecraft.network.packet.Packet;
import net.modificationstation.stationapi.api.entity.player.PlayerHelper;
import net.modificationstation.stationapi.api.network.packet.ManagedPacket;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;
import net.modificationstation.stationapi.api.network.packet.PacketType;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A request for a stack, answered by putting one in the sender's inventory.
 *
 * <p>Beta has no creative mode, so there is no vanilla packet a client could
 * use to conjure an item and no server that would honor one. This is that
 * packet, and the reason the whole feature needs a mod on both sides.
 *
 * <p>Everything in it is checked again here. The sender is a client, the fields
 * arrived over a socket, and being an operator is permission to spawn items,
 * not permission to be believed about what an item is.
 */
public class GiveC2SPacket extends Packet implements ManagedPacket<GiveC2SPacket> {
    public static final PacketType<GiveC2SPacket> TYPE =
            PacketType.builder(false, true, GiveC2SPacket::new).build();

    public int itemId;
    public int count;
    public int damage;

    public GiveC2SPacket() {}

    public GiveC2SPacket(ItemStack stack) {
        itemId = stack.itemId;
        count = stack.count;
        damage = stack.getDamage();
    }

    @Override
    public void read(DataInputStream in) {
        try {
            itemId = in.readInt();
            count = in.readInt();
            damage = in.readInt();
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            out.writeInt(itemId);
            out.writeInt(count);
            out.writeInt(damage);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void apply(NetworkHandler handler) {
        PlayerEntity player = PlayerHelper.getPlayerFromPacketHandler(handler);
        if (player == null) return;

        if (!Ops.allows(player)) {
            reply(player, "Requires operator", false);
            return;
        }
        if (itemId < 0 || itemId >= Item.ITEMS.length || Item.ITEMS[itemId] == null) {
            reply(player, "No such item", false);
            return;
        }

        Item item = Item.ITEMS[itemId];
        int given = Math.max(1, Math.min(count, item.getMaxCount()));
        boolean added = player.inventory.addStack(new ItemStack(itemId, given, damage));
        reply(player, added ? "Given " + given : "Inventory full", added);
    }

    private static void reply(PlayerEntity player, String text, boolean ok) {
        PacketHelper.sendTo(player, new StatusS2CPacket(DevStatus.GIVE, text, ok));
    }

    @Override
    public int size() {
        return Integer.BYTES * 3;
    }

    @Override
    public @NotNull PacketType<GiveC2SPacket> getType() {
        return TYPE;
    }
}
