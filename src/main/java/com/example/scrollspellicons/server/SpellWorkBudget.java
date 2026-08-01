package com.example.scrollspellicons.server;

/** Tick-local budget for deferrable pure spell calculations. */
public final class SpellWorkBudget {
    private long tick = Long.MIN_VALUE;
    private long remainingNanos;

    public void beginTick(long tickId, long budgetNanos) {
        tick = tickId;
        remainingNanos = Math.max(0, budgetNanos);
    }

    public boolean tryConsume(long nanos) {
        long cost = Math.max(0, nanos);
        if (remainingNanos != 0 && cost > remainingNanos) {
            return false;
        }
        if (remainingNanos != 0) {
            remainingNanos -= cost;
        }
        return true;
    }

    public long remainingNanos() {
        return remainingNanos;
    }

    public long tick() {
        return tick;
    }
}
