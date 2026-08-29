package com.example.scrollspellicons.mixin;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips the one server-only config payload that breaks Flashback player spawn. */
@Mixin(value = PacketDistributor.class, remap = false)
public abstract class FlashbackReplayConfigPayloadGuardMixin {
    private static final ResourceLocation APOTHIC_CONFIG =
            ResourceLocation.fromNamespaceAndPath("apothic_attributes", "config");

    @Inject(method = "sendToPlayer(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagic$skipReplayServerOnlyConfig(
            ServerPlayer player, CustomPacketPayload payload,
            CustomPacketPayload[] additional, CallbackInfo ci) {
        if (!APOTHIC_CONFIG.equals(payload.type().id())) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null
                && "com.moulberry.flashback.playback.ReplayServer".equals(server.getClass().getName())) {
            ci.cancel();
        }
    }
}
