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
 */
public abstract class SwitchSetting implements Setting<Switch> {
    private static final List<Switch> VALUES = List.of(Switch.values());

    /** Whether it is on for this player, as {@link Setting#current} would say. */
    public abstract boolean isOn(PlayerEntity player);

    /** @return why it could not be, or null if it is done */
    protected abstract @Nullable Message switchTo(PlayerEntity player, boolean on);

    @Override
    public final List<Switch> values() {
        return VALUES;
    }

    @Override
    public final boolean stays() {
        return true;
    }

    @Override
    public final Switch current(PlayerEntity player) {
        return Switch.of(isOn(player));
    }

    @Override
    public final @Nullable Message set(PlayerEntity player, Switch value) {
        return switchTo(player, value.isOn());
    }

    @Override
    public String valueKey(Switch value) {
        return value.translationKey();
    }
}
