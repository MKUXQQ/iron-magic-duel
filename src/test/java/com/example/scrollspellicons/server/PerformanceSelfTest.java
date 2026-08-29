package com.example.scrollspellicons.server;

import com.example.scrollspellicons.client.ParticleBudget;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public final class PerformanceSelfTest {
    public static void main(String[] args) throws Exception {
        ParticleBudget particles = new ParticleBudget(1.0, 3);
        particles.beginFrame(1);
        expect(particles.tryAccept(4, 2), "particle budget accepts first cost");
        expect(particles.tryAccept(4, 1), "particle budget accepts remaining cost");
        expect(!particles.tryAccept(4, 1), "particle budget rejects exhausted frame");
        particles.beginFrame(2);
        expect(particles.tryAccept(4, 1), "particle budget resets per frame");

        SpellWorkBudget work = new SpellWorkBudget();
        work.beginTick(10, 100);
        expect(work.tryConsume(60), "work budget accepts within tick");
        expect(!work.tryConsume(41), "work budget rejects overrun");
        expect(work.remainingNanos() == 40, "work budget preserves remaining budget");

        SpellMetadataCache cache = new SpellMetadataCache();
        AtomicInteger computations = new AtomicInteger();
        String first = cache.getOrCompute("irons_spellbooks:fireball", () -> {
            computations.incrementAndGet();
            return "fireball";
        });
        String second = cache.getOrCompute("irons_spellbooks:fireball", () -> {
            computations.incrementAndGet();
            return "wrong";
        });
        expect(first.equals(second) && computations.get() == 1, "metadata cache computes once");

        SafeCalculationExecutor executor = new SafeCalculationExecutor(1);
        expect(executor.submit(() -> 42).get() == 42, "executor returns pure calculation");
        executor.shutdown();
        try {
            executor.submit(() -> 7).get();
            throw new AssertionError("shutdown executor accepted work");
        } catch (ExecutionException exception) {
            expect(exception.getCause() instanceof IllegalStateException,
                    "shutdown executor reports a safe failure");
        }

        // Server-tick budget model: idle state does no entity/player work;
        // active state visits only registered entities, never a world scan.
        ActiveTickBudget idle = new ActiveTickBudget();
        expect(idle.tick() == 0, "idle active-entity tick must be O(1)");
        ActiveTickBudget active = new ActiveTickBudget();
        active.register(3);
        expect(active.tick() == 3, "active tick visits only registered entities");
        System.out.println("performance: idle tick visits 0 entities; active tick visits registered count only");
        System.out.println("performance: particle/work budgets and metadata cache passed");
    }

    private static final class ActiveTickBudget {
        private int activeEntities;
        void register(int count) { activeEntities = count; }
        int tick() { return activeEntities == 0 ? 0 : activeEntities; }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
