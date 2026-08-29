package com.example.scrollspellicons.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drops Heroic Spirit's spell camera-switch payload handlers on the client. */
@Pseudo
@Mixin(targets = "com.lin.heroic_spirit_spell.client.network.ThunderstruckClientPayloadHandlers", remap = false)
public abstract class HeroicCameraPayloadDisableMixin {
    @Inject(method = "handleCamera(Lcom/lin/heroic_spirit_spell/network/ThunderstruckCameraPayload;Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$disableThunderstruckCamera(
            @Coerce Object payload, @Coerce Object context, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "handleBloodBlossomCamera(Lcom/lin/heroic_spirit_spell/network/BloodBlossomCameraPayload;Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$disableBloodBlossomCamera(
            @Coerce Object payload, @Coerce Object context, CallbackInfo ci) {
        ci.cancel();
    }
}
