package com.example.scrollspellicons.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public final class PerformanceConfig {
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
        Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
    }

    private PerformanceConfig() {
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue enableClientOptimizations;
        public final ForgeConfigSpec.DoubleValue particleDistanceMultiplier;
        public final ForgeConfigSpec.IntValue maxParticlesPerFrame;
        public final ForgeConfigSpec.BooleanValue preloadSpellResources;
        public final ForgeConfigSpec.IntValue projectileRenderDistance;
        public final ForgeConfigSpec.IntValue maxVisibleSpellProjectiles;

        private Client(ForgeConfigSpec.Builder builder) {
            builder.comment("Iron Magic Duel - client settings").push("client");
            enableClientOptimizations = builder.comment("Enable client-side spell rendering/resource optimizations.")
                    .define("enableClientOptimizations", true);
            particleDistanceMultiplier = builder.comment("Particle visibility distance multiplier. 0 disables spell particles; 1 keeps the normal distance.")
                    .defineInRange("particleDistanceMultiplier", 1.0, 0.0, 4.0);
            maxParticlesPerFrame = builder.comment("Maximum weighted spell particles admitted per frame. 0 means unlimited.")
                    .defineInRange("maxParticlesPerFrame", 4096, 0, Integer.MAX_VALUE);
            preloadSpellResources = builder.comment("Preload spell icon resources after a client resource reload.")
                    .define("preloadSpellResources", true);
            projectileRenderDistance = builder.comment("Maximum client render distance for Iron spell projectiles. 0 means unlimited.")
                    .defineInRange("projectileRenderDistance", 128, 0, 512);
            maxVisibleSpellProjectiles = builder.comment("Maximum visible Iron spell projectiles at once. 0 means unlimited.")
                    .defineInRange("maxVisibleSpellProjectiles", 256, 0, 4096);
            builder.pop();
        }
    }

    public static final class Server {
        public final ForgeConfigSpec.BooleanValue enableServerOptimizations;
        public final ForgeConfigSpec.LongValue maxSpellScanMillisPerTick;
        public final ForgeConfigSpec.BooleanValue enableSafeAsyncCalculations;
        public final ForgeConfigSpec.BooleanValue debugPerformanceLogging;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> fakePlayers;

        private Server(ForgeConfigSpec.Builder builder) {
            builder.comment("Iron Magic Duel - server settings").push("server");
            enableServerOptimizations = builder.comment("Enable server-side safe spell workload optimizations.")
                    .define("enableServerOptimizations", true);
            maxSpellScanMillisPerTick = builder.comment("Budget in milliseconds for deferrable pure spell calculations per tick.")
                    .defineInRange("maxSpellScanMillisPerTick", 2L, 0L, 50L);
            enableSafeAsyncCalculations = builder.comment("Allow copied immutable spell math to run on a small worker pool.")
                    .define("enableSafeAsyncCalculations", true);
            debugPerformanceLogging = builder.comment("Log one performance summary per second.")
                    .define("debugPerformanceLogging", false);
            fakePlayers = builder.comment("Names passed to the /player <name> spawn command by /spell_duel fake_players. Add or remove names here.")
                    .defineList("fakePlayers", List.of("Alex", "XingYear_", "Steve", "fomg23333"),
                            value -> value instanceof String string && !string.isBlank());
            builder.pop();
        }
    }
}
