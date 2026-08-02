package com.example.scrollspellicons.client;

import com.example.scrollspellicons.config.PerformanceConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@EventBusSubscriber(modid = "iron_magic_duel", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientResourceEvents {
    private ClientResourceEvents() {
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SpellResourcePreloader());
    }

    private static final class SpellResourcePreloader implements PreparableReloadListener {
        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager,
                                              ProfilerFiller preparationProfiler, ProfilerFiller reloadProfiler,
                                              Executor preparationExecutor, Executor reloadExecutor) {
            return CompletableFuture.supplyAsync(() -> {
                if (!PerformanceConfig.CLIENT.preloadSpellResources.get()) {
                    return java.util.List.<ResourceLocation>of();
                }
                return manager.listResources("gui/spell_icons", location -> location.getPath().endsWith(".png"))
                        .keySet().stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
            }, preparationExecutor).thenCompose(barrier::wait).thenAcceptAsync(
                    ClientPerformanceState::replaceSpellResources, reloadExecutor);
        }

        @Override
        public String getName() {
            return "iron_magic_duel_spell_resources";
        }
    }
}
