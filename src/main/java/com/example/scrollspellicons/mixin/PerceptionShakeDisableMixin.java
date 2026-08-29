package com.example.scrollspellicons.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes Perception's additive camera shake channel used by spell effects. */
@Mixin(targets = "it.hurts.octostudios.perception.common.modules.shake.ShakeManager", remap = false)
public abstract class PerceptionShakeDisableMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$disableSpellShake(@Coerce Object shake, CallbackInfo ci) {
        ci.cancel();
    }
}
