package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.feature.player.Power;
import net.mine_diver.developermode.feature.player.Powers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ammunition that does not run out.
 *
 * <p>A bow spends an arrow that is not the item in hand, by asking the
 * inventory to take one of a kind out from wherever it is. Nothing around the
 * held stack sees that, so it is answered here: still yes if there is one to
 * take, since having none is still having none, but without taking it.
 */
@Mixin(PlayerInventory.class)
abstract class PlayerInventoryMixin {
    @Shadow
    public PlayerEntity player;

    @Shadow
    private int indexOf(int itemId) {
        throw new AssertionError();
    }

    @Inject(method = "remove(I)Z", at = @At("HEAD"), cancellable = true)
    private void developermode_keepAmmo(int itemId, CallbackInfoReturnable<Boolean> cir) {
        if (Powers.has(player, Power.ENDLESS)) cir.setReturnValue(indexOf(itemId) >= 0);
    }
}
