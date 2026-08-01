package com.example.scrollspellicons.server;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpellMetadataCacheTest {
    @Test
    void computesEachSpellOnlyOnceUntilCleared() {
        SpellMetadataCache cache = new SpellMetadataCache();
        AtomicInteger computations = new AtomicInteger();

        assertEquals("fireball", cache.getOrCompute("irons_spellbooks:fireball", () -> {
            computations.incrementAndGet();
            return "fireball";
        }));
        assertEquals("fireball", cache.getOrCompute("irons_spellbooks:fireball", () -> {
            computations.incrementAndGet();
            return "wrong";
        }));
        assertEquals(1, computations.get());

        cache.clear();
        assertEquals("new", cache.getOrCompute("irons_spellbooks:fireball", () -> {
            computations.incrementAndGet();
            return "new";
        }));
        assertEquals(2, computations.get());
    }
}
