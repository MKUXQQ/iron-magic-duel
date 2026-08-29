package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile;
import io.redspace.ironsspellbooks.spells.EntityCastData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.Map;
import java.util.WeakHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.objectweb.asm.Opcodes;

/**
 * Keeps only a live EntityCastData-backed spray cone at a six-tick damage
 * cadence.  The setter is deliberately not injected: Iron's MagicManager
 * calls it every ten ticks, so gating the setter cannot make a spray faster.
 */
@Mixin(value = AbstractConeProjectile.class, remap = false)
public abstract class ContinuousConeCadenceMixin {
    private static final long SPRAY_MIN_ACTIVE_INTERVAL = 6L;
    private static final Map<Entity, Long> LAST_ACTIVE_TICK = new WeakHashMap<>();

    @Shadow
    private boolean dealDamageActive;

    /**
     * This is immediately before AbstractConeProjectile.tick consumes its
     * private active flag.  It can therefore either suppress an early native
     * activation or create the next six-tick activation without a global tick
     * scan and without rebuilding EntityCastData.
     */
    @Inject(method = "tick()V", at = @At(value = "FIELD",
            target = "Lio/redspace/ironsspellbooks/entity/spells/AbstractConeProjectile;dealDamageActive:Z",
            opcode = Opcodes.GETFIELD, ordinal = 0), remap = false)
    private void ironMagicDuel$gateAndRearmBeforeDamage(CallbackInfo ci) {
        AbstractConeProjectile cone = (AbstractConeProjectile) (Object) this;
        if (cone.level().isClientSide) return;
        if (!(cone.getOwner() instanceof LivingEntity owner)) {
            LAST_ACTIVE_TICK.remove(cone);
            return;
        }
        MagicData magic = MagicData.getPlayerMagicData(owner);
        boolean strictSpray = magic.isCasting() && magic.getCastType() == CastType.CONTINUOUS
                && magic.getAdditionalCastData() instanceof EntityCastData castData
                && castData.getCastingEntity() == cone
                && !cone.isRemoved() && cone.isAlive();
        if (!strictSpray) {
            LAST_ACTIVE_TICK.remove(cone);
            return;
        }

        long now = cone.level().getGameTime();
        Long previous = LAST_ACTIVE_TICK.get(cone);
        boolean due = previous == null || now - previous >= SPRAY_MIN_ACTIVE_INTERVAL;
        if (dealDamageActive) {
            if (due) {
                LAST_ACTIVE_TICK.put(cone, now);
            } else {
                // MagicManager's ten-tick activation arrived too early for
                // the unified six-tick cadence.  Suppress this one consume;
                // the original flag is still used for the actual hit path.
                dealDamageActive = false;
            }
        } else if (due) {
            cone.setDealDamageActive();
            LAST_ACTIVE_TICK.put(cone, now);
        }
    }
}
