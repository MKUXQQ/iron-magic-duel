package com.example.scrollspellicons.server;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = "iron_magic_duel")
public final class SpellGravityCommand {
    private static final SpellGravityState STATE = new SpellGravityState();

    private SpellGravityCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        var command = Commands.literal("ironspell")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("gravity")
                        .then(Commands.literal("on").executes(context -> setGravity(context.getSource(), true)))
                        .then(Commands.literal("off").executes(context -> setGravity(context.getSource(), false))));
        dispatcher.register(command);
        dispatcher.register(Commands.literal("spellgravity")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("on").executes(context -> setGravity(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setGravity(context.getSource(), false))));
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (STATE.isNoGravity() && !event.getLevel().isClientSide() && isIronSpellProjectile(event.getEntity())) {
            event.getEntity().setNoGravity(true);
        }
    }

    private static int setGravity(CommandSourceStack source, boolean noGravity) {
        STATE.setNoGravity(noGravity);
        int affected = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (isIronSpellProjectile(entity)) {
                    entity.setNoGravity(noGravity);
                    affected++;
                }
            }
        }
        String status = noGravity ? "开启" : "关闭";
        int affectedEntities = affected;
        source.sendSuccess(() -> Component.literal("铁魔法法术无下坠已" + status + "，影响 " + affectedEntities + " 个法术投射物"), true);
        return affected;
    }

    private static boolean isIronSpellProjectile(Entity entity) {
        String name = entity.getClass().getName();
        return name.startsWith("io.redspace.ironsspellbooks.entity.spells.")
                && name.endsWith("Projectile");
    }
}
