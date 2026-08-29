package com.example.scrollspellicons.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public final class PerformanceConfig {
    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Server SERVER;
    public static final ModConfigSpec SERVER_SPEC;
    private static volatile boolean clientConfigLoaded;
    private static volatile boolean serverConfigLoaded;
    private static volatile ClientValues clientValues = ClientValues.defaults();
    private static volatile ServerValues serverValues = ServerValues.defaults();

    static {
        Pair<Client, ModConfigSpec> client = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
        Pair<Server, ModConfigSpec> server = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
    }

    private PerformanceConfig() {
    }

    /** Loading/reloading events are the only code paths allowed to read ConfigValue.get(). */
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        refresh(event.getConfig());
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        refresh(event.getConfig());
    }

    private static void refresh(ModConfig config) {
        if (config.getSpec() == CLIENT_SPEC) {
            clientValues = new ClientValues(CLIENT.enableClientOptimizations.get(),
                    CLIENT.particleDistanceMultiplier.get(), CLIENT.maxParticlesPerFrame.get(),
                    CLIENT.preloadSpellResources.get(), CLIENT.deferSpellResourcePreloadAtStartup.get(),
                    CLIENT.projectileRenderDistance.get(), CLIENT.maxVisibleSpellProjectiles.get(),
                    CLIENT.debugSprayDiagnostics.get());
            clientConfigLoaded = true;
        } else if (config.getSpec() == SERVER_SPEC) {
            serverValues = new ServerValues(SERVER.enableServerOptimizations.get(),
                    SERVER.maxSpellScanMillisPerTick.get(), SERVER.enableSafeAsyncCalculations.get(),
                    SERVER.deferDuelDataLoadAtStartup.get(), SERVER.deferWorkerPoolAtStartup.get(),
                    SERVER.debugPerformanceLogging.get(), SERVER.debugSprayDiagnostics.get(),
                    List.copyOf(SERVER.fakePlayers.get()));
            serverConfigLoaded = true;
        }
    }

    public static boolean isClientConfigLoaded() {
        return clientConfigLoaded;
    }

    public static boolean isServerConfigLoaded() {
        return serverConfigLoaded;
    }

    public static ClientValues clientValues() {
        return clientValues;
    }

    public static ServerValues serverValues() {
        return serverValues;
    }

    public record ClientValues(boolean enableClientOptimizations, double particleDistanceMultiplier,
                               int maxParticlesPerFrame, boolean preloadSpellResources,
                               boolean deferSpellResourcePreloadAtStartup, int projectileRenderDistance,
                               int maxVisibleSpellProjectiles, boolean debugSprayDiagnostics) {
        private static ClientValues defaults() {
            return new ClientValues(false, 1.0D, 0, false, true, 128, 256, false);
        }
    }

    public record ServerValues(boolean enableServerOptimizations, long maxSpellScanMillisPerTick,
                               boolean enableSafeAsyncCalculations, boolean deferDuelDataLoadAtStartup,
                               boolean deferWorkerPoolAtStartup, boolean debugPerformanceLogging,
                               boolean debugSprayDiagnostics, List<String> fakePlayers) {
        private static ServerValues defaults() {
            return new ServerValues(false, 0L, false, true, true, false, false, List.of());
        }
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue enableClientOptimizations;
        public final ModConfigSpec.DoubleValue particleDistanceMultiplier;
        public final ModConfigSpec.IntValue maxParticlesPerFrame;
        public final ModConfigSpec.BooleanValue preloadSpellResources;
        public final ModConfigSpec.BooleanValue deferSpellResourcePreloadAtStartup;
        public final ModConfigSpec.IntValue projectileRenderDistance;
        public final ModConfigSpec.IntValue maxVisibleSpellProjectiles;
        public final ModConfigSpec.BooleanValue debugSprayDiagnostics;

        private Client(ModConfigSpec.Builder builder) {
            builder.comment("Iron Magic Duel - client settings").push("client");
            enableClientOptimizations = builder.comment("Enable client-side spell rendering/resource optimizations.")
                    .define("enableClientOptimizations", true);
            particleDistanceMultiplier = builder.comment("Particle visibility distance multiplier. 0 disables spell particles; 1 keeps the normal distance.")
                    .defineInRange("particleDistanceMultiplier", 1.0, 0.0, 4.0);
            maxParticlesPerFrame = builder.comment("Maximum weighted spell particles admitted per frame. 0 means unlimited.")
                    .defineInRange("maxParticlesPerFrame", 4096, 0, Integer.MAX_VALUE);
            preloadSpellResources = builder.comment("Preload spell icon resources after a client resource reload.")
                    .define("preloadSpellResources", true);
            deferSpellResourcePreloadAtStartup = builder.comment("Skip the non-essential spell-icon scan during the first startup reload to reduce launch time.")
                    .define("deferSpellResourcePreloadAtStartup", true);
            projectileRenderDistance = builder.comment("Maximum client render distance for Iron spell projectiles. 0 means unlimited.")
                    .defineInRange("projectileRenderDistance", 128, 0, 512);
            maxVisibleSpellProjectiles = builder.comment("Maximum visible Iron spell projectiles at once. 0 means unlimited.")
                    .defineInRange("maxVisibleSpellProjectiles", 256, 0, 4096);
            debugSprayDiagnostics = builder.comment("Enable rate-limited spray diagnostics while troubleshooting AbstractConeProjectile.")
                    .define("debugSprayDiagnostics", false);
            builder.pop();
        }
    }

    public static final class Server {
        public final ModConfigSpec.BooleanValue enableServerOptimizations;
        public final ModConfigSpec.LongValue maxSpellScanMillisPerTick;
        public final ModConfigSpec.BooleanValue enableSafeAsyncCalculations;
        public final ModConfigSpec.BooleanValue deferDuelDataLoadAtStartup;
        public final ModConfigSpec.BooleanValue deferWorkerPoolAtStartup;
        public final ModConfigSpec.BooleanValue debugPerformanceLogging;
        public final ModConfigSpec.BooleanValue debugSprayDiagnostics;
        public final ModConfigSpec.ConfigValue<List<? extends String>> fakePlayers;

        private Server(ModConfigSpec.Builder builder) {
            builder.comment("Iron Magic Duel - server settings").push("server");
            enableServerOptimizations = builder.comment("Enable server-side safe spell workload optimizations.")
                    .define("enableServerOptimizations", true);
            maxSpellScanMillisPerTick = builder.comment("Budget in milliseconds for deferrable pure spell calculations per tick.")
                    .defineInRange("maxSpellScanMillisPerTick", 2L, 0L, 50L);
            enableSafeAsyncCalculations = builder.comment("Allow copied immutable spell math to run on a small worker pool.")
                    .define("enableSafeAsyncCalculations", true);
            deferDuelDataLoadAtStartup = builder.comment("Load duel points and no-cast data only when first needed, instead of during server startup.")
                    .define("deferDuelDataLoadAtStartup", true);
            deferWorkerPoolAtStartup = builder.comment("Create the optional safe-calculation worker pool only when a calculation actually needs it.")
                    .define("deferWorkerPoolAtStartup", true);
            debugPerformanceLogging = builder.comment("Log one performance summary per second.")
                    .define("debugPerformanceLogging", false);
            debugSprayDiagnostics = builder.comment("Enable rate-limited diagnostics only for real AbstractConeProjectile spray entities.")
                    .define("debugSprayDiagnostics", false);
            fakePlayers = builder.comment("Names passed to the /player <name> spawn command by /spell_duel fake_players. Add or remove names here.")
                    .defineList("fakePlayers", List.of("Alex", "XingYear_", "Steve", "fomg23333"),
                            value -> value instanceof String string && !string.isBlank());
            builder.pop();
        }
    }
}
