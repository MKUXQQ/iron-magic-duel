package com.example.scrollspellicons.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Raises only the verified GlacialEdge projectile launch speed. */
@Pseudo
@Mixin(targets = "net.acetheeldritchking.discerning_the_eldritch.entity.spells.glacial_edge.GlacialEdge", remap = false)
public abstract class GlacialEdgeSpeedMixin {
    private static final float IRON_MAGIC_SPEED_MULTIPLIER = 1.75F;

    @Inject(method = "getSpeed()F", at = @At("HEAD"), cancellable = true, remap = false)
    private void ironMagicDuel$increaseGlacialEdgeSpeed(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(IRON_MAGIC_SPEED_MULTIPLIER);
    }
}
