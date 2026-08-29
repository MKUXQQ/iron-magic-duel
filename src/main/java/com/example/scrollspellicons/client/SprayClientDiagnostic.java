package com.example.scrollspellicons.client;

import com.example.scrollspellicons.IronSpellPerformance;
import com.example.scrollspellicons.config.PerformanceConfig;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Low-noise client-side companion to {@link com.example.scrollspellicons.duel.SprayDiagnostic}. */
public final class SprayClientDiagnostic {
    private static String lastState;
    private static long lastRecoveryPacketTick = Long.MIN_VALUE;

    private SprayClientDiagnostic() {}

    public static void observe(Minecraft minecraft, SpellData selected, boolean keyDown) {
        if (!PerformanceConfig.isClientConfigLoaded()
                || !PerformanceConfig.clientValues().debugSprayDiagnostics()) return;
        String selectedId = selected == null || selected == SpellData.EMPTY
                ? "-" : selected.getSpell().getSpellResource().toString();
        String castingId = ClientMagicData.isCasting() ? ClientMagicData.getCastingSpellId() : "-";
        String state = "keyDown=" + keyDown
                + ",selected=" + selectedId
                + ",casting=" + ClientMagicData.isCasting()
                + ",castType=" + ClientMagicData.getCastType()
                + ",castingSpell=" + castingId;
        if (!state.equals(lastState)) {
            IronSpellPerformance.LOGGER.info("[SprayDiag][client] tick={} {}{}",
                    minecraft.player == null ? -1 : minecraft.player.tickCount,
                    state, lastState == null ? "" : " (previous: " + lastState + ")");
            lastState = state;
        }
    }

    public static void recoveryPacket(Minecraft minecraft, ResourceLocation spellId) {
        if (!PerformanceConfig.isClientConfigLoaded()
                || !PerformanceConfig.clientValues().debugSprayDiagnostics()) return;
        long tick = minecraft.player == null ? 0L : minecraft.player.tickCount;
        if (tick - lastRecoveryPacketTick >= 10L) {
            lastRecoveryPacketTick = tick;
            IronSpellPerformance.LOGGER.info("[SprayDiag][client] recovery CastPacket sent while key remained held: tick={} spell={}",
                    tick, spellId);
        }
    }

    public static void clear() {
        lastState = null;
        lastRecoveryPacketTick = Long.MIN_VALUE;
    }
}
