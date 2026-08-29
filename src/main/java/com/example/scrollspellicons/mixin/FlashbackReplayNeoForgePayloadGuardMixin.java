package com.example.scrollspellicons.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps only NeoForge's attachment handshake out of Flashback's fake replay
 * connection. All other NeoForge and third-party payloads remain available
 * to Flashback's recorder/replayer.
 */
@Mixin(targets = "net.neoforged.neoforge.network.registration.NetworkRegistry", remap = false)
public abstract class FlashbackReplayNeoForgePayloadGuardMixin {
    private static final ResourceLocation SYNC_ATTACHMENTS =
            ResourceLocation.fromNamespaceAndPath("neoforge", "sync_attachments");
    @Inject(
            method = "handleModdedPayload(Lnet/minecraft/network/protocol/common/ClientCommonPacketListener;Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void ironMagic$dropReplayNeoForgePayload(
            ClientCommonPacketListener listener,
            ClientboundCustomPayloadPacket packet,
            CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() == null
                || !"com.moulberry.flashback.playback.ReplayServer".equals(
                minecraft.getSingleplayerServer().getClass().getName())) {
            return;
        }
        if (SYNC_ATTACHMENTS.equals(packet.payload().type().id())) {
            ci.cancel();
        }
    }
}
