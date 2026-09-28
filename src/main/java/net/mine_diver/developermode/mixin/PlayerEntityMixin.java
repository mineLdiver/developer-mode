package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.feature.player.Empowered;
import net.mine_diver.developermode.feature.player.Power;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What a player has been granted, and the first thing it is good for.
 *
 * <p>The mask lives on the player because that is what it is about, and it is
 * saved with the rest of them: in the world's own file in singleplayer, and in
 * the player's file on a server. A player object does not last as long as the
 * player does, though. Dying replaces it, and on a server the client replaces
 * its own copy on every change of dimension too, so what the new one has is
 * carried over from the old one where that happens rather than here.
 *
 * <p>Written only when there is something to write, so a player who never
 * touched a power is saved exactly as Beta would have saved them.
 *
 * <p>God mode is taken at the one door every kind of damage comes through,
 * rather than at each of the things that can do it, so a mob, a cactus, the
 * fall, the fire and whatever a mod under test invents are all covered by the
 * same answer. Returning false is the answer Beta already gives for damage
 * that missed, so nothing downstream has to be taught about invulnerability:
 * no hurt animation, no knockback, no anger.
 */
@Mixin(PlayerEntity.class)
abstract class PlayerEntityMixin implements Empowered {
    /** Named for the mod, since a player's compound is shared with every other one. */
    @Unique
    private static final String DEVELOPERMODE_POWERS_KEY = "DeveloperModePowers";

    @Unique
    private int developermode_powers;

    @Override
    public int developermode_powers() {
        return developermode_powers;
    }

    @Override
    public void developermode_powers(int mask) {
        developermode_powers = mask;
    }

    @Inject(method = "damage(Lnet/minecraft/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void developermode_refuseDamage(Entity attacker, int amount, CallbackInfoReturnable<Boolean> cir) {
        if ((developermode_powers & Power.GOD) != 0) cir.setReturnValue(false);
    }

    @Inject(method = "readNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
    private void developermode_readPowers(NbtCompound nbt, CallbackInfo ci) {
        // A number off the disk, which a hand edit or a later version could
        // have made anything.
        developermode_powers = Power.legal(nbt.getInt(DEVELOPERMODE_POWERS_KEY));
    }

    @Inject(method = "writeNbt(Lnet/minecraft/nbt/NbtCompound;)V", at = @At("TAIL"))
    private void developermode_writePowers(NbtCompound nbt, CallbackInfo ci) {
        if (developermode_powers != Power.NONE) nbt.putInt(DEVELOPERMODE_POWERS_KEY, developermode_powers);
    }
}
