package com.example.scrollspellicons.mixin;

import com.example.scrollspellicons.server.ChanneledCastGuard;
import io.redspace.ironsspellbooks.network.casting.CancelCastPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A started chant is server-authoritative and cannot be cancelled early. */
@Mixin(value = CancelCastPacket.class, remap = false)
public abstract class UninterruptibleChanneledCastMixin {
    @Inject(method = "cancelCast(Lnet/minecraft/server/level/ServerPlayer;Z)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagicDuel$denyChantCancellation(ServerPlayer player, boolean triggerCooldown, CallbackInfo ci) {
        if (ChanneledCastGuard.isIntentionalSprayCancellation(player)) return;
        if (ChanneledCastGuard.isChannelling(player)) ci.cancel();
    }
}
