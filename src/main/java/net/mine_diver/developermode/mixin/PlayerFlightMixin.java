package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.developermode.feature.player.Powers;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
 * <p>Being granted flight is being allowed to fly, not flying. Whether you are
 * in the air right now is kept here and nowhere else: it is only ever needed by
 * the movement this client simulates, and the server's one question, whether
 * this player may float without being thrown off, is answered by the grant.
 * Two quick presses of jump take off or drop out of the air, and coming down
 * onto the ground lands you. Being granted it while already off the ground
 * takes off at once, so a grant made mid fall catches you and one made standing
 * leaves you standing.
 *
 * <p>Noclip keeps you in the air for as long as it lasts. Blocks have stopped
 * catching you, so there is no ground to land on, and dropping out of the air
 * would be dropping out of the world.
 *
 * <p>The whole of the normal movement is replaced rather than adjusted while
 * flying: gravity, water, ladders, slipperiness and step height are all things
 * that happen to somebody standing in the world, and the point of flight is
 * not to be.
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
    /**
     * Ticks after a press of jump in which the next one counts as the second
     * of a pair. A third of a second: long enough for a deliberate double tap,
     * and shorter than the jump it interrupts, so taking off from the ground
     * happens while the first press still has you off it.
     */
    private static final int DOUBLE_TAP_TICKS = 7;

    /** Whether this player is in the air by choice rather than falling. */
    @Unique
    private boolean developermode_flying;
    /** Whether flight was granted as of the last tick, so a new grant can be told apart. */
    @Unique
    private boolean developermode_wasAllowed;
    /** Whether jump was down as of the last tick, so a press can be told from a hold. */
    @Unique
    private boolean developermode_wasJumping;
    /** Ticks left for a second press of jump to make a pair with the first. */
    @Unique
    private int developermode_tapWindow;

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
        boolean noclip = Powers.has(self, Power.NOCLIP);
        noClip = noclip;
        if (!developermode_takeOffOrLand(Powers.has(self, Power.FLIGHT), noclip)) return;

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

        // Only a move that went down and was stopped counts as reaching the
        // ground, so skimming along it at the same height is still flying.
        // Noclip never reaches it, and moving through blocks does not touch
        // whether the last real move did.
        if (onGround && !noclip) developermode_flying = false;

        // None of this is falling, and the ground must not think it was.
        fallDistance = 0;
        ci.cancel();
    }

    /**
     * Works out, once a tick, whether this tick is flown.
     *
     * <p>Presses are counted here, from the same flag the jump itself reads,
     * rather than from the keyboard. That makes a press whatever the jump key
     * is bound to, and a tick is also the unit the rest of movement is in: two
     * presses inside one tick were never two jumps either.
     *
     * <p>The first press of a pair is an ordinary jump, since nothing can know
     * yet that a second is coming. That is also what makes taking off from the
     * ground work: by the second press, the first has already lifted you.
     */
    @Unique
    private boolean developermode_takeOffOrLand(boolean allowed, boolean noclip) {
        boolean pressed = jumping && !developermode_wasJumping;
        developermode_wasJumping = jumping;

        if (!allowed) {
            developermode_flying = false;
            developermode_wasAllowed = false;
            developermode_tapWindow = 0;
            return false;
        }

        if (!developermode_wasAllowed) developermode_flying = !onGround;
        developermode_wasAllowed = true;

        if (pressed && developermode_tapWindow > 0) {
            developermode_flying = !developermode_flying;
            // Spent, so a third press starts a new pair rather than finishing
            // this one a second time.
            developermode_tapWindow = 0;
        } else if (pressed) {
            developermode_tapWindow = DOUBLE_TAP_TICKS;
        } else if (developermode_tapWindow > 0) {
            developermode_tapWindow--;
        }

        if (noclip) developermode_flying = true;
        return developermode_flying;
    }
}
