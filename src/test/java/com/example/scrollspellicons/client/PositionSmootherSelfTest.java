package com.example.scrollspellicons.client;

public final class PositionSmootherSelfTest {
    public static void main(String[] args) {
        PositionSmoother.Position result = PositionSmoother.lerp(
                new PositionSmoother.Position(0, 0, 0),
                new PositionSmoother.Position(10, 4, -2),
                0.25);
        expect(result.x() == 2.5 && result.y() == 1.0 && result.z() == -0.5,
                "position smoothing uses the configured interpolation factor");
        expect(PositionSmoother.factorForSpeed(0.01) == 1.0,
                "slow entities are not delayed");
        expect(PositionSmoother.factorForSpeed(1.0) < 1.0,
                "fast entities are smoothed");
    }

    private static void expect(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
