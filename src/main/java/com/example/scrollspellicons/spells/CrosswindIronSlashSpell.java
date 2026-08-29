package com.example.scrollspellicons.spells;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 暗铁追魂斩：以 Hazennstuff 的真夜之刃斩击模型为基础的三段地面十字追魂斩。
 * 只生成短暂投射实体，不生成生物；锁定施法者视线内 16 格的目标。
 */
public final class CrosswindIronSlashSpell extends AbstractSpell {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("iron_magic_duel", "crosswind_iron_slash");
    private static final float DAMAGE_PER_SPELL_POWER = 5.0F;
    /** Slow each slash frame so the three-hit sequence is readable. */
    private static final int STAGE_TICKS = 16;
    private static final int STAGE_COUNT = 3;
    private static final double MAX_TARGET_DISTANCE = 16.0D;
    private static final double MAX_TARGET_DISTANCE_SQR = MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE;
    private static final Map<MinecraftServer, Map<UUID, Sequence>> ACTIVE = new HashMap<>();
    private final DefaultConfig defaultConfig;

    public CrosswindIronSlashSpell() {
        defaultConfig = new DefaultConfig()
                .setMinRarity(SpellRarity.UNCOMMON)
                .setSchoolResource(ResourceLocation.fromNamespaceAndPath("wind_spellbooks", "wind"))
                .setMaxLevel(1).setCooldownSeconds(4.0D).setAllowCrafting(true).build();
        baseManaCost = 25;
        manaCostPerLevel = 0;
        baseSpellPower = 4;
        spellPowerPerLevel = 0;
        // Keep the framework/configured cast time.  The inscription-table
        // balance value is the only authority for this spell's chant length.
    }

    @Override public CastType getCastType() { return CastType.LONG; }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public ResourceLocation getSpellResource() { return ID; }

    @Override public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float damage = damageFor(spellLevel, caster);
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", String.format(java.util.Locale.ROOT, "%.1f", damage)),
                Component.literal("暗铁追魂斩：地面十字追魂斩，三段真夜之刃剑气"),
                Component.literal("16格内锁定，左右交叉、身后反穿、放大斜斩终结"),
                Component.literal("伤害随法力倍率/法术强度实时提升"));
    }

    @Override public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource,
                                 MagicData magic) {
        if (level.isClientSide || caster.getServer() == null || !(level instanceof ServerLevel serverLevel)) return;
        Vec3 forward = caster.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
        if (forward.lengthSqr() < 1.0E-6D) return;
        LivingEntity target = findTarget(serverLevel, caster, forward);
        Vec3 targetPoint = target == null
                ? caster.position().add(forward.scale(MAX_TARGET_DISTANCE))
                : target.position().add(0.0D, 0.15D, 0.0D);
        Sequence sequence = new Sequence(caster, getDamageSource(caster), caster.position(), forward,
                target, targetPoint, spellLevel, level.getGameTime());
        ACTIVE.computeIfAbsent(caster.getServer(), ignored -> new HashMap<>()).put(caster.getUUID(), sequence);
    }

    /** Called from the server tick event; all three hit frames are server-authoritative. */
    public static void tick(MinecraftServer server) {
        Map<UUID, Sequence> sequences = ACTIVE.get(server);
        if (sequences == null) return;
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Sequence>> iterator = sequences.entrySet().iterator();
        while (iterator.hasNext()) {
            Sequence sequence = iterator.next().getValue();
            if (sequence.caster.isRemoved() || !sequence.caster.isAlive()) {
                iterator.remove();
                continue;
            }
            int stage = (int) ((now - sequence.startTick) / STAGE_TICKS);
            if (stage >= STAGE_COUNT) {
                iterator.remove();
                continue;
            }
            if (stage != sequence.lastStage) {
                sequence.lastStage = stage;
                spawnStage(sequence, stage);
            }
            showTelegraph(sequence, now);
        }
        if (sequences.isEmpty()) ACTIVE.remove(server);
    }

    private static void spawnStage(Sequence sequence, int stage) {
        if (!(sequence.caster.level() instanceof ServerLevel level)) return;
        Vec3 point = sequence.target != null && sequence.target.isAlive()
                ? sequence.target.position().add(0.0D, 0.15D, 0.0D) : sequence.targetPoint;
        Vec3 forward = sequence.forward;
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x).normalize();
        switch (stage) {
            case 0 -> {
                // Two blades approach from opposite sides and cross on the target.
                spawnBlade(level, sequence.caster, point.subtract(forward.scale(5.0D)).add(side.scale(2.4D)), point, 0.0F);
                spawnBlade(level, sequence.caster, point.subtract(forward.scale(5.0D)).subtract(side.scale(2.4D)), point, 0.0F);
                slashParticles(level, point, 1.15F, false);
                applyStageDamage(sequence, point, 0);
            }
            case 1 -> {
                // A reverse pass comes from behind the locked target and cuts back through it.
                Vec3 behind = point.add(forward.scale(6.0D));
                spawnBlade(level, sequence.caster, behind.add(side.scale(1.4D)), point.subtract(forward.scale(4.0D)), 0.0F);
                spawnBlade(level, sequence.caster, behind.subtract(side.scale(1.4D)), point.subtract(forward.scale(4.0D)), 0.0F);
                slashParticles(level, point, 1.3F, true);
                applyStageDamage(sequence, point, 1);
            }
            case 2 -> {
                // Enlarged diagonal finisher; the particle scale makes the hit frame read clearly.
                Vec3 start = point.add(side.scale(4.0D)).add(0.0D, 2.4D, 0.0D);
                Vec3 end = point.subtract(side.scale(4.0D)).add(0.0D, 0.15D, 0.0D);
                spawnBlade(level, sequence.caster, start, end, 1.8F);
                slashParticles(level, point.add(0.0D, 1.0D, 0.0D), 2.0F, true);
                applyStageDamage(sequence, point, 2);
            }
            default -> { }
        }
        level.playSound(null, point.x, point.y, point.z, SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.25F, 0.78F + stage * 0.12F);
    }

    private static void applyStageDamage(Sequence sequence, Vec3 point, int stage) {
        if (!(sequence.caster.level() instanceof ServerLevel level)) return;
        LivingEntity target = sequence.target;
        if (target != null && target.isAlive() && !target.isRemoved()
                && target.distanceToSqr(sequence.caster) <= MAX_TARGET_DISTANCE_SQR) {
            revealForTeamHealthbar(sequence.caster, target);
            if (target.hurt(sequence.damageSource, damageFor(sequence.spellLevel, sequence.caster)) && stage == 1) {
                Vec3 velocity = target.getDeltaMovement();
                target.setDeltaMovement(velocity.x * 0.3D, 0.72D, velocity.z * 0.3D);
                target.hasImpulse = true;
                target.hurtMarked = true;
            }
            return;
        }
        AABB box = new AABB(point, point).inflate(1.8D, 1.3D, 1.8D);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity != sequence.caster && entity.isAlive() && !entity.isRemoved())) {
            revealForTeamHealthbar(sequence.caster, victim);
            victim.hurt(sequence.damageSource, damageFor(sequence.spellLevel, sequence.caster));
        }
    }

    private static void showTelegraph(Sequence sequence, long now) {
        if (!(sequence.caster.level() instanceof ServerLevel level) || (now & 1L) != 0L) return;
        Vec3 p = sequence.targetPoint;
        level.sendParticles(ParticleTypes.CRIT, p.x, p.y + 0.25D, p.z, 3, 0.35D, 0.15D, 0.35D, 0.02D);
    }

    private static float damageFor(int spellLevel, LivingEntity caster) {
        AbstractSpell spell = SpellRegistry.getSpell(ID);
        float power = spell == SpellRegistry.none() ? 1.0F : spell.getSpellPower(spellLevel, caster);
        return Math.max(1.0F, DAMAGE_PER_SPELL_POWER * power);
    }

    private static LivingEntity findTarget(ServerLevel level, LivingEntity caster, Vec3 forward) {
        AABB box = caster.getBoundingBox().expandTowards(forward.scale(MAX_TARGET_DISTANCE)).inflate(1.6D, 1.6D, 1.6D);
        return level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity != caster && entity.isAlive()
                        && caster.distanceToSqr(entity) <= MAX_TARGET_DISTANCE_SQR)
                .stream().filter(entity -> {
                    Vec3 to = entity.getEyePosition().subtract(caster.getEyePosition());
                    return to.lengthSqr() > 0.01D && forward.dot(to.normalize()) > 0.35D;
                }).min(Comparator.comparingDouble(caster::distanceToSqr)).orElse(null);
    }

    private static void spawnBlade(ServerLevel level, LivingEntity caster, Vec3 start, Vec3 end, float scale) {
        Vec3 delta = end.subtract(start);
        Vec3 direction = delta.lengthSqr() < 1.0E-6D ? new Vec3(0, 0, 1) : delta.normalize();
        try {
            Class<?> type = Class.forName("net.hazen.hazennstuff.Entity.Spells.Shadow.NightsEdgeAfterSlash.NightsEdgeAfterSlash");
            Constructor<?> constructor = type.getConstructor(Level.class, LivingEntity.class);
            Object value = constructor.newInstance(level, caster);
            if (!(value instanceof Entity blade)) return;
            blade.setPos(start.x, start.y, start.z);
            blade.setDeltaMovement(direction.scale(1.6D));
            blade.setYRot((float) (Math.atan2(-direction.x, direction.z) * 180.0D / Math.PI));
            blade.setXRot((float) (Math.asin(-direction.y) * 180.0D / Math.PI));
            Method setDamage = type.getMethod("setDamage", float.class);
            setDamage.invoke(blade, 0.0F); // this spell applies its own scaled damage exactly once
            if (scale > 1.0F) trySetScale(blade, scale);
            level.addFreshEntity(blade);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Hazennstuff is optional; the particle fallback below still shows the hit frame.
        }
        slashParticles(level, start.add(end).scale(0.5D), Math.max(1.0F, scale), direction.y > 0.3D);
    }

    private static void trySetScale(Entity blade, float scale) {
        try {
            Method method = blade.getClass().getMethod("setScale", float.class);
            method.invoke(blade, scale);
        } catch (ReflectiveOperationException ignored) { }
    }

    private static void slashParticles(ServerLevel level, Vec3 pos, float scale, boolean vertical) {
        try {
            Field field = Class.forName("net.hazen.hazennstuff.Registries.HnSParticleHelper")
                    .getField("NIGHTS_EDGE_PARTICLE");
            Object value = field.get(null);
            if (value instanceof ParticleOptions particle) {
                level.sendParticles(particle, pos.x, pos.y, pos.z, 12, 0.55D * scale, 0.45D * scale,
                        0.55D * scale, 0.25D);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) { }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y, pos.z, 7,
                0.7D * scale, 0.55D * scale, 0.7D * scale, 0.02D);
    }

    private static void revealForTeamHealthbar(LivingEntity caster, LivingEntity target) {
        if (!(caster instanceof ServerPlayer player)) return;
        try {
            Class<?> tracker = Class.forName("com.lin.teamhealthbar.server.AttackTracker");
            tracker.getMethod("markAsAttacked", ServerPlayer.class, LivingEntity.class).invoke(null, player, target);
        } catch (ReflectiveOperationException | LinkageError ignored) { }
    }

    private static final class Sequence {
        final LivingEntity caster;
        final DamageSource damageSource;
        final Vec3 origin;
        final Vec3 forward;
        final LivingEntity target;
        final Vec3 targetPoint;
        final int spellLevel;
        final long startTick;
        int lastStage = -1;

        Sequence(LivingEntity caster, DamageSource damageSource, Vec3 origin, Vec3 forward,
                 LivingEntity target, Vec3 targetPoint,
                 int spellLevel, long startTick) {
            this.caster = caster;
            this.damageSource = damageSource;
            this.origin = origin;
            this.forward = forward;
            this.target = target;
            this.targetPoint = targetPoint;
            this.spellLevel = spellLevel;
            this.startTick = startTick;
        }
    }
}
