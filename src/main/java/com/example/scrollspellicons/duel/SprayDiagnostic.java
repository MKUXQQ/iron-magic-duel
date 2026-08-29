package com.example.scrollspellicons.duel;

import com.example.scrollspellicons.IronSpellPerformance;
import com.example.scrollspellicons.config.PerformanceConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile;
import io.redspace.ironsspellbooks.spells.EntityCastData;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Low-noise diagnostics for the spray/ice transition.  It records only a
 * changed state, so a normal long spray does not fill latest.log every tick.
 * Search the log for {@code [SprayDiag][server]} after reproducing the issue.
 */
public final class SprayDiagnostic {
    private static final Map<UUID, String> LAST_SERVER_STATE = new HashMap<>();
    private static final Map<UUID, Long> LAST_LOG_TICKS = new HashMap<>();

    private SprayDiagnostic() {}

    public static void observeServer(ServerPlayer player, MagicData magic, AbstractConeProjectile cone) {
        // Diagnostics are an opt-in troubleshooting aid.  In particular do
        // not inspect or log ordinary LONG spells (shield, fang_swirl,
        // void_bulwark, etc.) and do not allocate state for players without a
        // real cone entity.
        if (!PerformanceConfig.isServerConfigLoaded()
                || !PerformanceConfig.serverValues().debugSprayDiagnostics() || cone == null) return;
        long now = player.serverLevel().getGameTime();
        Long lastLog = LAST_LOG_TICKS.get(player.getUUID());
        if (lastLog != null && now - lastLog < 20L) return;
        LAST_LOG_TICKS.put(player.getUUID(), now);
        Entity vehicle = player.getVehicle();
        boolean ridingIceTomb = vehicle != null
                && "irons_spellbooks:ice_tomb".equals(String.valueOf(vehicle.getType().builtInRegistryHolder().key().location()));
        String spell = magic.isCasting() ? magic.getCastingSpellId() : "-";
        CastType castType = magic.getCastType();
        boolean hasEntityCastData = magic.getAdditionalCastData() instanceof EntityCastData;
        boolean removed = false;
        int entityId = -1;
        if (cone != null) {
            entityId = cone.getId();
            removed = cone.isRemoved();
        }
        String state = "casting=" + magic.isCasting()
                + ",castType=" + castType
                + ",spell=" + spell
                + ",entityCastData=" + hasEntityCastData
                + ",coneId=" + entityId
                + ",coneRemoved=" + removed
                + ",iceTomb=" + ridingIceTomb
                + ",frozen=" + (player.getTicksFrozen() > 0)
                + ",chilled=" + player.hasEffect(MobEffectRegistry.CHILLED);
        String previous = LAST_SERVER_STATE.put(player.getUUID(), state);
        if (!state.equals(previous)) {
            IronSpellPerformance.LOGGER.info("[SprayDiag][server] player={} tick={} {}{}",
                    player.getGameProfile().getName(), now, state,
                    previous == null ? "" : " (previous: " + previous + ")");
        }
        if (magic.isCasting() && removed && (previous == null || !previous.contains("coneRemoved=true"))) {
            IronSpellPerformance.LOGGER.warn(
                    "[SprayDiag][server] continuous spray entity was removed during an active cast: player={} tick={} coneId={}",
                    player.getGameProfile().getName(), player.serverLevel().getGameTime(), entityId);
        }
        if (previous != null && magic.isCasting() && cone == null && previous.contains("coneId=")
                && !previous.contains("coneId=-1")) {
            IronSpellPerformance.LOGGER.warn(
                    "[SprayDiag][server] spray entity disappeared while the player was still casting: player={} tick={}",
                    player.getGameProfile().getName(), player.serverLevel().getGameTime());
        }
    }

    public static void clear(ServerPlayer player) {
        LAST_SERVER_STATE.remove(player.getUUID());
        LAST_LOG_TICKS.remove(player.getUUID());
    }
}
