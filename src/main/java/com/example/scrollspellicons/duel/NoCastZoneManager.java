package com.example.scrollspellicons.duel;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent square areas in which Iron's Spells casts are refused. */
public final class NoCastZoneManager {
    private static final String FILE_NAME = "iron_magic_duel_no_cast_zones.nbt";
    private final Path file;
    private final List<Zone> zones = new ArrayList<>();
    private final Map<UUID, PendingZone> pendingZones = new HashMap<>();

    public NoCastZoneManager(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME);
        load();
    }

    /** Selects one independently editable no-cast zone. */
    public String selectZone(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Zone existing = findAt(level.dimension(), pos);
        String id = existing == null ? nextId() : existing.id;
        pendingZones.put(player.getUUID(), new PendingZone(id, level.dimension(), pos.getX(), pos.getY(), pos.getZ()));
        if (existing == null) {
            // The tool itself creates an independent, persisted zone immediately.
            // /spell_duel no_cast range then edits its square radius.
            zones.add(new Zone(id, level.dimension(), pos.getX(), pos.getY(), pos.getZ(), 0));
            save();
        }
        return id;
    }

    /** Range is measured horizontally from the centre block to every square edge. */
    public Zone savePendingZone(ServerPlayer player, int range) {
        PendingZone pending = pendingZones.get(player.getUUID());
        if (pending == null) return null;
        Zone zone = new Zone(pending.id, pending.dimension, pending.x, pending.y, pending.z, range);
        for (int index = 0; index < zones.size(); index++) {
            if (zones.get(index).id.equals(zone.id)) {
                zones.set(index, zone);
                save();
                return zone;
            }
        }
        zones.add(zone);
        save();
        return zone;
    }

    public boolean selectExisting(ServerPlayer player, String id) {
        for (Zone zone : zones) {
            if (zone.id.equals(id)) {
                pendingZones.put(player.getUUID(), new PendingZone(zone.id, zone.dimension, zone.x, zone.y, zone.z));
                return true;
            }
        }
        return false;
    }

    public boolean remove(String id) {
        boolean removed = zones.removeIf(zone -> zone.id.equals(id));
        if (removed) save();
        return removed;
    }

    public List<Zone> zones() {
        return List.copyOf(zones);
    }

    public boolean blocksCasting(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        for (Zone zone : zones) {
            if (zone.dimension.equals(dimension) && Math.abs(player.getX() - zone.x) <= zone.range
                    && Math.abs(player.getZ() - zone.z) <= zone.range) return true;
        }
        return false;
    }

    public int clearAll() {
        int count = zones.size();
        zones.clear();
        pendingZones.clear();
        save();
        return count;
    }

    private String nextId() {
        int id = 1;
        while (hasId("no_cast_" + id)) id++;
        return "no_cast_" + id;
    }

    private boolean hasId(String id) {
        for (Zone zone : zones) if (zone.id.equals(id)) return true;
        return false;
    }

    private Zone findAt(ResourceKey<Level> dimension, BlockPos pos) {
        for (Zone zone : zones) {
            if (zone.dimension.equals(dimension) && zone.x == pos.getX() && zone.y == pos.getY() && zone.z == pos.getZ()) return zone;
        }
        return null;
    }

    private void load() {
        zones.clear();
        if (!java.nio.file.Files.isRegularFile(file)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            if (root == null) return;
            ListTag stored = root.getList("zones", Tag.TAG_COMPOUND);
            for (Tag entry : stored) {
                CompoundTag tag = (CompoundTag) entry;
                ResourceKey<Level> dimension = Level.RESOURCE_KEY_CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("dimension"))
                        .result().orElse(Level.OVERWORLD);
                zones.add(new Zone(tag.getString("id"), dimension, tag.getInt("x"), tag.getInt("y"), tag.getInt("z"), tag.getInt("range")));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load no-cast zones from " + file, exception);
        }
    }

    private void save() {
        CompoundTag root = new CompoundTag();
        ListTag stored = new ListTag();
        for (Zone zone : zones) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", zone.id);
            tag.put("dimension", Level.RESOURCE_KEY_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, zone.dimension).result().orElseThrow());
            tag.putInt("x", zone.x);
            tag.putInt("y", zone.y);
            tag.putInt("z", zone.z);
            tag.putInt("range", zone.range);
            stored.add(tag);
        }
        root.put("zones", stored);
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(root, file);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save no-cast zones to " + file, exception);
        }
    }

    public record Zone(String id, ResourceKey<Level> dimension, int x, int y, int z, int range) {}
    private record PendingZone(String id, ResourceKey<Level> dimension, int x, int y, int z) {}
}
