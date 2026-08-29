package com.example.scrollspellicons.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removes the SpellLib screen-shake entities used by geomancy effects. */
@Pseudo
@Mixin(targets = "com.gametechbc.spelllib.entity.GSLScreenShakeEntity", remap = false)
public abstract class GtbcsScreenShakeDisableMixin {
    @Inject(method = "getShakeAmount(Lnet/minecraft/world/entity/player/Player;F)F",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void ironMagic$disableShake(Player player, float partialTick,
                                         CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(0.0F);
    }
}
