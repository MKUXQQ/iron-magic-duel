package com.example.scrollspellicons.spells;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ICastData;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Shared base for the two visual duel spells.  Earlier versions delegated to
 * addon summon spells, which created persistent summon entities.  These spells
 * deliberately use only server-side particles and direct hit detection: the
 * addon-inspired visuals are transient effects, never summon creatures.
 */
public abstract class AddonModelSpell extends AbstractSpell {
    private final ResourceLocation id;
    private final DefaultConfig defaultConfig;
    private final CastType castType;

    protected AddonModelSpell(ResourceLocation id, ResourceLocation sourceId,
                               ResourceLocation school, SpellRarity rarity,
                               int manaCost, int castTime, CastType castType) {
        this.id = id;
        // Keep the framework-provided cast type and base cast time.  The
        // portable inscription/SpellBalanceStore modifier is allowed to
        // replace the spell's effective cast time; this class must not turn
        // every authored spell into an unconditional instant cast.
        this.castType = castType;
        this.defaultConfig = new DefaultConfig()
                .setMinRarity(rarity)
                .setSchoolResource(school)
                .setMaxLevel(1)
                // Keep the new visual spells subject to the same native
                // cooldown gate as every other duel spell.
                .setCooldownSeconds(8.0D)
                // Portable Inscription Table uses this flag when it rewrites
                // a cat_rune/scroll. Authored duel spells are intentionally
                // editable just like addon spells.
                .setAllowCrafting(true)
                .build();
        this.baseManaCost = manaCost;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.castTime = Math.max(0, castTime);
    }

    @Override public final ResourceLocation getSpellResource() { return id; }
    @Override public final DefaultConfig getDefaultConfig() { return defaultConfig; }
    @Override public final CastType getCastType() { return castType; }

    @Override public AnimationHolder getCastStartAnimation() {
        return AnimationHolder.pass();
    }

    @Override public AnimationHolder getCastFinishAnimation() {
        return AnimationHolder.pass();
    }

    @Override public Optional<SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override public void onServerPreCast(Level level, int spellLevel, LivingEntity caster, MagicData magic) {
        // Intentionally empty: no addon summon is created during pre-cast.
    }

    @Override public void onClientPreCast(Level level, int spellLevel, LivingEntity caster,
                                          net.minecraft.world.InteractionHand hand, MagicData magic) {
        super.onClientPreCast(level, spellLevel, caster, hand, magic);
    }

    @Override public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource,
                                 MagicData magic) {
        // Implemented by each direct visual spell.
    }

    @Override public void onClientCast(Level level, int spellLevel, LivingEntity caster, ICastData data) {
        super.onClientCast(level, spellLevel, caster, data);
    }

    @Override public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(Component.literal("瞬发法术"));
    }

    @Override public boolean requiresLearning() { return false; }
    @Override public boolean allowLooting() { return false; }

    protected static void particle(ServerLevel level, ParticleOptions type, Vec3 pos, int count,
                                   double dx, double dy, double dz, double speed) {
        level.sendParticles(type, pos.x, pos.y, pos.z, count, dx, dy, dz, speed);
    }

    protected void hit(ServerLevel level, LivingEntity caster, Vec3 center, double radius,
                       float damage, boolean launch) {
        AABB box = new AABB(center, center).inflate(radius, radius, radius);
        DamageSource source = getDamageSource(caster);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity != caster && entity.isAlive() && !entity.isRemoved())) {
            markTeamHealthbar(caster, target);
            if (target.hurt(source, damage) && launch) {
                Vec3 delta = target.getDeltaMovement();
                target.setDeltaMovement(delta.x * 0.25D, 0.58D, delta.z * 0.25D);
                target.hasImpulse = true;
                target.hurtMarked = true;
            }
        }
    }

    /** Team Healthbar reveals a target after AttackEntityEvent; spell damage
     * does not fire that event, so explicitly mark spell victims as attacked. */
    protected static void markTeamHealthbar(LivingEntity caster, LivingEntity target) {
        if (!(caster instanceof ServerPlayer player)) return;
        try {
            Class<?> tracker = Class.forName("com.lin.teamhealthbar.server.AttackTracker");
            tracker.getMethod("markAsAttacked", ServerPlayer.class, LivingEntity.class)
                    .invoke(null, player, target);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Team Healthbar is optional; normal spell damage is unaffected.
        }
    }

    protected static void sound(ServerLevel level, Vec3 pos, SoundEvent event, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, event, SoundSource.PLAYERS, volume, pitch);
    }

    /** Resolve an optional add-on particle without hard-linking this mod to it. */
    protected static ParticleOptions addonParticle(String holderClass, String fieldName) {
        try {
            Object holder = Class.forName(holderClass).getField(fieldName).get(null);
            Object particle = holder.getClass().getMethod("get").invoke(holder);
            return particle instanceof ParticleOptions options ? options : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
