package com.example.scrollspellicons.server;

import com.example.scrollspellicons.duel.SpellDuelEvents;
import com.example.scrollspellicons.IronSpellPerformance;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.MagicHelper;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.capabilities.magic.CooldownInstance;
import io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile;
import io.redspace.ironsspellbooks.network.casting.CancelCastPacket;
import io.redspace.ironsspellbooks.spells.EntityCastData;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Server-side predicates for chants that must finish before another spell can start. */
public final class ChanneledCastGuard {
    private static final ThreadLocal<UUID> ALLOWED_SPRAY_CANCEL = new ThreadLocal<>();
    private static final boolean DEBUG_SPRAY_TOGGLE =
            Boolean.getBoolean("iron_magic_duel.debugSprayToggle");

    private ChanneledCastGuard() {}

    public static boolean isChannelling(ServerPlayer player) {
        if (player == null) return false;
        MagicData magic = MagicData.getPlayerMagicData(player);
        return magic.isCasting() && magic.getCastDuration() > 0 && magic.getCastDurationRemaining() > 0;
    }

    /** True when a key requests a different spell before the active chant has completed. */
    public static boolean blocksReplacement(ServerPlayer player, SpellData requestedSpell) {
        MagicData magic = MagicData.getPlayerMagicData(player);
        // Authored duel spells are instantaneous and have their own
        // player+spell cooldowns. A stale global cast marker from one of them
        // must never block another authored spell.
        if (SpellDuelEvents.usesAuthoredCooldown(magic.getCastingSpellId())) return false;
        return isChannelling(player)
                && requestedSpell != null
                && requestedSpell != SpellData.EMPTY
                && !magic.getCastingSpellId().equals(requestedSpell.getSpell().getSpellId());
    }

    /** Strict server-side identity for a currently active spray cone. */
    public static boolean isActiveSpray(ServerPlayer player, String requestedSpellId) {
        if (player == null || requestedSpellId == null) return false;
        MagicData magic = MagicData.getPlayerMagicData(player);
        if (!magic.isCasting() || magic.getCastType() != CastType.CONTINUOUS
                || !requestedSpellId.equals(magic.getCastingSpellId())
                || !(magic.getAdditionalCastData() instanceof EntityCastData castData)
                || !(castData.getCastingEntity() instanceof AbstractConeProjectile cone)
                || cone.level().isClientSide || cone.isRemoved() || !cone.isAlive()
                || cone.getOwner() != player) return false;
        return true;
    }

    /**
     * Uses Iron's native cancellation path.  The thread-local marker lets the
     * existing uninterruptible-chant guard distinguish this intentional spray
     * toggle from an unrelated cancellation request.
     */
    public static boolean cancelActiveSpray(ServerPlayer player, String requestedSpellId) {
        if (!isActiveSpray(player, requestedSpellId)) return false;
        MagicData magic = MagicData.getPlayerMagicData(player);
        SpellData capturedCasting = magic.getCastingSpell();
        if (capturedCasting == null || capturedCasting.getSpell() == null) return false;
        AbstractSpell capturedSpell = capturedCasting.getSpell();
        CastSource capturedSource = magic.getCastSource();
        EntityCastData capturedCastData = magic.getAdditionalCastData() instanceof EntityCastData data
                ? data : null;
        AbstractConeProjectile capturedCone = capturedCastData != null
                && capturedCastData.getCastingEntity() instanceof AbstractConeProjectile cone
                ? cone : null;
        if (capturedCone == null) return false;

        String beforeId = magic.getCastingSpellId();
        CastType beforeType = magic.getCastType();
        int cooldownBefore = cooldownRemaining(magic, capturedSpell.getSpellId());
        boolean cancelReturned = false;
        boolean sprayTerminated = false;
        boolean oldCastDataCleared = false;
        boolean nativeCooldownInvoked = false;
        ALLOWED_SPRAY_CANCEL.set(player.getUUID());
        try {
            // Use the no-cooldown native cleanup first.  Heroic Spirit Spell
            // has a CancelCastPacket HEAD hook that can rewrite an early
            // true request into a recursive false request and cancel the
            // outer call.  Calling false here makes cleanup deterministic;
            // the cooldown is added below only after the old spray is gone.
            CancelCastPacket.cancelCast(player, false);
            cancelReturned = true;

            Object afterCastData = magic.getAdditionalCastData();
            oldCastDataCleared = !(afterCastData instanceof EntityCastData after)
                    || after.getCastingEntity() != capturedCone;
            sprayTerminated = !magic.isCasting()
                    && oldCastDataCleared
                    && (capturedCone.isRemoved() || !capturedCone.isAlive());

            // PlayerCooldowns and IMagicManager are Iron's only cooldown
            // authority.  Avoid a second write if another native listener
            // already installed the same spell cooldown.
            if (sprayTerminated && cooldownRemaining(magic, capturedSpell.getSpellId()) <= 0) {
                MagicHelper.MAGIC_MANAGER.addCooldown(player, capturedSpell, capturedSource);
                nativeCooldownInvoked = true;
            }
            return sprayTerminated;
        } finally {
            ALLOWED_SPRAY_CANCEL.remove();
            if (DEBUG_SPRAY_TOGGLE) {
                IronSpellPerformance.LOGGER.debug(
                        "[SprayToggle] player={} requested={} captured={} beforeId={} source={} beforeType={} "
                                + "cancelReturned={} castDataCleared={} terminated={} coneRemoved={} coneAlive={} "
                                + "nativeAddCooldown={} cooldownBefore={} cooldownAfter={} castingAfter={} "
                                + "castingAfterId={}",
                        player.getGameProfile().getName(), requestedSpellId, capturedSpell.getSpellId(), beforeId,
                        capturedSource, beforeType, cancelReturned, oldCastDataCleared, sprayTerminated,
                        capturedCone.isRemoved(), capturedCone.isAlive(), nativeCooldownInvoked,
                        cooldownBefore, cooldownRemaining(magic, capturedSpell.getSpellId()),
                        magic.isCasting(), magic.getCastingSpellId());
            }
        }
    }

    private static int cooldownRemaining(MagicData magic, String spellId) {
        CooldownInstance cooldown = magic.getPlayerCooldowns().getSpellCooldowns().get(spellId);
        return cooldown == null ? 0 : cooldown.getCooldownRemaining();
    }

    public static boolean isIntentionalSprayCancellation(ServerPlayer player) {
        return player != null && player.getUUID().equals(ALLOWED_SPRAY_CANCEL.get());
    }
}
