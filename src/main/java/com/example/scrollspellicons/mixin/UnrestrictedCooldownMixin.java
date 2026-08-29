package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.PlayerCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Retained as a compatibility class for old configs. Native cooldowns are no
 * longer bypassed: Iron's SpellConfigHolder is the only cooldown authority.
 */
@Mixin(value = PlayerCooldowns.class, remap = false)
public abstract class UnrestrictedCooldownMixin {
    @Inject(method = "isOnCooldown", at = @At("HEAD"), cancellable = true, remap = false)
    private void ironMagicDuel$ignoreCooldown(AbstractSpell spell, CallbackInfoReturnable<Boolean> cir) {
    }

    @Inject(method = "getCooldownPercent", at = @At("HEAD"), cancellable = true, remap = false)
    private void ironMagicDuel$hideCooldown(AbstractSpell spell, CallbackInfoReturnable<Float> cir) {
    }
}
