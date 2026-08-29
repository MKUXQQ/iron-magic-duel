package com.example.scrollspellicons.mixin;

import com.example.scrollspellicons.duel.SpellDuelEvents;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cone of Cold carries Iron's native 80-tick freeze value.  Our successful
 * server damage callback owns the bounded 36-tick state, so suppress only
 * that one native side effect. */
@Mixin(targets = "io.redspace.ironsspellbooks.damage.DamageSources", remap = false)
public abstract class ManagedFreezeSourceMixin {
    @Inject(method = "postHitEffects(Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Post;)V",
            at = @At("HEAD"), remap = false)
    private static void ironMagicDuel$boundManagedFreeze(LivingDamageEvent.Post event, CallbackInfo ci) {
        if (event.getSource() instanceof SpellDamageSource source && source.spell() != null
                && SpellDuelEvents.isConeOfColdSpell(source.spell().getSpellResource())) {
            source.setFreezeTicks(0);
        }
    }
}
