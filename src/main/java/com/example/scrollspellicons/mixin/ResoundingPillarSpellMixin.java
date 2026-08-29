package com.example.scrollspellicons.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.Comparator;

/**
 * Optional GTBCS compatibility: the original spell removes every pillar from
 * its caster before it makes a new one.  Keep up to four, replace only the
 * oldest on the fifth cast, and use a fixed twenty-second lifetime.
 */
@Pseudo
@Mixin(targets = "com.gametechbc.gtbcs_geomancy_plus.spells.geo.PillarOfTheResoundingEarthSpell", remap = false)
public abstract class ResoundingPillarSpellMixin {
    private static final ResourceLocation RESONATE_PILLAR = ResourceLocation.fromNamespaceAndPath(
            "gtbcs_geomancy_plus", "resonate_pillar");
    private static final int MAX_PILLARS = 4;
    private static final int DURATION_TICKS = 20 * 20;

    @Inject(method = "getDuration", at = @At("HEAD"), cancellable = true, remap = false)
    private void ironMagicDuel$twentySecondDuration(int spellLevel, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(DURATION_TICKS);
    }

    @Inject(method = "removeExistingPillars", at = @At("HEAD"), cancellable = true, remap = false)
    private void ironMagicDuel$keepFourPillars(Level level, LivingEntity caster, double radius, CallbackInfo callback) {
        if (level.isClientSide()) return;
        AABB area = caster.getBoundingBox().inflate(radius);
        Entity oldest = level.getEntities(caster, area, entity -> isOwnedResonatePillar(entity, caster))
                .stream()
                .max(Comparator.comparingInt(entity -> entity.tickCount))
                .orElse(null);
        long existing = level.getEntities(caster, area, entity -> isOwnedResonatePillar(entity, caster)).size();
        if (existing >= MAX_PILLARS && oldest != null) oldest.discard();
        // Never call the original method: it removes every existing pillar.
        callback.cancel();
    }

    private static boolean isOwnedResonatePillar(Entity entity, LivingEntity caster) {
        if (!RESONATE_PILLAR.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) return false;
        try {
            Method method = entity.getClass().getMethod("getSummoner");
            Object summoner = method.invoke(entity);
            return summoner instanceof LivingEntity owner && owner.getUUID().equals(caster.getUUID());
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
