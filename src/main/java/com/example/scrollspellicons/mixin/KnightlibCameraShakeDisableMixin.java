package com.example.scrollspellicons.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional compatibility with KnightLib's client shake queue. */
@Pseudo
@Mixin(targets = "dev.xylonity.knightlib.api.camera.shake.CameraShakeManager", remap = false)
public abstract class KnightlibCameraShakeDisableMixin {
    @Inject(method = "shake(Ldev/xylonity/knightlib/api/camera/shake/ShakeSettings;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$disableShake(@Coerce Object settings, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "shake(Ldev/xylonity/knightlib/api/camera/shake/ShakeSettings;Z)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$disableShakeWithCameraFlag(
            @Coerce Object settings, boolean affectsCamera, CallbackInfo ci) {
        ci.cancel();
    }
}
