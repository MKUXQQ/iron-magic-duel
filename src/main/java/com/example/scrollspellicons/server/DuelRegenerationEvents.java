package com.example.scrollspellicons.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps saturation full and gates regeneration behind five seconds without damage. */
@EventBusSubscriber(modid = "iron_magic_duel")
public final class DuelRegenerationEvents {
    private static final int REGEN_DELAY_TICKS = 100;
    private static final Map<UUID, Integer> LAST_DAMAGE_TICKS = new HashMap<>();

    private DuelRegenerationEvents() {}

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // A fatal hit must never leave a zero-health recovery floor behind.
            if (player.getHealth() <= 0.0F || player.isDeadOrDying()) {
                LAST_DAMAGE_TICKS.remove(player.getUUID());
            } else {
                LAST_DAMAGE_TICKS.put(player.getUUID(), player.getServer().getTickCount());
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_DAMAGE_TICKS.remove(player.getUUID());
        }
    }

    /** Blocks regeneration during the no-damage delay without ever changing a player's health directly. */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Integer lastDamageTick = LAST_DAMAGE_TICKS.get(player.getUUID());
        if (lastDamageTick != null && player.getServer().getTickCount() - lastDamageTick < REGEN_DELAY_TICKS) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        Integer lastDamageTick = LAST_DAMAGE_TICKS.get(player.getUUID());
        if (lastDamageTick != null && player.getServer().getTickCount() - lastDamageTick >= REGEN_DELAY_TICKS) {
            LAST_DAMAGE_TICKS.remove(player.getUUID());
        }
    }
}
