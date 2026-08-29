package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional fallback for Critical Strike's direct spell-damage integration. */
@Pseudo
@Mixin(targets = "net.critical_strike.internal.CritLogic", remap = false)
public abstract class CriticalStrikeCritLogicMixin {
    @Inject(method = "modifyDamage", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagicDuel$disableSpellCrit(@Coerce Object striker, DamageSource source, float amount,
                                                         CallbackInfoReturnable<Object> cir) {
        if (source instanceof SpellDamageSource) {
            cir.setReturnValue(null);
        }
    }
}
