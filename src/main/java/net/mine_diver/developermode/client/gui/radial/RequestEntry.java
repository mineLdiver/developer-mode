package net.mine_diver.developermode.client.gui.radial;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.feature.net.DevStatus;
import net.mine_diver.developermode.feature.player.Powers;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.function.Predicate;

/**
 * Slots that ask the world for something, which all have the same problem.
 *
 * <p>Every one of them is a request the world is entitled to turn down, and a
 * refusal looks exactly like a grant from in here: nothing opens, and nothing
 * in the world says anything. So each one gives up its description for the
 * reason the last attempt failed, and takes it back once one succeeds.
 */
final class RequestEntry {
    private RequestEntry() {}

    /**
     * A switch on one of the player's powers.
     *
     * <p>Lit from {@link Powers} rather than from what it last asked for, so
     * the ring shows what was granted rather than what was wanted.
     */
    static RadialEntry power(int power, String label, String hint, ItemStack icon) {
        return new RadialEntry(label, hint, icon, returnTo -> Powers.toggle(player(), power)) {
            @Override
            public boolean on() {
                return Powers.has(player(), power);
            }

            @Override
            public String label() {
                // Said twice, the way a slot that leads somewhere is: lit in
                // the ring, and carried on the name, since the name is what
                // gets read before the click.
                return super.label() + (on() ? "  on" : "  off");
            }

            @Override
            public String hint() {
                return said(DevStatus.POWERS, super.hint());
            }
        };
    }

    /** A one off, which leaves nothing behind but whether it worked. */
    static RadialEntry action(String kind, String label, String hint, ItemStack icon, RadialAction action) {
        return new RadialEntry(label, hint, icon, action) {
            @Override
            public String hint() {
                return said(kind, super.hint());
            }
        };
    }

    /**
     * One of several states the world can be put in, lit while it is the one
     * the world is in.
     *
     * <p>Read from the world this client holds rather than from what was last
     * asked for, for the same reason a power is: the ring shows what happened.
     * Choosing the lit one again is not refused, since putting the world where
     * it already is does no harm and is sometimes the point.
     */
    static RadialEntry state(String kind, String label, String hint, ItemStack icon,
                             Predicate<World> current, RadialAction action) {
        return new RadialEntry(label, hint, icon, action) {
            @Override
            public boolean on() {
                World world = world();
                return world != null && current.test(world);
            }

            @Override
            public String hint() {
                return said(kind, super.hint());
            }
        };
    }

    /** Why the last request of this kind was refused, or what the slot is for. */
    private static String said(String kind, String describes) {
        String refusal = DevStatus.ok(kind) ? "" : DevStatus.message(kind);
        return refusal.isEmpty() ? describes : refusal;
    }

    /** The player these slots are about, or null before there is one. */
    private static PlayerEntity player() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        return minecraft == null ? null : minecraft.player;
    }

    /** The world as this client last heard of it, or null before there is one. */
    private static World world() {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        return minecraft == null ? null : minecraft.world;
    }
}
