package com.example.scrollspellicons.mixin;

import com.example.scrollspellicons.duel.SpellDuelEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents only FrostFields spawned by the two verified managed projectiles
 * from repeatedly extending ticksFrozen.  Snowball freezing is owned solely
 * by Snowball.onHit's actual EntityHitResult; an area pulse never freezes a
 * player. Other FrostFields remain vanilla. */
@Mixin(targets = "io.redspace.ironsspellbooks.entity.spells.snowball.FrostField", remap = false)
public abstract class ManagedFrostFieldMixin {
    @Inject(method = "applyEffect(Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void ironMagicDuel$skipManagedPulse(LivingEntity target, CallbackInfo ci) {
        Entity field = (Entity) (Object) this;
        SpellDuelEvents.FreezeReason reason = SpellDuelEvents.managedFrostFieldReason(field);
        if ((reason == SpellDuelEvents.FreezeReason.SNOWBALL
                || reason == SpellDuelEvents.FreezeReason.GLACIAL_EDGE)
                && target instanceof ServerPlayer) {
            // The direct Snowball.onHit hook is the only managed SNOWBALL
            // freeze entry. This field may still be entered by A or B later;
            // cancel its native pulse for players so neither can be added or
            // refreshed by area overlap.
            ci.cancel();
        }
    }
}
