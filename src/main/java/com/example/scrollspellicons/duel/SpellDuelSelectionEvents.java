package com.example.scrollspellicons.duel;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = "iron_magic_duel")
public final class SpellDuelSelectionEvents {
    private SpellDuelSelectionEvents() {}

    @SubscribeEvent
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer selector) || !is(event.getItemStack(), SpellDuelItems.PLAYER_SELECTOR)) return;
        if (!(event.getTarget() instanceof ServerPlayer target) || selector == target) return;
        SpellDuelEvents.manager(selector.getServer()).selectPlayer(selector.getUUID(), target.getUUID(), SpellDuelGroup.Team.A);
        selector.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 1.0F, 1.15F);
        selector.sendSystemMessage(net.minecraft.network.chat.Component.literal("[法术决斗] 已将 " + target.getGameProfile().getName() + " 加入 A 队"));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeftClickEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer selector) || !is(selector.getMainHandItem(), SpellDuelItems.PLAYER_SELECTOR)) return;
        Entity target = event.getTarget();
        if (!(target instanceof ServerPlayer targetPlayer) || selector == targetPlayer) return;
        SpellDuelEvents.manager(selector.getServer()).selectPlayer(selector.getUUID(), targetPlayer.getUUID(), SpellDuelGroup.Team.B);
        selector.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 1.0F, 0.9F);
        selector.sendSystemMessage(net.minecraft.network.chat.Component.literal("[法术决斗] 已将 " + targetPlayer.getGameProfile().getName() + " 加入 B 队"));
        event.setCanceled(true);
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
        SpellDuelManager manager = SpellDuelEvents.manager(player.getServer());
        ItemStack stack = event.getItemStack();
        if (is(stack, SpellDuelItems.PLAYER_SELECTOR)) {
            manager.cancelSelection(player.getUUID());
            player.closeContainer();
            SpellDuelNetwork.sendSelectionClose(player);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[法术决斗] 已取消玩家选择"));
            event.setCanceled(true);
        } else if (is(stack, SpellDuelItems.POINT_SELECTOR)) {
            String id = manager.createPointGroup(player.getUUID());
            if (id != null) player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(id == null
                    ? "[法术决斗] 创建点位组失败：请先设置 A 点和 B 点"
                    : "[法术决斗] 已创建/绑定点位组 " + id));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onOpenPlayerSelector(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().isClientSide()
                || player.isCrouching() || !is(event.getItemStack(), SpellDuelItems.PLAYER_SELECTOR)) return;
        SpellDuelNetwork.sendPlayerSelection(player);
        event.setCanceled(true);
    }

    private static boolean is(ItemStack stack, net.neoforged.neoforge.registries.DeferredItem<?> item) {
        return stack.is(item.get());
    }
}
