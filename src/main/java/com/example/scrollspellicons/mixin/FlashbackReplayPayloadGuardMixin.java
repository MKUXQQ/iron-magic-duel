package com.example.scrollspellicons.mixin;

import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops only NeoForge's attachment handshake from Flashback's fake replay
 * connection.  Flashback records and replays every other custom payload;
 * filtering those packets removes mod state and particle/visual updates.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class FlashbackReplayPayloadGuardMixin {
    private static final ResourceLocation SYNC_ATTACHMENTS =
            ResourceLocation.fromNamespaceAndPath("neoforge", "sync_attachments");
    @Shadow protected MinecraftServer server;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
    private void ironMagic$dropReplayOnlyConfig(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (!(packet instanceof ClientboundCustomPayloadPacket payload)
                || !"com.moulberry.flashback.playback.ReplayServer".equals(server.getClass().getName())) {
            return;
        }
        if (SYNC_ATTACHMENTS.equals(payload.payload().type().id())) {
            ci.cancel();
        }
    }
}
