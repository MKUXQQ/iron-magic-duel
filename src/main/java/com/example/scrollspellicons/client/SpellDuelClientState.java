package com.example.scrollspellicons.client;

import com.example.scrollspellicons.duel.SpellDuelNetwork;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SpellDuelClientState {
    private static boolean displayEnabled;
    private static String snapshotGroup = "";
    private static java.util.List<com.example.scrollspellicons.duel.SpellDuelNetwork.SnapshotEntry> snapshot = java.util.List.of();
    private static Map<UUID, Map<String, SyncedCooldown>> cooldowns = Map.of();
    private static java.util.List<SpellDuelNetwork.PointMarker> pointMarkers = java.util.List.of();
    // Large coordinates select the default position: centred above the vanilla hotbar.
    private static int hudX = 10000;
    private static int hudY = 10000;

    private SpellDuelClientState() {}

    public static boolean displayEnabled() { return displayEnabled; }
    public static void setDisplayEnabled(boolean enabled) { displayEnabled = enabled; }
    public static int hudX() { return hudX; }
    public static int hudY() { return hudY; }
    public static void setHudPosition(int x, int y) { hudX = Math.max(0, x); hudY = Math.max(0, y); }
    public static String snapshotGroup() { return snapshotGroup; }
    public static java.util.List<com.example.scrollspellicons.duel.SpellDuelNetwork.SnapshotEntry> snapshot() { return snapshot; }
    public static void setSnapshot(String group, java.util.List<com.example.scrollspellicons.duel.SpellDuelNetwork.SnapshotEntry> entries) { snapshotGroup = group; snapshot = java.util.List.copyOf(entries); }
    public static java.util.List<SpellDuelNetwork.PointMarker> pointMarkers() { return pointMarkers; }
    public static void setPointMarkers(java.util.List<SpellDuelNetwork.PointMarker> markers) { pointMarkers = java.util.List.copyOf(markers); }
    public static Map<String, Integer> cooldowns(UUID playerId) {
        long now = clientTick();
        Map<String, Integer> result = new HashMap<>();
        for (var entry : cooldowns.getOrDefault(playerId, Map.of()).entrySet()) {
            int remaining = remainingTicks(entry.getValue(), now);
            if (remaining > 0) result.put(entry.getKey(), remaining);
        }
        return result;
    }
    public static boolean hasCooldowns(UUID playerId) { return cooldowns.containsKey(playerId); }
    public static void setCooldowns(List<SpellDuelNetwork.CooldownEntry> entries) {
        long now = clientTick();
        Map<UUID, Map<String, SyncedCooldown>> updated = new HashMap<>();
        for (var existing : cooldowns.entrySet()) {
            Map<String, SyncedCooldown> active = new HashMap<>();
            for (var cooldown : existing.getValue().entrySet()) {
                if (remainingTicks(cooldown.getValue(), now) > 0) active.put(cooldown.getKey(), cooldown.getValue());
            }
            updated.put(existing.getKey(), Map.copyOf(active));
        }
        for (SpellDuelNetwork.CooldownEntry entry : entries) {
            Map<String, SyncedCooldown> merged = new HashMap<>(updated.getOrDefault(entry.playerId(), Map.of()));
            for (var cooldown : entry.cooldowns().entrySet()) {
                if (cooldown.getValue() > 0) merged.put(cooldown.getKey(), new SyncedCooldown(cooldown.getValue(), now));
            }
            updated.put(entry.playerId(), Map.copyOf(merged));
        }
        cooldowns = Map.copyOf(updated);
    }

    private static long clientTick() {
        return Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
    }

    private static int remainingTicks(SyncedCooldown cooldown, long now) {
        long elapsed = Math.max(0L, now - cooldown.receivedAt());
        return elapsed >= cooldown.ticks() ? 0 : cooldown.ticks() - (int) elapsed;
    }

    private record SyncedCooldown(int ticks, long receivedAt) {}

}
