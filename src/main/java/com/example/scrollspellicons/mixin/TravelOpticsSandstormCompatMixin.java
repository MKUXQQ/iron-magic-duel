package com.example.scrollspellicons.mixin;

import net.neoforged.neoforge.registries.DeferredHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;

/** Repairs TravelOptics 4.4.0.1's removed Cataclysm SANDSTORM particle reference. */
@Pseudo
@Mixin(targets = "com.gametechbc.traveloptics.spells.holy.SummonDesertDwellers", remap = false)
public abstract class TravelOpticsSandstormCompatMixin {
    private static final String SANDSTORM_FIELD = "Lcom/github/L_Ender/cataclysm/init/ModParticle;SANDSTORM:Lnet/neoforged/neoforge/registries/DeferredHolder;";

    @Redirect(method = "onServerCastTick", at = @At(value = "FIELD", target = SANDSTORM_FIELD), remap = false, require = 0)
    private static DeferredHolder<?, ?> ironMagicDuel$replaceRemovedSandstormOnTick() { return dustBlast(); }

    @Redirect(method = "onCast", at = @At(value = "FIELD", target = SANDSTORM_FIELD), remap = false, require = 0)
    private static DeferredHolder<?, ?> ironMagicDuel$replaceRemovedSandstormOnCast() { return dustBlast(); }

    private static DeferredHolder<?, ?> dustBlast() {
        try {
            Class<?> particles = Class.forName("com.github.L_Ender.cataclysm.init.ModParticle");
            Field field = particles.getField("DUST_BLAST");
            return (DeferredHolder<?, ?>) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cataclysm DUST_BLAST particle is unavailable", exception);
        }
    }
}
