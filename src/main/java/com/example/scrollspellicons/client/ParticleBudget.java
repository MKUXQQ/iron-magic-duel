package com.example.scrollspellicons.client;

/** Allocation-free per-frame admission control for spell particles. */
public final class ParticleBudget {
    private static final double DEFAULT_MAX_DISTANCE = 10.0;

    private final double maxDistanceSquared;
    private final int maxCostPerFrame;
    private long frameId = Long.MIN_VALUE;
    private int usedCost;

    public ParticleBudget(double distanceMultiplier, int maxCostPerFrame) {
        double multiplier = Math.max(0.0, distanceMultiplier);
        this.maxDistanceSquared = DEFAULT_MAX_DISTANCE * DEFAULT_MAX_DISTANCE * multiplier * multiplier;
        this.maxCostPerFrame = Math.max(0, maxCostPerFrame);
    }

    public void beginFrame(long newFrameId) {
        if (frameId != newFrameId) {
            frameId = newFrameId;
            usedCost = 0;
        }
    }

    public boolean tryAccept(double squaredDistance, int cost) {
        if (squaredDistance > maxDistanceSquared) {
            return false;
        }
        int safeCost = Math.max(0, cost);
        if (maxCostPerFrame != 0 && (long) usedCost + safeCost > maxCostPerFrame) {
            return false;
        }
        usedCost += safeCost;
        return true;
    }
}
