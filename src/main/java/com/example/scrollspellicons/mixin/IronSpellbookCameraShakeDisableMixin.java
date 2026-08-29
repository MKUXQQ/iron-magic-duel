package com.example.scrollspellicons.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes Iron's Spells' client camera-angle mutation and shake queues. */
@Mixin(targets = "io.redspace.ironsspellbooks.api.util.CameraShakeManager", remap = false)
public abstract class IronSpellbookCameraShakeDisableMixin {
    @Inject(method = "addCameraShake", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$dropServerShake(@Coerce Object data, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "addClientCameraShake", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$dropClientShake(@Coerce Object data, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "handleCameraShake(Lnet/neoforged/neoforge/client/event/ViewportEvent$ComputeCameraAngles;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$cancelCameraAngles(@Coerce Object event, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "handleCameraShake(Lnet/neoforged/neoforge/client/event/ClientTickEvent$Post;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$skipShakeBookkeeping(@Coerce Object event, CallbackInfo ci) {
        ci.cancel();
    }
}
