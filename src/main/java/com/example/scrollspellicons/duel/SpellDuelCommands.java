package com.example.scrollspellicons.duel;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.example.scrollspellicons.config.PerformanceConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = "iron_magic_duel")
public final class SpellDuelCommands {
    private SpellDuelCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("spell_duel")
                .then(Commands.literal("start").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("all").executes(context -> startAll(context.getSource())))
                        .then(Commands.argument("group", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        SpellDuelEvents.manager(context.getSource().getServer()).groups().keySet(), builder))
                                .executes(context -> startOne(context.getSource(), StringArgumentType.getString(context, "group")))))
                .then(Commands.literal("stop").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("all").executes(context -> stopAll(context.getSource())))
                        .then(Commands.argument("group", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        SpellDuelEvents.manager(context.getSource().getServer()).groups().keySet(), builder))
                                .executes(context -> stopOne(context.getSource(), StringArgumentType.getString(context, "group")))))
                .then(Commands.literal("tool").requires(source -> source.hasPermission(2))
                        .executes(context -> giveTools(context.getSource())))
                .then(Commands.literal("shop").requires(source -> source.hasPermission(2))
                        .executes(context -> giveShop(context.getSource())))
                .then(Commands.literal("hud")
                        .then(Commands.argument("x", IntegerArgumentType.integer(0, 10000))
                                .then(Commands.argument("y", IntegerArgumentType.integer(0, 10000))
                                        .executes(context -> setHudPosition(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "x"),
                                                IntegerArgumentType.getInteger(context, "y"))))))
                .then(Commands.literal("fake_players").requires(source -> source.hasPermission(2))
                        .executes(context -> fakePlayers(context.getSource())))
                .then(Commands.literal("clear").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("all").executes(context -> clearAll(context.getSource())))
                        .then(Commands.literal("group")
                                .executes(context -> clearPlayersAll(context.getSource()))
                                .then(groupArgument().executes(context -> clearPlayers(context.getSource(), StringArgumentType.getString(context, "groupId")))))
                        .then(Commands.literal("point")
                                .executes(context -> clearPointsAll(context.getSource()))
                                .then(groupArgument().executes(context -> clearPoints(context.getSource(), StringArgumentType.getString(context, "groupId"))))))
                .then(Commands.literal("spectate")
                        .then(Commands.literal("leave").executes(context -> leaveSpectate(context.getSource())))
                        .then(Commands.argument("group", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        SpellDuelEvents.manager(context.getSource().getServer()).groups().keySet(), builder))
                                .executes(context -> spectate(context.getSource(), StringArgumentType.getString(context, "group")))));
        event.getDispatcher().register(root);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> groupArgument() {
        return Commands.argument("groupId", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        SpellDuelEvents.manager(context.getSource().getServer()).groups().keySet(), builder));
    }

    private static int startAll(CommandSourceStack source) {
        SpellDuelEvents.manager(source.getServer()).startAll()
                .forEach((id, result) -> source.sendSuccess(() -> Component.literal("[法术决斗] " + result), true));
        return 1;
    }

    private static int startOne(CommandSourceStack source, String id) {
        source.sendSuccess(() -> Component.literal("[法术决斗] " + SpellDuelEvents.manager(source.getServer()).start(id)), true);
        return 1;
    }

    private static int stopAll(CommandSourceStack source) {
        int stopped = SpellDuelEvents.manager(source.getServer()).stopAll();
        source.sendSuccess(() -> Component.literal("[法术决斗] 已取消 " + stopped + " 个进行中的决斗"), true);
        return stopped;
    }

    private static int stopOne(CommandSourceStack source, String id) {
        boolean stopped = SpellDuelEvents.manager(source.getServer()).stop(id);
        if (stopped) source.sendSuccess(() -> Component.literal("[法术决斗] 已取消决斗 " + id), true);
        else source.sendFailure(Component.literal("[法术决斗] 决斗组不存在或未在进行中：" + id));
        return stopped ? 1 : 0;
    }

    private static int spectate(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        SpellDuelManager manager = SpellDuelEvents.manager(source.getServer());
        if (manager.group(id) == null || !manager.group(id).active()) {
            source.sendFailure(Component.literal("[法术决斗] 决斗组不存在或尚未开始：" + id));
            return 0;
        }
        manager.joinSpectator(id, player);
        source.sendSuccess(() -> Component.literal("[法术决斗] 已开始观战：" + id + "；输入 /spell_duel spectate leave 退出观战"), false);
        return 1;
    }

    private static int leaveSpectate(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        boolean left = SpellDuelEvents.manager(source.getServer()).leaveSpectator(player);
        if (left) source.sendSuccess(() -> Component.literal("[法术决斗] 已退出观战，并恢复观战前状态"), false);
        else source.sendFailure(Component.literal("[法术决斗] 你当前没有在观战"));
        return left ? 1 : 0;
    }

    private static int fakePlayers(CommandSourceStack source) {
        var server = source.getServer();
        for (String name : PerformanceConfig.SERVER.fakePlayers.get()) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "player " + name + " spawn");
        }
        source.sendSuccess(() -> Component.literal("[法术决斗] 已召唤假人"), true);
        return 1;
    }

    private static int giveTools(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(SpellDuelItems.PLAYER_SELECTOR.get()));
        player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(SpellDuelItems.POINT_SELECTOR.get()));
        source.sendSuccess(() -> Component.literal("[法术决斗] 已获得玩家选择器和点位选择器"), false);
        return 1;
    }

    private static int setPoint(CommandSourceStack source, SpellDuelGroup.Team team, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        boolean ok = SpellDuelEvents.manager(source.getServer()).setPointFromCommand(id, team, player);
        source.sendSuccess(() -> Component.literal(ok ? "[法术决斗] 已设置 " + id + " 的 " + team + " 点位" : "[法术决斗] 决斗组不存在、已开始或无法修改"), true);
        return ok ? 1 : 0;
    }

    private static int giveShop(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        player.getInventory().placeItemBackInInventory(SpellDuelShop.shopStack());
        player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(SpellDuelItems.SHOP_EDITOR.get()));
        source.sendSuccess(() -> Component.literal("[法术决斗] 已获得商店木桶和商店编辑器"), false);
        return 1;
    }

    private static int display(CommandSourceStack source, boolean enabled) {
        SpellDuelManager manager = SpellDuelEvents.manager(source.getServer());
        manager.setDisplayEnabled(enabled);
        SpellDuelNetwork.broadcastDisplay(source.getServer(), enabled);
        source.sendSuccess(() -> Component.literal("[法术决斗] HUD 已" + (enabled ? "开启" : "关闭")), true);
        return 1;
    }

    private static int setHudPosition(CommandSourceStack source, int x, int y) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        SpellDuelNetwork.sendHudPosition(player, x, y);
        source.sendSuccess(() -> Component.literal("[法术决斗] HUD 坐标已设置为 x=" + x + " y=" + y), false);
        return 1;
    }

    private static int clearAll(CommandSourceStack source) {
        int count = SpellDuelEvents.manager(source.getServer()).clearGroups();
        source.sendSuccess(() -> Component.literal("[法术决斗] 已清理全部决斗组：" + count), true);
        return count;
    }

    private static int clearPlayersAll(CommandSourceStack source) {
        SpellDuelEvents.manager(source.getServer()).clearPlayers();
        source.sendSuccess(() -> Component.literal("[法术决斗] 已清理所有玩家组"), true);
        return 1;
    }

    private static int clearPlayers(CommandSourceStack source, String id) {
        boolean cleared = SpellDuelEvents.manager(source.getServer()).clearPlayers(id);
        source.sendSuccess(() -> Component.literal("[法术决斗] " + (cleared ? "已清理玩家组：" : "找不到决斗组：") + id), true);
        return cleared ? 1 : 0;
    }

    private static int clearPointsAll(CommandSourceStack source) {
        SpellDuelEvents.manager(source.getServer()).clearPoints();
        source.sendSuccess(() -> Component.literal("[法术决斗] 已清理所有点位"), true);
        return 1;
    }

    private static int clearPoints(CommandSourceStack source, String id) {
        boolean cleared = SpellDuelEvents.manager(source.getServer()).clearPoints(id);
        source.sendSuccess(() -> Component.literal("[法术决斗] " + (cleared ? "已清理点位：" : "找不到决斗组：") + id), true);
        return cleared ? 1 : 0;
    }
}
