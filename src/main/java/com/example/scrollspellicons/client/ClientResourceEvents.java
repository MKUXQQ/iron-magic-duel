package com.example.scrollspellicons.client;

import com.example.scrollspellicons.config.PerformanceConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@EventBusSubscriber(modid = "iron_magic_duel", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientResourceEvents {
    private static final AtomicBoolean FIRST_RESOURCE_RELOAD = new AtomicBoolean(true);

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
                boolean skipStartupPreload = FIRST_RESOURCE_RELOAD.getAndSet(false);
                if (!PerformanceConfig.isClientConfigLoaded()) {
                    return java.util.List.<ResourceLocation>of();
                }
                PerformanceConfig.ClientValues config = PerformanceConfig.clientValues();
                if (!config.preloadSpellResources()
                        || (skipStartupPreload && config.deferSpellResourcePreloadAtStartup())) {
                    return java.util.List.<ResourceLocation>of();
                }
                return manager.listResources("gui/spell_icons", location -> location.getPath().endsWith(".png"))
                        .keySet().stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
            }, preparationExecutor).thenCompose(barrier::wait).thenAcceptAsync(
                    resources -> {
                        ClientPerformanceState.replaceSpellResources(resources);
                    }, reloadExecutor);
        }

        @Override
        public String getName() {
            return "iron_magic_duel_spell_resources";
        }
    }
}
