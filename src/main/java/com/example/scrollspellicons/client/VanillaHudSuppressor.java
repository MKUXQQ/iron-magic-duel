package com.example.scrollspellicons.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Hides vanilla survival status widgets; hotbar and crosshair remain intact. */
@EventBusSubscriber(value = Dist.CLIENT, modid = "iron_magic_duel")
public final class VanillaHudSuppressor {
    private VanillaHudSuppressor() {}

    @SubscribeEvent
    public static void hideVanillaHealthAndFood(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)
                || event.getName().equals(VanillaGuiLayers.FOOD_LEVEL)
                || event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR)
                || event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
            event.setCanceled(true);
        }
    }
}
