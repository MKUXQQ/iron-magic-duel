package com.example.scrollspellicons.client;

public final class PositionSmoother {
    private PositionSmoother() {
    }

    public static Position lerp(Position from, Position to, double factor) {
        double clamped = Math.max(0.0, Math.min(1.0, factor));
        return new Position(
                from.x() + (to.x() - from.x()) * clamped,
                from.y() + (to.y() - from.y()) * clamped,
                from.z() + (to.z() - from.z()) * clamped);
    }

    public static double factorForSpeed(double speedSquared) {
        if (speedSquared <= 0.05) {
            return 1.0;
        }
        return 0.72;
    }

    public record Position(double x, double y, double z) {
    }
}
