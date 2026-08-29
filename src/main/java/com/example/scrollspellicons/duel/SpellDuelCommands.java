package com.example.scrollspellicons.duel;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.example.scrollspellicons.config.PerformanceConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

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
                .then(Commands.literal("duel")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        context.getSource().getServer().getPlayerList().getPlayers().stream()
                                                .map(player -> player.getGameProfile().getName()), builder))
                                .executes(context -> challenge(context.getSource(),
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("surround")
                        .then(Commands.literal("lock").requires(source -> source.hasPermission(2))
                                .executes(context -> lockSurround(context.getSource())))
                        // Ownership is checked by the manager so a de-opped creator can still release
                        // their own session, while every other player is refused server-side.
                        .then(Commands.literal("release").executes(context -> releaseSurround(context.getSource()))))
                .then(Commands.literal("no_cast").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("select")
                                .then(Commands.argument("zone", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                SpellDuelEvents.noCastZones(context.getSource().getServer()).zones().stream().map(NoCastZoneManager.Zone::id), builder))
                                        .executes(context -> selectNoCastZone(context.getSource(), StringArgumentType.getString(context, "zone")))
                                        .then(Commands.argument("range", IntegerArgumentType.integer(0, 10000))
                                                .executes(context -> editNoCastZone(context.getSource(),
                                                        StringArgumentType.getString(context, "zone"),
                                                        IntegerArgumentType.getInteger(context, "range"))))))
                        .then(Commands.literal("remove")
                                .then(Commands.literal("all").executes(context -> clearNoCastZones(context.getSource())))
                                .then(Commands.argument("zone", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                SpellDuelEvents.noCastZones(context.getSource().getServer()).zones().stream().map(NoCastZoneManager.Zone::id), builder))
                                        .executes(context -> removeNoCastZone(context.getSource(), StringArgumentType.getString(context, "zone"))))))
                .then(Commands.literal("point").requires(source -> source.hasPermission(2))
                        .then(groupArgument().then(Commands.literal("a").executes(context -> setPoint(context.getSource(), SpellDuelGroup.Team.A, StringArgumentType.getString(context, "groupId"))))
                                .then(Commands.literal("b").executes(context -> setPoint(context.getSource(), SpellDuelGroup.Team.B, StringArgumentType.getString(context, "groupId"))))))
                .then(Commands.literal("shop").requires(source -> source.hasPermission(2))
                        .executes(context -> giveShop(context.getSource())))
                .then(Commands.literal("display").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("on").executes(context -> display(context.getSource(), true)))
                        .then(Commands.literal("off").executes(context -> display(context.getSource(), false))))
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
        for (String name : PerformanceConfig.serverValues().fakePlayers()) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "player " + name + " spawn");
        }
        source.sendSuccess(() -> Component.literal("[法术决斗] 已召唤假人"), true);
        return 1;
    }

    private static int giveTools(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(SpellDuelItems.PLAYER_SELECTOR.get()));
        player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(SpellDuelItems.POINT_SELECTOR.get()));
        player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(SpellDuelItems.NO_CAST_SELECTOR.get()));
        player.getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(SpellDuelItems.PLAYER_INTERACTOR.get()));
        source.sendSuccess(() -> Component.literal("[法术决斗] 已获得玩家选择器、点位选择器和玩家交互器"), false);
        return 1;
    }

    private static int saveNoCastZone(CommandSourceStack source, int range) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        NoCastZoneManager.Zone zone = SpellDuelEvents.noCastZones(source.getServer()).savePendingZone(player, range);
        if (zone == null) {
            source.sendFailure(Component.literal("[法术决斗] 请先手持禁法区域工具右键方块标记中心点"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[法术决斗] 已创建 " + zone.id() + "：中心 " + zone.x() + ", " + zone.z()
                + "，正方形范围半径 " + zone.range() + " 格，区域内禁止施法"), true);
        return 1;
    }

    private static int selectNoCastZone(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean selected = SpellDuelEvents.noCastZones(source.getServer()).selectExisting(source.getPlayerOrException(), id);
        if (selected) source.sendSuccess(() -> Component.literal("[法术决斗] 正在编辑禁法区域 " + id + "；使用 /spell_duel no_cast range <范围> 保存"), false);
        else source.sendFailure(Component.literal("[法术决斗] 找不到禁法区域 " + id));
        return selected ? 1 : 0;
    }

    private static int challenge(CommandSourceStack source, String targetName)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer challenger = source.getPlayerOrException();
        net.minecraft.server.level.ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendFailure(Component.literal("[法术决斗] 找不到在线玩家：" + targetName));
            return 0;
        }
        SpellDuelManager.ChallengeResult result = SpellDuelEvents.manager(source.getServer())
                .requestChallenge(challenger, target);
        if (result.accepted()) source.sendSuccess(() -> Component.literal("[法术决斗] " + result.message()), false);
        else source.sendFailure(Component.literal("[法术决斗] " + result.message()));
        return result.accepted() ? 1 : 0;
    }

    private static int lockSurround(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SpellDuelManager.SurroundResult result = SpellDuelEvents.manager(source.getServer())
                .lockSurround(source.getPlayerOrException());
        if (result.accepted()) source.sendSuccess(() -> Component.literal("[法术决斗] " + result.message()), true);
        else source.sendFailure(Component.literal("[法术决斗] " + result.message()));
        return result.accepted() ? 1 : 0;
    }

    private static int releaseSurround(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SpellDuelManager.SurroundResult result = SpellDuelEvents.manager(source.getServer())
                .releaseSurround(source.getPlayerOrException());
        if (result.accepted()) source.sendSuccess(() -> Component.literal("[法术决斗] " + result.message()), true);
        else source.sendFailure(Component.literal("[法术决斗] " + result.message()));
        return result.accepted() ? 1 : 0;
    }

    private static int editNoCastZone(CommandSourceStack source, String id, int range) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        NoCastZoneManager zones = SpellDuelEvents.noCastZones(source.getServer());
        if (!zones.selectExisting(player, id)) {
            source.sendFailure(Component.literal("[法术决斗] 找不到禁法区域 " + id));
            return 0;
        }
        NoCastZoneManager.Zone zone = zones.savePendingZone(player, range);
        source.sendSuccess(() -> Component.literal("[法术决斗] 已修改禁法区域 " + id + " 的范围为 " + zone.range() + " 格"), true);
        return 1;
    }

    private static int removeNoCastZone(CommandSourceStack source, String id) {
        boolean removed = SpellDuelEvents.noCastZones(source.getServer()).remove(id);
        if (removed) source.sendSuccess(() -> Component.literal("[法术决斗] 已删除禁法区域 " + id), true);
        else source.sendFailure(Component.literal("[法术决斗] 找不到禁法区域 " + id));
        return removed ? 1 : 0;
    }

    private static int clearNoCastZones(CommandSourceStack source) {
        int count = SpellDuelEvents.noCastZones(source.getServer()).clearAll();
        source.sendSuccess(() -> Component.literal("[法术决斗] 已清除 " + count + " 个禁法区域"), true);
        return count;
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
        source.sendSuccess(() -> Component.literal("[法术决斗] 已获得商店嗅探兽蛋和商店编辑器"), false);
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
