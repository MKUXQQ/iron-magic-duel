package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iron's Root stores its victim in TargetEntityCastData during the pre-cast
 * hook.  Some cast-entry redirects call the hook after the generic condition
 * gate and an empty target-data object can otherwise make onCast silently do
 * nothing.  Keep the native targeting first; only provide the same 32-block
 * living-target fallback when the native hook did not select a target.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.spells.nature.RootSpell", remap = false)
public abstract class RootSpellCompatMixin {
    @Inject(method = "checkPreCastConditions", at = @At("RETURN"), cancellable = true, remap = false)
    private void ironMagicDuel$ensureRootTarget(Level level, int spellLevel, LivingEntity caster,
                                                 MagicData magic, CallbackInfoReturnable<Boolean> cir) {
        if (magic != null && magic.getAdditionalCastData() instanceof TargetEntityCastData) {
            cir.setReturnValue(true);
            return;
        }
        Vec3 start = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        AABB search = caster.getBoundingBox().expandTowards(look.scale(32.0D)).inflate(1.5D);
        LivingEntity selected = level.getEntitiesOfClass(LivingEntity.class, search,
                candidate -> candidate != caster && candidate.isAlive() && !candidate.isSpectator())
                .stream()
                .filter(candidate -> candidate.getBoundingBox().clip(start, start.add(look.scale(32.0D))).isPresent())
                .min(java.util.Comparator.comparingDouble(candidate -> caster.distanceToSqr(candidate)))
                .orElse(null);
        if (selected != null && magic != null) {
            magic.setAdditionalCastData(new TargetEntityCastData(selected));
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }
    }
}
