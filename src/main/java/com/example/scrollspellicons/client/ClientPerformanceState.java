package com.example.scrollspellicons.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientPerformanceState {
    private static final Map<ResourceLocation, Boolean> SPELL_RESOURCES = new ConcurrentHashMap<>();
    private static volatile long preloadedResourceCount;
    private static volatile long frameId;

    private ClientPerformanceState() {
    }

    public static void beginFrame() {
        frameId++;
    }

    public static long frameId() {
        return frameId;
    }

    public static void replaceSpellResources(Iterable<ResourceLocation> resources) {
        SPELL_RESOURCES.clear();
        for (ResourceLocation resource : resources) {
            SPELL_RESOURCES.put(resource, Boolean.TRUE);
        }
        preloadedResourceCount = SPELL_RESOURCES.size();
    }

    public static void clear() {
        SPELL_RESOURCES.clear();
        preloadedResourceCount = 0;
    }

    public static long preloadedResourceCount() {
        return preloadedResourceCount;
    }

    public static Map<ResourceLocation, Boolean> snapshot() {
        return Collections.unmodifiableMap(Map.copyOf(SPELL_RESOURCES));
    }
}
