package com.example.scrollspellicons.duel;

import com.example.scrollspellicons.IronSpellPerformance;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Forge 1.20.1 packet channel.  All gameplay state remains server authoritative. */
public final class SpellDuelNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(IronSpellPerformance.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();
    private static boolean registered;

    private SpellDuelNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(DisplayPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpellDuelNetwork::writeDisplay).decoder(SpellDuelNetwork::readDisplay)
                .consumerMainThread(SpellDuelNetwork::handleDisplay).add();
        CHANNEL.messageBuilder(HudPositionPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpellDuelNetwork::writeHudPosition).decoder(SpellDuelNetwork::readHudPosition)
                .consumerMainThread(SpellDuelNetwork::handleHudPosition).add();
        CHANNEL.messageBuilder(SnapshotPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpellDuelNetwork::writeSnapshot).decoder(SpellDuelNetwork::readSnapshot)
                .consumerMainThread(SpellDuelNetwork::handleSnapshot).add();
        CHANNEL.messageBuilder(CooldownPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpellDuelNetwork::writeCooldowns).decoder(SpellDuelNetwork::readCooldowns)
                .consumerMainThread(SpellDuelNetwork::handleCooldowns).add();
        CHANNEL.messageBuilder(PointMarkerPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpellDuelNetwork::writePointMarkers).decoder(SpellDuelNetwork::readPointMarkers)
                .consumerMainThread(SpellDuelNetwork::handlePointMarkers).add();
        CHANNEL.messageBuilder(PlayerSelectionPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpellDuelNetwork::writePlayerSelection).decoder(SpellDuelNetwork::readPlayerSelection)
                .consumerMainThread(SpellDuelNetwork::handlePlayerSelection).add();
        CHANNEL.messageBuilder(SelectionClosePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((p, b) -> {}).decoder(b -> new SelectionClosePayload())
                .consumerMainThread(SpellDuelNetwork::handleSelectionClose).add();
        CHANNEL.messageBuilder(SelectPlayerPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SpellDuelNetwork::writeSelectPlayer).decoder(SpellDuelNetwork::readSelectPlayer)
                .consumerMainThread(SpellDuelNetwork::handleSelectPlayer).add();
        CHANNEL.messageBuilder(CreateSelectionPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((p, b) -> {}).decoder(b -> new CreateSelectionPayload())
                .consumerMainThread(SpellDuelNetwork::handleCreateSelection).add();
        CHANNEL.messageBuilder(CancelSelectionPayload.class, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder((p, b) -> {}).decoder(b -> new CancelSelectionPayload())
                .consumerMainThread(SpellDuelNetwork::handleCancelSelection).add();
    }

    public static void sendToServer(Object payload) { CHANNEL.sendToServer(payload); }
    private static void send(ServerPlayer player, Object payload) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload); }

    public static void broadcastDisplay(net.minecraft.server.MinecraftServer server, boolean enabled) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player, new DisplayPayload(enabled));
    }
    public static void sendDisplay(ServerPlayer player, boolean enabled) { send(player, new DisplayPayload(enabled)); }
    public static void sendHudPosition(ServerPlayer player, int x, int y) { send(player, new HudPositionPayload(x, y)); }
    public static void sendPointMarkers(ServerPlayer player, List<PointMarker> markers) { send(player, new PointMarkerPayload(markers)); }

    public static void sendPlayerSelection(ServerPlayer player) {
        SpellDuelManager manager = SpellDuelEvents.manager(player.getServer());
        Map<UUID, PlayerChoice> byId = new LinkedHashMap<>();
        // This is the authoritative server player list.  Do not use client
        // tracking/player-info data here: players outside the selector's view
        // distance must still be selectable.
        for (ServerPlayer online : player.getServer().getPlayerList().getPlayers()) {
            SpellDuelGroup.Team team = manager.selectedTeam(player.getUUID(), online.getUUID());
            String groupId = manager.selectedGroup(online.getUUID());
            byId.put(online.getUUID(), new PlayerChoice(online.getUUID(), online.getGameProfile().getName(),
                    groupId == null ? "" : groupId, team == null ? -1 : team == SpellDuelGroup.Team.A ? 0 : 1));
        }
        List<PlayerChoice> choices = new ArrayList<>(byId.values());
        choices.sort(java.util.Comparator.comparing(PlayerChoice::name, String.CASE_INSENSITIVE_ORDER));
        send(player, new PlayerSelectionPayload(choices));
    }
    public static void sendSelectionClose(ServerPlayer player) { send(player, new SelectionClosePayload()); }

    public static void broadcastCooldowns(SpellDuelManager manager) {
        if (!manager.displayEnabled()) return;
        List<CooldownEntry> entries = new ArrayList<>();
        for (ServerPlayer player : manager.server().getPlayerList().getPlayers()) entries.add(new CooldownEntry(player.getUUID(), cooldowns(MagicData.getPlayerMagicData(player))));
        CooldownPayload payload = new CooldownPayload(entries);
        for (ServerPlayer player : manager.server().getPlayerList().getPlayers()) send(player, payload);
    }

    public static void broadcastSnapshots(SpellDuelManager manager) {
        for (SpellDuelGroup group : manager.groups().values()) {
            if (!group.active()) continue;
            SnapshotPayload payload = new SnapshotPayload(group.id(), snapshotEntries(manager, group));
            for (ServerPlayer spectator : manager.spectators(group.id())) send(spectator, payload);
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
    private static List<SnapshotEntry> snapshotEntries(SpellDuelManager manager, SpellDuelGroup group) {
        List<SnapshotEntry> result = new ArrayList<>();
        group.teamA().forEach(id -> addSnapshot(result, manager.server(), id, 0));
        group.teamB().forEach(id -> addSnapshot(result, manager.server(), id, 1));
        return result;
    }
    private static void addSnapshot(List<SnapshotEntry> result, net.minecraft.server.MinecraftServer server, UUID id, int team) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) return;
        MagicData magic = MagicData.getPlayerMagicData(player);
        List<String> spells = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.findCurios("spellbook").forEach(slot -> {
            if (ISpellContainer.isSpellContainer(slot.stack())) for (SpellSlot spell : ISpellContainer.get(slot.stack()).getActiveSpells())
                if (spell.getSpell() != null) spells.add(spell.getSpell().getSpellResource() + "|" + spell.getLevel());
        }));
        StringBuilder cooldowns = new StringBuilder();
        magic.getPlayerCooldowns().getSpellCooldowns().forEach((key, value) -> { if (value.getCooldownRemaining() > 0) cooldowns.append(key).append(':').append(value.getCooldownRemaining()).append(' '); });
        String casting = magic.getCastingSpellId() == null ? "" : magic.getCastingSpellId();
        result.add(new SnapshotEntry(team, player.getGameProfile().getName(), player.getHealth(), player.getMaxHealth(),
                magic.getMana(), (float) player.getAttributeValue(AttributeRegistry.MAX_MANA.get()),
                String.join(",", spells), casting, cooldowns.toString()));
    }

    private static void handleDisplay(DisplayPayload p, Supplier<NetworkEvent.Context> c) { c.get().enqueueWork(() -> com.example.scrollspellicons.client.SpellDuelClientState.setDisplayEnabled(p.enabled)); c.get().setPacketHandled(true); }
    private static void handleHudPosition(HudPositionPayload p, Supplier<NetworkEvent.Context> c) { c.get().enqueueWork(() -> com.example.scrollspellicons.client.SpellDuelClientState.setHudPosition(p.x, p.y)); c.get().setPacketHandled(true); }
    private static void handleSnapshot(SnapshotPayload p, Supplier<NetworkEvent.Context> c) { c.get().enqueueWork(() -> com.example.scrollspellicons.client.SpellDuelClientState.setSnapshot(p.groupId, p.entries)); c.get().setPacketHandled(true); }
    private static void handleCooldowns(CooldownPayload p, Supplier<NetworkEvent.Context> c) { c.get().enqueueWork(() -> com.example.scrollspellicons.client.SpellDuelClientState.setCooldowns(p.entries)); c.get().setPacketHandled(true); }
    private static void handlePointMarkers(PointMarkerPayload p, Supplier<NetworkEvent.Context> c) { c.get().enqueueWork(() -> com.example.scrollspellicons.client.SpellDuelClientState.setPointMarkers(p.markers)); c.get().setPacketHandled(true); }
    private static void handlePlayerSelection(PlayerSelectionPayload p, Supplier<NetworkEvent.Context> c) { c.get().enqueueWork(() -> com.example.scrollspellicons.client.PlayerSelectionScreen.open(p.players)); c.get().setPacketHandled(true); }
    private static void handleSelectionClose(SelectionClosePayload p, Supplier<NetworkEvent.Context> c) { c.get().enqueueWork(com.example.scrollspellicons.client.PlayerSelectionScreen::closeIfOpen); c.get().setPacketHandled(true); }
    private static void handleSelectPlayer(SelectPlayerPayload p, Supplier<NetworkEvent.Context> c) { ServerPlayer selector = c.get().getSender(); if (selector != null) c.get().enqueueWork(() -> { SpellDuelManager m = SpellDuelEvents.manager(selector.getServer()); if (p.team == 2) m.cancelSelectedPlayer(p.target); else m.addPlayerToEditingGroup(selector.getUUID(), p.target, p.team == 0 ? SpellDuelGroup.Team.A : SpellDuelGroup.Team.B); sendPlayerSelection(selector); }); c.get().setPacketHandled(true); }
    private static void handleCreateSelection(CreateSelectionPayload p, Supplier<NetworkEvent.Context> c) { ServerPlayer selector = c.get().getSender(); if (selector != null) c.get().enqueueWork(() -> { SpellDuelManager m = SpellDuelEvents.manager(selector.getServer()); String id = m.currentGroup(selector.getUUID()); if (m.finalizeEditingGroup(selector.getUUID())) { selector.sendSystemMessage(net.minecraft.network.chat.Component.literal("[法术决斗] 已创建决斗组 " + id)); sendSelectionClose(selector); } }); c.get().setPacketHandled(true); }
    private static void handleCancelSelection(CancelSelectionPayload p, Supplier<NetworkEvent.Context> c) { ServerPlayer selector = c.get().getSender(); if (selector != null) c.get().enqueueWork(() -> { SpellDuelEvents.manager(selector.getServer()).cancelEditingGroup(selector.getUUID()); sendSelectionClose(selector); }); c.get().setPacketHandled(true); }

    private static void writeDisplay(DisplayPayload p, FriendlyByteBuf b) { b.writeBoolean(p.enabled); } private static DisplayPayload readDisplay(FriendlyByteBuf b) { return new DisplayPayload(b.readBoolean()); }
    private static void writeHudPosition(HudPositionPayload p, FriendlyByteBuf b) { b.writeVarInt(p.x); b.writeVarInt(p.y); } private static HudPositionPayload readHudPosition(FriendlyByteBuf b) { return new HudPositionPayload(b.readVarInt(), b.readVarInt()); }
    private static void writeSelectPlayer(SelectPlayerPayload p, FriendlyByteBuf b) { b.writeUUID(p.target); b.writeByte(p.team); } private static SelectPlayerPayload readSelectPlayer(FriendlyByteBuf b) { return new SelectPlayerPayload(b.readUUID(), b.readByte()); }
    private static void writeSnapshot(SnapshotPayload p, FriendlyByteBuf b) { b.writeUtf(p.groupId,64); b.writeVarInt(p.entries.size()); for (SnapshotEntry e:p.entries) { b.writeVarInt(e.team); b.writeUtf(e.name,64); b.writeFloat(e.health); b.writeFloat(e.maxHealth); b.writeFloat(e.mana); b.writeFloat(e.maxMana); b.writeUtf(e.spells,2048); b.writeUtf(e.casting,256); b.writeUtf(e.cooldowns,2048); } }
    private static SnapshotPayload readSnapshot(FriendlyByteBuf b) { String id=b.readUtf(64); List<SnapshotEntry> es=new ArrayList<>(); for(int i=b.readVarInt();i>0;i--) es.add(new SnapshotEntry(b.readVarInt(),b.readUtf(64),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readUtf(2048),b.readUtf(256),b.readUtf(2048))); return new SnapshotPayload(id,es); }
    private static void writeCooldowns(CooldownPayload p, FriendlyByteBuf b) { b.writeVarInt(p.entries.size()); for(CooldownEntry e:p.entries){b.writeUUID(e.playerId);b.writeVarInt(e.cooldowns.size());e.cooldowns.forEach((k,v)->{b.writeUtf(k,256);b.writeVarInt(v);});} }
    private static CooldownPayload readCooldowns(FriendlyByteBuf b) { List<CooldownEntry> es=new ArrayList<>(); for(int i=b.readVarInt();i>0;i--){UUID id=b.readUUID();Map<String,Integer> m=new LinkedHashMap<>();for(int j=b.readVarInt();j>0;j--)m.put(b.readUtf(256),b.readVarInt());es.add(new CooldownEntry(id,m));}return new CooldownPayload(es); }
    private static void writePointMarkers(PointMarkerPayload p, FriendlyByteBuf b) { b.writeVarInt(p.markers.size()); for(PointMarker m:p.markers){b.writeUtf(m.label,64);b.writeDouble(m.x);b.writeDouble(m.y);b.writeDouble(m.z);} }
    private static PointMarkerPayload readPointMarkers(FriendlyByteBuf b) { List<PointMarker> markers=new ArrayList<>();for(int i=b.readVarInt();i>0;i--)markers.add(new PointMarker(b.readUtf(64),b.readDouble(),b.readDouble(),b.readDouble()));return new PointMarkerPayload(markers); }
    private static void writePlayerSelection(PlayerSelectionPayload p, FriendlyByteBuf b) { b.writeVarInt(p.players.size()); for(PlayerChoice e:p.players){b.writeUUID(e.id);b.writeUtf(e.name,64);b.writeUtf(e.selectedGroup,64);b.writeByte(e.ownTeam);} }
    private static PlayerSelectionPayload readPlayerSelection(FriendlyByteBuf b) { List<PlayerChoice> es=new ArrayList<>();for(int i=b.readVarInt();i>0;i--)es.add(new PlayerChoice(b.readUUID(),b.readUtf(64),b.readUtf(64),b.readByte()));return new PlayerSelectionPayload(es); }

    public record DisplayPayload(boolean enabled) {}
    public record HudPositionPayload(int x, int y) {}
    public record SnapshotPayload(String groupId, List<SnapshotEntry> entries) {}
    public record CooldownPayload(List<CooldownEntry> entries) {}
    public record PointMarkerPayload(List<PointMarker> markers) {}
    public record PointMarker(String label, double x, double y, double z) {}
    public record PlayerSelectionPayload(List<PlayerChoice> players) {}
    public record PlayerChoice(UUID id, String name, String selectedGroup, int ownTeam) {}
    public record SelectPlayerPayload(UUID target, byte team) {}
    public record CreateSelectionPayload() {}
    public record CancelSelectionPayload() {}
    public record SelectionClosePayload() {}
    public record CooldownEntry(UUID playerId, Map<String, Integer> cooldowns) {}
    public record SnapshotEntry(int team, String name, float health, float maxHealth, float mana, float maxMana, String spells, String casting, String cooldowns) {}
}
