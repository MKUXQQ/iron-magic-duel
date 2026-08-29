package com.example.scrollspellicons.mixin;

import com.example.scrollspellicons.duel.SpellDuelEvents;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile;
import io.redspace.ironsspellbooks.spells.EntityCastData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents Iron from deleting a live continuous spray at the end of a cast cycle. */
@Mixin(value = EntityCastData.class, remap = false)
public abstract class ContinuousConeLifetimeMixin {
    private static final ResourceLocation CONE_OF_COLD =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "cone_of_cold");
    @Shadow @Final
    private Entity castingEntity;

    @Inject(method = "discardCastingEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void ironMagicDuel$keepActiveContinuousCone(CallbackInfo ci) {
        if (castingEntity instanceof AbstractConeProjectile diagnosticCone) {
            SpellDuelEvents.recordConeResetDiagnostic(diagnosticCone, "EntityCastData.discardCastingEntity");
        }
        if (!(castingEntity instanceof AbstractConeProjectile cone)
                || !(cone.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        MagicData magic = MagicData.getPlayerMagicData(player);
        if (!player.level().isClientSide
                && magic.isCasting()
                && magic.getCastType() == CastType.CONTINUOUS
                && CONE_OF_COLD.toString().equals(magic.getCastingSpellId())
                && magic.getAdditionalCastData() == (Object) this) {
            if (cone.isRemoved() || !cone.isAlive() || cone.getOwner() != player) return;
            ci.cancel();
        }
    }
}
