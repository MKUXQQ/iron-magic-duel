package com.example.scrollspellicons.mixin;

import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * TravelOptics 4.4.0.1's fossil-summon interface calls a covariant
 * LivingEntity getSummoner() that it does not declare.  The JVM therefore
 * throws NoSuchMethodError when Wadjet is removed.  Redirect only that
 * malformed interface call to Iron's stable IMagicSummon API.
 */
@Pseudo
@Mixin(targets = "com.gametechbc.traveloptics.api.entity.mobs.MagicFossilSummon", remap = false)
public interface TravelOpticsMagicFossilSummonCompatMixin {
    @Redirect(
            method = "onRemovedHelper",
            at = @At(value = "INVOKE", target =
                    "Lcom/gametechbc/traveloptics/api/entity/mobs/MagicFossilSummon;getSummoner()Lnet/minecraft/world/entity/LivingEntity;"),
            remap = false,
            require = 1)
    private static LivingEntity ironMagicDuel$resolveSummoner(@Coerce Object summon) {
        if (summon instanceof IMagicSummon magicSummon) {
            Entity entity = magicSummon.getSummoner();
            return entity instanceof LivingEntity living ? living : null;
        }
        return null;
    }
}
