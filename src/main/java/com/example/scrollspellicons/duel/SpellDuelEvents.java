package com.example.scrollspellicons.duel;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.EventPriority;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;

import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = "iron_magic_duel")
public final class SpellDuelEvents {
    private static final Map<MinecraftServer, SpellDuelManager> MANAGERS = new WeakHashMap<>();
    private static long cooldownSyncTicks;

    private SpellDuelEvents() {}

    public static SpellDuelManager manager(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, SpellDuelManager::new);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        manager(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MANAGERS.remove(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SpellDuelManager manager = manager(event.getServer());
        manager.tick();
        if (event.getServer().getTickCount() % 5 == 0) {
            for (net.minecraft.server.level.ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                if (player.getMainHandItem().is(SpellDuelItems.POINT_SELECTOR.get())
                        || player.getOffhandItem().is(SpellDuelItems.POINT_SELECTOR.get())) {
                    manager.showPointMarkers(player);
                }
            }
        }
        SpellDuelNetwork.broadcastSnapshots(manager);
        if (++cooldownSyncTicks % 5 == 0) SpellDuelNetwork.broadcastCooldowns(manager);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            SpellDuelManager manager = manager(player.getServer());
            SpellDuelNetwork.sendDisplay(player, manager.displayEnabled());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && manager(player.getServer()).recordPlayerDeath(player)) {
            event.setCanceled(true);
        }
    }

    /** Spectators may inspect a duel, but can never affect it by casting. */
    @SubscribeEvent
    public static void onSpellPreCast(SpellPreCastEvent event) {
        if (!event.getEntity().isSpectator()) return;
        event.setCanceled(true);
        MagicData.getPlayerMagicData(event.getEntity()).resetCastingState();
        event.getEntity().stopUsingItem();
    }
}
