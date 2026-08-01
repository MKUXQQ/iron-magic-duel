package com.example.scrollspellicons.server;

import com.example.scrollspellicons.config.PerformanceConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.concurrent.Future;

import static com.example.scrollspellicons.IronSpellPerformance.LOGGER;

@EventBusSubscriber(modid = "iron_magic_duel", bus = EventBusSubscriber.Bus.GAME)
public final class ServerPerformanceEvents {
    private static final SpellWorkBudget WORK_BUDGET = new SpellWorkBudget();
    private static final SpellMetadataCache METADATA_CACHE = new SpellMetadataCache();
    private static SafeCalculationExecutor executor;
    private static long tick;
    private static long lastReportNanos = System.nanoTime();
    private static long ticksSinceReport;

    private ServerPerformanceEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (PerformanceConfig.SERVER.enableSafeAsyncCalculations.get()) {
            int workers = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));
            executor = new SafeCalculationExecutor(workers);
        }
        METADATA_CACHE.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        METADATA_CACHE.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        tick++;
        long millis = PerformanceConfig.SERVER.maxSpellScanMillisPerTick.get();
        WORK_BUDGET.beginTick(tick, millis == 0 ? 0 : millis * 1_000_000L);
        ticksSinceReport++;
        long now = System.nanoTime();
        if (PerformanceConfig.SERVER.debugPerformanceLogging.get() && now - lastReportNanos >= 1_000_000_000L) {
            LOGGER.info("Server spell performance: {} ticks/s, remaining work budget={}ns, metadata cache={}",
                    ticksSinceReport, WORK_BUDGET.remainingNanos(), METADATA_CACHE.size());
            ticksSinceReport = 0;
            lastReportNanos = now;
        }
    }

    public static SpellMetadataCache metadataCache() {
        return METADATA_CACHE;
    }

    public static boolean canSpendPureCalculation(long nanos) {
        return PerformanceConfig.SERVER.enableServerOptimizations.get() && WORK_BUDGET.tryConsume(nanos);
    }

    public static <T> Future<T> submitPureCalculation(java.util.concurrent.Callable<T> calculation) {
        if (executor == null || !PerformanceConfig.SERVER.enableSafeAsyncCalculations.get()) {
            throw new IllegalStateException("safe spell calculation executor is not enabled");
        }
        return executor.submit(calculation);
    }
}
