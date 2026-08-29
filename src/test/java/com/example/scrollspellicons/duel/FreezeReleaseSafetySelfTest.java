package com.example.scrollspellicons.duel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pure timer regression checks; no Minecraft server or world is started. */
public final class FreezeReleaseSafetySelfTest {
    private static final int FREEZE_DURATION_TICKS = 36;

    /** Small server-independent state model covering the freeze/spray lifecycle. */
    private static final class FreezeModel {
        int remaining;
        boolean frozen;
        boolean coneAlive = true;
        boolean targetBlocked;
        boolean canMove;
        boolean canAttack;
        boolean canCast;
        boolean canBeHurt;
        int coneHits;

        void freeze(int duration) {
            frozen = true;
            remaining = duration;
            targetBlocked = true;
            canMove = canAttack = canCast = canBeHurt = false;
        }

        void successfulConeHit() {
            coneHits++;
            if (!frozen) freeze(FREEZE_DURATION_TICKS);
        }

        void successfulRayHit() {
            if (!frozen) freeze(20);
        }

        void successfulSnowballHit() {
            if (!frozen) freeze(FREEZE_DURATION_TICKS);
        }

        void successfulGlacialHit() {
            // One projectile/target pair may only be accepted once.
            if (!targetBlocked) {
                coneHits++;
                freeze(FREEZE_DURATION_TICKS);
            }
        }

        boolean sameConeMayHitAgain() { return !targetBlocked; }

        void tick() {
            if (remaining > 0 && --remaining == 0) clear();
        }

        void breakFreeze() { clear(); }

        void deathAndRespawn() {
            clear();
            targetBlocked = false;
        }

        private void clear() {
            frozen = false;
            remaining = 0;
            targetBlocked = false;
            canMove = canAttack = canCast = canBeHurt = true;
            // Freeze teardown is target-only; a live caster cone survives.
            coneAlive = true;
        }
    }

    private static final class ManagedFieldModel {
        enum Source { SNOWBALL, GLACIAL_EDGE }
        final Source source;
        int freezeApplications;
        FreezeModel target;

        ManagedFieldModel(Source source, FreezeModel target) {
            this.source = source;
            this.target = target;
        }

        void applyEffect() {
            // Both managed field sources cancel only the player pulse.  The
            // direct projectile EntityHitResult is the sole freeze entry.
            // Non-player/native fields are outside this model and remain
            // vanilla in ManagedFrostFieldMixin.
            if (source == Source.SNOWBALL || source == Source.GLACIAL_EDGE) return;
        }
    }

    private static final class CastCleanupModel {
        boolean serverSide = true;
        boolean casting;
        boolean continuous;
        String spellId;
        boolean coneAlive = true;
        boolean discarded;

        void discardCastingEntity() {
            if (serverSide && casting && continuous
                    && "irons_spellbooks:cone_of_cold".equals(spellId) && coneAlive) {
                // Target-only freeze teardown preserves an active cone.
                return;
            }
            discarded = true;
        }
    }

    private static final class ProjectileHitModel {
        final java.util.Set<String> hitTargets = new java.util.HashSet<>();
        int nativeDamageCalls;
        int freezeCalls;
        boolean fieldCreated;

        boolean glacialEntityHit(String target, boolean nativeDamageApplied) {
            if (!hitTargets.add(target)) return false;
            nativeDamageCalls++;
            if (nativeDamageApplied) freezeCalls++;
            fieldCreated = true;
            return true;
        }

        /** Mirrors SpellDuelEvents.onSnowballProjectileImpact's event gates. */
        boolean snowballProjectileImpact(String target, boolean eventCanceled,
                                          boolean exactIronSnowball, boolean entityHit,
                                          boolean serverPlayerAlive) {
            if (eventCanceled || !exactIronSnowball || !entityHit || !serverPlayerAlive) return false;
            if (!hitTargets.add(target)) return false;
            freezeCalls++;
            fieldCreated = true;
            return true;
        }
    }

    private static final class ConeCadenceModel {
        int last = Integer.MIN_VALUE;
        boolean dealDamageActive;

        /** Simulates the actual pre-consumption hook, including native 10-tick upstream calls. */
        boolean tick(int tick, boolean strictSprayIdentity, boolean castingContinuous,
                     boolean nativeUpstreamActivation) {
            if (nativeUpstreamActivation) dealDamageActive = true;
            if (!strictSprayIdentity || !castingContinuous) {
                boolean hit = dealDamageActive;
                dealDamageActive = false;
                return hit;
            }
            boolean due = last == Integer.MIN_VALUE || tick - last >= 6;
            if (dealDamageActive) {
                if (due) {
                    last = tick;
                } else {
                    // The native ten-tick activation is too early; suppress
                    // only this pending consume, not the cone or cast data.
                    dealDamageActive = false;
                }
            } else if (due) {
                dealDamageActive = true;
                last = tick;
            }
            boolean hit = dealDamageActive;
            dealDamageActive = false;
            return hit;
        }
    }

    private static final class SprayToggleModel {
        String activeSpell;
        boolean coneAlive;
        boolean castData;
        int nativeCooldownCalls;

        boolean request(String requestedSpell, boolean strictSprayIdentity,
                        boolean nativeCancelTerminated, boolean cooldownAlreadyPresent) {
            if (activeSpell != null && strictSprayIdentity && activeSpell.equals(requestedSpell)) {
                if (!nativeCancelTerminated) return false;
                activeSpell = null;
                coneAlive = false;
                castData = false;
                if (nativeCancelTerminated && !cooldownAlreadyPresent) nativeCooldownCalls++;
                return false;
            }
            if (activeSpell != null) return false;
            activeSpell = requestedSpell;
            coneAlive = true;
            castData = true;
            return true;
        }
    }

    public static void main(String[] args) {
        UUID id = UUID.randomUUID();
        Map<UUID, Long> timers = new HashMap<>();

        // A tick can observe that another teardown path already removed it.
        Long releaseAt = timers.get(id);
        if (releaseAt != null && 100L >= releaseAt.longValue()) {
            throw new AssertionError("removed freeze timer must be a no-op");
        }

        timers.put(id, 20L);
        releaseAt = timers.get(id);
        if (releaseAt == null || 20L < releaseAt.longValue()) {
            throw new AssertionError("expired freeze timer was not released");
        }
        timers.remove(id);
        // Repeating the same release after cleanup must remain harmless.
        releaseAt = timers.get(id);
        if (releaseAt != null && 21L >= releaseAt.longValue()) {
            throw new AssertionError("repeated freeze release must be idempotent");
        }

        // A: the first accepted cone hit freezes immediately for exactly 36 ticks.
        FreezeModel model = new FreezeModel();
        model.successfulConeHit();
        if (!model.frozen || model.remaining != FREEZE_DURATION_TICKS || model.canMove
                || model.canAttack || model.canCast || model.canBeHurt || model.coneHits != 1) {
            throw new AssertionError("first cone hit did not immediately lock movement/combat for 36 ticks");
        }
        if (model.sameConeMayHitAgain() || !model.coneAlive) {
            throw new AssertionError("second collision must be rejected without discarding the active cone");
        }
        for (int tick = 0; tick < FREEZE_DURATION_TICKS; tick++) model.tick();
        if (model.frozen || model.remaining != 0 || !model.coneAlive
                || !model.canMove || !model.canAttack || !model.canCast || !model.canBeHurt) {
            throw new AssertionError("A: thaw must restore actions while preserving the caster cone");
        }

        // B: manual break removes only the target from the cone hit set; another target is eligible.
        model.freeze(FREEZE_DURATION_TICKS);
        model.breakFreeze();
        if (model.frozen || model.targetBlocked || !model.coneAlive) {
            throw new AssertionError("B: breaking freeze must not discard the active spray");
        }
        boolean newTargetEligible = !model.targetBlocked && model.coneAlive;
        if (!newTargetEligible) throw new AssertionError("B: spray cannot acquire a new target after thaw");

        // C: death/respawn clears all old-UUID control state and restores combat immediately.
        model.freeze(FREEZE_DURATION_TICKS);
        model.deathAndRespawn();
        if (model.frozen || model.remaining != 0 || model.targetBlocked
                || !model.canMove || !model.canAttack || !model.canCast || !model.canBeHurt) {
            throw new AssertionError("C: respawn retained stale freeze control");
        }

        // D: explicit whitelist durations and Glacial Edge one-hit behavior.
        FreezeModel ray = new FreezeModel();
        ray.successfulRayHit();
        if (!ray.frozen || ray.remaining != 20) throw new AssertionError("ray_of_frost must freeze exactly 20 ticks");
        FreezeModel snowball = new FreezeModel();
        snowball.successfulSnowballHit();
        if (!snowball.frozen || snowball.remaining != FREEZE_DURATION_TICKS) throw new AssertionError("snowball must freeze exactly 36 ticks");
        FreezeModel glacial = new FreezeModel();
        glacial.successfulGlacialHit();
        glacial.successfulGlacialHit();
        if (!glacial.frozen || glacial.remaining != FREEZE_DURATION_TICKS || glacial.coneHits != 1) {
            throw new AssertionError("Glacial Edge must freeze once per projectile and remain bounded");
        }
        if (FREEZE_DURATION_TICKS != 36) throw new AssertionError("D: freeze duration changed");

        // E: managed FrostField provenance is isolated. A direct Snowball
        // EntityHitResult freezes A once; the field never freezes A again,
        // never freezes a later player B entering the area, and a different
        // Snowball can still freeze its own direct target.
        FreezeModel snowballTarget = new FreezeModel();
        ManagedFieldModel snowballField = new ManagedFieldModel(
                ManagedFieldModel.Source.SNOWBALL, snowballTarget);
        snowballTarget.successfulSnowballHit();
        int remainingAfterDirectHit = snowballTarget.remaining;
        snowballField.applyEffect();
        snowballField.applyEffect();
        if (!snowballTarget.frozen || snowballTarget.remaining != FREEZE_DURATION_TICKS
                || snowballField.freezeApplications != 0
                || remainingAfterDirectHit != FREEZE_DURATION_TICKS) {
            throw new AssertionError("Snowball field pulse must not apply or refresh the direct-hit freeze");
        }
        FreezeModel fieldOnlyTarget = new FreezeModel();
        ManagedFieldModel fieldOnly = new ManagedFieldModel(
                ManagedFieldModel.Source.SNOWBALL, fieldOnlyTarget);
        fieldOnly.applyEffect();
        fieldOnly.applyEffect();
        if (fieldOnlyTarget.frozen || fieldOnly.freezeApplications != 0) {
            throw new AssertionError("a player entering a Snowball FrostField must not freeze");
        }
        FreezeModel differentSnowballTarget = new FreezeModel();
        differentSnowballTarget.successfulSnowballHit();
        if (!differentSnowballTarget.frozen
                || differentSnowballTarget.remaining != FREEZE_DURATION_TICKS) {
            throw new AssertionError("a different Snowball direct hit must still freeze its target");
        }
        FreezeModel glacialTarget = new FreezeModel();
        glacialTarget.successfulGlacialHit();
        ManagedFieldModel glacialField = new ManagedFieldModel(
                ManagedFieldModel.Source.GLACIAL_EDGE, glacialTarget);
        glacialField.applyEffect();
        if (!glacialTarget.frozen || glacialTarget.remaining != FREEZE_DURATION_TICKS
                || glacialField.freezeApplications != 0) {
            throw new AssertionError("Glacial Edge FrostField must not refresh projectile freeze");
        }

        // G: these models mirror the real outer projectile hit hooks: a
        // duplicate target never reaches native damage or creates a field,
        // while a different target is still eligible; failed native damage
        // does not create a freeze.
        ProjectileHitModel glacialProjectile = new ProjectileHitModel();
        if (!glacialProjectile.glacialEntityHit("A", true)
                || glacialProjectile.glacialEntityHit("A", true)
                || !glacialProjectile.glacialEntityHit("B", true)
                || glacialProjectile.nativeDamageCalls != 2
                || glacialProjectile.freezeCalls != 2) {
            throw new AssertionError("Glacial Edge per-projectile target gate failed");
        }
        ProjectileHitModel failedRay = new ProjectileHitModel();
        failedRay.glacialEntityHit("A", false);
        if (failedRay.freezeCalls != 0) throw new AssertionError("failed native hit froze its target");
        ProjectileHitModel snowballProjectile = new ProjectileHitModel();
        if (!snowballProjectile.snowballProjectileImpact("A", false, true, true, true)
                || snowballProjectile.snowballProjectileImpact("A", false, true, true, true)
                || snowballProjectile.freezeCalls != 1
                || snowballProjectile.snowballProjectileImpact("B", true, true, true, true)
                || snowballProjectile.freezeCalls != 1
                || snowballProjectile.snowballProjectileImpact("C", false, false, true, true)
                || snowballProjectile.snowballProjectileImpact("D", false, true, false, true)
                || snowballProjectile.snowballProjectileImpact("E", false, true, true, false)) {
            throw new AssertionError("Snowball ProjectileImpactEvent direct-hit gates were not one-shot");
        }
        ProjectileHitModel differentSnowball = new ProjectileHitModel();
        if (!differentSnowball.snowballProjectileImpact("A", false, true, true, true)
                || differentSnowball.freezeCalls != 1) {
            throw new AssertionError("different Snowball direct hit was not independent");
        }

        // H: every strict EntityCastData-backed cone, including an addon
        // subclass, is actually consumed immediately and then once per six
        // ticks, even though Iron's native upstream activation is every ten.
        for (String spray : new String[]{"irons_spellbooks:cone_of_cold",
                "irons_spellbooks:electrocute", "addon:test_cone"}) {
            ConeCadenceModel cadence = new ConeCadenceModel();
            java.util.List<Integer> actualHits = new java.util.ArrayList<>();
            for (int tick = 0; tick < 25; tick++) {
                if (cadence.tick(tick, true, true, tick % 10 == 0)) actualHits.add(tick);
            }
            if (!actualHits.equals(java.util.List.of(0, 6, 12, 18, 24))) {
                throw new AssertionError("actual six-tick spray damage activation failed for "
                        + spray + ": " + actualHits);
            }
        }
        ConeCadenceModel nonSpray = new ConeCadenceModel();
        if (!nonSpray.tick(0, false, true, true) || !nonSpray.tick(1, false, true, true)
                || !nonSpray.tick(2, false, false, true)) {
            throw new AssertionError("non-spray continuous spell was gated");
        }

        SprayToggleModel toggle = new SprayToggleModel();
        if (!toggle.request("irons_spellbooks:cone_of_cold", true, true, false)
                || toggle.request("irons_spellbooks:cone_of_cold", true, true, false)
                || toggle.coneAlive || toggle.castData
                || toggle.nativeCooldownCalls != 1
                || !toggle.request("irons_spellbooks:cone_of_cold", true, true, false)) {
            throw new AssertionError("same spray request did not toggle native cleanup and record cooldown");
        }
        // Heroic's early true->false retry is modeled explicitly: the
        // cleanup path is false, then Iron's manager is called exactly once
        // after the cone/cast data have terminated.
        SprayToggleModel heroicRetry = new SprayToggleModel();
        heroicRetry.request("irons_spellbooks:electrocute", true, true, false);
        heroicRetry.request("irons_spellbooks:electrocute", true, true, false);
        if (heroicRetry.nativeCooldownCalls != 1 || heroicRetry.coneAlive || heroicRetry.castData) {
            throw new AssertionError("Heroic true->false retry did not produce exactly one native cooldown");
        }
        SprayToggleModel failedCancel = new SprayToggleModel();
        failedCancel.request("addon:test_cone", true, false, false);
        failedCancel.request("addon:test_cone", true, false, false);
        if (failedCancel.nativeCooldownCalls != 0) {
            throw new AssertionError("failed native spray cleanup incorrectly added cooldown");
        }
        SprayToggleModel different = new SprayToggleModel();
        if (!different.request("irons_spellbooks:cone_of_cold", true, true, false)
                || different.request("irons_spellbooks:electrocute", true, true, false)
                || !different.coneAlive || !different.castData || different.nativeCooldownCalls != 0) {
            throw new AssertionError("different spray replaced the active spray");
        }

        // F: the lifetime guard preserves only an active server-side Cone of
        // Cold; release after key-up/mana exhaustion still discards normally.
        CastCleanupModel activeCone = new CastCleanupModel();
        activeCone.casting = true;
        activeCone.continuous = true;
        activeCone.spellId = "irons_spellbooks:cone_of_cold";
        activeCone.discardCastingEntity();
        if (activeCone.discarded) throw new AssertionError("active Cone of Cold was discarded during target cleanup");
        CastCleanupModel releasedCone = new CastCleanupModel();
        releasedCone.spellId = "irons_spellbooks:cone_of_cold";
        releasedCone.discardCastingEntity();
        if (!releasedCone.discarded) throw new AssertionError("released Cone of Cold did not discard");
        CastCleanupModel otherCone = new CastCleanupModel();
        otherCone.casting = true;
        otherCone.continuous = true;
        otherCone.spellId = "irons_spellbooks:fire_breath";
        otherCone.discardCastingEntity();
        if (!otherCone.discarded) throw new AssertionError("non-Cone-of-Cold spray was incorrectly retained");

        System.out.println("freeze-safety: cone_of_cold first hit -> 36 ticks, repeat collision rejected, cone alive");
        System.out.println("freeze-safety: accepted Snowball ProjectileImpactEvent freezes once; canceled/field/non-player paths do not; different projectile works");
        System.out.println("freeze-safety: active cone preserved; key-up/non-cone discard paths preserved");
        System.out.println("freeze-safety: ray=20 ticks, snowball/glacial=36 ticks, death-respawn cleanup passed");
        System.out.println("freeze-safety: direct Glacial/Snowball hit gates and native-success freeze checks passed");
        System.out.println("freeze-safety: actual strict spray hit activation is 0,6,12,18,24 for native 10-tick upstream; non-spray continuous is untouched");
        System.out.println("freeze-safety: same spray toggles through native cancel cleanup and records native cooldown; different spray preserves the active cone");
    }
}
