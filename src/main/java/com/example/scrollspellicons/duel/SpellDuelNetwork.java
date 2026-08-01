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

    private SpellDuelNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(DISPLAY_TYPE, DISPLAY_CODEC, SpellDuelNetwork::handleDisplay);
        registrar.playToClient(HUD_POSITION_TYPE, HUD_POSITION_CODEC, SpellDuelNetwork::handleHudPosition);
        registrar.playToClient(SNAPSHOT_TYPE, SNAPSHOT_CODEC, SpellDuelNetwork::handleSnapshot);
        registrar.playToClient(COOLDOWN_TYPE, COOLDOWN_CODEC, SpellDuelNetwork::handleCooldowns);
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

    private static List<SnapshotEntry> snapshotEntries(SpellDuelManager manager, SpellDuelGroup group) {
        List<SnapshotEntry> result = new ArrayList<>();
        group.teamA().forEach(uuid -> addSnapshot(result, manager.server(), uuid, 0));
        group.teamB().forEach(uuid -> addSnapshot(result, manager.server(), uuid, 1));
        return result;
    }

    private static void addSnapshot(List<SnapshotEntry> result, net.minecraft.server.MinecraftServer server, java.util.UUID uuid, int team) {
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
        String casting = magic.getCastingSpellId() == null ? "" : magic.getCastingSpellId();
        result.add(new SnapshotEntry(team, player.getGameProfile().getName(), player.getHealth(), player.getMaxHealth(),
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

    public record CooldownEntry(UUID playerId, Map<String, Integer> cooldowns) {}

    public record SnapshotEntry(int team, String name, float health, float maxHealth, float mana, float maxMana,
                                String spells, String casting, String cooldowns) {}
}
