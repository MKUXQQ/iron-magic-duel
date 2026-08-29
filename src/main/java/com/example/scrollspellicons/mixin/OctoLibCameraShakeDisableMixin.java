package com.example.scrollspellicons.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional compatibility with OctoLib's client shake queue. */
@Pseudo
@Mixin(targets = "it.hurts.octostudios.octolib.client.shake.ShakeSystem", remap = false)
public abstract class OctoLibCameraShakeDisableMixin {
    @Inject(method = "startShake(Lit/hurts/octostudios/octolib/client/shake/Shakeable;Lit/hurts/octostudios/octolib/client/shake/ShakeData;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$disableShake(
            @Coerce Object shakeable, @Coerce Object data, CallbackInfo ci) {
        ci.cancel();
    }
}
