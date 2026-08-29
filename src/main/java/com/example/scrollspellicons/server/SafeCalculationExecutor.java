package com.example.scrollspellicons.server;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;

/** Executor for copied immutable calculations; it never touches Minecraft world state. */
public final class SafeCalculationExecutor {
    private final ExecutorService executor;

    public SafeCalculationExecutor(int workers) {
        int count = Math.max(1, Math.min(workers, 4));
        this.executor = Executors.newFixedThreadPool(count, runnable -> {
            Thread thread = new Thread(runnable, "iron-spell-performance");
            thread.setDaemon(true);
            return thread;
        });
    }

    public <T> Future<T> submit(Callable<T> calculation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (executor.isShutdown()) {
            result.completeExceptionally(new IllegalStateException("spell calculation executor is shut down"));
            return result;
        }
        try {
            executor.submit(() -> {
                try {
                    result.complete(calculation.call());
                } catch (Throwable throwable) {
                    result.completeExceptionally(throwable);
                }
            });
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
