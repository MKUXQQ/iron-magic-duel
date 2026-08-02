package com.example.scrollspellicons.duel;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;

/** Item interactions for the player and point selectors. */
@EventBusSubscriber(modid = "iron_magic_duel")
public final class SpellDuelSelectionEvents {
    private SpellDuelSelectionEvents() {}

    @SubscribeEvent
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer && is(event.getItemStack(), SpellDuelItems.PLAYER_SELECTOR)
                && event.getTarget() instanceof ServerPlayer) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeftClickEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer selector && is(selector.getMainHandItem(), SpellDuelItems.PLAYER_SELECTOR)
                && event.getTarget() instanceof ServerPlayer) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().isClientSide()) return;
        if (!is(event.getItemStack(), SpellDuelItems.POINT_SELECTOR)) return;
        SpellDuelEvents.manager(player.getServer()).selectPoint(player.getUUID(), SpellDuelGroup.Team.A,
                (net.minecraft.server.level.ServerLevel) event.getLevel(), event.getPos());
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[法术决斗] 已设置 A 点：" + event.getPos().toShortString()));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().isClientSide()) return;
        if (!is(player.getMainHandItem(), SpellDuelItems.POINT_SELECTOR)) return;
        SpellDuelEvents.manager(player.getServer()).selectPoint(player.getUUID(), SpellDuelGroup.Team.B,
                (net.minecraft.server.level.ServerLevel) event.getLevel(), event.getPos());
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[法术决斗] 已设置 B 点：" + event.getPos().toShortString()));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onCrouchRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().isClientSide() || !player.isCrouching()) return;
        ItemStack stack = event.getItemStack();
        if (is(stack, SpellDuelItems.PLAYER_SELECTOR)) return;
        if (!is(stack, SpellDuelItems.POINT_SELECTOR)) return;
        SpellDuelManager manager = SpellDuelEvents.manager(player.getServer());
        String id = manager.createPointGroup(player.getUUID());
        if (id != null) player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(id == null
                ? "[法术决斗] 绑定点位失败：请先设置 A 点和 B 点"
                : "[法术决斗] 已绑定点位到决斗组 " + id));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onOpenPlayerSelector(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().isClientSide()
                || player.isCrouching() || !is(event.getItemStack(), SpellDuelItems.PLAYER_SELECTOR)) return;
        SpellDuelEvents.manager(player.getServer()).beginEditingGroup(player.getUUID());
        SpellDuelNetwork.sendPlayerSelection(player);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void showPointMarkers(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 5 != 0) return;
        if (!is(player.getMainHandItem(), SpellDuelItems.POINT_SELECTOR)
                && !is(player.getOffhandItem(), SpellDuelItems.POINT_SELECTOR)) return;
        SpellDuelEvents.manager(player.getServer()).showPointMarkers(player);
    }

    private static boolean is(ItemStack stack, net.minecraftforge.registries.RegistryObject<? extends net.minecraft.world.item.Item> item) {
        return stack.is(item.get());
    }
}
