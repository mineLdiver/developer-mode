package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.feature.player.Empowered;
import net.mine_diver.developermode.feature.player.Power;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What a player has been granted, and the first thing it is good for.
 *
 * <p>The mask lives on the player because that is what it is about. It lasts
 * exactly as long as the player object does: dying, leaving or changing world
 * ends a grant, and whoever comes back asks again.
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
}
