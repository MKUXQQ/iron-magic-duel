package com.example.scrollspellicons.mixin;

import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Heroic Spirit Spell has an optional Critical Strike bridge which runs before
 * every spell's DamageSources.applyDamage call.  Returning no crit result at
 * that bridge disables spell criticals while leaving the mod's melee path
 * untouched.  @Pseudo keeps the compatibility mixin inert when the addon is
 * not installed.
 */
@Pseudo
@Mixin(targets = "com.lin.heroic_spirit_spell.compat.CriticalStrikeSpellCritCompat", remap = false)
public abstract class HeroicSpellCritDisableMixin {
    @Inject(method = "tryApplySpellCrit", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ironMagicDuel$disableSpellCrit(DamageSource source, float amount,
                                                        CallbackInfoReturnable<Object> cir) {
        if (source != null && source instanceof io.redspace.ironsspellbooks.damage.SpellDamageSource) {
            cir.setReturnValue(null);
        }
    }
}
