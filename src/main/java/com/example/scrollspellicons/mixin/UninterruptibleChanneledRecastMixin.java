package com.example.scrollspellicons.mixin;

import com.example.scrollspellicons.server.ChanneledCastGuard;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A different spell cannot replace any active chant, including a continuous spray's wind-up. */
@Mixin(value = Utils.class, remap = false)
public abstract class UninterruptibleChanneledRecastMixin {
    @Inject(method = "serverSideInitiateCast(Lnet/minecraft/server/level/ServerPlayer;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagicDuel$denyReplacementDuringChant(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        SpellSelectionManager selection = new SpellSelectionManager(player);
        var requested = selection.getSelectedSpellData();
        if (requested != null && requested != SpellData.EMPTY
                && ChanneledCastGuard.cancelActiveSpray(player, requested.getSpell().getSpellId())) {
            cir.setReturnValue(false);
            return;
        }
        if (ChanneledCastGuard.blocksReplacement(player, requested)) cir.setReturnValue(false);
    }

    @Inject(method = "serverSideInitiateQuickCast(Lnet/minecraft/server/level/ServerPlayer;I)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagicDuel$denyQuickReplacementDuringChant(ServerPlayer player, int slot, CallbackInfoReturnable<Boolean> cir) {
        SpellSelectionManager selection = new SpellSelectionManager(player);
        var requested = selection.getSpellData(slot);
        if (requested != null && requested != SpellData.EMPTY
                && ChanneledCastGuard.cancelActiveSpray(player, requested.getSpell().getSpellId())) {
            cir.setReturnValue(false);
            return;
        }
        if (ChanneledCastGuard.blocksReplacement(player, requested)) cir.setReturnValue(false);
    }
}
