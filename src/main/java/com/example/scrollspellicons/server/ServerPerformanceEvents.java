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
        if (PerformanceConfig.isServerConfigLoaded()
                && PerformanceConfig.serverValues().enableSafeAsyncCalculations()
                && !PerformanceConfig.serverValues().deferWorkerPoolAtStartup()) {
            startWorkerPool();
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
        if (!PerformanceConfig.isServerConfigLoaded()) {
            return;
        }
        tick++;
        PerformanceConfig.ServerValues config = PerformanceConfig.serverValues();
        long millis = config.maxSpellScanMillisPerTick();
        WORK_BUDGET.beginTick(tick, millis == 0 ? 0 : millis * 1_000_000L);
        ticksSinceReport++;
        long now = System.nanoTime();
        if (config.debugPerformanceLogging() && now - lastReportNanos >= 1_000_000_000L) {
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
        return PerformanceConfig.isServerConfigLoaded()
                && PerformanceConfig.serverValues().enableServerOptimizations()
                && WORK_BUDGET.tryConsume(nanos);
    }

    public static <T> Future<T> submitPureCalculation(java.util.concurrent.Callable<T> calculation) {
        if (!PerformanceConfig.isServerConfigLoaded()
                || !PerformanceConfig.serverValues().enableSafeAsyncCalculations()) {
            throw new IllegalStateException("safe spell calculation executor is not enabled");
        }
        startWorkerPool();
        return executor.submit(calculation);
    }

    private static synchronized void startWorkerPool() {
        if (executor != null) return;
        int workers = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));
        executor = new SafeCalculationExecutor(workers);
    }
}
