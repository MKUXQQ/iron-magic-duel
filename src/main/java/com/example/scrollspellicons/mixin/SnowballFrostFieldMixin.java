package com.example.scrollspellicons.mixin;

import com.example.scrollspellicons.duel.SpellDuelEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Marks only the FrostField created by an Iron snowball. Direct player-hit
 * freezing is handled by SpellDuelEvents' accepted ProjectileImpactEvent
 * listener, before Snowball creates its field. */
@Mixin(targets = "io.redspace.ironsspellbooks.entity.spells.snowball.Snowball", remap = false)
public abstract class SnowballFrostFieldMixin {
    @ModifyArg(method = "createFrostField(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            index = 0, remap = false)
    private Entity ironMagicDuel$markFrostField(Entity field) {
        return SpellDuelEvents.markManagedFrostField(field, SpellDuelEvents.FreezeReason.SNOWBALL);
    }

    @Redirect(method = "onHit(Lnet/minecraft/world/phys/HitResult;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),
            remap = false)
    private boolean ironMagicDuel$keepNativeChilledForNonPlayers(LivingEntity target, MobEffectInstance effect) {
        if (target instanceof ServerPlayer
                && effect.getEffect().value() == io.redspace.ironsspellbooks.registries.MobEffectRegistry.CHILLED.value()) return true;
        return target.addEffect(effect);
    }
}
