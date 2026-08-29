package com.example.scrollspellicons.spells;

import com.mojang.math.Transformation;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 烬龙天门·赤霄断空。
 *
 * The gate and blade are made from particles plus an ItemDisplay carrying
 * hazennstuff:draconic_splitter. ItemDisplay is non-living, so no mob AI or
 * native weapon damage can interfere with the authoritative spell damage.
 */
public final class BlazingDragonCorridorSpell extends AddonModelSpell {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            "iron_magic_duel", "blazing_dragon_corridor");
    private static final ResourceLocation SOURCE = ResourceLocation.fromNamespaceAndPath(
            "iss_magicfromtheeast", "dragon_glide");
    private static final ResourceLocation DRACONIC_SPLITTER = ResourceLocation.fromNamespaceAndPath(
            "hazennstuff", "draconic_splitter");

    private static final int CHARGE_TICKS = 12;
    private static final int SWING_TICKS = 12;
    /** Keep the completed blade at the final impact point for three seconds. */
    private static final int CLEANUP_TICKS = CHARGE_TICKS + SWING_TICKS + 60;
    private static final double SLASH_DISTANCE = 14.0D;
    private static final double SLASH_WIDTH = 1.25D;
    private static final float SLASH_DAMAGE = 12.0F;
    private static final double HIGH_AIR_OFFSET = 8.0D;
    /** draconic_splitter is a tall centered item model; keep its tip above the floor. */
    private static final double BLADE_HALF_HEIGHT = 4.5D;
    private static final double TARGET_RADIUS = 1.8D;
    private static final double TARGET_TRACK_MAX_DISTANCE = 32.0D;
    private static final float DISPLAY_SCALE = 3.8F;
    private static final DustParticleOptions RED_FLAME =
            new DustParticleOptions(new Vector3f(0.92F, 0.025F, 0.01F), 1.55F);
    private static final DustParticleOptions GOLD_FLAME =
            new DustParticleOptions(new Vector3f(1.0F, 0.44F, 0.015F), 1.15F);
    private static final Map<MinecraftServer, Map<UUID, Sequence>> ACTIVE = new HashMap<>();
    private static final Set<MinecraftServer> STARTUP_CLEANED =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static Method itemDisplaySetTransform;
    private static Method itemDisplaySetInterpolationDuration;

    public BlazingDragonCorridorSpell() {
        super(ID, SOURCE, SchoolRegistry.FIRE_RESOURCE, SpellRarity.EPIC, 48, 0, CastType.LONG);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", "12.0").append("（落点爆炸）"),
                Component.literal("烬龙天门·赤霄断空：身后展开赤红天门，召出龙骨火刃"),
                Component.literal("剑刃升至高空，剑尖向下完整挥砍，落点爆炸造成唯一一次12点伤害"),
                Component.literal("爆炸后剑刃原地停留3秒并消散，不追加伤害、不修改方块"));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource,
                       MagicData magic) {
        if (level.isClientSide || caster.getServer() == null || !(level instanceof ServerLevel server)) return;
        Vec3 forward = caster.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (forward.lengthSqr() < 1.0E-6D) forward = new Vec3(0.0D, 0.0D, 1.0D);
        forward = forward.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x).normalize();
        Vec3 origin = caster.position().add(0.0D, 0.1D, 0.0D);
        // The gate is behind the caster. The blade rises from here into the
        // air and then drops tip-first; its whole model stays above the floor.
        Vec3 gate = origin.subtract(forward.scale(1.8D))
                .add(0.0D, BLADE_HALF_HEIGHT + 1.5D, 0.0D);
        LivingEntity target = findTarget(server, caster, forward);
        Vec3 impactPoint = target == null
                ? origin.add(forward.scale(Math.min(10.0D, SLASH_DISTANCE))).add(0.0D, 1.0D, 0.0D)
                : target.getBoundingBox().getCenter();
        // strikePoint is the display/model centre. The blade tip reaches the
        // actual impact point after the model is flipped tip-down.
        Vec3 strikePoint = impactPoint.add(0.0D, BLADE_HALF_HEIGHT, 0.0D);
        cleanupDragonDisplays(caster.getServer());
        Map<UUID, Sequence> active = ACTIVE.computeIfAbsent(caster.getServer(), ignored -> new HashMap<>());
        Sequence previous = active.remove(caster.getUUID());
        if (previous != null) previous.discard();
        Sequence sequence = new Sequence(caster, getDamageSource(caster), origin, gate, forward, side,
                target, strikePoint, impactPoint, level.getGameTime());
        sequence.blade = spawnDragonBlade(server, gate, forward);
        active.put(caster.getUUID(), sequence);
        drawGate(server, sequence, 0);
        server.playSound(null, gate.x, gate.y, gate.z, SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS, 1.15F, 0.65F);
    }

    public static void tick(MinecraftServer server) {
        // Remove ItemDisplays saved by an earlier build before processing any
        // new cast, so an old blade cannot survive a server restart.
        if (STARTUP_CLEANED.add(server)) cleanupDragonDisplays(server);
        Map<UUID, Sequence> sequences = ACTIVE.get(server);
        if (sequences == null) return;
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Sequence>> iterator = sequences.entrySet().iterator();
        while (iterator.hasNext()) {
            Sequence sequence = iterator.next().getValue();
            int age = (int) (now - sequence.startTick);
            if (!sequence.caster.isAlive() || sequence.caster.isRemoved() || age >= CLEANUP_TICKS) {
                sequence.discard();
                iterator.remove();
                continue;
            }
            if (!(sequence.caster.level() instanceof ServerLevel level)) continue;
            // Keep the locked player's current body centre as the authoritative
            // horizontal destination throughout the aerial descent. If the
            // player dies, disconnects, changes dimension, or runs too far,
            // retain the last valid point instead of snapping elsewhere.
            // Tracking ends on the exact tick the single impact explosion is
            // resolved.  The post-impact display is an inert visual at the
            // frozen final point and never follows the target again.
            if (!sequence.slashApplied) updateTrackedTarget(sequence);
            if (age < CHARGE_TICKS) {
                drawGate(level, sequence, age);
                drawCharge(level, sequence, age);
                double rise = easeInOut(age / (double) CHARGE_TICKS);
                Vec3 current = sequence.gate.lerp(sequence.highPoint(), rise);
                setBlade(sequence.blade, current, sequence.forward, 0.0F);
            } else if (age < CHARGE_TICKS + SWING_TICKS) {
                int swingAge = age - CHARGE_TICKS;
                double progress = easeInOut(Math.min(1.0D, (swingAge + 1.0D) / SWING_TICKS));
                Vec3 current = sequence.highPoint().lerp(sequence.strikePoint, progress);
                setBlade(sequence.blade, current, sequence.forward, 0.0F);
                drawDescendingSlash(level, sequence, current);
                // The hit is resolved at the end of the complete swing.  A
                // target can therefore never make the blade disappear early.
                if (swingAge == SWING_TICKS - 1 && !sequence.slashApplied) {
                    sequence.slashApplied = true;
                    damageDescendingStrike(sequence);
                    level.playSound(null, sequence.strikePoint.x, sequence.strikePoint.y,
                            sequence.strikePoint.z,
                            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.35F, 0.55F);
                }
            } else {
                setBlade(sequence.blade, sequence.strikePoint, sequence.forward, 0.0F);
                drawDescendingSlash(level, sequence, sequence.strikePoint);
            }
        }
        if (sequences.isEmpty()) ACTIVE.remove(server);
    }

    /** Discards every active sequence and its display entities during logout/server stop. */
    public static void cancelAll(MinecraftServer server) {
        Map<UUID, Sequence> sequences = ACTIVE.remove(server);
        if (sequences != null) {
            for (Sequence sequence : sequences.values()) sequence.discard();
        }
        cleanupDragonDisplays(server);
    }

    /** Cancels only the sequence owned by one player (death/logout/interruption). */
    public static void cancel(MinecraftServer server, UUID casterId) {
        Map<UUID, Sequence> sequences = ACTIVE.get(server);
        if (sequences == null) return;
        Sequence sequence = sequences.remove(casterId);
        if (sequence != null) sequence.discard();
        if (sequences.isEmpty()) ACTIVE.remove(server);
    }

    private static void drawGate(ServerLevel level, Sequence sequence, int age) {
        double pulse = 0.85D + Math.sin(age * 0.42D) * 0.15D;
        for (int y = 0; y <= 5; y++) {
            double height = y * 0.58D;
            for (int edge : new int[]{-1, 1}) {
                Vec3 point = sequence.gate.add(sequence.side.scale(edge * 2.0D * pulse))
                        .add(0.0D, height - 1.5D, 0.0D);
                level.sendParticles(RED_FLAME, point.x, point.y, point.z, 5,
                        0.13D, 0.11D, 0.13D, 0.015D);
                level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 3,
                        0.08D, 0.12D, 0.08D, 0.02D);
            }
        }
        for (int i = -4; i <= 4; i++) {
            Vec3 point = sequence.gate.add(sequence.side.scale(i * 0.5D)).add(0.0D, 1.5D, 0.0D);
            level.sendParticles(GOLD_FLAME, point.x, point.y, point.z, 3,
                    0.12D, 0.08D, 0.12D, 0.01D);
        }
        if ((age & 3) == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, sequence.gate.x,
                    sequence.gate.y + 1.5D, sequence.gate.z, 10, 1.7D, 1.1D, 0.12D, 0.03D);
        }
    }

    private static void drawCharge(ServerLevel level, Sequence sequence, int age) {
        Vec3 center = sequence.gate.add(0.0D, 1.5D, 0.0D);
        int count = 10 + age / 3;
        for (int i = 0; i < count; i++) {
            double angle = (age * 0.32D + i * Math.PI * 2.0D / count);
            Vec3 point = center.add(sequence.side.scale(Math.cos(angle) * 1.45D))
                    .add(0.0D, Math.sin(angle) * 1.3D, 0.0D);
            level.sendParticles(GOLD_FLAME, point.x, point.y, point.z, 1,
                    0.04D, 0.04D, 0.04D, 0.005D);
        }
        Vec3 rising = sequence.gate.lerp(sequence.highPoint(),
                easeInOut(Math.min(1.0D, age / (double) CHARGE_TICKS)));
        level.sendParticles(RED_FLAME, rising.x, rising.y, rising.z, 8,
                0.18D, 0.18D, 0.18D, 0.02D);
    }

    private static void drawDescendingSlash(ServerLevel level, Sequence sequence, Vec3 current) {
        Vec3 high = sequence.highPoint();
        for (int i = 0; i <= 28; i++) {
            Vec3 point = high.lerp(sequence.impactPoint, i / 28.0D);
            level.sendParticles(RED_FLAME, point.x, point.y, point.z, 2,
                    0.16D, 0.16D, 0.16D, 0.008D);
            if ((i & 1) == 0) {
                level.sendParticles(GOLD_FLAME, point.x, point.y + 0.08D, point.z, 1,
                        0.09D, 0.1D, 0.09D, 0.005D);
            }
        }
        level.sendParticles(ParticleTypes.FLAME,
                current.x, current.y, current.z,
                12, 0.35D, 0.35D, 0.35D, 0.025D);
    }

    private static void damageDescendingStrike(Sequence sequence) {
        if (!(sequence.caster.level() instanceof ServerLevel level)) return;
        // This spell locks one enemy player at cast time; do not turn the
        // visual landing point into a broad area-of-effect hit.
        LivingEntity target = sequence.target;
        Vec3 point = sequence.impactPoint;
        if (target != null && target.isAlive() && !target.isRemoved()
                && target.level() == sequence.caster.level()
                && sequence.slashHits.add(target.getUUID())) {
            markTeamHealthbar(sequence.caster, target);
            target.hurt(sequence.casterDamageSource, SLASH_DAMAGE);
            point = target.getBoundingBox().getCenter();
            // Freeze the exact body-centre used for this one and only damage
            // event; subsequent movement cannot move the visual or hit again.
            sequence.impactPoint = point;
            sequence.strikePoint = point.add(0.0D, BLADE_HALF_HEIGHT, 0.0D);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y,
                point.z, 2, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, point.x, point.y,
                point.z, 72, 0.75D, 0.9D, 0.75D, 0.09D);
        level.sendParticles(ParticleTypes.LAVA, point.x, point.y,
                point.z, 18, 0.45D, 0.6D, 0.45D, 0.02D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y,
                point.z, 28, 0.45D, 0.75D, 0.45D, 0.04D);
        level.playSound(null, point.x, point.y, point.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.8F, 0.75F);
        level.playSound(null, point.x, point.y, point.z,
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.65F);
        level.playSound(null, point.x, point.y, point.z,
                SoundEvents.ENDER_DRAGON_HURT, SoundSource.PLAYERS, 0.8F, 1.25F);
    }

    private static double easeInOut(double value) {
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static ServerPlayer findTarget(ServerLevel level, LivingEntity caster, Vec3 forward) {
        AABB box = caster.getBoundingBox().expandTowards(forward.scale(SLASH_DISTANCE))
                .inflate(1.8D, 2.5D, 1.8D);
        return level.getEntitiesOfClass(ServerPlayer.class, box,
                        entity -> entity != caster && entity.isAlive() && !entity.isRemoved()
                                && !entity.isSpectator())
                .stream()
                .filter(entity -> caster.distanceToSqr(entity) <= SLASH_DISTANCE * SLASH_DISTANCE)
                .filter(entity -> {
                    Vec3 to = entity.getEyePosition().subtract(caster.getEyePosition());
                    return to.lengthSqr() > 0.01D && forward.dot(to.normalize()) > 0.25D;
                })
                .min(Comparator.comparingDouble(caster::distanceToSqr)).orElse(null);
    }

    private static void updateTrackedTarget(Sequence sequence) {
        LivingEntity target = sequence.target;
        if (target == null || !target.isAlive() || target.isRemoved()
                || target.level() != sequence.caster.level()
                || sequence.caster.distanceToSqr(target) > TARGET_TRACK_MAX_DISTANCE * TARGET_TRACK_MAX_DISTANCE) {
            return;
        }
        sequence.impactPoint = target.getBoundingBox().getCenter();
        sequence.strikePoint = sequence.impactPoint.add(0.0D, BLADE_HALF_HEIGHT, 0.0D);
    }

    private static Entity spawnDragonBlade(ServerLevel level, Vec3 position, Vec3 forward) {
        if (!BuiltInRegistries.ITEM.containsKey(DRACONIC_SPLITTER)) return null;
        try {
            Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
            // Loading through the entity's public NBT path invokes the
            // private ItemDisplay setters safely on NeoForge 1.21.1.  This
            // keeps the exact draconic_splitter model instead of falling back
            // to a generic item sprite when mappings change.
            CompoundTag displayTag = new CompoundTag();
            CompoundTag itemTag = new CompoundTag();
            itemTag.putString("id", DRACONIC_SPLITTER.toString());
            itemTag.putInt("count", 1);
            displayTag.put("item", itemTag);
            displayTag.putString("item_display", "fixed");
            displayTag.putInt("interpolation_duration", 2);
            display.load(displayTag);
            invokeItemDisplay(display, forward);
            display.setPos(position.x, position.y, position.z);
            display.setNoGravity(true);
            // The weapon model itself must remain unlit: no entity outline,
            // glowing tag, or enchantment-style glint is applied here.
            display.setGlowingTag(false);
            display.getPersistentData().putBoolean("IronMagicDuelVisual", true);
            display.getPersistentData().putBoolean("IronMagicDuelDragonBlade", true);
            level.addFreshEntity(display);
            return display;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static void invokeItemDisplay(Display.ItemDisplay display, Vec3 forward)
            throws ReflectiveOperationException {
        if (itemDisplaySetTransform == null) {
            itemDisplaySetTransform = Display.class.getDeclaredMethod("setTransformation", Transformation.class);
            itemDisplaySetTransform.setAccessible(true);
        }
        setInterpolationDuration(display);
        float yaw = (float) Math.atan2(-forward.x, forward.z);
        // draconic_splitter's fixed item model is already vertical.  The
        // previous extra Z rotation laid it on its side.
        // The source model is vertical with its blade tip pointing up. Flip
        // around the local X axis so the tip points down for the aerial drop.
        Quaternionf rotation = new Quaternionf().rotateY(yaw).rotateX((float) Math.PI);
        itemDisplaySetTransform.invoke(display, new Transformation(new Vector3f(), rotation,
                new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE), new Quaternionf()));
    }

    private static void setBlade(Entity blade, Vec3 position, Vec3 forward, float roll) {
        if (blade == null || blade.isRemoved()) return;
        // Display transformation translations are local to the item's rotated
        // frame. Set the entity's world position directly so a moving target's
        // landing point cannot be rotated/offset away from its body centre.
        blade.setPos(position.x, position.y, position.z);
        blade.setYRot((float) (Math.atan2(-forward.x, forward.z) * 180.0D / Math.PI));
        blade.setXRot(0.0F);
        blade.setDeltaMovement(Vec3.ZERO);
        blade.setNoGravity(true);
        blade.hurtMarked = true;
        if (blade instanceof Display.ItemDisplay display) {
            try {
                applyBladeTransform(display, position, forward, roll);
            } catch (ReflectiveOperationException ignored) { }
        }
    }

    private static void applyBladeTransform(Display.ItemDisplay display, Vec3 translation, Vec3 forward,
                                            float swingDegrees)
            throws ReflectiveOperationException {
        if (itemDisplaySetTransform == null) {
            itemDisplaySetTransform = Display.class.getDeclaredMethod("setTransformation", Transformation.class);
            itemDisplaySetTransform.setAccessible(true);
        }
        float yaw = (float) Math.atan2(-forward.x, forward.z);
        Quaternionf rotation = new Quaternionf().rotateY(yaw + (float) Math.toRadians(swingDegrees))
                .rotateX((float) Math.PI);
        itemDisplaySetTransform.invoke(display, new Transformation(
                new Vector3f(), rotation,
                new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE), new Quaternionf()));
    }

    private static void setInterpolationDuration(Display.ItemDisplay display) {
        try {
            if (itemDisplaySetInterpolationDuration == null) {
                itemDisplaySetInterpolationDuration = Display.class.getDeclaredMethod(
                        "setTransformationInterpolationDuration", int.class);
                itemDisplaySetInterpolationDuration.setAccessible(true);
            }
            itemDisplaySetInterpolationDuration.invoke(display, 2);
        } catch (ReflectiveOperationException ignored) { }
    }

    /** Remove orphaned blades left by an interrupted cast or an older build. */
    private static void cleanupDragonDisplays(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Display.ItemDisplay display)) continue;
                boolean tagged = display.getPersistentData().getBoolean("IronMagicDuelDragonBlade");
                boolean legacy = display.getPersistentData().getBoolean("IronMagicDuelVisual")
                        && display.getSlot(0).get().is(BuiltInRegistries.ITEM.get(DRACONIC_SPLITTER));
                if (tagged || legacy) display.discard();
            }
        }
    }

    private static final class Sequence {
        final LivingEntity caster;
        final DamageSource casterDamageSource;
        final Vec3 origin;
        final Vec3 gate;
        final Vec3 forward;
        final Vec3 side;
        final LivingEntity target;
        Vec3 strikePoint;
        Vec3 impactPoint;
        final long startTick;
        final Set<UUID> slashHits = new HashSet<>();
        Entity blade;
        boolean slashApplied;

        Sequence(LivingEntity caster, DamageSource casterDamageSource, Vec3 origin, Vec3 gate,
                 Vec3 forward, Vec3 side,
                 LivingEntity target, Vec3 strikePoint, Vec3 impactPoint, long startTick) {
            this.caster = caster;
            this.casterDamageSource = casterDamageSource;
            this.origin = origin;
            this.gate = gate;
            this.forward = forward;
            this.side = side;
            this.target = target;
            this.strikePoint = strikePoint;
            this.impactPoint = impactPoint;
            this.startTick = startTick;
        }

        Vec3 highPoint() { return strikePoint.add(0.0D, HIGH_AIR_OFFSET, 0.0D); }

        void discard() {
            if (blade != null && !blade.isRemoved()) blade.discard();
        }
    }

}
