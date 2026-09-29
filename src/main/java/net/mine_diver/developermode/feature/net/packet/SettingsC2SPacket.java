package net.mine_diver.developermode.feature.net.packet;

import net.mine_diver.developermode.DeveloperMode;
import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.api.setting.LockableSetting;
import net.mine_diver.developermode.api.setting.Setting;
import net.mine_diver.developermode.api.setting.SettingRegistry;
import net.mine_diver.developermode.feature.net.Ops;
import net.mine_diver.developermode.feature.setting.SettingChange;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.NetworkHandler;
import net.minecraft.network.packet.Packet;
import net.modificationstation.stationapi.api.entity.player.PlayerHelper;
import net.modificationstation.stationapi.api.network.packet.ManagedPacket;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;
import net.modificationstation.stationapi.api.network.packet.PacketType;
import net.modificationstation.stationapi.api.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Changes to any number of settings, asked for together.
 *
 * <p>A slot sends one and a preset sends several, and either way they are
 * answered once, under whatever status the sender is showing, so the reason a
 * preset did not go through is said on the preset rather than scattered over
 * whichever slots its settings also have.
 *
 * <p>Settings go by identifier and are looked up again here. Everything a
 * setting could be handed wrong is checked before it is handed anything: that
 * it exists on this side, that the value is one of its own, and that a lock
 * is only asked of one that can be locked. Every change is tried even after
 * one fails, since they are independent: a preset chosen in the Nether still
 * switches its powers, and says the sky is missing.
 */
public class SettingsC2SPacket extends Packet implements ManagedPacket<SettingsC2SPacket> {
    public static final PacketType<SettingsC2SPacket> TYPE =
            PacketType.builder(false, true, SettingsC2SPacket::new).build();

    /** More than there are settings to change, and few enough that a bad packet is refused. */
    private static final int MAX_CHANGES = 64;
    private static final int MAX_LENGTH = 128;

    private static final SettingChange.Kind[] KINDS = SettingChange.Kind.values();

    /** A change as it travels, by name, since the other side has its own objects. */
    private record Entry(String id, SettingChange.Kind kind, String value) {}

    public String answerAs = "";
    private final List<Entry> entries = new ArrayList<>();

    public SettingsC2SPacket() {}

    public SettingsC2SPacket(String answerAs, List<SettingChange> changes) {
        this.answerAs = answerAs;
        for (SettingChange change : changes)
            entries.add(new Entry(String.valueOf(change.setting().id()), change.kind(), change.value()));
    }

    @Override
    public void read(DataInputStream in) {
        try {
            answerAs = readString(in, MAX_LENGTH);
            int count = in.readUnsignedByte();
            if (count > MAX_CHANGES) throw new IOException(count + " setting changes in one request");
            for (int i = 0; i < count; i++) {
                String id = readString(in, MAX_LENGTH);
                int kind = in.readUnsignedByte();
                if (kind >= KINDS.length) throw new IOException("No such kind of setting change: " + kind);
                entries.add(new Entry(id, KINDS[kind], readString(in, MAX_LENGTH)));
            }
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void write(DataOutputStream out) {
        try {
            writeString(answerAs, out);
            out.writeByte(entries.size());
            for (Entry entry : entries) {
                writeString(entry.id, out);
                out.writeByte(entry.kind.ordinal());
                writeString(entry.value, out);
            }
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void apply(NetworkHandler handler) {
        PlayerEntity player = PlayerHelper.getPlayerFromPacketHandler(handler);
        if (player == null) return;

        if (!Ops.allows(player)) {
            reply(player, Message.of("message.developermode.requires_operator"));
            return;
        }

        Message failure = null;
        for (Entry entry : entries) {
            Message result = change(player, entry);
            if (failure == null) failure = result;
        }
        reply(player, failure);
    }

    /** @return why it could not be, or null if it is done */
    private static Message change(PlayerEntity player, Entry entry) {
        Identifier id = Identifier.tryParse(entry.id);
        Setting setting = id == null ? null : SettingRegistry.INSTANCE.get(id);
        if (setting == null) return Message.of("message.developermode.no_such_setting", entry.id);
        LockableSetting lockable = setting instanceof LockableSetting it ? it : null;
        if (entry.kind != SettingChange.Kind.SET && lockable == null)
            return Message.of("message.developermode.nothing_to_lock");
        if (entry.kind != SettingChange.Kind.UNLOCK && !setting.values().contains(entry.value))
            return Message.of("message.developermode.no_such_value", entry.value);

        // A setting can come from any mod. One that throws is reported, and
        // does not get to take the connection down with it.
        try {
            return switch (entry.kind) {
                case SET -> setting.set(player, entry.value);
                case LOCK -> lockable.lock(player, entry.value);
                case UNLOCK -> lockable.unlock(player);
            };
        } catch (RuntimeException error) {
            DeveloperMode.LOGGER.error("Setting {} failed to change for {}", entry.id, player.name, error);
            return Message.of("message.developermode.setting_failed", entry.id);
        }
    }

    private void reply(PlayerEntity player, Message message) {
        PacketHelper.sendTo(player, new StatusS2CPacket(answerAs, message, message == null));
    }

    @Override
    public int size() {
        int size = sizeOf(answerAs) + 1;
        for (Entry entry : entries) size += sizeOf(entry.id) + 1 + sizeOf(entry.value);
        return size;
    }

    private static int sizeOf(String text) {
        return Short.BYTES + text.length() * Character.BYTES;
    }

    @Override
    public @NotNull PacketType<SettingsC2SPacket> getType() {
        return TYPE;
    }
}
