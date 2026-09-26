package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.developermode.feature.player.Powers;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flying, and passing through blocks, for the player this client is driving.
 *
 * <p>Both belong here because both are about the same tick of movement. Beta
 * moves a player entirely on the client that owns it and only reports where it
 * ended up, so there is nothing for a server to do about either beyond agreeing
 * to it, which it does by granting the power in the first place.
 *
 * <p>The whole of the normal movement is replaced rather than adjusted:
 * gravity, water, ladders, slipperiness and step height are all things that
 * happen to somebody standing in the world, and the point of flight is not to
 * be.
 *
 * <p>Extended from {@link LivingEntity} because everything the replacement
 * needs is declared further up than the target. A shadow only reaches what the
 * target class itself declares, so velocity, noclip and moving are inherited
 * here instead.
 */
@Mixin(PlayerEntity.class)
abstract class PlayerFlightMixin extends LivingEntity {
    /** Per tick push from the movement keys. */
    private static final float FLIGHT_PUSH = 0.05F;
    /**
     * What is left of that push each tick, which is what decides the top
     * speed: a push of p settling against a drag of d ends up at p*d/(1-d).
     */
    private static final double FLIGHT_DRAG = 0.91;
    /** Straight up or down, in blocks a tick. */
    private static final double FLIGHT_CLIMB = 0.35;

    /** Never called. Present so the superclass can be named at all. */
    private PlayerFlightMixin(World world) {
        super(world);
    }

    @Inject(method = "travel(FF)V", at = @At("HEAD"), cancellable = true)
    private void developermode_fly(float sideways, float forward, CallbackInfo ci) {
        Minecraft minecraft = DeveloperModeClient.minecraft();
        // Other players in the world are moved by what their own clients
        // report, and nothing here is about them.
        if (minecraft == null || (Object) this != minecraft.player) return;

        PlayerEntity self = (PlayerEntity) (Object) this;
        // Set every tick, including to false, since this is the one place that
        // knows whether it should still be on by the time the move happens.
        noClip = Powers.has(self, Power.NOCLIP);
        if (!Powers.has(self, Power.FLIGHT)) return;

        // Vertically a speed rather than a push, so letting go of the key holds
        // you at the height you let go at instead of coasting past it.
        velocityY = jumping ? FLIGHT_CLIMB : isSneaking() ? -FLIGHT_CLIMB : 0;

        moveNonSolid(sideways, forward, FLIGHT_PUSH);

        // Moving counts the ground covered, and that tally is what decides
        // when the next footstep is due. Flying covers it over open air, where
        // there is no block to make a sound and so nothing moves the tally
        // along either, and the debt is paid off one step a tick the moment
        // there is ground below again. Flying is not walking, so it leaves the
        // count where it found it, and the bob with it.
        float walked = horizontalSpeed;
        move(velocityX, velocityY, velocityZ);
        horizontalSpeed = walked;

        velocityX *= FLIGHT_DRAG;
        velocityZ *= FLIGHT_DRAG;

        // None of this is falling, and the ground must not think it was.
        fallDistance = 0;
        ci.cancel();
    }
}
