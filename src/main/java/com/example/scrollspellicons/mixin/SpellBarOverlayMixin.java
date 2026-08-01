package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.gui.overlays.SpellBarOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces Iron's stock spell-slot renderer with the custom Curios spellbook HUD. */
@Mixin(value = SpellBarOverlay.class, remap = false)
public abstract class SpellBarOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void ironSpellPerformance$hideOriginalSpellBar(GuiGraphics graphics, DeltaTracker deltaTracker,
                                                            CallbackInfo ci) {
        ci.cancel();
    }
}
