package net.mine_diver.developermode.feature.setting;

import net.mine_diver.developermode.api.Message;
import net.mine_diver.developermode.api.setting.SwitchSetting;
import net.mine_diver.developermode.feature.net.packet.PowersS2CPacket;
import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.developermode.feature.player.Powers;
import net.minecraft.entity.player.PlayerEntity;
import net.modificationstation.stationapi.api.network.packet.PacketHelper;

/**
 * One of the player's powers, as a setting.
 *
 * <p>The powers are kept together, as one mask on the player, and each of
 * these is one bit of it. Switching one changes that bit and tells the client
 * the whole mask, since the mask is what the client mirrors.
 *
 * <p>Noclip rests on flight, and {@link Power#legal} drops it whenever flight
 * is off. So switching noclip on switches flight on with it, rather than
 * being dropped straight away, and switching flight off takes noclip with it.
 */
final class PowerSetting extends SwitchSetting {
    private final int power;

    PowerSetting(int power) {
        this.power = power;
    }

    @Override
    public boolean isOn(PlayerEntity player) {
        return Powers.has(player, power);
    }

    @Override
    protected Message switchTo(PlayerEntity player, boolean on) {
        int mask = on ? Powers.of(player) | power : Powers.of(player) & ~power;
        if (on && power == Power.NOCLIP) mask |= Power.FLIGHT;
        Powers.set(player, mask);
        PacketHelper.sendTo(player, new PowersS2CPacket(Powers.of(player)));
        return null;
    }
}
