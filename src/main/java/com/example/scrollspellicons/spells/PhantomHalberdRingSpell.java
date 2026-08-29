package com.example.scrollspellicons.spells;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.particle.ZapParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 雷罚换血：锁定一名敌人，记录双方彼此造成的最终伤害并在十秒后结算。 */
public final class PhantomHalberdRingSpell extends AddonModelSpell {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            "iron_magic_duel", "phantom_halberd_ring");
    private static final ResourceLocation SOURCE = ResourceLocation.fromNamespaceAndPath(
            "hazennstuff", "dazzling_obliteration");
    private static final int DUEL_TICKS = 200;
    private static final double LOCK_RANGE = 16.0D;
    private static final float FINAL_LIGHTNING_DAMAGE = 25.0F;
    private static final Map<MinecraftServer, List<Duel>> ACTIVE = new HashMap<>();

    public PhantomHalberdRingSpell() {
        super(ID, SOURCE, SchoolRegistry.LIGHTNING_RESOURCE, SpellRarity.EPIC, 40, 0, CastType.LONG);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.literal("雷罚换血：锁定一名敌人，双方进入10秒强制决斗"),
                Component.literal("只累计双方彼此造成的最终实际伤害，第三方与环境伤害不计入"),
                Component.translatable("ui.irons_spellbooks.damage", "25.0（结算雷击）"),
                Component.literal("时间结束后伤害较低的一方受到25点雷击；相同伤害则平局"),
                Component.literal("仅生成雷电标记与粒子，不生成生物或戟环实体"));
    }

    /** Called before Iron initiates the cast; failure spends no cooldown or mana. */
    public static boolean canStart(ServerPlayer caster) {
        if (caster == null || caster.isSpectator() || activeFor(caster.getServer(), caster.getUUID()) != null) return false;
        LivingEntity target = findTarget(caster.serverLevel(), caster);
        return target != null && activeFor(caster.getServer(), target.getUUID()) == null;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource,
                       MagicData magic) {
        if (level.isClientSide || !(caster instanceof ServerPlayer player)
                || player.getServer() == null || !(level instanceof ServerLevel server)) return;
        if (activeFor(player.getServer(), player.getUUID()) != null) return;
        LivingEntity target = findTarget(server, player);
        if (target == null || activeFor(player.getServer(), target.getUUID()) != null) return;
        Duel duel = new Duel(player, target, server.getGameTime());
        ACTIVE.computeIfAbsent(player.getServer(), ignored -> new ArrayList<>()).add(duel);
        updateBossBar(duel, 0);
        drawLockVisual(duel);
    }

    public static void tick(MinecraftServer server) {
        List<Duel> duels = ACTIVE.get(server);
        if (duels == null) return;
        long now = server.overworld().getGameTime();
        // A settlement can synchronously fire LivingDeathEvent, whose cleanup
        // removes the same duel from ACTIVE.  Iterate over a snapshot so that
        // that legitimate lifecycle cleanup cannot invalidate this traversal.
        for (Duel duel : List.copyOf(duels)) {
            if (!duels.contains(duel)) continue;
            if (!validPair(duel)) {
                duel.closeBossBar();
                duels.remove(duel);
                continue;
            }
            int age = (int) Math.max(0L, now - duel.startTick);
            if (age >= DUEL_TICKS) {
                settle(duel);
                duel.closeBossBar();
                // settle() may have removed this duel through a death event;
                // remove is therefore intentionally idempotent here.
                duels.remove(duel);
                continue;
            }
            // BossEvent packets are deliberately rate-limited.  The same
            // server object stays visible for the whole duel; only its text
            // and progress are updated at a fixed 5-tick cadence.
            if (age % 5 == 0) updateBossBar(duel, age);
            drawMark(duel, age);
        }
        if (duels.isEmpty()) ACTIVE.remove(server);
    }

    /** Records LivingDamageEvent.Post's final post-mitigation damage. */
    public static void recordDamage(LivingEntity victim, DamageSource source, float finalDamage) {
        if (victim == null || source == null || finalDamage <= 0.0F) return;
        LivingEntity attacker = attackerOf(source);
        if (attacker == null || attacker == victim || victim.getServer() == null) return;
        Duel duel = activeFor(victim.getServer(), victim.getUUID());
        if (duel == null || !validPair(duel)) return;
        if (attacker.getUUID().equals(duel.caster.getUUID()) && victim.getUUID().equals(duel.target.getUUID())) {
            duel.casterToTarget += finalDamage;
        } else if (attacker.getUUID().equals(duel.target.getUUID()) && victim.getUUID().equals(duel.caster.getUUID())) {
            duel.targetToCaster += finalDamage;
        }
    }

    public static void cancelParticipant(MinecraftServer server, UUID participant) {
        List<Duel> duels = ACTIVE.get(server);
        if (duels == null) return;
        for (Duel duel : duels) {
            if (duel.caster.getUUID().equals(participant) || duel.target.getUUID().equals(participant)) {
                duel.closeBossBar();
            }
        }
        duels.removeIf(duel -> duel.caster.getUUID().equals(participant)
                || duel.target.getUUID().equals(participant));
        if (duels.isEmpty()) ACTIVE.remove(server);
    }

    public static void cancelAll(MinecraftServer server) {
        List<Duel> duels = ACTIVE.remove(server);
        if (duels != null) for (Duel duel : duels) duel.closeBossBar();
    }

    private static void settle(Duel duel) {
        ServerLevel level = duel.caster.serverLevel();
        if (Math.abs(duel.casterToTarget - duel.targetToCaster) < 0.0001F) {
            level.playSound(null, duel.caster.getX(), duel.caster.getY(), duel.caster.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.4F);
            return;
        }
        LivingEntity loser = duel.casterToTarget < duel.targetToCaster ? duel.caster : duel.target;
        if (!loser.isAlive() || loser.isRemoved()) return;
        Vec3 point = loser.position().add(0.0D, loser.getBbHeight() * 0.5D, 0.0D);
        spawnIronLightningStrike(level, point);
        drawLargePunishmentColumn(level, point);
        level.playSound(null, point.x, point.y, point.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.5F, 0.85F);
        level.playSound(null, point.x, point.y, point.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.4F, 0.7F);
        AddonModelSpell.markTeamHealthbar(duel.caster, loser);
        loser.hurt(level.damageSources().magic(), FINAL_LIGHTNING_DAMAGE);
    }

    private static void updateBossBar(Duel duel, int age) {
        int remaining = Math.max(0, DUEL_TICKS - age);
        String targetName = duel.target.getName().getString();
        String name = "雷罚换血  " + (remaining / 20.0F) + "秒  "
                + duel.caster.getName().getString() + " " + format(duel.casterToTarget)
                + "  :  " + targetName + " " + format(duel.targetToCaster);
        if (age == duel.lastBossAge && name.equals(duel.lastBossName)) return;
        duel.bossBar.setProgress(Math.max(0.0F, Math.min(1.0F, remaining / (float) DUEL_TICKS)));
        duel.bossBar.setName(Component.literal(name));
        duel.lastBossAge = age;
        duel.lastBossName = name;
    }

    /** Uses Iron's LightningStrike AOE entity so clients receive its fog,
     * shockwave, Zap particles and strike sound path rather than a tiny
     * vanilla lightning bolt. Damage is kept at zero here; the duel applies
     * exactly one 25-point hit to the selected loser below. */
    private static void spawnIronLightningStrike(ServerLevel level, Vec3 point) {
        io.redspace.ironsspellbooks.entity.spells.LightningStrike strike =
                new io.redspace.ironsspellbooks.entity.spells.LightningStrike(level);
        strike.setPos(point.x, point.y + 0.05D, point.z);
        // Iron's own LightningStrike renderer/effect remains authoritative;
        // enlarge its AOE shell and keep it alive long enough for the column
        // below to read as one continuous finishing strike. Damage is zero so
        // the duel's single fixed 25-point hit remains the only damage.
        strike.setRadius(8.0F);
        strike.setDuration(45);
        strike.setDelay(0);
        strike.setDamage(0.0F);
        strike.getPersistentData().putBoolean("IronMagicDuelPunishmentLightning", true);
        level.addFreshEntity(strike);
    }

    /**
     * Sends a large set of Iron's ZapParticle segments from a high point down
     * to the loser. Each segment is anchored to the same target center, so the
     * client sees a broad, continuous 28-block lightning column rather than a
     * small bolt or an unrelated sky effect. This is visual-only.
     */
    private static void drawLargePunishmentColumn(ServerLevel level, Vec3 point) {
        final double topY = point.y + 28.0D;
        for (int i = 0; i < 18; i++) {
            double angle = (Math.PI * 2.0D * i) / 18.0D;
            double radius = 0.35D + (i % 5) * 0.22D;
            Vec3 top = new Vec3(point.x + Math.cos(angle) * radius,
                    topY - (i % 4) * 1.5D,
                    point.z + Math.sin(angle) * radius);
            level.sendParticles(new ZapParticleOption(top), point.x, point.y, point.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y + 12.0D, point.z,
                320, 2.2D, 14.0D, 2.2D, 0.22D);
        level.sendParticles(ParticleTypes.END_ROD, point.x, point.y + 14.0D, point.z,
                160, 1.6D, 14.0D, 1.6D, 0.1D);
        level.sendParticles(ParticleTypes.FLASH, point.x, point.y + 1.0D, point.z,
                4, 0.8D, 0.8D, 0.8D, 0.0D);
    }

    private static void drawMark(Duel duel, int age) {
        ServerLevel level = duel.caster.serverLevel();
        double pulse = 0.28D + Math.sin(age * 0.35D) * 0.08D;
        for (LivingEntity entity : new LivingEntity[]{duel.caster, duel.target}) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(),
                    entity.getY() + entity.getBbHeight() + 0.35D, entity.getZ(), 4,
                    pulse, 0.12D, pulse, 0.02D);
            level.sendParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getY() + 1.0D,
                    entity.getZ(), 2 + (age / 40), pulse * 0.7D, 0.45D, pulse * 0.7D, 0.02D);
        }
        // A thin arc between the two participants makes the authoritative
        // pair obvious without creating a projectile or another hit source.
        if (age % 2 == 0) drawArc(level, duel.caster.getEyePosition(), duel.target.getEyePosition(), 8);
        if (age % 20 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT, duel.target.getX(), duel.target.getY() + 1.0D,
                    duel.target.getZ(), 10, 0.4D, 0.7D, 0.4D, 0.08D);
        }
    }

    private static void drawLockVisual(Duel duel) {
        ServerLevel level = duel.caster.serverLevel();
        drawArc(level, duel.caster.getEyePosition(), duel.target.getEyePosition(), 18);
        for (LivingEntity entity : new LivingEntity[]{duel.caster, duel.target}) {
            level.sendParticles(ParticleTypes.FLASH, entity.getX(), entity.getY() + 1.0D,
                    entity.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY() + 1.0D,
                    entity.getZ(), 24, 0.65D, 0.9D, 0.65D, 0.05D);
        }
        level.playSound(null, duel.target.getX(), duel.target.getY(), duel.target.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2F, 1.35F);
    }

    private static void drawArc(ServerLevel level, Vec3 from, Vec3 to, int segments) {
        Vec3 delta = to.subtract(from);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            Vec3 point = from.add(delta.scale(t));
            double wobble = Math.sin(i * 2.7D) * 0.08D;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x + wobble,
                    point.y, point.z - wobble, 2, 0.04D, 0.04D, 0.04D, 0.01D);
        }
    }

    private static String format(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static boolean validPair(Duel duel) {
        return duel.caster.isAlive() && !duel.caster.isRemoved()
                && duel.target.isAlive() && !duel.target.isRemoved()
                && duel.caster.level() == duel.target.level();
    }

    private static Duel activeFor(MinecraftServer server, UUID id) {
        if (server == null) return null;
        List<Duel> duels = ACTIVE.get(server);
        if (duels == null) return null;
        for (Duel duel : duels) {
            if (duel.caster.getUUID().equals(id) || duel.target.getUUID().equals(id)) return duel;
        }
        return null;
    }

    private static LivingEntity findTarget(ServerLevel level, ServerPlayer caster) {
        Vec3 look = caster.getLookAngle();
        AABB box = caster.getBoundingBox().expandTowards(look.scale(LOCK_RANGE)).inflate(2.0D, 2.0D, 2.0D);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity != caster && entity.isAlive() && !entity.isRemoved()
                        && !(entity instanceof Player player && player.isSpectator())
                        && caster.distanceToSqr(entity) <= LOCK_RANGE * LOCK_RANGE);
        Comparator<LivingEntity> byAim = Comparator.comparingDouble(entity -> {
            Vec3 to = entity.getEyePosition().subtract(caster.getEyePosition());
            return to.lengthSqr() < 0.0001D ? Double.MAX_VALUE : 1.0D - look.dot(to.normalize());
        });
        List<LivingEntity> players = candidates.stream().filter(entity -> entity instanceof ServerPlayer)
                .filter(entity -> aimDot(caster, entity) >= 0.35D).sorted(byAim).toList();
        if (!players.isEmpty()) return players.get(0);
        return candidates.stream().filter(entity -> aimDot(caster, entity) >= 0.35D)
                .sorted(byAim).findFirst().orElse(null);
    }

    private static double aimDot(ServerPlayer caster, LivingEntity target) {
        Vec3 to = target.getEyePosition().subtract(caster.getEyePosition());
        return to.lengthSqr() < 0.0001D ? -1.0D : caster.getLookAngle().dot(to.normalize());
    }

    private static LivingEntity attackerOf(DamageSource source) {
        if (source.getEntity() instanceof LivingEntity living) return living;
        if (source.getDirectEntity() instanceof LivingEntity living) return living;
        if (source.getEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof LivingEntity owner) return owner;
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof LivingEntity owner) return owner;
        return null;
    }

    private static final class Duel {
        final ServerPlayer caster;
        final LivingEntity target;
        final long startTick;
        final ServerBossEvent bossBar;
        float casterToTarget;
        float targetToCaster;
        int lastBossAge = Integer.MIN_VALUE;
        String lastBossName = "";
        boolean closed;

        Duel(ServerPlayer caster, LivingEntity target, long startTick) {
            this.caster = caster;
            this.target = target;
            this.startTick = startTick;
            this.bossBar = new ServerBossEvent(
                    Component.literal("雷罚换血"), BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.PROGRESS);
            this.bossBar.setDarkenScreen(false);
            this.bossBar.setCreateWorldFog(false);
            this.bossBar.setPlayBossMusic(false);
            this.bossBar.addPlayer(caster);
            if (target instanceof ServerPlayer targetPlayer) this.bossBar.addPlayer(targetPlayer);
        }

        void closeBossBar() {
            if (closed) return;
            closed = true;
            bossBar.removeAllPlayers();
        }
    }
}
