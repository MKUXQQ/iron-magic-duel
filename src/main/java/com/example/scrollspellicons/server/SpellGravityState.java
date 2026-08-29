package com.example.scrollspellicons.server;

public final class SpellGravityState {
    private volatile boolean noGravity;

    public boolean isNoGravity() {
        return noGravity;
    }

    public void setNoGravity(boolean noGravity) {
        this.noGravity = noGravity;
    }
}
