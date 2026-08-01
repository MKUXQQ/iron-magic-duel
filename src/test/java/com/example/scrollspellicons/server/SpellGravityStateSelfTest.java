package com.example.scrollspellicons.server;

public final class SpellGravityStateSelfTest {
    public static void main(String[] args) {
        SpellGravityState state = new SpellGravityState();
        expect(!state.isNoGravity(), "gravity is enabled by default");
        state.setNoGravity(true);
        expect(state.isNoGravity(), "no-gravity state can be enabled");
        state.setNoGravity(false);
        expect(!state.isNoGravity(), "no-gravity state can be disabled");
    }

    private static void expect(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
