package net.mine_diver.developermode.api.setting;

import net.mine_diver.developermode.api.Message;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A setting that is either on or off, and stays that way.
 *
 * <p>Most things worth switching during a test are this shape, so it is
 * worth a base of its own: implement {@link #isOn} and {@link #switchTo}, and
 * everything else a setting is asked follows from them. The ring shows one as
 * a switch rather than as a choice between two slots.
 *
 * <p>Its two values are named the same for every switch, so a switch only
 * needs translations for its own name and hint.
 */
public abstract class SwitchSetting implements Setting {
    public static final String OFF = "off";
    public static final String ON = "on";

    private static final List<String> VALUES = List.of(OFF, ON);

    /** Whether it is on for this player, as {@link Setting#current} would say. */
    public abstract boolean isOn(PlayerEntity player);

    /** @return why it could not be, or null if it is done */
    protected abstract @Nullable Message switchTo(PlayerEntity player, boolean on);

    @Override
    public final List<String> values() {
        return VALUES;
    }

    @Override
    public final boolean stays() {
        return true;
    }

    @Override
    public final String current(PlayerEntity player) {
        return isOn(player) ? ON : OFF;
    }

    @Override
    public final @Nullable Message set(PlayerEntity player, String value) {
        return switchTo(player, ON.equals(value));
    }

    @Override
    public String valueKey(String value) {
        return "gui.developermode.setting." + value;
    }
}
