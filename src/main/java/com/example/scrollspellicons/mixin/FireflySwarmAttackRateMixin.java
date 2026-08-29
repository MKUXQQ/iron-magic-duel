package com.example.scrollspellicons.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Firefly Swarm's native bite cadence is 15 ticks; make it 10 ticks. */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.entity.spells.firefly_swarm.FireflySwarmProjectile", remap = false)
public abstract class FireflySwarmAttackRateMixin {
    @ModifyConstant(method = "customServerAiStep", constant = @Constant(intValue = 15), require = 0, remap = false)
    private int ironMagicDuel$twoBitesPerSecond(int original) {
        return 10;
    }
}
