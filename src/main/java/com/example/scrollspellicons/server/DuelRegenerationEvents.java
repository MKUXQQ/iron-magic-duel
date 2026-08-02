package com.example.scrollspellicons.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps saturation full and gates natural regeneration behind ten seconds without damage. */
@EventBusSubscriber(modid = "iron_magic_duel")
public final class DuelRegenerationEvents {
    private static final int REGEN_DELAY_TICKS = 200;
    private static final Map<UUID, DamageState> DAMAGE = new HashMap<>();

    private DuelRegenerationEvents() {}

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DAMAGE.put(player.getUUID(), new DamageState(player.getServer().getTickCount(), player.getHealth()));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof ServerPlayer player)) return;
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        DamageState state = DAMAGE.get(player.getUUID());
        if (state == null) return;
        if (player.getServer().getTickCount() - state.lastDamageTick() < REGEN_DELAY_TICKS) {
            if (player.getHealth() > state.healthFloor()) player.setHealth(state.healthFloor());
            else if (player.getHealth() < state.healthFloor()) {
                DAMAGE.put(player.getUUID(), new DamageState(state.lastDamageTick(), player.getHealth()));
            }
        } else {
            DAMAGE.remove(player.getUUID());
        }
    }

    private record DamageState(int lastDamageTick, float healthFloor) {}
}
