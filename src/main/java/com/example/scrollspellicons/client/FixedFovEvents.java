package com.example.scrollspellicons.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Keeps the player's configured FOV as the only camera field-of-view value. */
@EventBusSubscriber(modid = "iron_magic_duel", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class FixedFovEvents {
    private FixedFovEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void keepConfiguredFov(ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options != null && minecraft.options.fov() != null) {
            event.setFOV(minecraft.options.fov().get());
        }
    }
}
