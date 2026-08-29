package com.example.scrollspellicons.client;

import com.example.scrollspellicons.IronSpellPerformance;
import com.mojang.blaze3d.platform.InputConstants;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Lets a player inspect the spell stored in a held Iron's spell scroll. */
@EventBusSubscriber(modid = IronSpellPerformance.MOD_ID, value = Dist.CLIENT)
public final class ScrollSpellIdKeybind {
    private static final ResourceLocation IRONS_SCROLL = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "scroll");
    private static final String CATEGORY = "key.categories.iron_magic_duel";
    private static final KeyMapping SHOW_SCROLL_SPELL_ID = new KeyMapping(
            "key.iron_magic_duel.show_scroll_spell_id",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            CATEGORY);

    private ScrollSpellIdKeybind() {
    }

    @EventBusSubscriber(modid = IronSpellPerformance.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(SHOW_SCROLL_SPELL_ID);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (SHOW_SCROLL_SPELL_ID.consumeClick()) {
            if (minecraft.player == null) {
                return;
            }
            ItemStack scroll = findHeldScroll(minecraft.player.getMainHandItem(), minecraft.player.getOffhandItem());
            if (scroll.isEmpty()) {
                minecraft.player.sendSystemMessage(Component.literal("[法术决斗] 请先手持一张法术卷轴，再按此按键。")
                        .withStyle(ChatFormatting.RED));
                continue;
            }
            if (!ISpellContainer.isSpellContainer(scroll)
                    || ISpellContainer.get(scroll).getActiveSpells().isEmpty()
                    || ISpellContainer.get(scroll).getSpellAtIndex(0).getSpell() == null) {
                minecraft.player.sendSystemMessage(Component.literal("[法术决斗] 这张法术卷轴没有保存法术。")
                        .withStyle(ChatFormatting.RED));
                continue;
            }

            ResourceLocation spellId = ISpellContainer.get(scroll).getSpellAtIndex(0).getSpell().getSpellResource();
            org.lwjgl.glfw.GLFW.glfwSetClipboardString(minecraft.getWindow().getWindow(), spellId.toString());
            minecraft.player.sendSystemMessage(Component.literal("[法术决斗] 已复制卷轴法术 ID：")
                    .append(Component.literal(spellId.toString()).withStyle(ChatFormatting.AQUA)));
        }
    }

    private static ItemStack findHeldScroll(ItemStack mainHand, ItemStack offHand) {
        if (isIronScroll(mainHand)) {
            return mainHand;
        }
        return isIronScroll(offHand) ? offHand : ItemStack.EMPTY;
    }

    private static boolean isIronScroll(ItemStack stack) {
        return !stack.isEmpty() && IRONS_SCROLL.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
