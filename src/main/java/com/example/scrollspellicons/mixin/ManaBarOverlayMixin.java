package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.gui.overlays.ManaBarOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide the duplicate vanilla mana bar; SpellDuelHud renders the native atlas inside the left HUD. */
@Mixin(value = ManaBarOverlay.class, remap = false)
public abstract class ManaBarOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void ironSpellPerformance$hideDuplicateManaBar(GuiGraphics graphics, DeltaTracker deltaTracker,
                                                            CallbackInfo ci) {
        ci.cancel();
    }
}
