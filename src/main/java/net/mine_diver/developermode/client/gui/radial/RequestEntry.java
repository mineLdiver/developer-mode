package net.mine_diver.developermode.client.gui.radial;

import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.api.setting.LockableSetting;
import net.mine_diver.developermode.api.setting.Setting;
import net.mine_diver.developermode.api.setting.Switch;
import net.mine_diver.developermode.api.setting.SwitchSetting;
import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.Lang;
import net.mine_diver.developermode.client.preset.Preset;
import net.mine_diver.developermode.client.preset.Presets;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.setting.SettingChange;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.modificationstation.stationapi.api.util.StringIdentifiable;

import java.util.List;

/**
 * Slots that ask the world for something, which all have the same problem.
 *
 * <p>Every one of them is a request the world is entitled to turn down, and a
 * refusal looks exactly like a grant from in here: nothing opens, and nothing
 * in the world says anything. So each one gives up its description for the
 * reason the last attempt failed, and takes it back once one succeeds.
 *
 * <p>A slot for a setting is named by the setting and hears back under it, so
 * every slot one setting has shares its last refusal: the four times of day
 * all say why the sun would not move.
 */
final class RequestEntry {
    private RequestEntry() {}

    /**
     * A switch on a setting that is on or off.
     *
     * <p>Lit from the setting rather than from what it last asked for, so the
     * ring shows what was granted rather than what was wanted.
     */
    static RadialEntry toggle(SwitchSetting setting, ItemStack icon) {
        return new RadialEntry(setting::translationKey, icon,
                returnTo -> request(setting, SettingChange.set(setting, Switch.of(!isOn(setting))))) {
            @Override
            public Badge badge() {
                return Badge.SWITCH;
            }

            @Override
            public boolean on() {
                return isOn(setting);
            }

            @Override
            public String label() {
                // Said twice, the way a slot that leads somewhere is: lit in
                // the ring, and carried on the name, since the name is what
                // gets read before the click.
                return super.label() + "  " + Lang.get(setting.valueKey(Switch.of(on())));
            }

            @Override
            public String hint() {
                return said(answerAs(setting), super.hint());
            }
        };
    }

    /**
     * One of the values of a setting, lit while it is the one the setting is
     * at, and one it can be locked to if the setting can be locked.
     *
     * <p>Read from what this client holds rather than from what was last
     * asked for, for the same reason a switch is: the ring shows what
     * happened. Choosing the lit one again is not refused, since putting the
     * world where it already is does no harm and is sometimes the point.
     */
    static <V extends Enum<V> & StringIdentifiable> RadialEntry choice(Setting<V> setting, V value, ItemStack icon) {
        LockableSetting<V> lockable = setting instanceof LockableSetting<V> it ? it : null;
        return new RadialEntry(() -> setting.valueKey(value), icon,
                returnTo -> request(setting, SettingChange.set(setting, value))) {
            @Override
            public boolean on() {
                PlayerEntity player = player();
                return player != null && setting.current(player) == value;
            }

            @Override
            public boolean lockable() {
                return lockable != null;
            }

            @Override
            public boolean locked() {
                PlayerEntity player = player();
                return player != null && lockable != null && lockable.locked(player) == value;
            }

            @Override
            public void toggleLock() {
                if (lockable == null) return;
                request(setting, locked() ? SettingChange.unlock(lockable) : SettingChange.lock(lockable, value));
            }

            @Override
            public String label() {
                return locked()
                        ? super.label() + "  " + Lang.get("gui.developermode.radial.locked")
                        : super.label();
            }

            @Override
            public String hint() {
                return said(answerAs(setting), super.hint());
            }
        };
    }

    /**
     * A preset, lit while everything it keeps is in place.
     *
     * <p>A switch when it keeps something, since choosing it again lets go of
     * it. One that only moves what drifts carries no mark, because it does
     * what a click on each of those slots does, and nothing more.
     *
     * <p>Its hint is what is in it, since a preset is named by whoever made it
     * and the name is no promise about what it does.
     */
    static RadialEntry preset(Preset preset) {
        ItemStack icon = preset.icon() == null ? new ItemStack(Item.BOOK) : preset.icon();
        return new RadialEntry("gui.developermode.radial.preset", icon, returnTo -> Presets.choose(preset, player())) {
            @Override
            public Badge badge() {
                return preset.lights() ? Badge.SWITCH : Badge.NONE;
            }

            @Override
            public boolean on() {
                return preset.inEffect(player());
            }

            @Override
            public String label() {
                // Named in its maker's own words rather than by a key.
                String name = preset.name();
                if (!preset.lights()) return name;
                return name + "  " + Lang.get(Switch.of(on()).translationKey());
            }

            @Override
            public String hint() {
                return said(DevStatus.PRESET, Presets.describe(preset));
            }
        };
    }

    /** A one off, which leaves nothing behind but whether it worked. */
    static RadialEntry action(String kind, String key, ItemStack icon, RadialAction action) {
        return new RadialEntry(key, icon, action) {
            @Override
            public String hint() {
                return said(kind, super.hint());
            }
        };
    }

    private static boolean isOn(SwitchSetting setting) {
        PlayerEntity player = player();
        return player != null && setting.isOn(player);
    }

    private static void request(Setting<?> setting, SettingChange change) {
        SettingChange.request(answerAs(setting), List.of(change));
    }

    /** The status a setting's slots hear back under, which is the setting's own name. */
    private static String answerAs(Setting<?> setting) {
        return String.valueOf(setting.id());
    }

    /** Why the last request of this kind was refused, or what the slot is for. */
    private static String said(String kind, String describes) {
        Message refusal = DevStatus.ok(kind) ? null : DevStatus.message(kind);
        return refusal == null ? describes : Lang.of(refusal);
    }

    /** The player these slots are about, or null before there is one. */
    private static PlayerEntity player() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        return minecraft == null ? null : minecraft.player;
    }
}
