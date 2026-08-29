package com.example.scrollspellicons.mixin;

import com.example.scrollspellicons.duel.SpellDuelEvents;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gates the verified Glacial Edge outer hit before it can create another
 * FrostField, then freezes only after native damage actually succeeds. */
@Pseudo
@Mixin(targets = "net.acetheeldritchking.discerning_the_eldritch.entity.spells.glacial_edge.GlacialEdge", remap = false)
public abstract class GlacialEdgeHitOnceMixin {
    private static final String HIT_METHOD = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V";

    @Inject(method = "onHit(Lnet/minecraft/world/phys/HitResult;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ironMagicDuel$gateOuterHit(HitResult hit, CallbackInfo ci) {
        if (hit instanceof EntityHitResult entityHit
                && !SpellDuelEvents.allowGlacialEdgeHit((Entity) (Object) this, entityHit.getEntity())) {
            ci.cancel();
        }
    }

    @Redirect(method = HIT_METHOD,
            at = @At(value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/damage/DamageSources;applyDamage(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;)Z"),
            remap = false)
    private boolean ironMagicDuel$freezeAfterNativeDamage(Entity target, float damage, DamageSource source) {
        boolean applied = DamageSources.applyDamage(target, damage, source);
        SpellDuelEvents.onGlacialEdgeDamageApplied((Entity) (Object) this, target, applied);
        return applied;
    }

    @Redirect(method = HIT_METHOD,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),
            remap = false)
    private boolean ironMagicDuel$ignoreNativeChilled(LivingEntity target, MobEffectInstance effect) {
        if (effect.getEffect().value() == io.redspace.ironsspellbooks.registries.MobEffectRegistry.CHILLED.value()) {
            return true;
        }
        return target.addEffect(effect);
    }

    @ModifyArg(method = "createFrostField(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            index = 0, remap = false)
    private Entity ironMagicDuel$markFrostField(Entity field) {
        return SpellDuelEvents.markManagedFrostField(field, SpellDuelEvents.FreezeReason.GLACIAL_EDGE);
    }
}
