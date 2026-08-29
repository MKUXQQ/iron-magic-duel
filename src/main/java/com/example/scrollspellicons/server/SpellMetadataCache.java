package com.example.scrollspellicons.server;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Cache for immutable spell metadata and other repeatable pure calculations. */
public final class SpellMetadataCache {
    private final ConcurrentHashMap<String, Object> values = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String id, Supplier<T> supplier) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(supplier, "supplier");
        return (T) values.computeIfAbsent(id, ignored -> Objects.requireNonNull(supplier.get(), "supplier result"));
    }

    public void clear() {
        values.clear();
    }

    public int size() {
        return values.size();
    }
}
