package com.example.scrollspellicons.server;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class SafeCalculationExecutorTest {
    @Test
    void runsPureCalculationsAndRejectsWorkAfterShutdown() throws Exception {
        SafeCalculationExecutor executor = new SafeCalculationExecutor(1);
        try {
            assertEquals(42, executor.submit(() -> 42).get());
        } finally {
            executor.shutdown();
        }

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> executor.submit(() -> 7).get());
        assertInstanceOf(IllegalStateException.class, exception.getCause());
    }
}
