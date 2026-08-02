package com.example.scrollspellicons.duel;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.particles.ParticleTypes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns all duel state for one running Minecraft server. */
public final class SpellDuelManager {
    private final MinecraftServer server;
    private final Map<String, SpellDuelGroup> groups = new LinkedHashMap<>();
    /** Groups which exist only while their creator is using the player picker. */
    private final Set<String> editingGroups = new LinkedHashSet<>();
    private final Map<UUID, PendingSelection> pending = new HashMap<>();
    private final Map<String, Map<UUID, SavedState>> savedStates = new HashMap<>();
    private final Map<String, PendingRestore> pendingRestores = new HashMap<>();
    /** Death-event eliminations remain authoritative even after the player entity is revived. */
    private final Map<String, Set<UUID>> eliminatedPlayers = new HashMap<>();
    private final Map<GlobalPos, String> catalystGroups = new LinkedHashMap<>();
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private static final String SELECTION_GLOW_TEAM = "iron_magic_glow";
    private final Map<UUID, String> previousTeams = new HashMap<>();
    private boolean displayEnabled;
    private long serverTicks;
    private final java.nio.file.Path pointFile;

    public SpellDuelManager(MinecraftServer server) {
        this.server = server;
        this.pointFile = server.getWorldPath(LevelResource.ROOT).resolve("data/iron_magic_duel_points.nbt");
        loadPoints();
    }

    public MinecraftServer server() { return server; }
    public Map<String, SpellDuelGroup> groups() { return Map.copyOf(groups); }
    public SpellDuelGroup group(String id) { return groups.get(id); }
    public boolean displayEnabled() { return displayEnabled; }
    public void setDisplayEnabled(boolean enabled) { displayEnabled = enabled; }

    public PendingSelection selection(UUID player) {
        return pending.computeIfAbsent(player, ignored -> new PendingSelection());
    }

    /** Starts a fresh, private editing group every time the picker is opened. */
    public String beginEditingGroup(UUID creator) {
        cancelEditingGroup(creator);
        PendingSelection state = selection(creator);
        SpellDuelGroup reusable = groups.values().stream()
                .filter(group -> !group.active() && group.teamA().isEmpty() && group.teamB().isEmpty())
                .sorted(java.util.Comparator.comparingInt(group -> groupOrder(group.id())))
                .findFirst().orElse(null);
        String id = reusable == null ? nextGroupId() : reusable.id();
        if (reusable == null) groups.put(id, new SpellDuelGroup(id));
        editingGroups.add(id);
        state.currentGroup = id;
        return id;
    }

    public boolean addPlayerToEditingGroup(UUID creator, UUID target, SpellDuelGroup.Team team) {
        PendingSelection state = selection(creator);
        SpellDuelGroup group = groups.get(state.currentGroup);
        if (group == null || !editingGroups.contains(group.id()) || group.active()) return false;
        String selectedGroup = selectedGroup(target);
        if (selectedGroup != null && !selectedGroup.equals(group.id())) {
            SpellDuelGroup previous = groups.get(selectedGroup);
            if (previous == null) return false;
            if (previous.active()) finish(previous, null, false);
            previous.add(target, null);
            clearSelectionGlow(Set.of(target));
        }
        group.add(target, team);
        ServerPlayer player = server.getPlayerList().getPlayer(target);
        if (player != null) setSelectionGlow(player);
        return true;
    }

    /** Removes a player from whichever non-active group currently owns them. */
    public boolean cancelSelectedPlayer(UUID target) {
        String id = selectedGroup(target);
        SpellDuelGroup group = id == null ? null : groups.get(id);
        if (group == null || group.active()) return false;
        boolean removed = group.add(target, null);
        if (removed) clearSelectionGlow(Set.of(target));
        return removed;
    }

    public boolean finalizeEditingGroup(UUID creator) {
        PendingSelection state = selection(creator);
        if (state.currentGroup == null || !editingGroups.remove(state.currentGroup)) return false;
        SpellDuelGroup group = groups.get(state.currentGroup);
        if (group != null) clearSelectionGlow(groupPlayers(group));
        return true;
    }

    /** Esc cancels the current edit: remove its temporary group and every selected player. */
    public boolean cancelEditingGroup(UUID creator) {
        PendingSelection state = pending.get(creator);
        if (state == null || state.currentGroup == null || !editingGroups.remove(state.currentGroup)) return false;
        SpellDuelGroup group = groups.get(state.currentGroup);
        if (group != null) {
            clearSelectionGlow(groupPlayers(group));
            // An editor can reuse a group that already owns saved A/B points.
            // Esc must cancel its players but must never destroy those point settings.
            if (group.pointA() != null || group.pointB() != null) group.clearPlayers();
            else {
                groups.remove(state.currentGroup);
                catalystGroups.entrySet().removeIf(entry -> state.currentGroup.equals(entry.getValue()));
            }
        }
        state.currentGroup = null;
        state.pointA = null;
        state.pointB = null;
        savePoints();
        return true;
    }

    public String selectedGroup(UUID target) {
        for (SpellDuelGroup group : groups.values()) if (group.contains(target)) return group.id();
        return null;
    }

    public SpellDuelGroup.Team selectedTeam(UUID selector, UUID target) {
        PendingSelection state = selection(selector);
        SpellDuelGroup group = groups.get(state.currentGroup);
        return group == null ? null : group.teamOf(target);
    }

    public boolean isEditingGroup(String id) { return editingGroups.contains(id); }

    private String nextGroupId() {
        int n = 1;
        String id;
        do id = "duel_" + n++; while (groups.containsKey(id));
        return id;
    }

    private static int groupOrder(String id) {
        if (id.startsWith("duel_")) {
            try { return Integer.parseInt(id.substring("duel_".length())); }
            catch (NumberFormatException ignored) { }
        }
        return Integer.MAX_VALUE;
    }

    private static Set<UUID> groupPlayers(SpellDuelGroup group) {
        Set<UUID> result = new LinkedHashSet<>(group.teamA());
        result.addAll(group.teamB());
        return result;
    }

    public String createPointGroup(UUID creator) {
        PendingSelection state = selection(creator);
        if (state.pointA == null || state.pointB == null) return null;
        SpellDuelGroup existing = state.currentGroup == null ? null : groups.get(state.currentGroup);
        if (existing != null && !existing.active()) {
            existing.setPoint(SpellDuelGroup.Team.A, state.pointA);
            existing.setPoint(SpellDuelGroup.Team.B, state.pointB);
            state.pointA = null;
            state.pointB = null;
            savePoints();
            return existing.id();
        }
        if (existing == null || existing.active()) {
            existing = groups.values().stream()
                    .filter(group -> !group.active() && (group.pointA() == null || group.pointB() == null)
                            && !group.teamA().isEmpty() && !group.teamB().isEmpty())
                    .findFirst().orElse(null);
        }
        if (existing != null && !existing.active() && (existing.pointA() == null || existing.pointB() == null)) {
            existing.setPoint(SpellDuelGroup.Team.A, state.pointA);
            existing.setPoint(SpellDuelGroup.Team.B, state.pointB);
            state.pointA = null;
            state.pointB = null;
            state.currentGroup = existing.id();
            return existing.id();
        }
        String id = nextGroupId();
        SpellDuelGroup group = new SpellDuelGroup(id);
        group.setPoint(SpellDuelGroup.Team.A, state.pointA);
        group.setPoint(SpellDuelGroup.Team.B, state.pointB);
        groups.put(id, group);
        savePoints();
        state.currentGroup = id;
        state.pointA = null;
        state.pointB = null;
        return id;
    }

    private void setSelectionGlow(ServerPlayer player) {
        var scoreboard = server.getScoreboard();
        PlayerTeam glowTeam = scoreboard.getPlayerTeam(SELECTION_GLOW_TEAM);
        if (glowTeam == null) {
            glowTeam = scoreboard.addPlayerTeam(SELECTION_GLOW_TEAM);
            glowTeam.setColor(ChatFormatting.GREEN);
        }
        PlayerTeam oldTeam = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (oldTeam != null && oldTeam != glowTeam) previousTeams.putIfAbsent(player.getUUID(), oldTeam.getName());
        scoreboard.addPlayerToTeam(player.getScoreboardName(), glowTeam);
        player.setGlowingTag(true);
    }

    private void clearSelectionGlow(Set<UUID> players) {
        var scoreboard = server.getScoreboard();
        PlayerTeam glowTeam = scoreboard.getPlayerTeam(SELECTION_GLOW_TEAM);
        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            boolean wasGlowingSelection = glowTeam != null && glowTeam.getPlayers().contains(player.getScoreboardName());
            if (wasGlowingSelection) scoreboard.removePlayerFromTeam(player.getScoreboardName());
            String oldTeamName = previousTeams.remove(uuid);
            if (wasGlowingSelection && oldTeamName != null) {
                PlayerTeam oldTeam = scoreboard.getPlayerTeam(oldTeamName);
                if (oldTeam != null) scoreboard.addPlayerToTeam(player.getScoreboardName(), oldTeam);
            }
            if (wasGlowingSelection) player.setGlowingTag(false);
        }
        if (glowTeam != null && glowTeam.getPlayers().isEmpty()) scoreboard.removePlayerTeam(glowTeam);
    }

    public void selectPoint(UUID selector, SpellDuelGroup.Team team, ServerLevel level, net.minecraft.core.BlockPos pos) {
        PendingSelection state = selection(selector);
        SpellDuelGroup.PointLocation point = new SpellDuelGroup.PointLocation(
                level.dimension().location().toString(), pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 0, 0);
        if (team == SpellDuelGroup.Team.A) state.pointA = point;
        else state.pointB = point;
    }

    /** Sends all saved point markers only to a player holding the point selector. */
    public void showPointMarkers(ServerPlayer player) {
        java.util.List<SpellDuelNetwork.PointMarker> labels = new ArrayList<>();
        for (SpellDuelGroup group : groups.values()) {
            sendPointMarker(player, labels, group.id(), "A", group.pointA(), ParticleTypes.END_ROD);
            sendPointMarker(player, labels, group.id(), "B", group.pointB(), ParticleTypes.SMOKE);
        }
        SpellDuelNetwork.sendPointMarkers(player, labels);
    }

    private void sendPointMarker(ServerPlayer player, java.util.List<SpellDuelNetwork.PointMarker> labels, String groupId, String pointName,
                                 SpellDuelGroup.PointLocation point,
                                 net.minecraft.core.particles.ParticleOptions particle) {
        if (point == null || !player.level().dimension().location().toString().equals(point.dimension())) return;
        player.serverLevel().sendParticles(player, particle, true, point.x(), point.y() + 0.25, point.z(), 5, 0.16, 0.20, 0.16, 0.01);
        labels.add(new SpellDuelNetwork.PointMarker(groupId + " · " + pointName, point.x(), point.y() + 0.85, point.z()));
    }

    public boolean setPointFromCommand(String id, SpellDuelGroup.Team team, ServerPlayer player) {
        SpellDuelGroup group = groups.get(id);
        if (group == null || group.active()) return false;
        group.setPoint(team, new SpellDuelGroup.PointLocation(player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        savePoints();
        return true;
    }

    public boolean bindPoints(UUID selector) {
        PendingSelection state = selection(selector);
        SpellDuelGroup group = groups.get(state.currentGroup);
        if (group == null || state.pointA == null || state.pointB == null) return false;
        group.setPoint(SpellDuelGroup.Team.A, state.pointA);
        group.setPoint(SpellDuelGroup.Team.B, state.pointB);
        state.pointA = null;
        state.pointB = null;
        savePoints();
        return true;
    }

    public String currentGroup(UUID selector) { return selection(selector).currentGroup; }

    public void setCatalystGroup(GlobalPos pos, String group) { catalystGroups.put(pos, group); }

    public String cycleCatalyst(GlobalPos pos) {
        ArrayList<String> ids = new ArrayList<>(groups.keySet());
        if (ids.isEmpty()) return null;
        String current = catalystGroups.get(pos);
        int next = current == null ? 0 : (ids.indexOf(current) + 1) % ids.size();
        String selected = ids.get(next);
        catalystGroups.put(pos, selected);
        return selected;
    }

    public String catalystGroup(GlobalPos pos) { return catalystGroups.get(pos); }

    public ArrayList<ServerPlayer> spectators(String groupId) {
        ArrayList<ServerPlayer> result = new ArrayList<>();
        for (UUID uuid : spectators) {
            if (groupId.equals(spectatorGroups.get(uuid))) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) result.add(player);
            }
        }
        return result;
    }

    public String start(String id) {
        SpellDuelGroup group = groups.get(id);
        if (group == null) return "不存在决斗组 " + id;
        if (editingGroups.contains(id)) return "决斗组 " + id + " 仍在编辑中，请先点击创建对战";
        if (group.active()) return "决斗组 " + id + " 已经在进行中";
        if (pendingRestores.containsKey(id)) return "决斗组 " + id + " 正在等待返回，剩余 "
                + Math.max(1, (pendingRestores.get(id).restoreAtTick() - serverTicks + 19) / 20) + " 秒";
        if (group.teamA().isEmpty()) return id + " 缺少 A 队玩家";
        if (group.teamB().isEmpty()) return id + " 缺少 B 队玩家";
        if (group.pointA() == null) return id + " 缺少 A 点位";
        if (group.pointB() == null) return id + " 缺少 B 点位";
        group.setActive(true);
        teleportTeam(group.teamA(), group.pointA(), group);
        teleportTeam(group.teamB(), group.pointB(), group);
        return "已启动 " + id;
    }

    public Map<String, String> startAll() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String id : new ArrayList<>(groups.keySet())) result.put(id, start(id));
        return result;
    }

    /** Cancels an active duel immediately and keeps its A/B point configuration. */
    public boolean stop(String id) {
        SpellDuelGroup group = groups.get(id);
        if (group == null || !group.active()) return false;
        finish(group, null, false);
        return true;
    }

    public int stopAll() {
        int stopped = 0;
        for (String id : new ArrayList<>(groups.keySet())) if (stop(id)) stopped++;
        return stopped;
    }

    private void teleportTeam(Set<UUID> players, SpellDuelGroup.PointLocation point, SpellDuelGroup group) {
        ServerLevel level = level(point.dimension());
        if (level == null) return;
        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            savedStates.computeIfAbsent(group.id(), ignored -> new HashMap<>())
                    .putIfAbsent(uuid, SavedState.capture(player));
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(level, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        }
    }

    public void joinSpectator(String groupId, ServerPlayer player) {
        joinSpectator(groupId, player, null);
    }

    public void joinSpectator(String groupId, ServerPlayer player, GlobalPos observationBlock) {
        SpellDuelGroup group = groups.get(groupId);
        if (group == null || !group.active()) return;
        savedStates.computeIfAbsent(groupId, ignored -> new HashMap<>())
                .putIfAbsent(player.getUUID(), SavedState.capture(player));
        spectators.add(player.getUUID());
        spectatorGroups.put(player.getUUID(), groupId);
        io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player).resetCastingState();
        player.stopUsingItem();
        player.setGameMode(GameType.SPECTATOR);
        if (observationBlock != null) {
            ServerLevel level = server.getLevel(observationBlock.dimension());
            if (level != null) player.teleportTo(level, observationBlock.pos().getX() + 0.5,
                    observationBlock.pos().getY() + 1.1, observationBlock.pos().getZ() + 0.5,
                    player.getYRot(), player.getXRot());
        } else {
            teleportSpectatorToCenter(group, player);
        }
    }

    public boolean leaveSpectator(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String groupId = spectatorGroups.remove(uuid);
        boolean wasSpectator = spectators.remove(uuid);
        if (groupId == null) return wasSpectator;
        Map<UUID, SavedState> states = savedStates.get(groupId);
        if (states != null) {
            SavedState saved = states.remove(uuid);
            if (saved != null) saved.restore(server, uuid);
            if (states.isEmpty()) savedStates.remove(groupId);
        }
        return true;
    }

    private void teleportSpectatorToCenter(SpellDuelGroup group, ServerPlayer player) {
        if (group.pointA() == null || group.pointB() == null) return;
        SpellDuelGroup.PointLocation a = group.pointA();
        SpellDuelGroup.PointLocation b = group.pointB();
        if (!a.dimension().equals(b.dimension())) {
            ServerLevel level = level(a.dimension());
            if (level != null) player.teleportTo(level, a.x(), a.y(), a.z(), a.yaw(), a.pitch());
            return;
        }
        ServerLevel level = level(a.dimension());
        if (level != null) {
            player.teleportTo(level, (a.x() + b.x()) / 2.0, (a.y() + b.y()) / 2.0 + 2.0,
                    (a.z() + b.z()) / 2.0, a.yaw(), a.pitch());
        }
    }

    public void tick() {
        serverTicks++;
        for (var iterator = pendingRestores.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            PendingRestore restore = entry.getValue();
            if (serverTicks >= restore.restoreAtTick()) {
                restore.states().forEach((uuid, state) -> state.restore(server, uuid));
                iterator.remove();
            }
        }
        for (SpellDuelGroup group : new ArrayList<>(groups.values())) {
            if (!group.active()) continue;
            Set<UUID> living = new LinkedHashSet<>();
            group.teamA().forEach(uuid -> addIfLiving(living, uuid));
            group.teamB().forEach(uuid -> addIfLiving(living, uuid));
            living.removeAll(eliminatedPlayers.getOrDefault(group.id(), Set.of()));
            SpellDuelGroup.Team winner = group.winner(living);
            if (winner != null || group.isTeamEliminated(SpellDuelGroup.Team.A, living) || group.isTeamEliminated(SpellDuelGroup.Team.B, living)) finish(group, winner, true);
        }
    }

    private void addIfLiving(Set<UUID> living, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null && player.isAlive() && !player.isDeadOrDying() && player.getHealth() > 0) living.add(uuid);
    }

    public Set<UUID> eliminatedPlayers(String groupId) {
        return Set.copyOf(eliminatedPlayers.getOrDefault(groupId, Set.of()));
    }

    /** Records and neutralizes a duel death before vanilla replaces/respawns the player. */
    public boolean recordPlayerDeath(ServerPlayer player) {
        UUID uuid = player.getUUID();
        for (SpellDuelGroup group : new ArrayList<>(groups.values())) {
            if (!group.active() || !group.contains(uuid)) continue;
            Set<UUID> eliminated = eliminatedPlayers.computeIfAbsent(group.id(), ignored -> new LinkedHashSet<>());
            if (!eliminated.add(uuid)) return true;
            player.stopUsingItem();
            io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player).resetCastingState();
            player.setHealth(player.getMaxHealth());
            player.deathTime = 0;
            player.setInvulnerable(true);
            player.setGameMode(GameType.SPECTATOR);
            SpellDuelNetwork.broadcastEliminationSnapshot(this, group);
            Set<UUID> living = livingPlayers(group);
            living.removeAll(eliminated);
            SpellDuelGroup.Team winner = group.winner(living);
            if (winner != null || group.isTeamEliminated(SpellDuelGroup.Team.A, living)
                    || group.isTeamEliminated(SpellDuelGroup.Team.B, living)) finish(group, winner, true);
            return true;
        }
        return false;
    }

    private void finish(SpellDuelGroup group, SpellDuelGroup.Team winner) {
        finish(group, winner, false);
    }

    private void finish(SpellDuelGroup group, SpellDuelGroup.Team winner, boolean delayRestore) {
        group.setActive(false);
        String winnerText = winner == null ? "平局" : group.livingTeam(winner, livingPlayers(group)).stream()
                .map(uuid -> { ServerPlayer p = server.getPlayerList().getPlayer(uuid); return p == null ? uuid.toString() : p.getGameProfile().getName(); })
                .reduce((a, b) -> a + "、" + b).orElse(winner == SpellDuelGroup.Team.A ? "A组" : "B组");
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "[法术决斗] " + group.id() + " 已结束，获胜玩家：" + winnerText), false);
        // Keep the saved A/B points, but clear the finished duel roster so the
        // same group can be configured and used again immediately.
        group.clearPlayers();
        eliminatedPlayers.remove(group.id());
        Map<UUID, SavedState> states = savedStates.remove(group.id());
        if (states != null) {
            if (delayRestore) {
                protectPendingRestore(states.keySet());
                pendingRestores.put(group.id(), new PendingRestore(states, serverTicks + 100));
            } else {
                for (UUID uuid : new ArrayList<>(states.keySet())) {
                    SavedState saved = states.get(uuid);
                    if (saved != null) saved.restore(server, uuid);
                }
            }
        }
        for (UUID uuid : new ArrayList<>(spectators)) {
            if (group.id().equals(spectatorGroups.get(uuid))) {
                spectators.remove(uuid);
                spectatorGroups.remove(uuid);
            }
        }
    }

    private void protectPendingRestore(Set<UUID> players) {
        for (UUID uuid : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            player.stopUsingItem();
            player.setHealth(player.getMaxHealth());
            player.deathTime = 0;
            player.setInvulnerable(true);
        }
    }

    private Set<UUID> livingPlayers(SpellDuelGroup group) {
        Set<UUID> result = new LinkedHashSet<>();
        group.teamA().forEach(uuid -> addIfLiving(result, uuid));
        group.teamB().forEach(uuid -> addIfLiving(result, uuid));
        result.removeAll(eliminatedPlayers.getOrDefault(group.id(), Set.of()));
        return result;
    }

    private void loadPoints() {
        if (!java.nio.file.Files.exists(pointFile)) return;
        try {
            CompoundTag root;
            try (var input = java.nio.file.Files.newInputStream(pointFile)) {
                root = NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }
            CompoundTag groupsTag = root.getCompound("groups");
            for (String id : groupsTag.getAllKeys()) {
                SpellDuelGroup group = new SpellDuelGroup(id);
                CompoundTag data = groupsTag.getCompound(id);
                if (data.contains("a")) group.setPoint(SpellDuelGroup.Team.A, readPoint(data.getCompound("a")));
                if (data.contains("b")) group.setPoint(SpellDuelGroup.Team.B, readPoint(data.getCompound("b")));
                groups.put(id, group);
            }
        } catch (Exception ignored) { }
    }

    private void savePoints() {
        try {
            java.nio.file.Files.createDirectories(pointFile.getParent());
            CompoundTag root = new CompoundTag(); CompoundTag groupsTag = new CompoundTag();
            groups.forEach((id, group) -> { CompoundTag data = new CompoundTag();
                if (group.pointA() != null) data.put("a", writePoint(group.pointA()));
                if (group.pointB() != null) data.put("b", writePoint(group.pointB())); groupsTag.put(id, data); });
            root.put("groups", groupsTag); NbtIo.writeCompressed(root, pointFile);
        } catch (Exception ignored) { }
    }

    private static CompoundTag writePoint(SpellDuelGroup.PointLocation p) {
        CompoundTag tag = new CompoundTag(); tag.putString("dimension", p.dimension()); tag.putDouble("x", p.x());
        tag.putDouble("y", p.y()); tag.putDouble("z", p.z()); tag.putFloat("yaw", p.yaw()); tag.putFloat("pitch", p.pitch()); return tag;
    }

    private static SpellDuelGroup.PointLocation readPoint(CompoundTag tag) {
        return new SpellDuelGroup.PointLocation(tag.getString("dimension"), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getFloat("yaw"), tag.getFloat("pitch"));
    }

    public void clearPlayers() {
        for (SpellDuelGroup group : groups.values()) {
            if (group.active()) finish(group, null);
            clearSelectionGlow(groupPlayers(group));
            group.clearPlayers();
        }
        pending.clear();
        editingGroups.clear();
    }

    public int clearPoints() {
        int cleared = 0;
        for (SpellDuelGroup group : groups.values()) {
            if (group.active()) continue;
            if (group.pointA() != null || group.pointB() != null) cleared++;
            group.setPoint(SpellDuelGroup.Team.A, null);
            group.setPoint(SpellDuelGroup.Team.B, null);
        }
        catalystGroups.clear();
        pending.values().forEach(selection -> {
            selection.pointA = null;
            selection.pointB = null;
        });
        savePoints();
        return cleared;
    }

    public boolean clearPoints(String id) {
        SpellDuelGroup group = groups.get(id);
        if (group == null) return false;
        if (group.active()) finish(group, null);
        group.setPoint(SpellDuelGroup.Team.A, null);
        group.setPoint(SpellDuelGroup.Team.B, null);
        catalystGroups.entrySet().removeIf(entry -> id.equals(entry.getValue()));
        savePoints();
        return true;
    }

    public boolean clearPlayers(String id) {
        SpellDuelGroup group = groups.get(id);
        if (group == null) return false;
        if (group.active()) finish(group, null);
        clearSelectionGlow(groupPlayers(group));
        group.clearPlayers();
        editingGroups.remove(id);
        pending.values().forEach(selection -> {
            if (id.equals(selection.currentGroup)) selection.currentGroup = null;
        });
        return true;
    }

    public boolean clearConfiguration(String id) {
        SpellDuelGroup group = groups.get(id);
        if (group == null) return false;
        if (group.active()) finish(group, null);
        group.clearPlayers();
        group.setPoint(SpellDuelGroup.Team.A, null);
        group.setPoint(SpellDuelGroup.Team.B, null);
        catalystGroups.entrySet().removeIf(entry -> id.equals(entry.getValue()));
        return true;
    }

    public boolean clearGroup(String id) {
        SpellDuelGroup group = groups.get(id);
        if (group == null) return false;
        if (group.active()) finish(group, null);
        groups.remove(id);
        editingGroups.remove(id);
        catalystGroups.entrySet().removeIf(entry -> id.equals(entry.getValue()));
        pending.values().forEach(selection -> {
            if (id.equals(selection.currentGroup)) selection.currentGroup = null;
        });
        return true;
    }

    public int clearGroups() {
        int cleared = 0;
        for (String id : new ArrayList<>(groups.keySet())) {
            SpellDuelGroup group = groups.get(id);
            if (group != null && group.active()) finish(group, null);
            if (groups.remove(id) != null) cleared++;
            catalystGroups.entrySet().removeIf(entry -> id.equals(entry.getValue()));
        }
        pending.clear();
        editingGroups.clear();
        return cleared;
    }

    private ServerLevel level(String id) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
        return server.getLevel(key);
    }

    private final Map<UUID, String> spectatorGroups = new HashMap<>();

    public static final class PendingSelection {
        private SpellDuelGroup.PointLocation pointA;
        private SpellDuelGroup.PointLocation pointB;
        private String currentGroup;
    }

    private record PendingRestore(Map<UUID, SavedState> states, long restoreAtTick) { }

    private record SavedState(ResourceKey<Level> dimension, Vec3 position, float yRot, float xRot,
                              GameType gameType, boolean invulnerable) {
        static SavedState capture(ServerPlayer player) {
            return new SavedState(player.level().dimension(), player.position(), player.getYRot(), player.getXRot(),
                    player.gameMode.getGameModeForPlayer(), player.isInvulnerable());
        }

        void restore(MinecraftServer server, UUID uuid) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            ServerLevel level = server.getLevel(dimension);
            if (player != null && level != null) {
                player.setGameMode(gameType);
                player.setInvulnerable(invulnerable);
                player.setHealth(player.getMaxHealth());
                player.deathTime = 0;
                player.teleportTo(level, position.x(), position.y(), position.z(), yRot, xRot);
            }
        }
    }
}
