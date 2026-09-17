package net.mine_diver.developermode.mixin;

import net.mine_diver.developermode.client.DeveloperModeClient;
import net.mine_diver.developermode.client.gui.Draw;
import net.mine_diver.developermode.client.gui.Theme;
import net.mine_diver.developermode.client.gui.window.SlotEditorWindow;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holding the inspect key over a slot offers its NBT, the way holding it in the
 * world offers an entity's.
 *
 * <p>A container screen already owns the mouse, so there is no mode to enter
 * here and nothing to aim: the slot under the cursor is the target, and the key
 * only decides whether a click edits the stack or picks it up.
 */
@Mixin(HandledScreen.class)
abstract class HandledScreenMixin extends Screen {
    @Shadow
    public ScreenHandler handler;

    @Shadow
    protected int backgroundWidth;

    @Shadow
    protected int backgroundHeight;

    @Shadow
    private Slot getSlotAt(int x, int y) {
        throw new AssertionError();
    }

    @Inject(method = "render(IIF)V", at = @At("RETURN"))
    private void developermode_offerSlot(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!developermode_inspecting()) return;

        Draw.text(minecraft, "editing NBT   click a slot", 4, 4, Theme.ACCENT);

        Slot slot = getSlotAt(mouseX, mouseY);
        if (slot == null || !slot.hasStack()) return;

        // Over the slot's own contents, so the stack is tinted rather than the
        // wash being painted under the thing it is meant to pick out.
        int x = (width - backgroundWidth) / 2 + slot.x;
        int y = (height - backgroundHeight) / 2 + slot.y;
        Draw.rect(x, y, x + 16, y + 16, Theme.ACCENT_FILL);
    }

    @Inject(method = "mouseClicked(III)V", at = @At("HEAD"), cancellable = true)
    private void developermode_editSlot(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (!developermode_inspecting() || button != 0) return;

        Slot slot = getSlotAt(mouseX, mouseY);
        if (slot == null || !slot.hasStack()) return;

        // Cancelled so the click does not also pick the stack up, which is what
        // it would otherwise mean.
        ci.cancel();
        SlotEditorWindow.open(handler.syncId, slot.id, slot.getStack());
    }

    @Unique
    private static boolean developermode_inspecting() {
        return Keyboard.isKeyDown(DeveloperModeClient.INSPECT_KEY.code);
    }
}
