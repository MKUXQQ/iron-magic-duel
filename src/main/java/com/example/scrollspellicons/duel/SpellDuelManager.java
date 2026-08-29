package com.example.scrollspellicons.duel;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.particles.ParticleTypes;
import com.example.scrollspellicons.IronSpellPerformance;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
    /** Participants currently teleported into a live duel, indexed independently of their entity state. */
    private final Map<UUID, String> activeDuelPlayers = new HashMap<>();
    /** Server-thread-only state for the separate player challenge flow. */
    private final Map<Long, ChallengeState> challenges = new LinkedHashMap<>();
    private final Map<UUID, Long> challengeByPlayer = new HashMap<>();
    private final Map<String, Long> reservedChallengeGroups = new HashMap<>();
    private final Map<UUID, InteractionSession> interactionSessions = new HashMap<>();
    private final Set<UUID> internalTeleports = new HashSet<>();
    /** One transient, server-thread-only administrative surround session. */
    private SurroundLockState surroundLock;
    /** Prevents this session's intentional cross-dimension teleports from looking like escapes. */
    private final Set<UUID> internalSurroundTeleports = new HashSet<>();
    private static final int SURROUND_JOIN_ATTEMPTS = 3;
    /** First attempt is next tick; later failures are deliberately bounded at +20 and +40 ticks. */
    private static final long[] SURROUND_RETRY_DELAYS = {1L, 20L, 40L};
    private static final long CHALLENGE_TIMEOUT_TICKS = 15L * 20L;
    private static final long INTERACTION_SESSION_TICKS = 40L;
    private long nextChallengeToken = 1L;
    /** A consumed death is recovered once on the following tick without cloning the player entity. */
    private final Map<UUID, PendingSafeRecovery> pendingSafeRecoveries = new LinkedHashMap<>();
    /** Victorious players remain in the arena briefly, then return to their saved location. */
    private final Map<UUID, PendingWinnerRestore> pendingWinnerRestores = new LinkedHashMap<>();
    private static final long WINNER_RETURN_DELAY_TICKS = 5L * 20L;
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

    /** Starts the shared command/right-click invitation flow. All callers are server-thread-only. */
    public ChallengeResult requestChallenge(ServerPlayer challenger, ServerPlayer target) {
        if (challenger == null || target == null || challenger == target || challenger.getUUID().equals(target.getUUID()))
            return ChallengeResult.failure("不能向自己发起单挑");
        if (!validChallengePlayer(challenger) || !validChallengePlayer(target))
            return ChallengeResult.failure("发起者和目标都必须在线、存活且不在旁观模式");
        if (challengeByPlayer.containsKey(challenger.getUUID()) || challengeByPlayer.containsKey(target.getUUID()))
            return ChallengeResult.failure("双方已有待处理或进行中的单挑");
        if (activeDuelPlayers.containsKey(challenger.getUUID()) || activeDuelPlayers.containsKey(target.getUUID()))
            return ChallengeResult.failure("双方已有进行中的决斗");
        if (availableChallengePoints().isEmpty())
            return ChallengeResult.failure("当前没有空闲的 duel_n 点位");

        long token = nextChallengeToken++;
        ChallengeState state = new ChallengeState(token, challenger.getUUID(), target.getUUID(),
                serverTicks + CHALLENGE_TIMEOUT_TICKS);
        challenges.put(token, state);
        challengeByPlayer.put(challenger.getUUID(), token);
        challengeByPlayer.put(target.getUUID(), token);
        SpellDuelNetwork.sendChallengeInvite(target, token, challenger.getGameProfile().getName(), state.expiresAt);
        return ChallengeResult.success(token, "已向 " + target.getGameProfile().getName() + " 发出单挑邀请（15秒内有效）");
    }

    public void openInteractionMenu(ServerPlayer actor, ServerPlayer target) {
        if (actor == null || target == null || actor == target || !validChallengePlayer(actor)
                || !validChallengePlayer(target) || actor.level() != target.level()
                || actor.distanceToSqr(target) > 64.0D) return;
        long token = nextChallengeToken++;
        interactionSessions.put(actor.getUUID(), new InteractionSession(token, target.getUUID(),
                serverTicks + INTERACTION_SESSION_TICKS));
        SpellDuelNetwork.sendInteractionMenu(actor, token, target.getUUID(), target.getGameProfile().getName());
    }

    public String executeInteraction(ServerPlayer actor, long token, UUID requestedTarget, byte action) {
        InteractionSession session = interactionSessions.get(actor.getUUID());
        if (session == null || session.token != token || !session.target.equals(requestedTarget) || serverTicks > session.expiresAt) {
            interactionSessions.remove(actor.getUUID());
            return "玩家交互已过期";
        }
        interactionSessions.remove(actor.getUUID());
        ServerPlayer target = server.getPlayerList().getPlayer(session.target);
        if (target == null || !validChallengePlayer(target) || !holdsInteractionItem(actor)
                || actor.level() != target.level() || actor.distanceToSqr(target) > 64.0D) return "目标已不可交互";
        if (action == 0) return requestChallenge(actor, target).message;
        if (action == 1) {
            EquippedSpellSnapshot snapshot = equippedSpellSnapshot(target);
            SpellDuelNetwork.sendLearnedSpellView(actor, target.getGameProfile().getName(),
                    snapshot.hasSpellbook(), snapshot.spellIds());
            return null;
        }
        return "无效的交互选项";
    }

    public ChallengeResult acceptChallenge(ServerPlayer target, long token) {
        ChallengeState state = challenges.get(token);
        if (state == null || state.token != token || !state.target.equals(target.getUUID())
                || state.accepted || serverTicks > state.expiresAt)
            return ChallengeResult.failure("单挑邀请已过期或无效");
        ServerPlayer challenger = server.getPlayerList().getPlayer(state.challenger);
        if (!validChallengePlayer(target) || !validChallengePlayer(challenger)) {
            cancelChallenge(state, "单挑双方必须保持在线且存活");
            return ChallengeResult.failure("单挑双方必须保持在线且存活");
        }
        if (availableChallengePoints().isEmpty()) {
            cancelChallenge(state, "没有可用的 duel_n 点位");
            return ChallengeResult.failure("当前没有空闲的 duel_n 点位");
        }
        state.accepted = true;
        state.expiresAt = serverTicks + CHALLENGE_TIMEOUT_TICKS;
        SpellDuelNetwork.sendChallengePoints(challenger, token, availableChallengePoints());
        return ChallengeResult.success(token, "已接受单挑，等待发起者选择点位");
    }

    public ChallengeResult rejectChallenge(ServerPlayer target, long token) {
        ChallengeState state = challenges.get(token);
        if (state == null || state.token != token || !state.target.equals(target.getUUID()))
            return ChallengeResult.failure("单挑邀请已过期或无效");
        cancelChallenge(state, "目标拒绝了单挑");
        return ChallengeResult.success(token, "已拒绝单挑邀请");
    }

    public ChallengeResult cancelChallenge(ServerPlayer challenger, long token) {
        ChallengeState state = challenges.get(token);
        if (state == null || state.token != token || !state.challenger.equals(challenger.getUUID()) || state.active)
            return ChallengeResult.failure("单挑已过期或已开始");
        cancelChallenge(state, "发起者取消了单挑");
        return ChallengeResult.success(token, "已取消单挑");
    }

    public List<ChallengePoint> availableChallengePoints() {
        List<ChallengePoint> result = new ArrayList<>();
        for (SpellDuelGroup group : groups.values()) {
            if (!isNumberedChallengeGroup(group.id()) || group.active() || reservedChallengeGroups.containsKey(group.id())
                    || editingGroups.contains(group.id())
                    || group.pointA() == null || group.pointB() == null) continue;
            if (level(group.pointA().dimension()) == null || level(group.pointB().dimension()) == null) continue;
            result.add(new ChallengePoint(group.id(), group.pointA(), group.pointB()));
        }
        result.sort(java.util.Comparator.comparing(ChallengePoint::id));
        return result;
    }

    private static boolean isNumberedChallengeGroup(String id) {
        if (id == null || !id.startsWith("duel_") || id.length() == "duel_".length()) return false;
        for (int i = "duel_".length(); i < id.length(); i++) {
            if (!Character.isDigit(id.charAt(i))) return false;
        }
        return true;
    }

    public ChallengeResult chooseChallengePoint(ServerPlayer challenger, long token, String groupId) {
        ChallengeState state = challenges.get(token);
        if (state == null || state.token != token || !state.challenger.equals(challenger.getUUID())
                || !state.accepted || serverTicks > state.expiresAt)
            return ChallengeResult.failure("点位选择已过期或无效");
        ServerPlayer target = server.getPlayerList().getPlayer(state.target);
        if (!validChallengePlayer(challenger) || !validChallengePlayer(target)) {
            cancelChallenge(state, "单挑双方状态已失效");
            return ChallengeResult.failure("单挑双方状态已失效");
        }
        SpellDuelGroup group = groups.get(groupId);
        if (group == null || !availableChallengePoints().stream().anyMatch(point -> point.id().equals(groupId)))
            return ChallengeResult.retry(token, "点位已被占用或不可用，请重新选择");
        reservedChallengeGroups.put(groupId, token);
        state.groupId = groupId;
        state.previousTeamA = new LinkedHashSet<>(group.teamA());
        state.previousTeamB = new LinkedHashSet<>(group.teamB());
        group.clearPlayers();
        group.add(challenger.getUUID(), SpellDuelGroup.Team.A);
        group.add(target.getUUID(), SpellDuelGroup.Team.B);
        group.setActive(true);
        state.active = true;
        teleportTeam(group.teamA(), group.pointA(), group);
        teleportTeam(group.teamB(), group.pointB(), group);
        return ChallengeResult.success(token, "已开始 " + groupId + " 单挑");
    }

    public boolean endChallengeForPlayer(UUID player) {
        Long token = challengeByPlayer.get(player);
        if (token == null) return false;
        ChallengeState state = challenges.get(token);
        if (state == null) {
            challengeByPlayer.remove(player);
            return false;
        }
        if (state.active && state.groupId != null) {
            SpellDuelGroup group = groups.get(state.groupId);
            if (group != null) forceEndChallenge(state, group, "单挑已取消");
            else releaseChallenge(state);
        } else cancelChallenge(state, "单挑已取消");
        return true;
    }

    /**
     * Ends only a new challenge cancellation path.  It deliberately bypasses
     * finish(): a cancellation has no winner, so neither participant may enter
     * the normal five-second winner restore delay.
     */
    private void forceEndChallenge(ChallengeState state, SpellDuelGroup group, String reason) {
        if (state == null) return;
        removePendingWinnerRestore(state.challenger, group.id());
        removePendingWinnerRestore(state.target, group.id());
        group.setActive(false);
        finishParticipants(group, Set.of(), null, 0L);
        restoreChallengeRoster(group);
        releaseChallengeForGroup(group.id());
        for (UUID uuid : Set.of(state.challenger, state.target)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(Component.literal("[法术决斗] " + reason));
                SpellDuelNetwork.sendSelectionClose(player);
            }
        }
    }

    /**
     * The admin picker has priority during the short post-victory grace period
     * of a new challenge. Legacy duel restores are deliberately unmarked.
     */
    private boolean forceEndPendingChallengeRestore(UUID player) {
        PendingWinnerRestore pending = pendingWinnerRestores.get(player);
        if (pending == null || pending.challengeToken() == 0L
                || !pendingWinnerRestores.remove(player, pending)) return false;
        restoreSavedState(player, pending.state());
        activeDuelPlayers.remove(player, pending.groupId());
        return true;
    }

    private void removePendingWinnerRestore(UUID player, String groupId) {
        PendingWinnerRestore pending = pendingWinnerRestores.get(player);
        if (pending != null && groupId.equals(pending.groupId())) {
            pendingWinnerRestores.remove(player, pending);
        }
    }

    public void onPlayerInvalidated(UUID player, boolean death) {
        interactionSessions.remove(player);
        Long token = challengeByPlayer.get(player);
        if (token == null) return;
        ChallengeState state = challenges.get(token);
        if (state == null) return;
        if (!state.active || !death) endChallengeForPlayer(player);
    }

    public void onPlayerChangedDimension(UUID player) {
        interactionSessions.remove(player);
        if (internalTeleports.remove(player)) return;
        endChallengeForPlayer(player);
    }

    public void closeChallenges() {
        for (ChallengeState state : new ArrayList<>(challenges.values())) {
            if (state.active && state.groupId != null) {
                SpellDuelGroup group = groups.get(state.groupId);
                if (group != null) forceEndChallenge(state, group, "服务器关闭");
                else releaseChallenge(state);
            } else cancelChallenge(state, "服务器关闭");
        }
        interactionSessions.clear();
        clearSurround();
    }

    /** Creates the one server-wide, transient administrative surround session. */
    public SurroundResult lockSurround(ServerPlayer owner) {
        if (owner == null || server.getPlayerList().getPlayer(owner.getUUID()) != owner || !owner.hasPermissions(2)) {
            return SurroundResult.failure("只有在线管理员可以创建环形禁锢");
        }
        if (surroundLock != null) return SurroundResult.failure("环形禁锢当前已启用");

        SurroundLockState candidate = new SurroundLockState(owner.getUUID());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.getUUID().equals(owner.getUUID())) candidate.anchors.put(player.getUUID(), null);
        }
        if (!reflowSurround(candidate, true)) {
            return SurroundResult.failure("无法为所有玩家找到安全环形落点，未执行锁定");
        }
        surroundLock = candidate;
        return SurroundResult.success("环形禁锢已启用，已锁定 " + candidate.anchors.size() + " 名玩家");
    }

    /** Only the UUID that created the session may release it. */
    public SurroundResult releaseSurround(ServerPlayer requester) {
        if (surroundLock == null) return SurroundResult.failure("当前没有启用的环形禁锢");
        if (requester == null || !surroundLock.owner.equals(requester.getUUID())) {
            return SurroundResult.failure("只有本次环形禁锢的创建者可解除");
        }
        clearSurround();
        return SurroundResult.success("环形禁锢已解除");
    }

    public boolean isSurroundLocked(UUID player) {
        return surroundLock != null && surroundLock.anchors.get(player) != null;
    }

    /** Login entities are admitted from the next server tick, never during login construction. */
    public void onSurroundPlayerLogin(ServerPlayer player) {
        if (surroundLock == null || player == null || surroundLock.owner.equals(player.getUUID())) return;
        requestSurroundReflow(surroundLock, player.getUUID());
    }

    /** A non-owner remains a member through death and is re-anchored after respawn. */
    public void onSurroundPlayerRespawn(ServerPlayer player) {
        if (surroundLock == null || player == null) return;
        if (surroundLock.owner.equals(player.getUUID())) {
            clearSurround();
        } else if (surroundLock.anchors.containsKey(player.getUUID())) {
            requestSurroundReflow(surroundLock, player.getUUID());
        }
    }

    public void onSurroundPlayerInvalidated(UUID player, boolean death) {
        if (surroundLock == null) return;
        if (surroundLock.owner.equals(player)) {
            clearSurround();
            return;
        }
        if (death) return;
        boolean wasAnchored = surroundLock.anchors.remove(player) != null;
        boolean wasPending = surroundLock.pendingAdmissions.remove(player) != null;
        if (wasAnchored || wasPending) {
            surroundLock.reflowRequested = true;
        }
    }

    public void onSurroundPlayerChangedDimension(UUID player) {
        if (surroundLock == null) return;
        if (surroundLock.owner.equals(player)) {
            clearSurround();
            return;
        }
        if (internalSurroundTeleports.remove(player)) return;
        if (surroundLock.anchors.containsKey(player)) requestSurroundReflow(surroundLock, player);
    }

    public void closeSurround() {
        clearSurround();
    }

    private void tickSurround() {
        SurroundLockState state = surroundLock;
        if (state == null) return;
        ServerPlayer owner = server.getPlayerList().getPlayer(state.owner);
        if (owner == null || !owner.isAlive() || owner.isDeadOrDying()) {
            clearSurround();
            return;
        }

        boolean admitted = false;
        for (var iterator = state.pendingAdmissions.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, PendingSurroundAdmission> entry = iterator.next();
            PendingSurroundAdmission pendingAdmission = entry.getValue();
            if (serverTicks < pendingAdmission.dueTick) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || state.owner.equals(entry.getKey())) {
                if (state.anchors.get(entry.getKey()) == null) state.anchors.remove(entry.getKey());
                iterator.remove();
                continue;
            }
            if (!player.isAlive() || player.isDeadOrDying()) {
                entry.setValue(new PendingSurroundAdmission(Long.MAX_VALUE, pendingAdmission.attempt));
                continue;
            }
            state.anchors.putIfAbsent(entry.getKey(), null);
            admitted = true;
            entry.setValue(new PendingSurroundAdmission(Long.MAX_VALUE, pendingAdmission.attempt));
        }
        if (admitted) state.reflowRequested = true;
        if (state.reflowRequested) {
            state.reflowRequested = false;
            if (!reflowSurround(state, false)) state.reflowRequested = false;
        }

        for (Map.Entry<UUID, SurroundAnchor> entry : state.anchors.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            SurroundAnchor anchor = entry.getValue();
            if (player == null || anchor == null || !player.isAlive() || player.isDeadOrDying()) continue;
            if (!player.level().dimension().equals(anchor.dimension)) continue;
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            if (player.position().distanceToSqr(anchor.position) > 0.0001D) {
                teleportSurround(player, anchor, false);
            }
        }
    }

    /** Plans every live member before teleporting any of them, preserving the old layout on failure. */
    private boolean reflowSurround(SurroundLockState state, boolean initial) {
        ServerPlayer owner = server.getPlayerList().getPlayer(state.owner);
        if (owner == null || !owner.isAlive() || owner.isDeadOrDying()) return false;
        List<UUID> members = new ArrayList<>();
        for (UUID id : state.anchors.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && player.isAlive() && !player.isDeadOrDying()) members.add(id);
        }
        members.sort(UUID::compareTo);
        Map<UUID, SurroundAnchor> planned = planSurroundAnchors(owner, members);
        if (planned == null) {
            if (!initial) rescheduleFailedSurroundRequests(state);
            return false;
        }
        Map<UUID, SurroundRollback> rollback = new LinkedHashMap<>();
        try {
            for (UUID id : members) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                SurroundAnchor anchor = planned.get(id);
                if (player == null || anchor == null) throw new IllegalStateException("环形禁锢目标在传送前失效");
                rollback.put(id, new SurroundRollback(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
                teleportSurround(player, anchor, true);
            }
        } catch (RuntimeException exception) {
            rollbackSurround(rollback);
            IronSpellPerformance.LOGGER.warn("[Iron Magic Duel] surround reflow rolled back", exception);
            if (!initial) rescheduleFailedSurroundRequests(state);
            return false;
        }
        for (Map.Entry<UUID, SurroundAnchor> entry : planned.entrySet()) {
            state.anchors.put(entry.getKey(), entry.getValue());
            state.pendingAdmissions.remove(entry.getKey());
        }
        return true;
    }

    private Map<UUID, SurroundAnchor> planSurroundAnchors(ServerPlayer owner, List<UUID> members) {
        Map<UUID, SurroundAnchor> planned = new LinkedHashMap<>();
        int count = members.size();
        if (count == 0) return planned;
        double radius = count == 1 ? 2.0D : Math.max(2.0D, 1.0D / (2.0D * Math.sin(Math.PI / count)));
        Vec3 center = owner.position();
        for (int index = 0; index < count; index++) {
            UUID id = members.get(index);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) return null;
            double angle = (Math.PI * 2.0D * index) / count;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            SurroundAnchor anchor = findSafeSurroundAnchor(owner.serverLevel(), player, center, x, z);
            if (anchor == null) return null;
            planned.put(id, anchor);
        }
        return planned;
    }

    /** Bounded local vertical search: support, empty player box, no liquid and no void placement. */
    private SurroundAnchor findSafeSurroundAnchor(ServerLevel level, ServerPlayer player, Vec3 center, double x, double z) {
        int baseY = (int) Math.floor(center.y);
        int minY = Math.max(level.getMinBuildHeight() + 1, baseY - 16);
        int maxY = Math.min(level.getMaxBuildHeight() - 3, baseY + 16);
        for (int y = maxY; y >= minY; y--) {
            BlockPos feet = BlockPos.containing(x, y, z);
            BlockPos below = feet.below();
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) continue;
            AABB box = player.getBoundingBox().move(x - player.getX(), y - player.getY(), z - player.getZ());
            if (!level.noCollision(player, box)) continue;
            float yaw = (float) (Math.atan2(-(center.x - x), center.z - z) * 180.0D / Math.PI);
            return new SurroundAnchor(level.dimension(), new Vec3(x, y, z), yaw);
        }
        return null;
    }

    private void teleportSurround(ServerPlayer player, SurroundAnchor anchor, boolean faceCenter) {
        ServerLevel level = server.getLevel(anchor.dimension);
        if (level == null) throw new IllegalStateException("环形禁锢维度不可用");
        if (!player.level().dimension().equals(anchor.dimension)) internalSurroundTeleports.add(player.getUUID());
        player.teleportTo(level, anchor.position.x, anchor.position.y, anchor.position.z,
                faceCenter ? anchor.yaw : player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }

    private void rollbackSurround(Map<UUID, SurroundRollback> rollback) {
        for (Map.Entry<UUID, SurroundRollback> entry : rollback.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            SurroundRollback state = entry.getValue();
            ServerLevel level = server.getLevel(state.dimension);
            if (player == null || level == null) continue;
            if (!player.level().dimension().equals(state.dimension)) internalSurroundTeleports.add(entry.getKey());
            player.teleportTo(level, state.position.x, state.position.y, state.position.z, state.yaw, state.pitch);
        }
    }

    private void requestSurroundReflow(SurroundLockState state, UUID player) {
        state.pendingAdmissions.put(player, new PendingSurroundAdmission(serverTicks + SURROUND_RETRY_DELAYS[0], 0));
    }

    /** Retries only members whose requested reflow was actually consumed this tick. */
    private void rescheduleFailedSurroundRequests(SurroundLockState state) {
        for (var iterator = state.pendingAdmissions.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, PendingSurroundAdmission> entry = iterator.next();
            PendingSurroundAdmission pending = entry.getValue();
            if (pending.dueTick != Long.MAX_VALUE) continue;
            int nextAttempt = pending.attempt + 1;
            if (nextAttempt >= SURROUND_JOIN_ATTEMPTS) {
                if (state.anchors.get(entry.getKey()) == null) state.anchors.remove(entry.getKey());
                iterator.remove();
                IronSpellPerformance.LOGGER.warn("[Iron Magic Duel] surround reflow for {} exhausted safe-placement retries", entry.getKey());
            } else {
                entry.setValue(new PendingSurroundAdmission(serverTicks + SURROUND_RETRY_DELAYS[nextAttempt], nextAttempt));
            }
        }
    }

    private void clearSurround() {
        surroundLock = null;
        internalSurroundTeleports.clear();
    }

    private boolean validChallengePlayer(ServerPlayer player) {
        return player != null && server.getPlayerList().getPlayer(player.getUUID()) == player
                && player.isAlive() && !player.isDeadOrDying()
                && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR;
    }

    private boolean holdsInteractionItem(ServerPlayer player) {
        return player.getMainHandItem().is(SpellDuelItems.PLAYER_INTERACTOR.get())
                || player.getOffhandItem().is(SpellDuelItems.PLAYER_INTERACTOR.get());
    }

    private EquippedSpellSnapshot equippedSpellSnapshot(ServerPlayer player) {
        Set<String> spellIds = new LinkedHashSet<>();
        boolean hasSpellbook = false;
        var inventory = CuriosApi.getCuriosInventory(player);
        if (inventory.isEmpty()) return new EquippedSpellSnapshot(false, List.of());
        for (var slot : inventory.orElseThrow().findCurios("spellbook")) {
            var stack = slot.stack();
            if (stack.isEmpty() || !ISpellContainer.isSpellContainer(stack)) continue;
            hasSpellbook = true;
            for (var spellSlot : ISpellContainer.get(stack).getActiveSpells()) {
                var spell = spellSlot.getSpell();
                if (spell != null && spell != SpellRegistry.none()) {
                    spellIds.add(spell.getSpellResource().toString());
                }
            }
        }
        return new EquippedSpellSnapshot(hasSpellbook, spellIds.stream().sorted().toList());
    }

    private void cancelChallenge(ChallengeState state, String reason) {
        UUID challenger = state.challenger;
        UUID target = state.target;
        releaseChallenge(state);
        ServerPlayer challengerPlayer = server.getPlayerList().getPlayer(challenger);
        ServerPlayer targetPlayer = server.getPlayerList().getPlayer(target);
        if (challengerPlayer != null) {
            challengerPlayer.sendSystemMessage(Component.literal("[法术决斗] " + reason));
            SpellDuelNetwork.sendSelectionClose(challengerPlayer);
        }
        if (targetPlayer != null) {
            targetPlayer.sendSystemMessage(Component.literal("[法术决斗] " + reason));
            SpellDuelNetwork.sendSelectionClose(targetPlayer);
        }
    }

    private void releaseChallenge(ChallengeState state) {
        challenges.remove(state.token);
        challengeByPlayer.remove(state.challenger, state.token);
        challengeByPlayer.remove(state.target, state.token);
        if (state.groupId != null) reservedChallengeGroups.remove(state.groupId, state.token);
    }

    /** Starts a fresh, private editing group every time the picker is opened. */
    public String beginEditingGroup(UUID creator) {
        if (!isAdministrator(creator)) return null;
        cancelEditingGroup(creator);
        PendingSelection state = selection(creator);
        // Saved A/B points are durable arena configurations.  Prefer the lowest
        // empty arena first, so opening the player selector fills duel_1,
        // duel_2, duel_3... instead of allocating a new group after unused
        // point-only groups and making the visible numbering jump.
        SpellDuelGroup reusable = groups.values().stream()
                .filter(group -> !group.active() && !editingGroups.contains(group.id())
                        && !reservedChallengeGroups.containsKey(group.id())
                        && group.teamA().isEmpty() && group.teamB().isEmpty()
                        && group.pointA() != null && group.pointB() != null)
                .sorted(java.util.Comparator.comparingInt(group -> groupOrder(group.id())))
                .findFirst().orElse(null);
        if (reusable == null) {
            reusable = groups.values().stream()
                    .filter(group -> !group.active() && !editingGroups.contains(group.id())
                            && !reservedChallengeGroups.containsKey(group.id())
                            && group.teamA().isEmpty() && group.teamB().isEmpty())
                    .sorted(java.util.Comparator.comparingInt(group -> groupOrder(group.id())))
                    .findFirst().orElse(null);
        }
        String id = reusable == null ? nextGroupId() : reusable.id();
        if (reusable == null) groups.put(id, new SpellDuelGroup(id));
        editingGroups.add(id);
        state.currentGroup = id;
        return id;
    }

    public boolean addPlayerToEditingGroup(UUID creator, UUID target, SpellDuelGroup.Team team) {
        if (!isAdministrator(creator)) return false;
        PendingSelection state = selection(creator);
        SpellDuelGroup group = groups.get(state.currentGroup);
        if (group == null || !editingGroups.contains(group.id()) || group.active()) return false;
        if (server.getPlayerList().getPlayer(target) == null) return false;
        // The admin picker may reclaim a participant from the new challenge
        // flow, but must not stop an unrelated legacy group here.
        endChallengeForPlayer(target);
        forceEndPendingChallengeRestore(target);
        String selectedGroup = selectedGroup(target);
        if (selectedGroup != null && !selectedGroup.equals(group.id())) {
            SpellDuelGroup previous = groups.get(selectedGroup);
            if (previous == null) return false;
            if (previous.active()) finish(previous, null);
            previous.add(target, null);
            clearSelectionGlow(Set.of(target));
        }
        group.add(target, team);
        ServerPlayer player = server.getPlayerList().getPlayer(target);
        if (player != null) setSelectionGlow(player);
        return true;
    }

    /** Removes a player from whichever non-active group currently owns them. */
    public boolean cancelSelectedPlayer(UUID creator, UUID target) {
        if (!isAdministrator(creator)) return false;
        String id = selectedGroup(target);
        SpellDuelGroup group = id == null ? null : groups.get(id);
        if (group == null || group.active()) return false;
        boolean removed = group.add(target, null);
        if (removed) clearSelectionGlow(Set.of(target));
        return removed;
    }

    public boolean finalizeEditingGroup(UUID creator) {
        if (!isAdministrator(creator)) return false;
        PendingSelection state = selection(creator);
        if (state.currentGroup == null || !editingGroups.remove(state.currentGroup)) return false;
        SpellDuelGroup group = groups.get(state.currentGroup);
        if (group != null) clearSelectionGlow(groupPlayers(group));
        return true;
    }

    /** Esc cancels the current edit: remove its temporary group and every selected player. */
    public boolean cancelEditingGroup(UUID creator) {
        if (!isAdministrator(creator)) return false;
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

    private boolean isAdministrator(UUID player) {
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player);
        return serverPlayer != null && serverPlayer.hasPermissions(2);
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
        if (reservedChallengeGroups.containsKey(id)) return "决斗组 " + id + " 已被单挑预占";
        if (editingGroups.contains(id)) return "决斗组 " + id + " 仍在编辑中，请先点击创建对战";
        if (group.active()) return "决斗组 " + id + " 已经在进行中";
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
        finish(group, null);
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
            activeDuelPlayers.put(uuid, group.id());
            player.setGameMode(GameType.SURVIVAL);
            if (!player.level().dimension().equals(level.dimension())) internalTeleports.add(uuid);
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
        tickSurround();
        for (ChallengeState state : new ArrayList<>(challenges.values())) {
            if (!state.active && serverTicks > state.expiresAt) cancelChallenge(state, "单挑邀请已超时");
        }
        processSafeRecoveries();
        processWinnerRestores();
        for (SpellDuelGroup group : new ArrayList<>(groups.values())) {
            if (!group.active()) continue;
            Set<UUID> living = new LinkedHashSet<>();
            group.teamA().forEach(uuid -> addIfLiving(living, uuid));
            group.teamB().forEach(uuid -> addIfLiving(living, uuid));
            living.removeAll(eliminatedPlayers.getOrDefault(group.id(), Set.of()));
            SpellDuelGroup.Team winner = group.winner(living);
            if (winner != null || group.isTeamEliminated(SpellDuelGroup.Team.A, living) || group.isTeamEliminated(SpellDuelGroup.Team.B, living)) finish(group, winner);
        }
    }

    private void addIfLiving(Set<UUID> living, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null && player.isAlive() && !player.isDeadOrDying() && player.getHealth() > 0) living.add(uuid);
    }

    public Set<UUID> eliminatedPlayers(String groupId) {
        return Set.copyOf(eliminatedPlayers.getOrDefault(groupId, Set.of()));
    }

    /**
     * Records a participant defeat without changing the vanilla death or respawn flow.
     */
    public boolean recordDuelDeathAndScheduleRecovery(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String groupId = activeDuelPlayers.get(uuid);
        SpellDuelGroup group = groupId == null ? null : groups.get(groupId);
        // The cache is only an optimisation. A live group remains authoritative,
        // so a real duel death can never fall through to vanilla just because a
        // participant was not added to the cache at an earlier teleport.
        if (group == null || !group.active() || !group.contains(uuid)) {
            group = groups.values().stream()
                    .filter(candidate -> candidate.active() && candidate.contains(uuid))
                    .findFirst().orElse(null);
            if (group == null) return false;
            activeDuelPlayers.put(uuid, group.id());
        }

        Set<UUID> eliminated = eliminatedPlayers.computeIfAbsent(group.id(), ignored -> new LinkedHashSet<>());
        if (!eliminated.add(uuid)) return true;
        player.stopUsingItem();
        io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player).resetCastingState();
        SpellDuelNetwork.broadcastEliminationSnapshot(this, group);
        Set<UUID> living = livingPlayers(group);
        living.removeAll(eliminated);
        SpellDuelGroup.Team winner = group.winner(living);
        if (winner != null || group.isTeamEliminated(SpellDuelGroup.Team.A, living)
                || group.isTeamEliminated(SpellDuelGroup.Team.B, living)) finishAfterVanillaDeath(group, winner, uuid);
        return true;
    }

    /**
     * Direct /kill bypasses LivingDeathEvent.  It must be intercepted for every
     * player because the affected modpack's normal /kill respawn loop is not
     * limited to active duel participants.
     */
    /**
     * Single safety entrance for every player death.  This modpack repeatedly
     * clones a player after normal death, so non-duel deaths are recovered at
     * that player's own spawn while duel deaths keep their special outcome.
     */
    public void handleAnyPlayerDeath(ServerPlayer player) {
        boolean duelDeath = recordDuelDeathAndScheduleRecovery(player);
        if (!duelDeath) scheduleSafeRecovery(null, player, RecoveryDestination.PLAYER_SPAWN);
        // This method is called from the head of ServerPlayer.die. Do not defer
        // recovery to the next tick: a single synchronized health=0 frame is
        // enough for this modpack to start its external clone-respawn loop.
        processSafeRecoveries();
        IronSpellPerformance.LOGGER.info("[Iron Magic Duel] intercepted player death for {} (duel={})", player.getGameProfile().getName(), duelDeath);
    }

    private void scheduleSafeRecovery(String groupId, ServerPlayer player, RecoveryDestination destination) {
        pendingSafeRecoveries.putIfAbsent(player.getUUID(), new PendingSafeRecovery(groupId, player, destination));
    }

    private void processSafeRecoveries() {
        for (var iterator = pendingSafeRecoveries.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, PendingSafeRecovery> entry = iterator.next();
            UUID uuid = entry.getKey();
            PendingSafeRecovery pendingRecovery = entry.getValue();
            ServerPlayer player = pendingRecovery.player();
            // The player remains the same entity; no PlayerList.respawn and no PlayerEvent.Clone.
            if (server.getPlayerList().getPlayer(uuid) == player && !player.isRemoved()) {
                player.setHealth(player.getMaxHealth());
                player.setAbsorptionAmount(0.0F);
                player.clearFire();
                player.stopUsingItem();
                io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player).resetCastingState();
                if (pendingRecovery.destination() == RecoveryDestination.WORLD_SPAWN) {
                    ServerLevel overworld = server.overworld();
                    var spawn = overworld.getSharedSpawnPos();
                    player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                            overworld.getSharedSpawnAngle(), 0.0F);
                } else {
                    DimensionTransition target = player.findRespawnPositionAndUseSpawnBlock(false, DimensionTransition.DO_NOTHING);
                    player.teleportTo(target.newLevel(), target.pos().x, target.pos().y, target.pos().z,
                            target.yRot(), target.xRot());
                }
            }
            if (pendingRecovery.groupId() != null) activeDuelPlayers.remove(uuid, pendingRecovery.groupId());
            iterator.remove();
        }
    }

    private void processWinnerRestores() {
        for (var iterator = pendingWinnerRestores.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, PendingWinnerRestore> entry = iterator.next();
            PendingWinnerRestore restore = entry.getValue();
            if (serverTicks >= restore.restoreAtTick()) {
                restoreSavedState(entry.getKey(), restore.state());
                activeDuelPlayers.remove(entry.getKey(), restore.groupId());
                iterator.remove();
            }
        }
    }

    private void finish(SpellDuelGroup group, SpellDuelGroup.Team winner) {
        group.setActive(false);
        Set<UUID> winners = winningPlayers(group, winner);
        ChallengeState challenge = challengeForGroup(group.id());
        String winnerText = winner == null ? "平局" : group.livingTeam(winner, livingPlayers(group)).stream()
                .map(uuid -> { ServerPlayer p = server.getPlayerList().getPlayer(uuid); return p == null ? uuid.toString() : p.getGameProfile().getName(); })
                .reduce((a, b) -> a + "、" + b).orElse(winner == SpellDuelGroup.Team.A ? "A组" : "B组");
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "[法术决斗] " + group.id() + " 已结束，获胜玩家：" + winnerText), false);
        // Keep the saved A/B points, but clear the finished duel roster so the
        // same group can be configured and used again immediately.
        finishParticipants(group, winners, null, challenge == null ? 0L : challenge.token);
        restoreChallengeRoster(group);
        releaseChallengeForGroup(group.id());
    }

    /** Ends the duel while the defeated participant completes one normal vanilla respawn. */
    private void finishAfterVanillaDeath(SpellDuelGroup group, SpellDuelGroup.Team winner, UUID deadPlayer) {
        group.setActive(false);
        Set<UUID> winners = winningPlayers(group, winner);
        ChallengeState challenge = challengeForGroup(group.id());
        String winnerText = winner == null ? "平局" : group.livingTeam(winner, livingPlayers(group)).stream()
                .map(uuid -> { ServerPlayer p = server.getPlayerList().getPlayer(uuid); return p == null ? uuid.toString() : p.getGameProfile().getName(); })
                .reduce((a, b) -> a + "、" + b).orElse(winner == SpellDuelGroup.Team.A ? "A组" : "B组");
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "[法术决斗] " + group.id() + " 已结束，获胜玩家：" + winnerText), false);
        // The defeated player stays entirely in vanilla's death/respawn flow.
        // In particular, never heal, clone, or teleport this entity here: doing
        // so was the source of the repeated death/respawn loop.
        finishParticipants(group, winners, deadPlayer, challenge == null ? 0L : challenge.token);
        restoreChallengeRoster(group);
        releaseChallengeForGroup(group.id());
    }

    private void restoreChallengeRoster(SpellDuelGroup group) {
        for (ChallengeState state : new ArrayList<>(challenges.values())) {
            if (group.id().equals(state.groupId) && state.previousTeamA != null) {
                group.restorePlayers(state.previousTeamA, state.previousTeamB);
                state.previousTeamA = null;
                state.previousTeamB = null;
            }
        }
    }

    /** Restores a saved player without treating the intentional teleport as a
     * player-initiated challenge exit. */
    private void restoreSavedState(UUID uuid, SavedState saved) {
        if (saved == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        ServerLevel destination = server.getLevel(saved.dimension());
        if (player != null && destination != null
                && !player.level().dimension().equals(destination.dimension())) {
            internalTeleports.add(uuid);
        }
        saved.restore(server, uuid);
        internalTeleports.remove(uuid);
    }

    private void releaseChallengeForGroup(String groupId) {
        for (ChallengeState state : new ArrayList<>(challenges.values())) {
            if (groupId.equals(state.groupId)) releaseChallenge(state);
        }
    }

    private Set<UUID> winningPlayers(SpellDuelGroup group, SpellDuelGroup.Team winner) {
        if (winner == null) return Set.of();
        return new LinkedHashSet<>(winner == SpellDuelGroup.Team.A ? group.teamA() : group.teamB());
    }

    private ChallengeState challengeForGroup(String groupId) {
        for (ChallengeState state : challenges.values()) {
            if (state.active && groupId.equals(state.groupId)) return state;
        }
        return null;
    }

    /** Restores losers immediately, keeps living winners in-arena for exactly five seconds. */
    private void finishParticipants(SpellDuelGroup group, Set<UUID> winners, UUID deadPlayer,
                                    long challengeToken) {
        group.clearPlayers();
        eliminatedPlayers.remove(group.id());
        Map<UUID, SavedState> states = savedStates.remove(group.id());
        if (states != null) {
            for (Map.Entry<UUID, SavedState> entry : states.entrySet()) {
                UUID uuid = entry.getKey();
                SavedState saved = entry.getValue();
                if (deadPlayer != null && deadPlayer.equals(uuid)) {
                    activeDuelPlayers.remove(uuid, group.id());
                    continue;
                }
                if (winners.contains(uuid)) {
                    ServerPlayer winner = server.getPlayerList().getPlayer(uuid);
                    if (winner != null && winner.isAlive() && !winner.isDeadOrDying()) {
                        // Prevent a second death while the post-victory timer is
                        // visible. SavedState restores the original flag later.
                        winner.setInvulnerable(true);
                        pendingWinnerRestores.put(uuid,
                                new PendingWinnerRestore(group.id(), saved, serverTicks + WINNER_RETURN_DELAY_TICKS,
                                        challengeToken));
                        continue;
                    }
                }
                restoreSavedState(uuid, saved);
                activeDuelPlayers.remove(uuid, group.id());
            }
        }
        for (UUID uuid : new ArrayList<>(spectators)) {
            if (group.id().equals(spectatorGroups.get(uuid))) {
                spectators.remove(uuid);
                spectatorGroups.remove(uuid);
            }
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

    public record ChallengeResult(boolean accepted, long token, String message) {
        static ChallengeResult success(long token, String message) { return new ChallengeResult(true, token, message); }
        static ChallengeResult failure(String message) { return new ChallengeResult(false, 0L, message); }
        static ChallengeResult retry(long token, String message) { return new ChallengeResult(false, token, message); }
    }

    public record SurroundResult(boolean accepted, String message) {
        static SurroundResult success(String message) { return new SurroundResult(true, message); }
        static SurroundResult failure(String message) { return new SurroundResult(false, message); }
    }

    public record ChallengePoint(String id, SpellDuelGroup.PointLocation pointA,
                                 SpellDuelGroup.PointLocation pointB) {}

    private static final class ChallengeState {
        private final long token;
        private final UUID challenger;
        private final UUID target;
        private long expiresAt;
        private boolean accepted;
        private boolean active;
        private String groupId;
        private Set<UUID> previousTeamA;
        private Set<UUID> previousTeamB;

        private ChallengeState(long token, UUID challenger, UUID target, long expiresAt) {
            this.token = token;
            this.challenger = challenger;
            this.target = target;
            this.expiresAt = expiresAt;
        }
    }

    private record InteractionSession(long token, UUID target, long expiresAt) {}

    private static final class SurroundLockState {
        private final UUID owner;
        private final Map<UUID, SurroundAnchor> anchors = new LinkedHashMap<>();
        private final Map<UUID, PendingSurroundAdmission> pendingAdmissions = new LinkedHashMap<>();
        private boolean reflowRequested;

        private SurroundLockState(UUID owner) {
            this.owner = owner;
        }
    }

    private record SurroundAnchor(ResourceKey<Level> dimension, Vec3 position, float yaw) {}

    private record SurroundRollback(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {}

    private record PendingSurroundAdmission(long dueTick, int attempt) {}

    private record EquippedSpellSnapshot(boolean hasSpellbook, List<String> spellIds) {}

    private enum RecoveryDestination { PLAYER_SPAWN, WORLD_SPAWN }

    private record PendingSafeRecovery(String groupId, ServerPlayer player, RecoveryDestination destination) {}

    private record PendingWinnerRestore(String groupId, SavedState state, long restoreAtTick, long challengeToken) {}

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
