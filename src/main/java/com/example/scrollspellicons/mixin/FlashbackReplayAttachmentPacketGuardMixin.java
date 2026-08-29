package com.example.scrollspellicons.mixin;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Allows the one optional NeoForge attachment packet during Flashback replay. */
@Mixin(value = NetworkRegistry.class, remap = false)
public abstract class FlashbackReplayAttachmentPacketGuardMixin {
    private static final ResourceLocation SYNC_ATTACHMENTS =
            ResourceLocation.fromNamespaceAndPath("neoforge", "sync_attachments");

    @Inject(
            method = "checkPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/protocol/common/ServerCommonPacketListener;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void ironMagic$allowReplayAttachmentPacket(
            Packet<?> packet,
            ServerCommonPacketListener listener,
            CallbackInfo ci) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null
                || !"com.moulberry.flashback.playback.ReplayServer".equals(server.getClass().getName())) {
            return;
        }
        if (packet instanceof ClientboundCustomPayloadPacket payload
                && SYNC_ATTACHMENTS.equals(payload.payload().type().id())) {
            ci.cancel();
        }
    }
}
