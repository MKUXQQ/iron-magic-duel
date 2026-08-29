package com.example.scrollspellicons.spells;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.util.List;

/** Travel Optics' immediate gyro-slash model sequence. */
public final class AstralPredatorSpell extends AddonModelSpell {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            "iron_magic_duel", "astral_predator");
    private static final ResourceLocation SOURCE = ResourceLocation.fromNamespaceAndPath(
            "traveloptics", "mechanized_predator");
    private static final double MAX_RANGE = 16.0D;
    private static final double BEAM_HALF_WIDTH = 1.55D;

    public AstralPredatorSpell() {
        super(ID, SOURCE, SchoolRegistry.LIGHTNING_RESOURCE, SpellRarity.EPIC, 55, 15, CastType.LONG);
    }

    @Override public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.translatable("ui.irons_spellbooks.damage", "10.0"),
                Component.literal("星轨裂光：Gyro Slash 3D 追猎斩"),
                Component.literal("16格内沿瞄准线造成 10 点雷电伤害并挑飞"));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData magic) {
        if (level.isClientSide || !(level instanceof ServerLevel server)) return;
        Vec3 origin = caster.getEyePosition().add(0.0D, 0.2D, 0.0D);
        Vec3 forward = caster.getForward().normalize();
        for (int i = 0; i < 72; i++) {
            double t = i / 71.0D;
            Vec3 point = origin.add(forward.scale(0.4D + t * (MAX_RANGE - 0.4D)))
                    .add(0.0D, Math.sin(t * Math.PI * 2.0D) * 0.9D, 0.0D);
            particle(server, ParticleTypes.ELECTRIC_SPARK, point, 5, 0.16D, 0.16D, 0.16D, 0.03D);
            if ((i & 3) == 0) particle(server, ParticleTypes.END_ROD, point, 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2.0D * i / 16.0D;
            Vec3 point = origin.add(new Vec3(Math.cos(angle) * 1.8D, 0.35D, Math.sin(angle) * 1.8D));
            particle(server, ParticleTypes.WITCH, point, 3, 0.06D, 0.06D, 0.06D, 0.02D);
        }
        Vec3 impact = origin.add(forward.scale(MAX_RANGE));
        // The original single thunder cue was too quiet, especially when the
        // target was near the edge of the arena.  Give the cast a clear local
        // cue and use a stronger impact cue at the end of the 16-block beam.
        sound(server, origin, SoundEvents.PLAYER_ATTACK_SWEEP, 1.35F, 0.55F);
        try {
            Class<?> type = Class.forName("com.gametechbc.traveloptics.entity.spells.gyro_slash.GyroSlashVisual");
            Constructor<?> constructor = type.getConstructor(Level.class, boolean.class);
            for (int i = 0; i < 3; i++) {
                Vec3 bladePos = origin.add(forward.scale(3.5D + i * 5.8D));
                Object visual = constructor.newInstance(server, (i & 1) == 1);
                if (visual instanceof Entity entity) {
                    entity.setPos(bladePos.x, bladePos.y, bladePos.z);
                    server.addFreshEntity(entity);
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Particle beam remains available if Travel Optics is not installed.
        }
        particle(server, ParticleTypes.FLASH, impact, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        sound(server, impact, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.85F, 1.25F);
        // Test the whole 16-block line.  Endpoint-only damage created an
        // artificial minimum range, so nearby targets were never hit.
        hitBeam(server, caster, getDamageSource(caster), origin, forward, MAX_RANGE, BEAM_HALF_WIDTH, 10.0F, true);
    }

    private static void hitBeam(ServerLevel level, LivingEntity caster, DamageSource damageSource, Vec3 origin,
                                Vec3 direction, double range, double halfWidth,
                                float damage, boolean launch) {
        Vec3 end = origin.add(direction.scale(range));
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(origin, end)
                .inflate(halfWidth + 1.0D, halfWidth + 1.0D, halfWidth + 1.0D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity != caster && entity.isAlive() && !entity.isRemoved())) {
            Vec3 center = target.getBoundingBox().getCenter();
            Vec3 relative = center.subtract(origin);
            double along = relative.dot(direction);
            if (along < -0.75D || along > range + 0.75D) continue;
            double perpendicularSquared = Math.max(0.0D, relative.lengthSqr() - along * along);
            double allowed = halfWidth + Math.max(0.45D, target.getBbWidth() * 0.5D);
            if (perpendicularSquared > allowed * allowed) continue;
            markTeamHealthbar(caster, target);
            if (target.hurt(damageSource, damage) && launch) {
                Vec3 delta = target.getDeltaMovement();
                target.setDeltaMovement(delta.x * 0.25D, 0.58D, delta.z * 0.25D);
                target.hasImpulse = true;
                target.hurtMarked = true;
            }
        }
    }
}
