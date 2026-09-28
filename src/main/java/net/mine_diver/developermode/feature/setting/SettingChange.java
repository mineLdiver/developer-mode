package net.mine_diver.developermode.feature.setting;

import net.mine_diver.developermode.api.setting.Setting;
import net.mine_diver.developermode.feature.net.packet.SettingsC2SPacket;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;

import java.util.List;

/**
 * One thing to do to a setting, which is what a slot or a preset asks for.
 *
 * <p>Always a state wanted, never a flip. A change says what the setting
 * should be, so two requests that cross on the wire, or several in one, cannot
 * leave it the opposite of what any of them asked for.
 *
 * @param value the value to set or lock at, and empty for an unlock
 */
public record SettingChange(Setting setting, Kind kind, String value) {
    public enum Kind {
        SET,
        LOCK,
        UNLOCK
    }

    public static SettingChange set(Setting setting, String value) {
        return new SettingChange(setting, Kind.SET, value);
    }

    public static SettingChange lock(Setting setting, String value) {
        return new SettingChange(setting, Kind.LOCK, value);
    }

    public static SettingChange unlock(Setting setting) {
        return new SettingChange(setting, Kind.UNLOCK, "");
    }

    /**
     * Asks for all of them at once, and has the answer come back as a status
     * of the given kind, which is whichever one the asker is showing.
     */
    public static void request(String answerAs, List<SettingChange> changes) {
        if (!changes.isEmpty()) PacketHelper.send(new SettingsC2SPacket(answerAs, changes));
    }
}
