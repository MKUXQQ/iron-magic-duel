package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.network.casting.CastErrorPacket;
import io.redspace.ironsspellbooks.player.ClientSpellCastHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses only Iron's red "currently on cooldown" overlay message. */
@Mixin(value = ClientSpellCastHelper.class, remap = false)
public abstract class CooldownMessageMixin {
    @Inject(method = "handleCastErrorMessage", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagicDuel$hideCooldownMessage(CastErrorPacket packet, CallbackInfo ci) {
        if (packet.errorType == CastErrorPacket.ErrorType.COOLDOWN) {
            ci.cancel();
        }
    }
}
