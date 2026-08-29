package com.example.scrollspellicons.duel;

import com.example.scrollspellicons.IronSpellPerformance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.capabilities.magic.CooldownInstance;
import top.theillusivec4.curios.api.CuriosApi;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpellDuelNetwork {
    private static final CustomPacketPayload.Type<DisplayPayload> DISPLAY_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "display"));
    private static final StreamCodec<RegistryFriendlyByteBuf, DisplayPayload> DISPLAY_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.enabled),
            buf -> new DisplayPayload(buf.readBoolean()));
    private static final CustomPacketPayload.Type<HudPositionPayload> HUD_POSITION_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "hud_position"));
    private static final StreamCodec<RegistryFriendlyByteBuf, HudPositionPayload> HUD_POSITION_CODEC = StreamCodec.of(
            (buf, payload) -> { buf.writeVarInt(payload.x); buf.writeVarInt(payload.y); },
            buf -> new HudPositionPayload(buf.readVarInt(), buf.readVarInt()));
    private static final CustomPacketPayload.Type<SnapshotPayload> SNAPSHOT_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "snapshot"));
    private static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPayload> SNAPSHOT_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.groupId, 64);
                buf.writeVarInt(payload.entries.size());
                for (SnapshotEntry entry : payload.entries) {
                    buf.writeVarInt(entry.team);
                    buf.writeUtf(entry.name, 64);
                    buf.writeFloat(entry.health);
                    buf.writeFloat(entry.maxHealth);
                    buf.writeFloat(entry.mana);
                    buf.writeFloat(entry.maxMana);
                    buf.writeUtf(entry.spells, 2048);
                    buf.writeUtf(entry.casting, 256);
                    buf.writeUtf(entry.cooldowns, 2048);
                }
            },
            buf -> {
                String groupId = buf.readUtf(64);
                int count = buf.readVarInt();
                List<SnapshotEntry> entries = new ArrayList<>();
                for (int i = 0; i < count; i++) entries.add(new SnapshotEntry(buf.readVarInt(), buf.readUtf(64),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                        buf.readUtf(2048), buf.readUtf(256), buf.readUtf(2048)));
                return new SnapshotPayload(groupId, entries);
            });
    private static final CustomPacketPayload.Type<CooldownPayload> COOLDOWN_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "cooldowns"));
    private static final StreamCodec<RegistryFriendlyByteBuf, CooldownPayload> COOLDOWN_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entries.size());
                for (CooldownEntry entry : payload.entries) {
                    buf.writeUUID(entry.playerId);
                    buf.writeVarInt(entry.cooldowns.size());
                    for (var cooldown : entry.cooldowns.entrySet()) {
                        buf.writeUtf(cooldown.getKey(), 256);
                        buf.writeVarInt(cooldown.getValue());
                    }
                }
            },
            buf -> {
                List<CooldownEntry> entries = new ArrayList<>();
                int players = buf.readVarInt();
                for (int player = 0; player < players; player++) {
                    UUID playerId = buf.readUUID();
                    Map<String, Integer> cooldowns = new LinkedHashMap<>();
                    int cooldownCount = buf.readVarInt();
                    for (int cooldown = 0; cooldown < cooldownCount; cooldown++) {
                        cooldowns.put(buf.readUtf(256), buf.readVarInt());
                    }
                    entries.add(new CooldownEntry(playerId, cooldowns));
                }
                return new CooldownPayload(entries);
            });
    private static final CustomPacketPayload.Type<PointMarkerPayload> POINT_MARKER_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "point_markers"));
    private static final StreamCodec<RegistryFriendlyByteBuf, PointMarkerPayload> POINT_MARKER_CODEC = StreamCodec.of(
            (buf, payload) -> { buf.writeVarInt(payload.markers.size()); for (PointMarker marker : payload.markers) { buf.writeUtf(marker.label, 64); buf.writeDouble(marker.x); buf.writeDouble(marker.y); buf.writeDouble(marker.z); } },
            buf -> { List<PointMarker> markers = new ArrayList<>(); for (int i = buf.readVarInt(); i > 0; i--) markers.add(new PointMarker(buf.readUtf(64), buf.readDouble(), buf.readDouble(), buf.readDouble())); return new PointMarkerPayload(markers); });
    private static final CustomPacketPayload.Type<PlayerSelectionPayload> PLAYER_SELECTION_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "player_selection"));
    private static final StreamCodec<RegistryFriendlyByteBuf, PlayerSelectionPayload> PLAYER_SELECTION_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.players.size());
                for (PlayerChoice player : payload.players) {
                    buf.writeUUID(player.id);
                    buf.writeUtf(player.name, 64);
                    buf.writeUtf(player.selectedGroup, 64);
                    buf.writeByte(player.ownTeam);
                }
            },
            buf -> {
                List<PlayerChoice> players = new ArrayList<>();
                for (int i = 0, count = buf.readVarInt(); i < count; i++) {
                    players.add(new PlayerChoice(buf.readUUID(), buf.readUtf(64), buf.readUtf(64), buf.readByte()));
                }
                return new PlayerSelectionPayload(players);
            });
    private static final CustomPacketPayload.Type<SelectPlayerPayload> SELECT_PLAYER_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "select_player"));
    private static final StreamCodec<RegistryFriendlyByteBuf, SelectPlayerPayload> SELECT_PLAYER_CODEC = StreamCodec.of(
            (buf, payload) -> { buf.writeUUID(payload.target); buf.writeByte(payload.team); },
            buf -> new SelectPlayerPayload(buf.readUUID(), buf.readByte()));
    private static final CustomPacketPayload.Type<CreateSelectionPayload> CREATE_SELECTION_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "create_selection"));
    private static final StreamCodec<RegistryFriendlyByteBuf, CreateSelectionPayload> CREATE_SELECTION_CODEC = StreamCodec.unit(new CreateSelectionPayload());
    private static final CustomPacketPayload.Type<CancelSelectionPayload> CANCEL_SELECTION_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "cancel_selection"));
    private static final StreamCodec<RegistryFriendlyByteBuf, CancelSelectionPayload> CANCEL_SELECTION_CODEC = StreamCodec.unit(new CancelSelectionPayload());
    private static final CustomPacketPayload.Type<SelectionClosePayload> SELECTION_CLOSE_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "selection_close"));
    private static final StreamCodec<RegistryFriendlyByteBuf, SelectionClosePayload> SELECTION_CLOSE_CODEC = StreamCodec.unit(new SelectionClosePayload());
    private static final CustomPacketPayload.Type<InteractionMenuPayload> INTERACTION_MENU_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "interaction_menu"));
    private static final StreamCodec<RegistryFriendlyByteBuf, InteractionMenuPayload> INTERACTION_MENU_CODEC = StreamCodec.of(
            (buf, payload) -> { buf.writeLong(payload.token); buf.writeUUID(payload.target); buf.writeUtf(payload.targetName, 64); },
            buf -> new InteractionMenuPayload(buf.readLong(), buf.readUUID(), buf.readUtf(64)));
    private static final CustomPacketPayload.Type<InteractionActionPayload> INTERACTION_ACTION_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "interaction_action"));
    private static final StreamCodec<RegistryFriendlyByteBuf, InteractionActionPayload> INTERACTION_ACTION_CODEC = StreamCodec.of(
            (buf, payload) -> { buf.writeLong(payload.token); buf.writeUUID(payload.target); buf.writeByte(payload.action); },
            buf -> new InteractionActionPayload(buf.readLong(), buf.readUUID(), buf.readByte()));
    private static final CustomPacketPayload.Type<ChallengeInvitePayload> CHALLENGE_INVITE_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "challenge_invite"));
    private static final StreamCodec<RegistryFriendlyByteBuf, ChallengeInvitePayload> CHALLENGE_INVITE_CODEC = StreamCodec.of(
            (buf, payload) -> { buf.writeLong(payload.token); buf.writeUtf(payload.challengerName, 64); buf.writeLong(payload.expiresAt); },
            buf -> new ChallengeInvitePayload(buf.readLong(), buf.readUtf(64), buf.readLong()));
    private static final CustomPacketPayload.Type<ChallengeReplyPayload> CHALLENGE_REPLY_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "challenge_reply"));
    private static final StreamCodec<RegistryFriendlyByteBuf, ChallengeReplyPayload> CHALLENGE_REPLY_CODEC = StreamCodec.of(
            (buf, payload) -> { buf.writeLong(payload.token); buf.writeBoolean(payload.accepted); },
            buf -> new ChallengeReplyPayload(buf.readLong(), buf.readBoolean()));
    private static final CustomPacketPayload.Type<ChallengePointsPayload> CHALLENGE_POINTS_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "challenge_points"));
    private static final StreamCodec<RegistryFriendlyByteBuf, ChallengePointsPayload> CHALLENGE_POINTS_CODEC = StreamCodec.of(
            (buf, payload) -> { buf.writeLong(payload.token); buf.writeVarInt(payload.points.size()); for (ChallengePoint point : payload.points) buf.writeUtf(point.id, 64); },
            buf -> { long token = buf.readLong(); List<ChallengePoint> points = new ArrayList<>(); for (int i = 0, count = buf.readVarInt(); i < count; i++) points.add(new ChallengePoint(buf.readUtf(64))); return new ChallengePointsPayload(token, points); });
    private static final CustomPacketPayload.Type<ChallengePointChoicePayload> CHALLENGE_POINT_CHOICE_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "challenge_point_choice"));
    private static final StreamCodec<RegistryFriendlyByteBuf, ChallengePointChoicePayload> CHALLENGE_POINT_CHOICE_CODEC = StreamCodec.of(
            (buf, payload) -> { buf.writeLong(payload.token); buf.writeUtf(payload.groupId, 64); },
            buf -> new ChallengePointChoicePayload(buf.readLong(), buf.readUtf(64)));
    private static final CustomPacketPayload.Type<ChallengeCancelPayload> CHALLENGE_CANCEL_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "challenge_cancel"));
    private static final StreamCodec<RegistryFriendlyByteBuf, ChallengeCancelPayload> CHALLENGE_CANCEL_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeLong(payload.token), buf -> new ChallengeCancelPayload(buf.readLong()));
    private static final CustomPacketPayload.Type<LearnedSpellViewPayload> LEARNED_SPELL_VIEW_TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(IronSpellPerformance.MOD_ID, "learned_spell_view"));
    private static final StreamCodec<RegistryFriendlyByteBuf, LearnedSpellViewPayload> LEARNED_SPELL_VIEW_CODEC = StreamCodec.of(
            (buf, payload) -> { buf.writeUtf(payload.targetName, 64); buf.writeBoolean(payload.hasSpellbook); buf.writeVarInt(payload.spellIds.size()); for (String id : payload.spellIds) buf.writeUtf(id, 256); },
            buf -> { String name = buf.readUtf(64); boolean hasSpellbook = buf.readBoolean(); List<String> ids = new ArrayList<>(); for (int i = 0, count = buf.readVarInt(); i < count; i++) ids.add(buf.readUtf(256)); return new LearnedSpellViewPayload(name, hasSpellbook, ids); });

    private SpellDuelNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(DISPLAY_TYPE, DISPLAY_CODEC, SpellDuelNetwork::handleDisplay);
        registrar.playToClient(HUD_POSITION_TYPE, HUD_POSITION_CODEC, SpellDuelNetwork::handleHudPosition);
        registrar.playToClient(SNAPSHOT_TYPE, SNAPSHOT_CODEC, SpellDuelNetwork::handleSnapshot);
        registrar.playToClient(COOLDOWN_TYPE, COOLDOWN_CODEC, SpellDuelNetwork::handleCooldowns);
        registrar.playToClient(POINT_MARKER_TYPE, POINT_MARKER_CODEC, SpellDuelNetwork::handlePointMarkers);
        registrar.playToClient(PLAYER_SELECTION_TYPE, PLAYER_SELECTION_CODEC, SpellDuelNetwork::handlePlayerSelection);
        registrar.playToClient(SELECTION_CLOSE_TYPE, SELECTION_CLOSE_CODEC, SpellDuelNetwork::handleSelectionClose);
        registrar.playToClient(INTERACTION_MENU_TYPE, INTERACTION_MENU_CODEC, SpellDuelNetwork::handleInteractionMenu);
        registrar.playToClient(CHALLENGE_INVITE_TYPE, CHALLENGE_INVITE_CODEC, SpellDuelNetwork::handleChallengeInvite);
        registrar.playToClient(CHALLENGE_POINTS_TYPE, CHALLENGE_POINTS_CODEC, SpellDuelNetwork::handleChallengePoints);
        registrar.playToClient(LEARNED_SPELL_VIEW_TYPE, LEARNED_SPELL_VIEW_CODEC, SpellDuelNetwork::handleLearnedSpellView);
        registrar.playToServer(SELECT_PLAYER_TYPE, SELECT_PLAYER_CODEC, SpellDuelNetwork::handleSelectPlayer);
        registrar.playToServer(CREATE_SELECTION_TYPE, CREATE_SELECTION_CODEC, SpellDuelNetwork::handleCreateSelection);
        registrar.playToServer(CANCEL_SELECTION_TYPE, CANCEL_SELECTION_CODEC, SpellDuelNetwork::handleCancelSelection);
        registrar.playToServer(INTERACTION_ACTION_TYPE, INTERACTION_ACTION_CODEC, SpellDuelNetwork::handleInteractionAction);
        registrar.playToServer(CHALLENGE_REPLY_TYPE, CHALLENGE_REPLY_CODEC, SpellDuelNetwork::handleChallengeReply);
        registrar.playToServer(CHALLENGE_POINT_CHOICE_TYPE, CHALLENGE_POINT_CHOICE_CODEC, SpellDuelNetwork::handleChallengePointChoice);
        registrar.playToServer(CHALLENGE_CANCEL_TYPE, CHALLENGE_CANCEL_CODEC, SpellDuelNetwork::handleChallengeCancel);
    }

    public static void broadcastDisplay(net.minecraft.server.MinecraftServer server, boolean enabled) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, new DisplayPayload(enabled));
        }
    }

    public static void sendDisplay(ServerPlayer player, boolean enabled) {
        PacketDistributor.sendToPlayer(player, new DisplayPayload(enabled));
    }

    public static void sendHudPosition(ServerPlayer player, int x, int y) {
        PacketDistributor.sendToPlayer(player, new HudPositionPayload(x, y));
    }
    public static void sendPointMarkers(ServerPlayer player, List<PointMarker> markers) { PacketDistributor.sendToPlayer(player, new PointMarkerPayload(markers)); }

    public static void sendInteractionMenu(ServerPlayer player, long token, UUID target, String targetName) {
        PacketDistributor.sendToPlayer(player, new InteractionMenuPayload(token, target, targetName));
    }

    public static void sendChallengeInvite(ServerPlayer target, long token, String challengerName, long expiresAt) {
        PacketDistributor.sendToPlayer(target, new ChallengeInvitePayload(token, challengerName, expiresAt));
    }

    public static void sendChallengePoints(ServerPlayer challenger, long token, List<SpellDuelManager.ChallengePoint> points) {
        List<ChallengePoint> payloadPoints = new ArrayList<>();
        for (SpellDuelManager.ChallengePoint point : points) payloadPoints.add(new ChallengePoint(point.id()));
        PacketDistributor.sendToPlayer(challenger, new ChallengePointsPayload(token, payloadPoints));
    }

    public static void sendLearnedSpellView(ServerPlayer viewer, String targetName, boolean hasSpellbook, List<String> spellIds) {
        PacketDistributor.sendToPlayer(viewer, new LearnedSpellViewPayload(targetName, hasSpellbook, spellIds));
    }

    private static void sendDuelClose(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SelectionClosePayload());
    }

    private static void handleDisplay(DisplayPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.example.scrollspellicons.client.SpellDuelClientState.setDisplayEnabled(payload.enabled));
    }

    private static void handleHudPosition(HudPositionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.example.scrollspellicons.client.SpellDuelClientState.setHudPosition(payload.x, payload.y));
    }

    private static void handleSnapshot(SnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.example.scrollspellicons.client.SpellDuelClientState.setSnapshot(payload.groupId, payload.entries));
    }


    private static void handleCooldowns(CooldownPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.example.scrollspellicons.client.SpellDuelClientState.setCooldowns(payload.entries));
    }
    private static void handlePointMarkers(PointMarkerPayload payload, IPayloadContext context) { context.enqueueWork(() -> com.example.scrollspellicons.client.SpellDuelClientState.setPointMarkers(payload.markers)); }

    public static void sendPlayerSelection(ServerPlayer player) {
        SpellDuelManager manager = SpellDuelEvents.manager(player.getServer());
        List<PlayerChoice> choices = new ArrayList<>();
        for (ServerPlayer online : collectAllOnlinePlayers(player.getServer())) {
            SpellDuelGroup.Team team = manager.selectedTeam(player.getUUID(), online.getUUID());
            String groupId = manager.selectedGroup(online.getUUID());
            choices.add(new PlayerChoice(online.getUUID(), online.getGameProfile().getName(),
                    groupId == null ? "" : groupId, team == null ? -1 : team == SpellDuelGroup.Team.A ? 0 : 1));
        }
        PacketDistributor.sendToPlayer(player, new PlayerSelectionPayload(choices));
    }

    /** Complete server-side online snapshot, including cross-dimension and fake players. */
    private static List<ServerPlayer> collectAllOnlinePlayers(net.minecraft.server.MinecraftServer server) {
        Map<UUID, ServerPlayer> byId = new LinkedHashMap<>();
        for (ServerPlayer online : server.getPlayerList().getPlayers()) byId.put(online.getUUID(), online);
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer online : level.players()) byId.put(online.getUUID(), online);
        }
        List<ServerPlayer> result = new ArrayList<>(byId.values());
        result.sort(java.util.Comparator.comparing(online -> online.getGameProfile().getName(), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public static void sendSelectionClose(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SelectionClosePayload());
    }

    private static void handlePlayerSelection(PlayerSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.example.scrollspellicons.client.PlayerSelectionScreen.open(payload.players));
    }

    private static void handleSelectionClose(SelectionClosePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.example.scrollspellicons.client.PlayerSelectionScreen.closeIfOpen();
            com.example.scrollspellicons.client.DuelClientScreens.closeIfOpen();
        });
    }
    private static void handleInteractionMenu(InteractionMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.example.scrollspellicons.client.DuelClientScreens.openInteraction(payload.token, payload.target, payload.targetName));
    }
    private static void handleChallengeInvite(ChallengeInvitePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.example.scrollspellicons.client.DuelClientScreens.openInvite(payload.token, payload.challengerName, payload.expiresAt));
    }
    private static void handleChallengePoints(ChallengePointsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.example.scrollspellicons.client.DuelClientScreens.openPoints(payload.token, payload.points.stream().map(ChallengePoint::id).toList()));
    }
    private static void handleLearnedSpellView(LearnedSpellViewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.example.scrollspellicons.client.DuelClientScreens.openLearned(payload.targetName, payload.hasSpellbook, payload.spellIds));
    }
    private static void handleSelectPlayer(SelectPlayerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer selector)) return;
            if (!selector.hasPermissions(2)) return;
            if (payload.team < 0 || payload.team > 2) return;
            SpellDuelManager manager = SpellDuelEvents.manager(selector.getServer());
            if (payload.team == 2) {
                manager.cancelSelectedPlayer(selector.getUUID(), payload.target);
                sendPlayerSelection(selector);
                return;
            }
            manager.addPlayerToEditingGroup(selector.getUUID(), payload.target,
                    payload.team == 0 ? SpellDuelGroup.Team.A : SpellDuelGroup.Team.B);
            sendPlayerSelection(selector);
        });
    }

    private static void handleCreateSelection(CreateSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer selector)) return;
            if (!selector.hasPermissions(2)) return;
            SpellDuelManager manager = SpellDuelEvents.manager(selector.getServer());
            String id = manager.currentGroup(selector.getUUID());
            if (!manager.finalizeEditingGroup(selector.getUUID())) return;
            selector.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            selector.sendSystemMessage(net.minecraft.network.chat.Component.literal("[法术决斗] 已创建决斗组 " + id));
            sendSelectionClose(selector);
        });
    }

    private static void handleCancelSelection(CancelSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer selector)) return;
            if (!selector.hasPermissions(2)) return;
            SpellDuelEvents.manager(selector.getServer()).cancelEditingGroup(selector.getUUID());
            sendSelectionClose(selector);
        });
    }

    private static void handleInteractionAction(InteractionActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer actor)) return;
            String result = SpellDuelEvents.manager(actor.getServer()).executeInteraction(actor, payload.token, payload.target, payload.action);
            if (result != null) actor.sendSystemMessage(net.minecraft.network.chat.Component.literal("[法术决斗] " + result));
        });
    }

    private static void handleChallengeReply(ChallengeReplyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer target)) return;
            SpellDuelManager.ChallengeResult result = payload.accepted
                    ? SpellDuelEvents.manager(target.getServer()).acceptChallenge(target, payload.token)
                    : SpellDuelEvents.manager(target.getServer()).rejectChallenge(target, payload.token);
            if (result.message() != null) target.sendSystemMessage(net.minecraft.network.chat.Component.literal("[法术决斗] " + result.message()));
            sendDuelClose(target);
        });
    }

    private static void handleChallengePointChoice(ChallengePointChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer challenger)) return;
            SpellDuelManager manager = SpellDuelEvents.manager(challenger.getServer());
            SpellDuelManager.ChallengeResult result = manager.chooseChallengePoint(challenger, payload.token, payload.groupId);
            challenger.sendSystemMessage(net.minecraft.network.chat.Component.literal("[法术决斗] " + result.message()));
            if (result.accepted()) sendDuelClose(challenger);
            else if (result.token() != 0L) sendChallengePoints(challenger, result.token(), manager.availableChallengePoints());
        });
    }

    private static void handleChallengeCancel(ChallengeCancelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer challenger)) return;
            SpellDuelManager manager = SpellDuelEvents.manager(challenger.getServer());
            SpellDuelManager.ChallengeResult result = manager.cancelChallenge(challenger, payload.token);
            if (result.message() != null) challenger.sendSystemMessage(net.minecraft.network.chat.Component.literal("[法术决斗] " + result.message()));
            sendDuelClose(challenger);
        });
    }

    public static void broadcastCooldowns(SpellDuelManager manager) {
        if (!manager.displayEnabled()) return;
        List<CooldownEntry> entries = new ArrayList<>();
        for (ServerPlayer player : manager.server().getPlayerList().getPlayers()) {
            entries.add(new CooldownEntry(player.getUUID(), cooldowns(MagicData.getPlayerMagicData(player))));
        }
        CooldownPayload payload = new CooldownPayload(entries);
        for (ServerPlayer recipient : manager.server().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(recipient, payload);
        }
    }

    private static Map<String, Integer> cooldowns(MagicData magic) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var entry : magic.getPlayerCooldowns().getSpellCooldowns().entrySet()) {
            int remaining = entry.getValue().getCooldownRemaining();
            if (remaining > 0) result.put(entry.getKey(), remaining);
        }
        return result;
    }

    public static void broadcastSnapshots(SpellDuelManager manager) {
        for (SpellDuelGroup group : manager.groups().values()) {
            if (!group.active()) continue;
            SnapshotPayload payload = new SnapshotPayload(group.id(), snapshotEntries(manager, group));
            for (ServerPlayer spectator : manager.spectators(group.id())) PacketDistributor.sendToPlayer(spectator, payload);
        }
    }


    /** Sends the death frame before finish() clears the roster. */
    public static void broadcastEliminationSnapshot(SpellDuelManager manager, SpellDuelGroup group) {
        SnapshotPayload payload = new SnapshotPayload(group.id(), snapshotEntries(manager, group));
        for (ServerPlayer spectator : manager.spectators(group.id())) PacketDistributor.sendToPlayer(spectator, payload);
    }

    private static List<SnapshotEntry> snapshotEntries(SpellDuelManager manager, SpellDuelGroup group) {
        List<SnapshotEntry> result = new ArrayList<>();
        Set<java.util.UUID> eliminated = manager.eliminatedPlayers(group.id());
        group.teamA().forEach(uuid -> addSnapshot(result, manager.server(), uuid, 0, eliminated.contains(uuid)));
        group.teamB().forEach(uuid -> addSnapshot(result, manager.server(), uuid, 1, eliminated.contains(uuid)));
        return result;
    }

    private static void addSnapshot(List<SnapshotEntry> result, net.minecraft.server.MinecraftServer server,
                                    java.util.UUID uuid, int team, boolean eliminated) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) return;
        MagicData magic = MagicData.getPlayerMagicData(player);
        List<String> spells = new ArrayList<>();
        List<net.minecraft.world.item.ItemStack> stacks = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                handler.findCurios("spellbook").forEach(slot -> stacks.add(slot.stack())));
        for (net.minecraft.world.item.ItemStack stack : stacks) {
            if (!ISpellContainer.isSpellContainer(stack)) continue;
            for (SpellSlot slot : ISpellContainer.get(stack).getActiveSpells()) {
                if (slot.getSpell() != null) {
                    spells.add(slot.getSpell().getSpellResource() + "|" + slot.getLevel());
                }
            }
        }
        StringBuilder cooldowns = new StringBuilder();
        for (var entry : magic.getPlayerCooldowns().getSpellCooldowns().entrySet()) {
            if (entry.getValue().getCooldownRemaining() > 0) cooldowns.append(entry.getKey()).append(':').append(entry.getValue().getCooldownRemaining()).append(' ');
        }
        String casting = eliminated || magic.getCastingSpellId() == null ? "" : magic.getCastingSpellId();
        float snapshotHealth = eliminated || !player.isAlive() || player.isDeadOrDying() ? 0.0F : Math.max(0.0F, player.getHealth());
        result.add(new SnapshotEntry(team, player.getGameProfile().getName(), snapshotHealth, player.getMaxHealth(),
                magic.getMana(), (float) player.getAttributeValue(AttributeRegistry.MAX_MANA),
                String.join(",", spells), casting, cooldowns.toString()));
    }

    public record DisplayPayload(boolean enabled) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return DISPLAY_TYPE; }
    }

    public record HudPositionPayload(int x, int y) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return HUD_POSITION_TYPE; }
    }

    public record SnapshotPayload(String groupId, List<SnapshotEntry> entries) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return SNAPSHOT_TYPE; }
    }

    public record CooldownPayload(List<CooldownEntry> entries) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return COOLDOWN_TYPE; }
    }
    public record PointMarkerPayload(List<PointMarker> markers) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return POINT_MARKER_TYPE; }
    }
    public record PointMarker(String label, double x, double y, double z) {}

    public record PlayerSelectionPayload(List<PlayerChoice> players) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return PLAYER_SELECTION_TYPE; }
    }

    public record PlayerChoice(UUID id, String name, String selectedGroup, int ownTeam) {}

    public record SelectPlayerPayload(UUID target, byte team) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return SELECT_PLAYER_TYPE; }
    }

    public record CreateSelectionPayload() implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return CREATE_SELECTION_TYPE; }
    }

    public record CancelSelectionPayload() implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return CANCEL_SELECTION_TYPE; }
    }

    public record SelectionClosePayload() implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return SELECTION_CLOSE_TYPE; }
    }
    public record InteractionMenuPayload(long token, UUID target, String targetName) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return INTERACTION_MENU_TYPE; }
    }
    public record InteractionActionPayload(long token, UUID target, byte action) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return INTERACTION_ACTION_TYPE; }
    }
    public record ChallengeInvitePayload(long token, String challengerName, long expiresAt) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return CHALLENGE_INVITE_TYPE; }
    }
    public record ChallengeReplyPayload(long token, boolean accepted) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return CHALLENGE_REPLY_TYPE; }
    }
    public record ChallengePoint(String id) {}
    public record ChallengePointsPayload(long token, List<ChallengePoint> points) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return CHALLENGE_POINTS_TYPE; }
    }
    public record ChallengePointChoicePayload(long token, String groupId) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return CHALLENGE_POINT_CHOICE_TYPE; }
    }
    public record ChallengeCancelPayload(long token) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return CHALLENGE_CANCEL_TYPE; }
    }
    public record LearnedSpellViewPayload(String targetName, boolean hasSpellbook, List<String> spellIds) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return LEARNED_SPELL_VIEW_TYPE; }
    }
    public record CooldownEntry(UUID playerId, Map<String, Integer> cooldowns) {}

    public record SnapshotEntry(int team, String name, float health, float maxHealth, float mana, float maxMana,
                                String spells, String casting, String cooldowns) {}
}
