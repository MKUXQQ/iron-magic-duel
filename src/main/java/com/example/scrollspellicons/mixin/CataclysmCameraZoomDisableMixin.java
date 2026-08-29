package com.example.scrollspellicons.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Cataclysm's spell lens zoom from changing the configured FOV. */
@Pseudo
@Mixin(targets = "com.github.L_Ender.cataclysm.client.event.CameraZoomManager", remap = false)
public abstract class CataclysmCameraZoomDisableMixin {
    @Inject(method = "startZoom(IFFFF)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$disableZoom(int duration, float maxDistance,
                                               float startWeight, float midWeight,
                                               float endWeight, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "getZoomOffset(F)F", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$disableZoomOffset(float partialTick,
                                                     CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(0.0F);
    }
}
