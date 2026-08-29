package com.example.scrollspellicons.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sets firesenderexpansion:hollow_crystal's chant to 0.8 seconds (16 game ticks). */
@Pseudo
@Mixin(targets = "net.fireofpower.firesenderexpansion.spells.HollowCrystalSpell", remap = false)
public abstract class HollowCrystalCastTimeMixin {
    @Inject(method = "getCastTime", at = @At("HEAD"), cancellable = true, remap = false)
    private void ironMagicDuel$setHollowCrystalCastTime(int spellLevel, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(16);
    }
}
