package com.example.scrollspellicons.client;

import com.example.scrollspellicons.IronSpellPerformance;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Toggleable client-side sprint key which keeps the same sprint speed in every horizontal direction. */
@EventBusSubscriber(modid = IronSpellPerformance.MOD_ID, value = Dist.CLIENT)
public final class AutoSprintKeybind {
    private static final String CATEGORY = "key.categories.iron_magic_duel";
    private static final KeyMapping TOGGLE_AUTO_SPRINT = new KeyMapping(
            "key.iron_magic_duel.toggle_auto_sprint",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);
    private static boolean enabled;

    private AutoSprintKeybind() {
    }

    @EventBusSubscriber(modid = IronSpellPerformance.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_AUTO_SPRINT);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (TOGGLE_AUTO_SPRINT.consumeClick()) {
            enabled = !enabled;
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.translatable(
                        enabled ? "message.iron_magic_duel.auto_sprint_enabled" : "message.iron_magic_duel.auto_sprint_disabled"));
            }
        }
        if (minecraft.player == null) {
            return;
        }

        boolean movingHorizontally = Math.abs(minecraft.player.input.forwardImpulse) > 0.0F
                || Math.abs(minecraft.player.input.leftImpulse) > 0.0F;
        if (enabled && !minecraft.player.isSpectator() && movingHorizontally) {
            minecraft.player.setSprinting(true);
        } else if (enabled) {
            // This sprint state belongs to the toggle: clear it immediately when horizontal movement stops.
            minecraft.player.setSprinting(false);
        }
    }
}
