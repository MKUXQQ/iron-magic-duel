package com.example.scrollspellicons.duel;

import com.example.scrollspellicons.config.PerformanceConfig;
import com.example.scrollspellicons.IronSpellPerformance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.LearnedSpellData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.spells.magic_missile.MagicMissileProjectile;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import io.redspace.ironsspellbooks.entity.mobs.SummonedHorse;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import com.example.scrollspellicons.mixin.SyncedSpellDataLearnedAccessorMixin;
import io.redspace.ironsspellbooks.entity.spells.ender_chain.EnderChain;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import com.example.scrollspellicons.spells.CrosswindIronSlashSpell;
import com.example.scrollspellicons.spells.PhantomHalberdRingSpell;
import com.example.scrollspellicons.spells.BlazingDragonCorridorSpell;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Collections;
import java.util.HashSet;

@EventBusSubscriber(modid = "iron_magic_duel")
public final class SpellDuelEvents {
    private static final Map<MinecraftServer, SpellDuelManager> MANAGERS = new WeakHashMap<>();
    private static final Map<MinecraftServer, NoCastZoneManager> NO_CAST_ZONES = new WeakHashMap<>();
    private static final Map<java.util.UUID, Long> LAST_DAMAGE_TICKS = new java.util.HashMap<>();
    /** Five hearts (10 health) as a discrete pulse every two seconds after combat. */
    private static final float AUTO_REGEN_PER_PULSE = 10.0F;
    private static final long AUTO_REGEN_DELAY_TICKS = 100L;
    private static final long AUTO_REGEN_FIRST_PULSE_TICKS = 140L;
    private static final long AUTO_REGEN_INTERVAL_TICKS = 40L;
    private static final Map<java.util.UUID, Long> ICE_FREEZE_RELEASE_TICKS = new java.util.HashMap<>();
    /** One authoritative freeze state per target; all teardown goes through clearFreezeState. */
    public enum FreezeReason { CONE_OF_COLD, RAY_OF_FROST, SNOWBALL, GLACIAL_EDGE }
    private static final class FreezeState {
        final long startedAt;
        final long endsAt;
        final FreezeReason reason;
        final boolean appliedTicksFrozen;
        final boolean appliedSlowdown;
        final boolean appliedChilled;

        FreezeState(long startedAt, long endsAt, FreezeReason reason,
                    boolean appliedTicksFrozen, boolean appliedSlowdown, boolean appliedChilled) {
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.reason = reason;
            this.appliedTicksFrozen = appliedTicksFrozen;
            this.appliedSlowdown = appliedSlowdown;
            this.appliedChilled = appliedChilled;
        }
    }
    private static final Map<java.util.UUID, FreezeState> FREEZE_STATES = new java.util.HashMap<>();
    private static final Map<java.util.UUID, Long> ICE_TOMB_RELEASE_TICKS = new java.util.HashMap<>();
    /** Prevents the same continuous cone from immediately rebuilding a freeze after thaw. */
    private static final Map<java.util.UUID, Long> SPRAY_REFREEZE_BLOCK_UNTIL = new java.util.HashMap<>();
    /** Single authoritative duration for every freeze controlled by this mod. */
    public static final int FREEZE_DURATION_TICKS = 36;
    public static final int RAY_OF_FROST_FREEZE_TICKS = 20;
    public static final int SNOWBALL_FREEZE_TICKS = 36;
    private static final ResourceLocation ICE_TOMB_ENTITY_ID = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ice_tomb");
    private static final ResourceLocation CONE_OF_COLD_SPELL_ID = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "cone_of_cold");
    private static final ResourceLocation RAY_OF_FROST_SPELL_ID = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ray_of_frost");
    private static final ResourceLocation SNOWBALL_SPELL_ID = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "snowball");
    private static final ResourceLocation GLACIAL_EDGE_SPELL_ID = ResourceLocation.fromNamespaceAndPath("discerning_the_eldritch", "glacial_edge");
    private static final String GLACIAL_EDGE_CLASS = "net.acetheeldritchking.discerning_the_eldritch.entity.spells.glacial_edge.GlacialEdge";
    private static final String CONE_OF_COLD_PROJECTILE_CLASS = "io.redspace.ironsspellbooks.entity.spells.cone_of_cold.ConeOfColdProjectile";
    private static final String SNOWBALL_PROJECTILE_CLASS = "io.redspace.ironsspellbooks.entity.spells.snowball.Snowball";
    /** Per-projectile hit sets prevent Glacial Edge's persistent collision box from re-hurting a player. */
    private static final Map<Entity, Set<java.util.UUID>> GLACIAL_EDGE_HITS = new WeakHashMap<>();
    /** A Snowball may emit more than one impact callback while being removed. */
    private static final Map<Entity, Set<java.util.UUID>> SNOWBALL_FREEZE_HITS = new WeakHashMap<>();
    /** FrostField has no owner provenance, so projectile mixins mark fields created by the whitelist spells. */
    private static final Map<Entity, FreezeReason> MANAGED_FROST_FIELDS = new WeakHashMap<>();
    private static final Set<Entity> CONE_RESET_DIAGNOSTICS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Entity> SNOWBALL_DIAGNOSTICS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final String MAGIC_MISSILE_SPELL_ID = "irons_spellbooks:magic_missile";
    private static final Set<String> AUTHORED_SPELL_IDS = Set.of(
            "iron_magic_duel:crosswind_iron_slash",
            "iron_magic_duel:phantom_halberd_ring",
            "iron_magic_duel:astral_predator",
            "iron_magic_duel:blazing_dragon_corridor");

    /**
     * Identifies authored spell behavior without providing a second cooldown
     * source. The actual cooldown always comes from Iron's SpellConfigHolder,
     * including values applied by SpellBalanceStore.
     */
    public static boolean usesAuthoredCooldown(String spellId) {
        return spellId != null && AUTHORED_SPELL_IDS.contains(spellId);
    }
    private static final String EMPULSE_SPELL_ID = "traveloptics:em_pulse";
    private static final String THUNDER_LANCE_TAG = "IronMagicThunderLance";
    private static final String THUNDER_LANCE_FALLING_TAG = "Falling";
    private static final String DESERT_WINDS_SPEED_TAG = "IronMagicDesertWindsSpeed";
    private static final String BALL_LIGHTNING_SPEED_TAG = "IronMagicBallLightningSpeed";
    private static final String PETRIVISE_CLEANUP_TAG = "IronMagicPetriviseCleanup";
    private static final String FISSURE_COPY_TAG = "IronMagicFissureCopy";
    private static final String FISSURE_GROUP_TAG = "IronMagicFissureGroup";
    private static final String FISSURE_DIR_X_TAG = "IronMagicFissureDirX";
    private static final String FISSURE_DIR_Y_TAG = "IronMagicFissureDirY";
    private static final String FISSURE_DIR_Z_TAG = "IronMagicFissureDirZ";
    private static final String THUNDER_LANCE_HITBOX_TAG = "IronMagicThunderLanceHitbox";
    private static final String FISSURE_TARGET_TAG = "IronMagicFissureTarget";
    private static final String GRAVITY_FISSURE_TAG = "IronMagicGravityFissure";
    private static final String GRAVITY_FISSURE_START_TICK = "IronMagicGravityFissureStart";
    private static final long GRAVITY_FISSURE_LIFETIME = 80L;
    private static final double GRAVITY_FISSURE_RADIUS = 5.0D;
    private static final String SUMMONED_HORSE_TAG = "IronMagicSummonedHorse";
    private static final String SUMMONED_HORSE_HITS = "IronMagicSummonedHorseHits";
    private static final String SUMMONED_HORSE_RIDER = "IronMagicSummonedHorseRider";
    private static final String SUMMONED_HORSE_RELEASING = "IronMagicSummonedHorseReleasing";
    private static final Map<java.util.UUID, Long> RECENT_EMPULSE_CASTERS = new java.util.HashMap<>();
    private static final Map<java.util.UUID, Long> FIREFLY_TARGET_DAMAGE_TICKS = new java.util.HashMap<>();
    private static final Map<String, Long> BLOOD_HEAL_EVENTS = new java.util.HashMap<>();
    private static final Map<java.util.UUID, java.util.UUID> ARCANE_CHAIN_TARGETS = new java.util.HashMap<>();
    /** Entities currently participating in one of the optional per-tick behaviours. */
    private static final Set<Entity> ACTIVE_TRACKED_ENTITIES =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static long cooldownSyncTicks;
    private static long snapshotSyncTicks;
    private static long spellUnlockSyncTicks;

    private SpellDuelEvents() {}

    public static SpellDuelManager manager(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, SpellDuelManager::new);
    }

    public static NoCastZoneManager noCastZones(MinecraftServer server) {
        return NO_CAST_ZONES.computeIfAbsent(server, NoCastZoneManager::new);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        indexExistingTrackedEntities(event.getServer());
        if (PerformanceConfig.isServerConfigLoaded()
                && !PerformanceConfig.serverValues().deferDuelDataLoadAtStartup()) {
            manager(event.getServer());
            noCastZones(event.getServer());
        }
        for (net.minecraft.server.level.ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            unlockAllLearnableSpells(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BlazingDragonCorridorSpell.cancelAll(event.getServer());
        PhantomHalberdRingSpell.cancelAll(event.getServer());
        SpellDuelManager duelManager = MANAGERS.get(event.getServer());
        if (duelManager != null) duelManager.closeChallenges();
        for (net.minecraft.server.level.ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            clearLifecycleFreezeState(player, true);
        }
        ICE_FREEZE_RELEASE_TICKS.clear();
        FREEZE_STATES.clear();
        ICE_TOMB_RELEASE_TICKS.clear();
        SPRAY_REFREEZE_BLOCK_UNTIL.clear();
        FIREFLY_TARGET_DAMAGE_TICKS.clear();
        BLOOD_HEAL_EVENTS.clear();
        GLACIAL_EDGE_HITS.clear();
        SNOWBALL_FREEZE_HITS.clear();
        MANAGED_FROST_FIELDS.clear();
        CONE_RESET_DIAGNOSTICS.clear();
        SNOWBALL_DIAGNOSTICS.clear();
        clearArcaneShackleTitles(event.getServer());
        MANAGERS.remove(event.getServer());
        NO_CAST_ZONES.remove(event.getServer());
        ACTIVE_TRACKED_ENTITIES.clear();
        cooldownSyncTicks = 0L;
        snapshotSyncTicks = 0L;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        CrosswindIronSlashSpell.tick(event.getServer());
        PhantomHalberdRingSpell.tick(event.getServer());
        BlazingDragonCorridorSpell.tick(event.getServer());
        tickThunderLances(event.getServer());
        tickFangTargets(event.getServer());
        tickPetriviseCleanup(event.getServer());
        tickFissures(event.getServer());
        tickGravityFissures(event.getServer());
        tickSummonedHorses(event.getServer());
        tickArcaneShackles(event.getServer());
        SpellDuelManager manager = MANAGERS.get(event.getServer());
        if (manager != null) manager.tick();
        long now = event.getServer().getTickCount();
        tickManagedFreezes(event.getServer(), now);
        for (net.minecraft.server.level.ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Long lastDamage = LAST_DAMAGE_TICKS.get(player.getUUID());
            if (lastDamage != null) {
                long elapsed = now - lastDamage;
                if (elapsed >= AUTO_REGEN_FIRST_PULSE_TICKS
                        && (elapsed - AUTO_REGEN_FIRST_PULSE_TICKS) % AUTO_REGEN_INTERVAL_TICKS == 0L
                        && player.isAlive() && player.getHealth() > 0.0F
                        && player.getHealth() < player.getMaxHealth()) {
                    // Stable discrete two-second pulses: ten health points (five hearts).
                    player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + AUTO_REGEN_PER_PULSE));
                }
            }
        }
        if (manager != null && event.getServer().getTickCount() % 5 == 0) {
            for (net.minecraft.server.level.ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                if (player.getMainHandItem().is(SpellDuelItems.POINT_SELECTOR.get())
                        || player.getOffhandItem().is(SpellDuelItems.POINT_SELECTOR.get())) {
                    manager.showPointMarkers(player);
                }
            }
        }
        // Addon spells can finish registration after a player joins. Recheck at
        // a low frequency so all online players receive newly available spells.
        if (++spellUnlockSyncTicks % 200 == 0) {
            for (net.minecraft.server.level.ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                unlockAllLearnableSpells(player);
            }
        }
        if (manager == null) return;
        if (++snapshotSyncTicks % 10L == 0L) {
            SpellDuelNetwork.broadcastSnapshots(manager);
        }
        if (manager.displayEnabled() && ++cooldownSyncTicks % 5L == 0L) {
            SpellDuelNetwork.broadcastCooldowns(manager);
        }
    }

    /**
     * Sole direct-hit freeze entry for Iron's Snowball. AbstractMagicProjectile
     * posts this event after its collision result is known and before it calls
     * Snowball.onHit, so canceled collisions and FrostField area pulses cannot
     * create or refresh a managed freeze.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSnowballProjectileImpact(ProjectileImpactEvent event) {
        if (event.isCanceled()
                || !(event.getProjectile() instanceof io.redspace.ironsspellbooks.entity.spells.snowball.Snowball snowball)
                || !(snowball.level() instanceof ServerLevel)
                || !(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !player.isAlive() || player.isDeadOrDying()
                || !markSnowballFreezeHit(snowball, player.getUUID())) {
            return;
        }
        int before = managedFreezeRemaining(player);
        applyManagedFreeze(player, FreezeReason.SNOWBALL);
        recordSnowballImpactDiagnostic(snowball, entityHit, player, before,
                managedFreezeRemaining(player));
    }

    private static boolean markSnowballFreezeHit(Entity snowball, java.util.UUID targetId) {
        return SNOWBALL_FREEZE_HITS
                .computeIfAbsent(snowball, ignored -> new HashSet<>())
                .add(targetId);
    }

    /**
     * Freeze work is event-driven: only UUIDs with an active owned state are
     * inspected.  The snapshot is bounded by the number of frozen targets and
     * avoids a full online-player/world scan when no freeze is active.
     */
    private static void tickManagedFreezes(MinecraftServer server, long now) {
        if (FREEZE_STATES.isEmpty() && ICE_FREEZE_RELEASE_TICKS.isEmpty()
                && ICE_TOMB_RELEASE_TICKS.isEmpty()) return;
        java.util.HashSet<java.util.UUID> active = new java.util.HashSet<>(FREEZE_STATES.keySet());
        active.addAll(ICE_FREEZE_RELEASE_TICKS.keySet());
        active.addAll(ICE_TOMB_RELEASE_TICKS.keySet());
        for (java.util.UUID id : active) {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive() || player.isDeadOrDying()) {
                if (player != null) clearLifecycleFreezeState(player, true);
                else {
                    FREEZE_STATES.remove(id);
                    ICE_FREEZE_RELEASE_TICKS.remove(id);
                    ICE_TOMB_RELEASE_TICKS.remove(id);
                    SPRAY_REFREEZE_BLOCK_UNTIL.remove(id);
                }
                continue;
            }
            if (isFreezeControlActive(player)) {
                player.setDeltaMovement(Vec3.ZERO);
            }
            releaseExpiredIceFreeze(player, now);
        }
    }

    /** Index entities on join instead of scanning every dimension each tick. */
    private static void trackEntity(Entity entity) {
        if (entity != null && isTrackedEntity(entity)) ACTIVE_TRACKED_ENTITIES.add(entity);
    }

    private static boolean isTrackedEntity(Entity entity) {
        if (entity instanceof SummonedHorse || entity instanceof EnderChain) return true;
        String name = entity.getClass().getName();
        return isThunderLance(entity)
                || isThunderLanceHitbox(entity)
                || entity.getPersistentData().getBoolean(GRAVITY_FISSURE_TAG)
                || name.equals("io.redspace.ironsspellbooks.entity.spells.FangSwirlEntity")
                || name.equals("com.gametechbc.gtbcs_geomancy_plus.entity.extended.projectiles.ExtendedEntityFissure")
                || name.equals("com.gametechbc.gtbcs_geomancy_plus.entity.projectiles.petrivise_pillar.PetrivisePillarEntity");
    }

    /** One bounded load-time pass covers entities saved before this version. */
    private static void indexExistingTrackedEntities(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) trackEntity(entity);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            LAST_DAMAGE_TICKS.remove(player.getUUID());
            manager(player.getServer()).onPlayerInvalidated(player.getUUID(), true);
            // A previous crash/restart can leave a client-side freeze overlay or
            // an Ice Tomb passenger relation without its server-side timer.
            // Clear only stale freeze state; normal Chilled effects are kept.
            if (isRidingIceTomb(player)
                    || ICE_FREEZE_RELEASE_TICKS.containsKey(player.getUUID())
                    || FREEZE_STATES.containsKey(player.getUUID())
                    || ICE_TOMB_RELEASE_TICKS.containsKey(player.getUUID())) {
                clearLifecycleFreezeState(player, true);
            }
            SpellDuelManager manager = manager(player.getServer());
            manager.onSurroundPlayerLogin(player);
            SpellDuelNetwork.sendDisplay(player, manager.displayEnabled());
            unlockAllLearnableSpells(player);
        }
    }

    /**
     * Cone collision gate: only a target controlled by this mod is removed
     * from subsequent collisions.  Vanilla/other-mod Chilled or ticksFrozen
     * values are not proof that this spray already froze the target.
     */
    public static boolean isFrozenConeTarget(LivingEntity target) {
        if (target == null || !target.isAlive() || target.isDeadOrDying()) return true;
        if (target instanceof net.minecraft.server.level.ServerPlayer player) {
            java.util.UUID id = player.getUUID();
            return FREEZE_STATES.containsKey(id)
                    || ICE_FREEZE_RELEASE_TICKS.containsKey(id)
                    || ICE_TOMB_RELEASE_TICKS.containsKey(id);
        }
        return false;
    }

    /**
     * Returns the exact freeze duration owned by this mod for a spell resource.
     * All other damage sources are deliberately ignored, including sources that
     * merely carry a non-zero Iron freezeTicks value.
     */
    public static int managedFreezeDuration(ResourceLocation spellId) {
        if (CONE_OF_COLD_SPELL_ID.equals(spellId)) return FREEZE_DURATION_TICKS;
        if (RAY_OF_FROST_SPELL_ID.equals(spellId)) return RAY_OF_FROST_FREEZE_TICKS;
        if (SNOWBALL_SPELL_ID.equals(spellId)) return SNOWBALL_FREEZE_TICKS;
        if (GLACIAL_EDGE_SPELL_ID.equals(spellId)) return FREEZE_DURATION_TICKS;
        return 0;
    }

    public static boolean isManagedFreezeSpell(ResourceLocation spellId) {
        return managedFreezeDuration(spellId) > 0;
    }

    private static FreezeReason freezeReason(ResourceLocation spellId) {
        if (CONE_OF_COLD_SPELL_ID.equals(spellId)) return FreezeReason.CONE_OF_COLD;
        if (RAY_OF_FROST_SPELL_ID.equals(spellId)) return FreezeReason.RAY_OF_FROST;
        if (SNOWBALL_SPELL_ID.equals(spellId)) return FreezeReason.SNOWBALL;
        if (GLACIAL_EDGE_SPELL_ID.equals(spellId)) return FreezeReason.GLACIAL_EDGE;
        return null;
    }

    public static boolean isGlacialEdgeEntity(Entity entity) {
        return entity != null && GLACIAL_EDGE_CLASS.equals(entity.getClass().getName());
    }

    /**
     * Resolves a managed freeze from the authoritative damage event.  Some
     * addon projectiles construct a SpellDamageSource without retaining the
     * spell instance; in that case the direct projectile class is the stable
     * server-side identity.  The fallback remains restricted to the three
     * verified Iron projectiles and Glacial Edge.
     */
    private static FreezeReason managedFreezeReason(LivingDamageEvent.Post event) {
        if (event.getSource() instanceof SpellDamageSource source && source.spell() != null) {
            FreezeReason reason = freezeReason(source.spell().getSpellResource());
            if (reason != null) return reason;
        }
        Entity direct = event.getSource().getDirectEntity();
        if (direct == null) return null;
        String className = direct.getClass().getName();
        if (GLACIAL_EDGE_CLASS.equals(className)) return FreezeReason.GLACIAL_EDGE;
        if (CONE_OF_COLD_PROJECTILE_CLASS.equals(className)) return FreezeReason.CONE_OF_COLD;
        if (SNOWBALL_PROJECTILE_CLASS.equals(className)) return FreezeReason.SNOWBALL;
        return null;
    }

    /** Called by the verified projectile mixin before its original hit method. */
    public static boolean allowGlacialEdgeHit(Entity projectile, Entity target) {
        if (!isGlacialEdgeEntity(projectile) || !(target instanceof net.minecraft.server.level.ServerPlayer player)) {
            return true;
        }
        Set<java.util.UUID> hitTargets = GLACIAL_EDGE_HITS.computeIfAbsent(projectile, ignored -> new HashSet<>());
        java.util.UUID targetId = player.getUUID();
        return hitTargets.add(targetId);
    }

    /** Called only by the Glacial Edge damage redirect after the native
     * applyDamage call has returned true.  The projectile mixin owns the
     * per-projectile/per-target hit gate; this method owns the one freeze. */
    public static void onGlacialEdgeDamageApplied(Entity projectile, Entity target, boolean applied) {
        if (applied && isGlacialEdgeEntity(projectile)
                && target instanceof net.minecraft.server.level.ServerPlayer player) {
            applyManagedFreeze(player, FreezeReason.GLACIAL_EDGE);
        }
    }

    /** Mark a FrostField before it is inserted into the level with its exact source. */
    public static Entity markManagedFrostField(Entity field, FreezeReason reason) {
        if (field != null && reason != null) MANAGED_FROST_FIELDS.put(field, reason);
        return field;
    }

    public static boolean isManagedFrostField(Entity field) {
        return field != null && MANAGED_FROST_FIELDS.containsKey(field);
    }

    public static FreezeReason managedFrostFieldReason(Entity field) {
        return field == null ? null : MANAGED_FROST_FIELDS.get(field);
    }

    /** Public one-shot entry used by the managed Snowball FrostField mixin. */
    public static void applyManagedFreeze(net.minecraft.server.level.ServerPlayer player,
                                          FreezeReason reason) {
        if (player == null || reason == null || !player.isAlive() || player.isDeadOrDying()) return;
        if (!isManagedFreezeSpell(freezeSpellId(reason))) return;
        applyCompleteFreeze(player, reason);
    }

    /** Returns the remaining owned freeze time for debug/test evidence. */
    public static int managedFreezeRemaining(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return -1;
        FreezeState state = FREEZE_STATES.get(player.getUUID());
        if (state == null) return 0;
        long remaining = state.endsAt - player.getServer().getTickCount();
        return (int) Math.max(0L, remaining);
    }

    /**
     * Default-off, one-record-per-projectile evidence for the actual Snowball
     * EntityHitResult path.  It is intentionally not a normal INFO path.
     */
    public static void recordSnowballImpactDiagnostic(Entity snowball, net.minecraft.world.phys.HitResult hit,
                                                      Entity target, int remainingBefore, int remainingAfter) {
        if (snowball == null || !Boolean.getBoolean("iron_magic_duel.debugSnowballFreeze")
                || !SNOWBALL_DIAGNOSTICS.add(snowball)) return;
        boolean serverPlayer = target instanceof net.minecraft.server.level.ServerPlayer;
        IronSpellPerformance.LOGGER.debug(
                "snowball ProjectileImpactEvent accepted=true hitType={} targetType={} targetUuid={} serverPlayer={} "
                        + "applyBeforeRemaining={} applyAfterRemaining={} removed={}",
                hit == null ? "null" : hit.getType(),
                target == null ? "none" : target.getClass().getName(),
                target == null ? "none" : target.getUUID(),
                serverPlayer, remainingBefore, remainingAfter, snowball.isRemoved());
    }

    private static ResourceLocation freezeSpellId(FreezeReason reason) {
        return switch (reason) {
            case CONE_OF_COLD -> CONE_OF_COLD_SPELL_ID;
            case RAY_OF_FROST -> RAY_OF_FROST_SPELL_ID;
            case SNOWBALL -> SNOWBALL_SPELL_ID;
            case GLACIAL_EDGE -> GLACIAL_EDGE_SPELL_ID;
        };
    }

    public static boolean isConeOfColdSpell(ResourceLocation spellId) {
        return CONE_OF_COLD_SPELL_ID.equals(spellId);
    }

    /** Default-off, one-record-per-cone evidence hook for the unresolved
     * IceTomb/reset path. It never runs in ordinary spell play. */
    public static void recordConeResetDiagnostic(Entity cone, String reason) {
        if (cone == null || !Boolean.getBoolean("iron_magic_duel.debugConeReset")) return;
        if (CONE_RESET_DIAGNOSTICS.add(cone)) {
            IronSpellPerformance.LOGGER.debug("cone_of_cold reset/discard entry entity={} reason={}",
                    cone.getUUID(), reason);
        }
    }

    /** True only while this mod owns an active freeze timer/state for a player. */
    private static boolean isFreezeControlActive(net.minecraft.server.level.ServerPlayer player) {
        java.util.UUID id = player.getUUID();
        return FREEZE_STATES.containsKey(id) || ICE_FREEZE_RELEASE_TICKS.containsKey(id)
                || ICE_TOMB_RELEASE_TICKS.containsKey(id);
    }

    public static boolean isFreezeControlActivePublic(net.minecraft.server.level.ServerPlayer player) {
        return player != null && isFreezeControlActive(player);
    }

    /** Unlocks every enabled base or addon spell without inserting scrolls or changing spellbooks. */
    private static void unlockAllLearnableSpells(net.minecraft.server.level.ServerPlayer player) {
        var synced = MagicData.getPlayerMagicData(player).getSyncedData();
        LearnedSpellData learnedData = ((SyncedSpellDataLearnedAccessorMixin) (Object) synced)
                .ironMagic$getLearnedSpellData();
        boolean changed;
        // writeToBuffer takes this same monitor before making its immutable
        // snapshot.  All mutations from this mod therefore stay on the server
        // thread and cannot race Netty's sync_player_data encoder.
        synchronized (learnedData.learnedSpells) {
            changed = false;
            for (AbstractSpell spell : SpellRegistry.getEnabledSpells()) {
                if (spell == null || spell == SpellRegistry.none() || synced.isSpellLearned(spell)) continue;
                if (MAGIC_MISSILE_SPELL_ID.equals(spell.getSpellResource().toString())) continue;
                synced.learnSpell(spell);
                changed = true;
            }
            changed |= removeMagicMissileFromLearned(synced);
        }
        if (changed) synced.syncToPlayer(player);
    }

    private static boolean removeMagicMissileFromLearned(Object synced) {
        try {
            java.lang.reflect.Field learnedData = synced.getClass().getDeclaredField("learnedSpellData");
            learnedData.setAccessible(true);
            Object data = learnedData.get(synced);
            if (data == null) return false;
            java.lang.reflect.Field learnedSpells = data.getClass().getField("learnedSpells");
            Object value = learnedSpells.get(data);
            if (value instanceof Set<?> spells) {
                return spells.remove(ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "magic_missile"));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return false;
    }

    /** Records the duel outcome but leaves death and respawn fully vanilla. */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            LAST_DAMAGE_TICKS.remove(player.getUUID());
            SpellDuelManager duelManager = MANAGERS.get(player.getServer());
            if (duelManager != null) {
                duelManager.onPlayerInvalidated(player.getUUID(), true);
                duelManager.onSurroundPlayerInvalidated(player.getUUID(), true);
            }
            clearLifecycleFreezeState(player, true);
            releaseSummonedHorses(player);
            BlazingDragonCorridorSpell.cancel(player.getServer(), player.getUUID());
            PhantomHalberdRingSpell.cancelParticipant(player.getServer(), player.getUUID());
            manager(player.getServer()).recordDuelDeathAndScheduleRecovery(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            LAST_DAMAGE_TICKS.remove(player.getUUID());
            SpellDuelManager duelManager = MANAGERS.get(player.getServer());
            if (duelManager != null) {
                duelManager.onPlayerInvalidated(player.getUUID(), false);
                duelManager.onSurroundPlayerInvalidated(player.getUUID(), false);
            }
            releaseSummonedHorses(player);
            clearLifecycleFreezeState(player, true);
            BlazingDragonCorridorSpell.cancel(player.getServer(), player.getUUID());
            PhantomHalberdRingSpell.cancelParticipant(player.getServer(), player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            SpellDuelManager duelManager = MANAGERS.get(player.getServer());
            if (duelManager != null) {
                duelManager.endChallengeForPlayer(player.getUUID());
                duelManager.onSurroundPlayerRespawn(player);
            }
            clearLifecycleFreezeState(player, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            SpellDuelManager duelManager = MANAGERS.get(player.getServer());
            if (duelManager != null) {
                duelManager.onPlayerChangedDimension(player.getUUID());
                duelManager.onSurroundPlayerChangedDimension(player.getUUID());
            }
            clearLifecycleFreezeState(player, true);
        }
    }

    /** Restores the normal hurt window for players without overriding bypass damage. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void enforcePlayerHurtImmunity(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || player.invulnerableTime <= 0
                || event.getSource().is(DamageTypeTags.BYPASSES_COOLDOWN)) {
            return;
        }
        // Reject only the repeated player hit during the vanilla hurt window.
        // Other living entities and explicit bypass damage keep their native path.
        event.setCanceled(true);
    }

    /** Starts automatic recovery only after the player has avoided all damage for five seconds. */
    @SubscribeEvent
    public static void onPlayerHurt(LivingDamageEvent.Post event) {
        PhantomHalberdRingSpell.recordDamage(event.getEntity(), event.getSource(), event.getNewDamage());
        applyBloodSpellLifesteal(event);
        countSummonedHorseHit(event);
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            long now = player.getServer().getTickCount();
            if (event.getNewDamage() > 0.0F) {
                LAST_DAMAGE_TICKS.put(player.getUUID(), now);
            }
            // A lethal hit must follow the vanilla death path exactly once;
            // never apply or refresh this mod's freeze state on a dying player.
            if (!player.isAlive() || player.isDeadOrDying()) {
                clearLifecycleFreezeState(player, true);
                return;
            }
            if (event.getNewDamage() > 0.0F) {
                FreezeReason reason = managedFreezeReason(event);
                if (reason != null && !FREEZE_STATES.containsKey(player.getUUID())) {
                    if (reason == FreezeReason.CONE_OF_COLD) {
                        onConeOfColdPlayerDamaged(player);
                    } else {
                        applyCompleteFreeze(player, reason);
                    }
                }
            }
        }
    }

    /** Unified server-authoritative lifesteal for the installed blood spell family. */
    private static void applyBloodSpellLifesteal(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F || !(event.getSource() instanceof SpellDamageSource source)
                || source.spell() == null || !isBloodSpell(source.spell().getSpellResource())) return;
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker) || !livingAttacker.isAlive()) return;
        Entity direct = source.getDirectEntity();
        long tick = event.getEntity().level().getGameTime();
        String key = livingAttacker.getUUID() + ":" + event.getEntity().getUUID() + ":"
                + (direct == null ? "none" : direct.getUUID()) + ":" + tick;
        if (BLOOD_HEAL_EVENTS.putIfAbsent(key, tick) != null) return;
        BLOOD_HEAL_EVENTS.entrySet().removeIf(entry -> tick - entry.getValue() > 2L);
        livingAttacker.heal(Math.min(10.0F, livingAttacker.getMaxHealth() - livingAttacker.getHealth()));
    }

    private static boolean isBloodSpell(ResourceLocation id) {
        if (id == null) return false;
        String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
        return path.contains("blood") || path.contains("hemorr") || path.contains("sanguin")
                || path.contains("lifesteal") || path.contains("life_steal") || path.contains("vampir");
    }

    /**
     * Magic Missile is disabled for this duel build.  The pre-cast guard below
     * blocks normal spell activation; this second guard also covers a stray
     * missile created by a fallback CastPacket or another addon.
     */
    @SubscribeEvent
    public static void onMagicMissileSpellDamage(SpellDamageEvent event) {
        SpellDamageSource source = event.getSpellDamageSource();
        if (source != null && source.spell() != null
                && MAGIC_MISSILE_SPELL_ID.equals(source.spell().getSpellResource().toString())) {
            event.setCanceled(true);
        }
    }

    /** Firefly Swarm is allowed two bites per second, never one bite per tick. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFireflySwarmDamage(LivingDamageEvent.Pre event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct == null || !direct.getClass().getName().equals(
                "io.redspace.ironsspellbooks.entity.spells.firefly_swarm.FireflySwarmProjectile")) return;
        long tick = event.getEntity().level().getGameTime();
        Long previous = FIREFLY_TARGET_DAMAGE_TICKS.get(event.getEntity().getUUID());
        if (previous != null && tick - previous < 10L) {
            // Do not retry in the same tick or pierce vanilla hurt-resistance.
            event.setNewDamage(0.0F);
            return;
        }
        FIREFLY_TARGET_DAMAGE_TICKS.put(event.getEntity().getUUID(), tick);
    }

    /**
     * Void Bulwark's Cataclysm rune has one damage path: its private
     * Void_Rune_Entity#damage method.  The compatibility mixin tags only
     * entities spawned by void_bulwark (the unrelated void_rune spell is not
     * tagged), so every direct, continuous and collision hit from this spell
     * reaches this single server-side final-damage gate.  Pre is fired after
     * armor/potion reductions and before health is changed; keeping vanilla
     * hurt-resistance untouched preserves the normal invulnerability frame.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onVoidBulwarkDamage(LivingDamageEvent.Pre event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct == null || !direct.getPersistentData().getBoolean("IronMagicVoidBulwarkRune")) return;
        if (event.getNewDamage() > 0.0F) event.setNewDamage(10.0F);
    }

    /** A single valid attack breaks only the Arcane Shackle chain it hit. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onArcaneShackleAttack(AttackEntityEvent event) {
        Entity chainEntity = event.getTarget();
        EnderChain chain = null;
        if (chainEntity instanceof EnderChain direct) chain = direct;
        else if (chainEntity instanceof net.neoforged.neoforge.entity.PartEntity<?> part
                && part.getParent() instanceof EnderChain parent) chain = parent;
        if (chain == null || !chain.getPersistentData().getBoolean("IronMagicArcaneShackle")) return;
        Entity victim = chain.getVictim();
        if (victim instanceof net.minecraft.server.level.ServerPlayer player) clearArcaneShackleTitle(player);
        chain.breakChain();
        event.setCanceled(true);
    }

    /** Frozen players cannot submit a melee attack during the control window. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFrozenPlayerAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && isFreezeControlActive(player)) {
            event.setCanceled(true);
        }
    }

    /** Maintains target-only titles for chains and removes them exactly once. */
    private static void tickArcaneShackles(MinecraftServer server) {
        Set<java.util.UUID> liveChains = new java.util.HashSet<>();
        ACTIVE_TRACKED_ENTITIES.removeIf(entity -> entity == null || entity.isRemoved());
        if (ACTIVE_TRACKED_ENTITIES.isEmpty()) {
            clearArcaneShackleTitles(server);
            return;
        }
        for (Entity tracked : ACTIVE_TRACKED_ENTITIES) {
            if (!(tracked instanceof EnderChain chain)
                    || !chain.getPersistentData().getBoolean("IronMagicArcaneShackle")) continue;
                liveChains.add(chain.getUUID());
                Entity victim = chain.getVictim();
                if (!(victim instanceof net.minecraft.server.level.ServerPlayer player)
                        || !player.isAlive() || player.isRemoved()) continue;
                ARCANE_CHAIN_TARGETS.put(chain.getUUID(), player.getUUID());
                if (!player.getPersistentData().getBoolean("IronMagicArcaneShackleTitle")) {
                    player.getPersistentData().putBoolean("IronMagicArcaneShackleTitle", true);
                    player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("被控住")));
                    player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("攻击锁链即可解除控制")));
                }
                // The entity's original spell scales health with spell power;
                // this rule intentionally makes each generated chain exactly 1 HP.
                if (chain.getHealth() > 1.0F) chain.setHealth(1.0F);
        }
        ARCANE_CHAIN_TARGETS.entrySet().removeIf(entry -> {
            if (liveChains.contains(entry.getKey())) return false;
            Entity entity = server.overworld().getEntity(entry.getValue());
            if (entity instanceof net.minecraft.server.level.ServerPlayer player) clearArcaneShackleTitle(player);
            return true;
        });
    }

    private static void clearArcaneShackleTitle(net.minecraft.server.level.ServerPlayer player) {
        if (!player.getPersistentData().getBoolean("IronMagicArcaneShackleTitle")) return;
        player.getPersistentData().remove("IronMagicArcaneShackleTitle");
        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
    }

    private static void clearArcaneShackleTitles(MinecraftServer server) {
        for (java.util.UUID playerId : ARCANE_CHAIN_TARGETS.values()) {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) clearArcaneShackleTitle(player);
        }
        ARCANE_CHAIN_TARGETS.clear();
    }

    /** Prevents disabled Magic Missile projectiles from ever entering a level. */
    @SubscribeEvent
    public static void onMagicMissileJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof SummonedHorse horse) {
            horse.getPersistentData().putBoolean(SUMMONED_HORSE_TAG, true);
            if (!horse.getPersistentData().contains(SUMMONED_HORSE_HITS)) {
                horse.getPersistentData().putInt(SUMMONED_HORSE_HITS, 0);
            }
        }
        if (event.getEntity() instanceof MagicMissileProjectile) {
            event.setCanceled(true);
            return;
        }
        // The Cataclysm base mod reuses Void_Rune_Entity for void_rune and
        // void_bulwark.  Only the latter is tagged by the spell-specific
        // mixin, allowing the damage rule to remain isolated to void_bulwark.
        boostDesertWindsProjectile(event.getEntity());
        boostBallLightningProjectile(event.getEntity());
        enhanceFangAndPetrivise(event.getEntity());
        enhanceFissure(event.getEntity());
        markGravityFissure(event.getEntity());
        trackEntity(event.getEntity());
    }

    /** Remember the owner when the spell's spectral horse is mounted. */
    @SubscribeEvent
    public static void onSummonedHorseMount(EntityMountEvent event) {
        if (!(event.getEntityBeingMounted() instanceof SummonedHorse horse)
                || !horse.getPersistentData().getBoolean(SUMMONED_HORSE_TAG)) return;
        if (event.isMounting() && event.getEntityMounting() instanceof net.minecraft.server.level.ServerPlayer rider
                && isHorseOwner(horse, rider)) {
            horse.getPersistentData().putUUID(SUMMONED_HORSE_RIDER, rider.getUUID());
        } else if (event.isDismounting() && !horse.getPersistentData().getBoolean(SUMMONED_HORSE_RELEASING)
                && horse.isAlive()) {
            // Shift, the dismount key and ordinary interaction all arrive here.
            event.setCanceled(true);
        }
    }

    private static boolean isHorseOwner(SummonedHorse horse, net.minecraft.server.level.ServerPlayer player) {
        if (!(horse instanceof IMagicSummon summon)) return false;
        Entity owner = summon.getSummoner();
        return owner == player || (owner != null && owner.getUUID().equals(player.getUUID()));
    }

    private static void countSummonedHorseHit(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof SummonedHorse horse)
                || !horse.getPersistentData().getBoolean(SUMMONED_HORSE_TAG)
                || !horse.isAlive() || event.getNewDamage() <= 0.0F) return;
        int hits = horse.getPersistentData().getInt(SUMMONED_HORSE_HITS) + 1;
        horse.getPersistentData().putInt(SUMMONED_HORSE_HITS, hits);
        if (hits >= 5) {
            horse.getPersistentData().putBoolean(SUMMONED_HORSE_RELEASING, true);
            horse.setHealth(0.0F);
            horse.die(event.getSource());
        }
    }

    private static void tickSummonedHorses(MinecraftServer server) {
        ACTIVE_TRACKED_ENTITIES.removeIf(entity -> entity == null || entity.isRemoved());
        if (ACTIVE_TRACKED_ENTITIES.isEmpty()) return;
        for (Entity entity : ACTIVE_TRACKED_ENTITIES) {
            if (!(entity instanceof SummonedHorse horse)
                    || !horse.getPersistentData().getBoolean(SUMMONED_HORSE_TAG)
                    || !horse.isAlive()) continue;
            if (!horse.getPersistentData().hasUUID(SUMMONED_HORSE_RIDER)) continue;
            net.minecraft.server.level.ServerPlayer rider = server.getPlayerList().getPlayer(
                    horse.getPersistentData().getUUID(SUMMONED_HORSE_RIDER));
            if (rider == null || !rider.isAlive()) {
                horse.getPersistentData().putBoolean(SUMMONED_HORSE_RELEASING, true);
                horse.ejectPassengers();
                horse.discard();
                continue;
            }
            if (rider.getVehicle() != horse) rider.startRiding(horse, true);
        }
    }

    private static void releaseSummonedHorses(net.minecraft.server.level.ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (SummonedHorse horse : level.getEntitiesOfClass(SummonedHorse.class,
                    player.getBoundingBox().inflate(64.0D),
                    candidate -> candidate.getPersistentData().getBoolean(SUMMONED_HORSE_TAG)
                            && candidate.getPersistentData().hasUUID(SUMMONED_HORSE_RIDER)
                            && player.getUUID().equals(candidate.getPersistentData().getUUID(SUMMONED_HORSE_RIDER)))) {
                horse.getPersistentData().putBoolean(SUMMONED_HORSE_RELEASING, true);
                horse.ejectPassengers();
                horse.discard();
            }
        }
    }

    /** Marks Iron's BlackHole entity so the generic server pull below can cover
     * living entities and every projectile/spell entity, not just arrows. */
    private static void markGravityFissure(Entity entity) {
        if (!"io.redspace.ironsspellbooks.entity.spells.black_hole.BlackHole".equals(entity.getClass().getName())) return;
        entity.getPersistentData().putBoolean(GRAVITY_FISSURE_TAG, true);
        entity.getPersistentData().putLong(GRAVITY_FISSURE_START_TICK, entity.level().getGameTime());
        trackEntity(entity);
        try {
            entity.getClass().getMethod("setRadius", float.class).invoke(entity, (float) GRAVITY_FISSURE_RADIUS);
        } catch (ReflectiveOperationException ignored) { }
    }

    /** Applies a server-authoritative inward impulse to all movable entities in
     * the five-block radius. No blocks are queried or modified. */
    private static void tickGravityFissures(MinecraftServer server) {
        ACTIVE_TRACKED_ENTITIES.removeIf(entity -> entity == null || entity.isRemoved());
        if (ACTIVE_TRACKED_ENTITIES.isEmpty()) return;
        for (Entity fissure : ACTIVE_TRACKED_ENTITIES) {
            if (!(fissure.level() instanceof ServerLevel level)
                    || !fissure.getPersistentData().getBoolean(GRAVITY_FISSURE_TAG) || fissure.isRemoved()) continue;
                long started = fissure.getPersistentData().getLong(GRAVITY_FISSURE_START_TICK);
                if (started == 0L) {
                    started = level.getGameTime();
                    fissure.getPersistentData().putLong(GRAVITY_FISSURE_START_TICK, started);
                }
                if (level.getGameTime() - started >= GRAVITY_FISSURE_LIFETIME) {
                    fissure.discard();
                    continue;
                }
                Vec3 center = fissure.position();
                AABB area = fissure.getBoundingBox().inflate(GRAVITY_FISSURE_RADIUS + 0.5D);
                for (Entity entity : level.getEntitiesOfClass(Entity.class, area,
                        candidate -> candidate != fissure && !candidate.isRemoved() && candidate.isAlive())) {
                    Vec3 offset = center.subtract(entity.getBoundingBox().getCenter());
                    double distance = offset.length();
                    if (distance < 0.15D || distance > GRAVITY_FISSURE_RADIUS) continue;
                    Vec3 direction = offset.scale(1.0D / distance);
                    Vec3 velocity = entity.getDeltaMovement();
                    double outward = velocity.dot(direction.scale(-1.0D));
                    if (outward > 0.0D) velocity = velocity.add(direction.scale(outward));
                    double strength = 0.30D + (GRAVITY_FISSURE_RADIUS - distance) * 0.075D;
                    entity.setDeltaMovement(velocity.scale(0.28D).add(direction.scale(strength)));
                    entity.hasImpulse = true;
                    entity.hurtMarked = true;
                }
        }
    }

    /** Cataclysm's Sandstorm projectile stores its velocity in public power fields. */
    private static void boostDesertWindsProjectile(Entity entity) {
        if (!"com.github.L_Ender.cataclysm.entity.projectile.Sandstorm_Projectile".equals(entity.getClass().getName())
                || entity.getPersistentData().getBoolean(DESERT_WINDS_SPEED_TAG)) return;
        try {
            java.lang.reflect.Field xPower = entity.getClass().getField("xPower");
            java.lang.reflect.Field yPower = entity.getClass().getField("yPower");
            java.lang.reflect.Field zPower = entity.getClass().getField("zPower");
            double multiplier = 3.0D;
            xPower.setDouble(entity, xPower.getDouble(entity) * multiplier);
            yPower.setDouble(entity, yPower.getDouble(entity) * multiplier);
            zPower.setDouble(entity, zPower.getDouble(entity) * multiplier);
            entity.getPersistentData().putBoolean(DESERT_WINDS_SPEED_TAG, true);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Cataclysm is optional at compile time; leave the native projectile untouched if absent.
        }
    }

    /** Ball Lightning uses AbstractMagicProjectile#getSpeed only when it is shot. */
    private static void boostBallLightningProjectile(Entity entity) {
        if (!"io.redspace.ironsspellbooks.entity.spells.ball_lightning.BallLightning".equals(entity.getClass().getName())
                || entity.getPersistentData().getBoolean(BALL_LIGHTNING_SPEED_TAG)
                || entity.level().isClientSide()) return;
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() > 1.0E-6D) {
            // Native speed is 0.6 blocks/tick; 2.5x is fast enough to feel
            // responsive while preserving the spell's bounce and hit logic.
            entity.setDeltaMovement(velocity.scale(2.5D));
            entity.getPersistentData().putBoolean(BALL_LIGHTNING_SPEED_TAG, true);
        }
    }

    private static void enhanceFangAndPetrivise(Entity entity) {
        String name = entity.getClass().getName();
        try {
            if (name.equals("io.redspace.ironsspellbooks.entity.spells.ExtendedEvokerFang")) {
                java.lang.reflect.Field warmup = entity.getClass().getDeclaredField("warmupDelayTicks");
                warmup.setAccessible(true);
                warmup.setInt(entity, Math.min(1, warmup.getInt(entity)));
            } else if (name.equals("io.redspace.ironsspellbooks.entity.spells.FangSwirlEntity")) {
                trackEntity(entity);
                entity.getClass().getMethod("setDelay", int.class).invoke(entity, 8);
                Entity owner = entity instanceof Projectile projectile ? projectile.getOwner() : null;
                Entity target = nearestLivingPlayer(entity, owner, 16.0D);
                if (target != null) entity.getPersistentData().putUUID("FollowTarget", target.getUUID());
            } else if (name.equals("com.gametechbc.gtbcs_geomancy_plus.entity.projectiles.petrivise_pillar.PetrivisePillarEntity")) {
                trackEntity(entity);
                entity.noPhysics = false;
                long now = entity.level().getGameTime();
                int animationTicks = 40;
                try {
                    Object warmup = entity.getClass().getMethod("getWarmupDelay").invoke(entity);
                    if (warmup instanceof Number number) animationTicks += Math.max(0, number.intValue());
                } catch (ReflectiveOperationException ignored) {
                }
                try {
                    Object slam = entity.getClass().getMethod("getSlamDelay").invoke(entity);
                    if (slam instanceof Number number) animationTicks += Math.max(0, number.intValue());
                } catch (ReflectiveOperationException ignored) {
                }
                // Animation end + 2 seconds. The tag is checked server-side
                // so a pillar cannot remain stuck in a wall indefinitely.
                entity.getPersistentData().putLong(PETRIVISE_CLEANUP_TAG, now + animationTicks + 40L);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    /**
     * Gives Geomancy's fissure a server-authoritative, unlimited-range target
     * and fans one cast out into four independently tracking fissures.  The
     * copies use the registered ExtendedEntityFissure type, so their native
     * spike animation and damage remain intact instead of replacing the
     * original spell with a custom projectile.
     */
    private static void enhanceFissure(Entity entity) {
        if (!"com.gametechbc.gtbcs_geomancy_plus.entity.extended.projectiles.ExtendedEntityFissure"
                .equals(entity.getClass().getName()) || entity.level().isClientSide()) return;
        Entity owner = entity instanceof Projectile projectile ? projectile.getOwner() : null;
        Entity target = nearestLivingPlayerUnbounded(entity, owner);
        if (target != null) {
            entity.getPersistentData().putUUID(FISSURE_TARGET_TAG, target.getUUID());
            if (!entity.getPersistentData().getBoolean(FISSURE_COPY_TAG)) {
                Vec3 forward = horizontalDirection(entity.getDeltaMovement(),
                        target.getBoundingBox().getCenter().subtract(entity.position()));
                entity.getPersistentData().putDouble(FISSURE_DIR_X_TAG, forward.x);
                entity.getPersistentData().putDouble(FISSURE_DIR_Y_TAG, 0.0D);
                entity.getPersistentData().putDouble(FISSURE_DIR_Z_TAG, forward.z);
                entity.setDeltaMovement(forward.scale(0.42D));
                entity.setYRot((float) Math.toDegrees(Math.atan2(-forward.x, forward.z)));
                spawnFissureCopies(entity, owner, target, forward);
            }
        }
        try {
            Class<?> base = Class.forName("com.bobmowzie.mowziesmobs.server.entity.effects.geomancy.EntityFissure");
            java.lang.reflect.Field cadence = base.getField("TICKS_PER_PIECE");
            cadence.setInt(null, 1);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    /** Creates the remaining three fissures from a single native fissure cast. */
    private static void spawnFissureCopies(Entity original, Entity owner, Entity target, Vec3 forward) {
        if (!(original.level() instanceof ServerLevel level)
                || original.getPersistentData().getBoolean(FISSURE_GROUP_TAG)) return;
        original.getPersistentData().putBoolean(FISSURE_GROUP_TAG, true);
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        if (side.lengthSqr() < 1.0E-6D) side = new Vec3(1, 0, 0);
        side = side.normalize();
        // Four cardinal attack vectors: the original approaches from the
        // front, while the copies approach from the right, rear and left.
        Vec3[] attackDirections = {side, forward.scale(-1.0D), side.scale(-1.0D)};
        double[] offsets = {-0.9D, -0.3D, 0.3D};
        for (int index = 0; index < attackDirections.length; index++) {
            double offset = offsets[index];
            Vec3 attackDirection = attackDirections[index].normalize();
            Entity copy;
            try {
                copy = original.getType().create(level);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (copy == null || copy == original
                    || !"com.gametechbc.gtbcs_geomancy_plus.entity.extended.projectiles.ExtendedEntityFissure"
                    .equals(copy.getClass().getName())) continue;
            trackEntity(copy);
            Vec3 spawn = original.position().add(side.scale(offset));
            copy.setPos(spawn);
            copy.setDeltaMovement(attackDirection.scale(0.42D));
            copy.setYRot((float) Math.toDegrees(Math.atan2(-attackDirection.x, attackDirection.z)));
            copy.setXRot((float) Math.toDegrees(Math.asin(-attackDirection.y)));
            copy.setNoGravity(true);
            copy.getPersistentData().putBoolean(FISSURE_COPY_TAG, true);
            copy.getPersistentData().putBoolean(FISSURE_GROUP_TAG, true);
            copy.getPersistentData().putUUID(FISSURE_TARGET_TAG, target.getUUID());
            copy.getPersistentData().putDouble(FISSURE_DIR_X_TAG, attackDirection.x);
            copy.getPersistentData().putDouble(FISSURE_DIR_Y_TAG, attackDirection.y);
            copy.getPersistentData().putDouble(FISSURE_DIR_Z_TAG, attackDirection.z);
            if (copy instanceof Projectile projectile && owner != null) projectile.setOwner(owner);
            copy.tickCount = original.tickCount;
            copy.getPersistentData().putDouble("IronMagicFissureOffset", offset);
            try {
                copy.getClass().getMethod("setTravelling", boolean.class).invoke(copy, true);
                java.lang.reflect.Method getDamage = original.getClass().getMethod("getSpikeDamage");
                java.lang.reflect.Method setDamage = copy.getClass().getMethod("setSpikeDamage", double.class);
                setDamage.invoke(copy, ((Number) getDamage.invoke(original)).doubleValue());
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Keep the native defaults if an addon changes an accessor name.
            }
            level.addFreshEntity(copy);
        }
    }

    private static Vec3 horizontalDirection(Vec3 preferred, Vec3 fallback) {
        Vec3 value = new Vec3(preferred.x, 0.0D, preferred.z);
        if (value.lengthSqr() < 1.0E-6D) value = new Vec3(fallback.x, 0.0D, fallback.z);
        if (value.lengthSqr() < 1.0E-6D) value = new Vec3(0, 0, 1);
        return value.normalize();
    }

    private static Entity nearestLivingPlayer(Entity origin, Entity excluded, double radius) {
        if (!(origin.level() instanceof ServerLevel level)) return null;
        Entity nearest = null;
        double distance = radius * radius;
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (player == excluded || !player.isAlive()) continue;
            double candidate = player.distanceToSqr(origin);
            if (candidate < distance) { distance = candidate; nearest = player; }
        }
        return nearest;
    }

    private static Entity nearestLivingPlayerUnbounded(Entity origin, Entity excluded) {
        if (!(origin.level() instanceof ServerLevel level)) return null;
        Entity nearest = null;
        double distance = Double.MAX_VALUE;
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (player == excluded || !player.isAlive()) continue;
            double candidate = player.distanceToSqr(origin);
            if (candidate < distance) {
                distance = candidate;
                nearest = player;
            }
        }
        return nearest;
    }

    /** Last damage-layer guard for a missile that bypasses SpellDamageEvent. */
    @SubscribeEvent
    public static void onMagicMissileLivingDamage(LivingDamageEvent.Pre event) {
        Entity direct = event.getSource().getDirectEntity();
        Entity owner = event.getSource().getEntity();
        if (direct instanceof MagicMissileProjectile || owner instanceof MagicMissileProjectile) {
            event.setNewDamage(0.0F);
        }
    }

    /**
     * All custom spell damage deliberately goes through LivingEntity.hurt().
     * That is the vanilla authoritative path and preserves the normal hurt
     * immunity window for every player and mob; this class never resets those fields
     * or marks a source as bypassing invulnerability.
     */

    /**
     * Geomancy's native fissure creates an ExtendedEntityEarthSpike one tick
     * after the travelling fissure stops.  The four tracked copies did not
     * always inherit the original spell's damage through addon versions, so
     * enforce the native 10-point spike damage at the final damage event.
     */
    @SubscribeEvent
    public static void onFissureSpikeDamage(LivingDamageEvent.Pre event) {
        Entity direct = event.getSource().getDirectEntity();
        Entity owner = event.getSource().getEntity();
        if ((direct != null && "com.gametechbc.gtbcs_geomancy_plus.entity.extended.projectiles.ExtendedEntityEarthSpike"
                .equals(direct.getClass().getName()))
                || (owner != null && "com.gametechbc.gtbcs_geomancy_plus.entity.extended.projectiles.ExtendedEntityEarthSpike"
                .equals(owner.getClass().getName()))) {
            event.setNewDamage(Math.max(10.0F, event.getNewDamage()));
        }
    }

    /**
     * Ice Tomb locks its target by making it a passenger.  Expiring a number
     * alone can leave that passenger relation or the vanilla freeze overlay
     * behind, so release both server-side at the same authoritative tick.
     */
    private static void releaseExpiredIceFreeze(net.minecraft.server.level.ServerPlayer player, long now) {
        if (!player.isAlive() || player.isDeadOrDying()) {
            clearFreezeState(player, true);
            return;
        }
        Entity vehicle = player.getVehicle();
        boolean ridingIceTomb = isRidingIceTomb(player);
        if (ridingIceTomb) {
            // Keep the visible, real Ice Tomb.  The cone-collision mixin
            // handles its line of sight separately, so this tomb no longer
            // prevents sprays from reaching the passenger.
            FreezeState state = FREEZE_STATES.get(player.getUUID());
            Long releaseAt = state == null
                    ? ICE_FREEZE_RELEASE_TICKS.get(player.getUUID()) : Long.valueOf(state.endsAt);
            if (releaseAt == null) {
                // Another teardown path may have removed the timer between
                // ticks. Treat this as an already-finished stale tomb; never
                // unbox a missing map value and never recreate its timer.
                clearFreezeState(player, true);
                return;
            }
            long end = releaseAt.longValue();
            ICE_TOMB_RELEASE_TICKS.put(player.getUUID(), end);
            if (now >= end) {
                clearFreezeState(player, true);
            } else if (now % 2L == 0L) {
                emitFrozenParticles(player);
            }
            return;
        }
        // A normally-destroyed tomb may still leave its rider's freeze overlay
        // for a client tick; remove that stale state immediately on dismount.
        if (ICE_TOMB_RELEASE_TICKS.remove(player.getUUID()) != null) {
            clearFreezeState(player, false);
            return;
        }
        FreezeState state = FREEZE_STATES.get(player.getUUID());
        Long releaseAt = state == null
                ? ICE_FREEZE_RELEASE_TICKS.get(player.getUUID()) : Long.valueOf(state.endsAt);
        if (releaseAt == null) {
            clearFreezeState(player, true);
            return;
        }
        long end = releaseAt.longValue();
        if (now >= end) {
            clearFreezeState(player, true);
        } else if (now % 2L == 0L) {
            emitFrozenParticles(player);
        }
    }

    private static boolean isRidingIceTomb(net.minecraft.server.level.ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        return vehicle != null
                && ICE_TOMB_ENTITY_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()));
    }

    /** Clears every temporary freeze component exactly once. */
    private static void clearLifecycleFreezeState(net.minecraft.server.level.ServerPlayer player,
                                                   boolean discardIceTomb) {
        clearFreezeState(player, discardIceTomb, true);
    }

    private static void clearFreezeState(net.minecraft.server.level.ServerPlayer player, boolean discardIceTomb) {
        clearFreezeState(player, discardIceTomb, false);
    }

    /** Clears one target's state; terminal lifecycle cleanup also drops all auxiliary timers. */
    private static void clearFreezeState(net.minecraft.server.level.ServerPlayer player,
                                          boolean discardIceTomb, boolean terminal) {
        java.util.UUID id = player.getUUID();
        FreezeState state = FREEZE_STATES.remove(id);
        if (state != null && state.reason == FreezeReason.SNOWBALL
                && Boolean.getBoolean("iron_magic_duel.debugSnowballFreeze")) {
            IronSpellPerformance.LOGGER.debug("snowball clearFreezeState reason={} target={} terminal={}",
                    state.reason, id, terminal);
        }
        boolean legacyFreeze = ICE_FREEZE_RELEASE_TICKS.remove(id) != null;
        Long tombRelease = ICE_TOMB_RELEASE_TICKS.remove(id);
        boolean customFreeze = state != null || legacyFreeze || tombRelease != null;
        boolean coneFreeze = state != null && state.reason == FreezeReason.CONE_OF_COLD;
        SPRAY_REFREEZE_BLOCK_UNTIL.remove(id);
        boolean tracked = state != null || legacyFreeze || tombRelease != null;
        if (coneFreeze && !terminal && player.isAlive() && !player.isDeadOrDying()) {
            // Keep the current continuous cone from rebuilding the same
            // freeze immediately after thaw.  A new hit cycle may start after
            // this explicit three-second quiet period.
            SPRAY_REFREEZE_BLOCK_UNTIL.put(id, player.getServer().getTickCount() + 60L);
        }
        boolean removedIceTomb = false;
        Entity vehicle = player.getVehicle();
        if (vehicle != null && ICE_TOMB_ENTITY_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()))) {
            player.stopRiding();
            if (discardIceTomb && !vehicle.isRemoved()) vehicle.discard();
            tracked = true;
            removedIceTomb = true;
        }
        // Only clear the vanilla fields this state actually set.  A normal
        // world freeze or another mod's effect must remain untouched.
        if (customFreeze && (state == null || state.appliedTicksFrozen || legacyFreeze)
                && player.getTicksFrozen() > 0) {
            player.setTicksFrozen(0);
            tracked = true;
        }
        MobEffectInstance slowdown = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (customFreeze && (state == null || state.appliedSlowdown || legacyFreeze)
                && slowdown != null && slowdown.getAmplifier() == 255) {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            tracked = true;
        }
        if (customFreeze && (state == null || state.appliedChilled || legacyFreeze)
                && player.hasEffect(MobEffectRegistry.CHILLED)) {
            player.removeEffect(MobEffectRegistry.CHILLED);
            tracked = true;
        }
        // Freeze teardown must never touch another spell's cast state.  In
        // particular, a caster may still own a live AbstractConeProjectile
        // while its target is leaving an ice tomb.  The old unconditional
        // reset here could discard/reset a continuous spray at that exact
        // transition.  Invalid cast payloads are cleaned independently by
        // the caster-side tick path below, never from a freeze release.
        // A position packet is needed only when the player was actually a
        // passenger of our Ice Tomb.  Never erase velocity or teleport a
        // normally frozen player on thaw.
        if (removedIceTomb) synchronizeReleasedPlayer(player);
    }

    /** Applies one bounded, idempotent 36-tick frozen state; hits cannot stack it. */
    private static void applyCompleteFreeze(net.minecraft.server.level.ServerPlayer player, FreezeReason reason) {
        if (player == null || !player.isAlive() || player.isDeadOrDying()) return;
        java.util.UUID id = player.getUUID();
        if (FREEZE_STATES.containsKey(id) || ICE_FREEZE_RELEASE_TICKS.containsKey(id)) return;
        int duration = switch (reason) {
            case RAY_OF_FROST -> RAY_OF_FROST_FREEZE_TICKS;
            case CONE_OF_COLD, SNOWBALL, GLACIAL_EDGE -> FREEZE_DURATION_TICKS;
        };
        // Snowball first applies Chilled; a full freeze then produces Iron's
        // visible ice-tomb state. Apply both in this order for an immediate,
        // synchronized full-freeze visual rather than only a cold overlay.
        boolean appliedChilled = !player.hasEffect(MobEffectRegistry.CHILLED);
        if (appliedChilled) {
            player.addEffect(new MobEffectInstance(MobEffectRegistry.CHILLED, duration, 0, false, true, true));
        }
        boolean appliedSlowdown = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null;
        if (appliedSlowdown) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    duration, 255, false, false, true));
        }
        boolean appliedTicksFrozen = player.getTicksFrozen() <= 0;
        if (appliedTicksFrozen) player.setTicksFrozen(duration);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        emitFrozenParticles(player);
        long now = player.getServer().getTickCount();
        FREEZE_STATES.put(id, new FreezeState(now, now + duration, reason,
                appliedTicksFrozen, appliedSlowdown, appliedChilled));
        ICE_FREEZE_RELEASE_TICKS.put(id, now + duration);
    }

    /** Shows an obvious frozen-target effect without spawning a collision-blocking Ice Tomb. */
    private static void emitFrozenParticles(net.minecraft.server.level.ServerPlayer player) {
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                player.getX(), player.getY() + player.getBbHeight() * 0.55D, player.getZ(),
                10, 0.38D, player.getBbHeight() * 0.45D, 0.38D, 0.02D);
    }

    /** Forces tracking clients to accept a dismounted Ice Tomb position. */
    private static void synchronizeReleasedPlayer(net.minecraft.server.level.ServerPlayer player) {
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    /** A first successful Cone of Cold hit immediately starts the fixed freeze. */
    public static void onConeOfColdPlayerDamaged(net.minecraft.server.level.ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isDeadOrDying()) return;
        long now = player.getServer().getTickCount();
        Long blockedUntil = SPRAY_REFREEZE_BLOCK_UNTIL.get(player.getUUID());
        if (blockedUntil != null) {
            if (now < blockedUntil) {
                return;
            }
            SPRAY_REFREEZE_BLOCK_UNTIL.remove(player.getUUID());
        }
        if (FREEZE_STATES.containsKey(player.getUUID()) || isRidingIceTomb(player)) return;
        // This callback is reached only after a positive, accepted damage
        // event.  Do not wait for a tick-spaced damage cadence that normal
        // hurt-resistance prevents from ever producing.
        applyCompleteFreeze(player, FreezeReason.CONE_OF_COLD);
    }

    /** Spectators may inspect a duel, but can never affect it by casting. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpellPreCast(SpellPreCastEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            if (isFreezeControlActive(player)) {
                event.setCanceled(true);
                return;
            }
            if (manager(player.getServer()).isSurroundLocked(player.getUUID())) {
                event.setCanceled(true);
                MagicData.getPlayerMagicData(player).resetCastingState();
                player.stopUsingItem();
                return;
            }
            if (usesAuthoredCooldown(event.getSpellId())) {
                // Run last so another addon cannot leave an authored spell
                // cancelled after its cooldown has already expired.  The
                // authored cooldown map is the only gate for these spells;
                // this also avoids arena/no-cast-zone state being mistaken for
                // a cooldown failure.
                if (player.isSpectator()) {
                    event.setCanceled(true);
                    return;
                }
                if (PhantomHalberdRingSpell.ID.toString().equals(event.getSpellId())
                        && !PhantomHalberdRingSpell.canStart(player)) {
                    event.setCanceled(true);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "[法术决斗] 雷罚换血需要准星锁定16格内的有效敌人，且不能叠加对决"));
                    return;
                }
                MagicData magic = MagicData.getPlayerMagicData(player);
                // A rejected packet from an earlier cast can leave the client
                // and server cast flags latched even after the cooldown has
                // expired.  A same-spell retry is safe to re-arm; a different
                // spell still respects the active cast and is rejected.
                if (magic.isCasting()) {
                    String active = magic.getCastingSpellId();
                    boolean activeChant = magic.getCastDuration() > 0
                            && magic.getCastDurationRemaining() > 0;
                    // A real chant is an independent state machine: do not
                    // reset it just because another authored spell was
                    // requested.  This prevents a second spell from
                    // cancelling the first animation while still allowing a
                    // different spell immediately after the first one has
                    // actually finished.
                    if (activeChant) {
                        event.setCanceled(true);
                        return;
                    }
                    // A completed cast may leave the marker set for one
                    // packet.  Clear only that stale marker; cooldowns remain
                    // in Iron's per-spell map and are never reset here.
                    magic.resetCastingState();
                    player.stopUsingItem();
                }
                event.setCanceled(false);
                return;
            }
        }
        // This duel build never permits the vanilla fallback Magic Missile.
        // Cancel at the common pre-cast event so native key handling, held-key
        // recovery, quick-cast packets, and other addons all share one guard.
        if (MAGIC_MISSILE_SPELL_ID.equals(event.getSpellId())) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && noCastZones(player.getServer()).blocksCasting(player)) {
            event.setCanceled(true);
            MagicData.getPlayerMagicData(player).resetCastingState();
            player.stopUsingItem();
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[法术决斗] 此区域禁止施法"));
            return;
        }
        if (!event.getEntity().isSpectator()) return;
        event.setCanceled(true);
        MagicData.getPlayerMagicData(event.getEntity()).resetCastingState();
        event.getEntity().stopUsingItem();
    }

    /** Records the caster; Travel Optics applies its stun during the same cast. */
    @SubscribeEvent
    public static void onSpellCast(SpellOnCastEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            // No custom cooldown is written here. Iron's castSpell path has
            // already installed the current SpellConfigHolder cooldown, which
            // is where portable-inscription-table balance edits are applied.
        }
        if (EMPULSE_SPELL_ID.equals(event.getSpellId())
                && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            RECENT_EMPULSE_CASTERS.put(player.getUUID(), (long) player.getServer().getTickCount() + 8L);
        }
    }

    @SubscribeEvent
    public static void onPlayerAttackThunderLance(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer attacker)
                || !(event.getTarget() instanceof Entity target)) return;
        if (isThunderLanceHitbox(target)) {
            String caster = target.getPersistentData().getString("Caster");
            if (!attacker.getUUID().toString().equals(caster)
                    || !attacker.getMainHandItem().isEmpty()
                    || !attacker.getOffhandItem().isEmpty()) return;
            event.setCanceled(true);
            activateThunderLance(attacker.serverLevel(), target);
            return;
        }
        if (!isThunderLance(target)) return;
        Entity lance = target;
        String caster = lance.getPersistentData().getString("Caster");
        if (!attacker.getUUID().toString().equals(caster)
                || !attacker.getMainHandItem().isEmpty()
                || !attacker.getOffhandItem().isEmpty()) return;
        event.setCanceled(true);
        activateThunderLance(attacker.serverLevel(), lance);
    }

    private static boolean isThunderLanceHitbox(Entity entity) {
        return entity instanceof Interaction
                && entity.getPersistentData().getBoolean(THUNDER_LANCE_HITBOX_TAG);
    }

    /** Converts the invisible selectable proxy (or the lance itself) into a falling attack. */
    private static void activateThunderLance(ServerLevel level, Entity hitEntity) {
        Entity lance = hitEntity;
        String lanceId = hitEntity.getPersistentData().getString("Lance");
        if (!lanceId.isEmpty()) {
            try {
                Entity linked = level.getEntity(java.util.UUID.fromString(lanceId));
                if (linked != null) lance = linked;
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (isThunderLance(lance)) {
            lance.getPersistentData().putBoolean(THUNDER_LANCE_FALLING_TAG, true);
            lance.setNoGravity(false);
            lance.setDeltaMovement(new Vec3(0.0D, -1.8D, 0.0D));
        }
        if (hitEntity != lance && !hitEntity.isRemoved()) hitEntity.discard();
    }

    private static void tickThunderLances(MinecraftServer server) {
        long now = server.getTickCount();
        RECENT_EMPULSE_CASTERS.entrySet().removeIf(entry -> entry.getValue() < now);
        boolean hasTrackedLance = false;
        for (Entity tracked : ACTIVE_TRACKED_ENTITIES) {
            if (tracked != null && !tracked.isRemoved() && (isThunderLance(tracked) || isThunderLanceHitbox(tracked))) {
                hasTrackedLance = true;
                break;
            }
        }
        if (!hasTrackedLance && RECENT_EMPULSE_CASTERS.isEmpty()) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.player.Player targetPlayer : level.players()) {
                for (Entity lance : level.getEntitiesOfClass(Entity.class,
                        targetPlayer.getBoundingBox().inflate(3.0D, 4.0D, 3.0D),
                        candidate -> isThunderLance(candidate)
                                && targetPlayer.getUUID().toString().equals(candidate.getPersistentData().getString("Target")))) {
                    if (lance.isRemoved()) continue;
                    if (!targetPlayer.isAlive() || !isStunned(targetPlayer)) {
                        lance.discard();
                        continue;
                    }
                    if (!lance.getPersistentData().getBoolean(THUNDER_LANCE_FALLING_TAG)) {
                        lance.setPos(targetPlayer.getX(), targetPlayer.getY() + targetPlayer.getBbHeight() + 1.35D, targetPlayer.getZ());
                        ensureThunderLanceHitbox(level, lance, targetPlayer);
                    } else if (lance.distanceTo(targetPlayer) <= 1.5F || lance.getY() <= targetPlayer.getY() + 0.65D) {
                        targetPlayer.hurt(level.damageSources().magic(), 15.0F);
                        lance.discard();
                        removeThunderLanceHitbox(level, lance);
                    }
                    if (now % 2L == 0L && !lance.isRemoved()) {
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                                lance.getX(), lance.getY(), lance.getZ(), 5, 0.22D, 0.35D, 0.22D, 0.03D);
                    }
                }
            }
            for (Map.Entry<java.util.UUID, Long> casterEntry : RECENT_EMPULSE_CASTERS.entrySet()) {
                Entity caster = level.getEntity(casterEntry.getKey());
                if (!(caster instanceof LivingEntity livingCaster) || !livingCaster.isAlive()) continue;
                for (net.minecraft.world.entity.player.Player target : level.players()) {
                    if (target == livingCaster || !target.isAlive() || !isStunned(target)
                            || target.distanceToSqr(livingCaster) > 24.0D * 24.0D) continue;
                    if (!hasLanceFor(level, target.getUUID())) spawnThunderLance(level, target, livingCaster);
                }
            }
        }
    }

    private static void tickFangTargets(MinecraftServer server) {
        ACTIVE_TRACKED_ENTITIES.removeIf(entity -> entity == null || entity.isRemoved());
        if (ACTIVE_TRACKED_ENTITIES.isEmpty()) return;
        for (Entity fang : ACTIVE_TRACKED_ENTITIES) {
            if (!fang.getClass().getName().equals("io.redspace.ironsspellbooks.entity.spells.FangSwirlEntity")
                    || !(fang.level() instanceof ServerLevel level)
                    || !fang.getPersistentData().hasUUID("FollowTarget")) continue;
            Entity target = level.getEntity(fang.getPersistentData().getUUID("FollowTarget"));
            if (!(target instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) {
                fang.discard();
                continue;
            }
            fang.setPos(living.getX(), living.getY(), living.getZ());
            try {
                fang.getClass().getMethod("setStartPos", Vec3.class).invoke(fang, living.position());
                fang.getClass().getMethod("setDelay", int.class).invoke(fang, 8);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private static void tickFissures(MinecraftServer server) {
        ACTIVE_TRACKED_ENTITIES.removeIf(entity -> entity == null || entity.isRemoved());
        if (ACTIVE_TRACKED_ENTITIES.isEmpty()) return;
        for (Entity fissure : ACTIVE_TRACKED_ENTITIES) {
                if (!(fissure.level() instanceof ServerLevel level)
                        || !"com.gametechbc.gtbcs_geomancy_plus.entity.extended.projectiles.ExtendedEntityFissure"
                        .equals(fissure.getClass().getName()) || fissure.isRemoved()) continue;
                if (!fissure.getPersistentData().hasUUID(FISSURE_TARGET_TAG)) {
                    // Old worlds may contain a partially written fissure.  It
                    // has no valid target and must never reach CompoundTag's
                    // NbtUtils.loadUUID path.
                    continue;
                }
                java.util.UUID targetId = fissure.getPersistentData().getUUID(FISSURE_TARGET_TAG);
                Entity target = level.getEntity(targetId);
                if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                    fissure.discard();
                    continue;
                }
                try {
                    Object travelling = fissure.getClass().getMethod("isTravelling").invoke(fissure);
                    if (!(travelling instanceof Boolean) || !((Boolean) travelling)) continue;
                } catch (ReflectiveOperationException ignored) {
                    continue;
                }
                Vec3 approach = new Vec3(
                        fissure.getPersistentData().getDouble(FISSURE_DIR_X_TAG),
                        fissure.getPersistentData().getDouble(FISSURE_DIR_Y_TAG),
                        fissure.getPersistentData().getDouble(FISSURE_DIR_Z_TAG));
                if (approach.lengthSqr() < 1.0E-6D) approach = living.getBoundingBox().getCenter().subtract(fissure.position());
                else approach = approach.normalize();
                // Aim at a point just outside the target on each assigned
                // cardinal side, making the four fissures visibly converge
                // from different directions instead of stacking in one line.
                Vec3 aim = living.getBoundingBox().getCenter().add(approach.scale(1.4D)).subtract(fissure.position());
                if (aim.lengthSqr() < 1.0E-6D) continue;
                // Keep the native fissure's visual cadence but make its path
                // continuously home to the selected target, regardless of the
                // original spell's short cast range.
                fissure.setDeltaMovement(aim.normalize().scale(0.42D));
                fissure.setYRot((float) (Math.toDegrees(Math.atan2(-aim.x, aim.z))));
        }
    }

    private static boolean isStunned(LivingEntity entity) {
        return entity.getActiveEffects().stream().anyMatch(effect -> {
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            return id != null && "stun".equals(id.getPath());
        });
    }

    private static boolean hasLanceFor(ServerLevel level, java.util.UUID targetId) {
        return !level.getEntitiesOfClass(Entity.class,
                level.getEntity(targetId) instanceof Entity entity
                        ? entity.getBoundingBox().inflate(3.0D, 4.0D, 3.0D)
                        : new net.minecraft.world.phys.AABB(-3.0E7D, -2.0E7D, -3.0E7D, 3.0E7D, 2.0E7D, 3.0E7D),
                stand -> isThunderLance(stand) && targetId.toString().equals(stand.getPersistentData().getString("Target"))).isEmpty();
    }

    private static void spawnThunderLance(ServerLevel level, Entity target, Entity caster) {
        try {
            Class<?> type = Class.forName("io.redspace.ironsspellbooks.entity.spells.lightning_lance.LightningLanceProjectile");
            Object value = type.getConstructor(Level.class, LivingEntity.class).newInstance(level, caster);
            if (!(value instanceof Entity lance)) return;
            lance.setPos(target.getX(), target.getY() + target.getBbHeight() + 1.35D, target.getZ());
            lance.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            lance.setNoGravity(true);
            type.getMethod("setDamage", float.class).invoke(lance, 15.0F);
            lance.getPersistentData().putBoolean(THUNDER_LANCE_TAG, true);
            lance.getPersistentData().putString("Caster", caster.getUUID().toString());
            lance.getPersistentData().putString("Target", target.getUUID().toString());
            level.addFreshEntity(lance);
            ensureThunderLanceHitbox(level, lance, target);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private static void ensureThunderLanceHitbox(ServerLevel level, Entity lance, Entity target) {
        String proxyId = lance.getPersistentData().getString("Hitbox");
        Interaction proxy = null;
        if (!proxyId.isEmpty()) {
            try {
                Entity existing = level.getEntity(java.util.UUID.fromString(proxyId));
                if (existing instanceof Interaction interaction && !interaction.isRemoved()) proxy = interaction;
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (proxy == null) {
            proxy = EntityType.INTERACTION.create(level);
            if (proxy == null) return;
            proxy.getPersistentData().putBoolean(THUNDER_LANCE_HITBOX_TAG, true);
            proxy.getPersistentData().putString("Lance", lance.getUUID().toString());
            proxy.getPersistentData().putString("Caster", lance.getPersistentData().getString("Caster"));
            proxy.getPersistentData().putString("Target", target.getUUID().toString());
            try {
                java.lang.reflect.Method setWidth = Interaction.class.getDeclaredMethod("setWidth", float.class);
                java.lang.reflect.Method setHeight = Interaction.class.getDeclaredMethod("setHeight", float.class);
                setWidth.setAccessible(true);
                setHeight.setAccessible(true);
                setWidth.invoke(proxy, 4.2F);
                setHeight.invoke(proxy, 4.6F);
            } catch (ReflectiveOperationException ignored) {
            }
            lance.getPersistentData().putString("Hitbox", proxy.getUUID().toString());
            level.addFreshEntity(proxy);
        }
        proxy.setPos(lance.getX(), lance.getY() - 0.7D, lance.getZ());
    }

    private static void removeThunderLanceHitbox(ServerLevel level, Entity lance) {
        String proxyId = lance.getPersistentData().getString("Hitbox");
        if (proxyId.isEmpty()) return;
        try {
            Entity proxy = level.getEntity(java.util.UUID.fromString(proxyId));
            if (proxy != null && !proxy.isRemoved()) proxy.discard();
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void tickPetriviseCleanup(MinecraftServer server) {
        long now = server.getTickCount();
        ACTIVE_TRACKED_ENTITIES.removeIf(entity -> entity == null || entity.isRemoved());
        if (ACTIVE_TRACKED_ENTITIES.isEmpty()) return;
        for (Entity pillar : ACTIVE_TRACKED_ENTITIES) {
                // Connector/async entity lists can briefly expose a null or
                // already-removed slot while an entity is being unloaded.
                // Never let cleanup of an optional Petrivise pillar take down
                // the dedicated server tick loop.
                if (pillar == null || pillar.isRemoved()) continue;
                if (pillar.getClass().getName().equals(
                        "com.gametechbc.gtbcs_geomancy_plus.entity.projectiles.petrivise_pillar.PetrivisePillarEntity")) {
                    long cleanup = pillar.getPersistentData().getLong(PETRIVISE_CLEANUP_TAG);
                    int animationEnd = 50;
                    try {
                        int warmup = ((Number) pillar.getClass().getMethod("getWarmupDelay").invoke(pillar)).intValue();
                        int slam = ((Number) pillar.getClass().getMethod("getSlamDelay").invoke(pillar)).intValue();
                        animationEnd = warmup + slam;
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                    }
                    // The source entity has no natural despawn path. Use both
                    // the server-time tag and its age so old/stuck pillars are
                    // removed even if they joined before the tag was written.
                    if ((cleanup > 0L && now >= cleanup) || pillar.tickCount >= animationEnd + 40) {
                        pillar.discard();
                    }
                }
        }
    }

    private static boolean isThunderLance(Entity entity) {
        return entity.getPersistentData().getBoolean(THUNDER_LANCE_TAG)
                && entity.getClass().getName().equals("io.redspace.ironsspellbooks.entity.spells.lightning_lance.LightningLanceProjectile");
    }
}
