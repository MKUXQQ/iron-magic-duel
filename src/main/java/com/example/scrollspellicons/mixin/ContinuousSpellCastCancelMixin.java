package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.network.casting.CancelCastPacket;
import io.redspace.ironsspellbooks.network.casting.CastPacket;
import io.redspace.ironsspellbooks.network.casting.QuickCastPacket;
import io.redspace.ironsspellbooks.player.ClientInputEvents;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A fresh key press during an existing cast cancels it; a held key is handled separately for repeat casts. */
@Mixin(value = ClientInputEvents.class, remap = false)
public abstract class ContinuousSpellCastCancelMixin {
    @Redirect(
            method = "handleKeybinds",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/network/PacketDistributor;sendToServer(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"),
            remap = false)
    private static void ironMagicDuel$cancelOnSecondSpellKeyPress(CustomPacketPayload payload, CustomPacketPayload[] extra) {
        if ((payload instanceof CastPacket || payload instanceof QuickCastPacket) && ClientMagicData.isCasting()) {
            PacketDistributor.sendToServer(new CancelCastPacket(false));
            return;
        }
        PacketDistributor.sendToServer(payload, extra);
    }
}
