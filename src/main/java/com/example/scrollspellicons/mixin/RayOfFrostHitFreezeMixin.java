package com.example.scrollspellicons.mixin;

import com.example.scrollspellicons.duel.SpellDuelEvents;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Uses Ray of Frost's one verified applyDamage call as the freeze trigger. */
@Mixin(targets = "io.redspace.ironsspellbooks.spells.ice.RayOfFrostSpell", remap = false)
public abstract class RayOfFrostHitFreezeMixin {
    @Redirect(method = "onCast(Lnet/minecraft/world/level/Level;ILnet/minecraft/world/entity/LivingEntity;Lio/redspace/ironsspellbooks/api/spells/CastSource;Lio/redspace/ironsspellbooks/api/magic/MagicData;)V",
            at = @At(value = "INVOKE",
                    target = "Lio/redspace/ironsspellbooks/damage/DamageSources;applyDamage(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;)Z"),
            remap = false)
    private boolean ironMagicDuel$freezeAfterSuccessfulRay(Entity target, float damage, DamageSource source) {
        boolean applied = DamageSources.applyDamage(target, damage, source);
        if (applied && target instanceof ServerPlayer player && player.isAlive() && !player.isDeadOrDying()) {
            SpellDuelEvents.applyManagedFreeze(player, SpellDuelEvents.FreezeReason.RAY_OF_FROST);
        }
        return applied;
    }
}
