package com.example.scrollspellicons.duel;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        SpellDuelManager manager = manager(event.getServer());
        manager.tick();
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

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            manager(player.getServer()).recordPlayerDeath(player);
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
