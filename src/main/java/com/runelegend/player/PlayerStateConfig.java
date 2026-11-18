package com.runelegend.player;

import com.google.gson.JsonObject;
import com.runelegend.RuneLegendMod;

/** Immutable instance configuration, read once when the logical server starts. */
public record PlayerStateConfig(double reviveDistance, double reviveDurationSeconds,
                                double downedDurationSeconds, double reviveHealthPercent) {
    public static final PlayerStateConfig DEFAULT = new PlayerStateConfig(2, 2, 15, 50);

    public PlayerStateConfig {
        if (!Double.isFinite(reviveDistance) || reviveDistance <= 0 || !Double.isFinite(reviveDistance * reviveDistance))
            throw new IllegalArgumentException("revive_distance must be positive with a finite square");
        ticks(reviveDurationSeconds);
        ticks(downedDurationSeconds);
        if (!Double.isFinite(reviveHealthPercent) || reviveHealthPercent <= 0 || reviveHealthPercent > 100)
            throw new IllegalArgumentException("revive_health_percent must be in (0, 100]");
    }

    public int reviveTicks() { return ticks(reviveDurationSeconds); }
    public int downedTicks() { return ticks(downedDurationSeconds); }
    public double distanceSquared() { return reviveDistance * reviveDistance; }

    private static int ticks(double seconds) {
        double value = Math.ceil(seconds * 20);
        if (!Double.isFinite(seconds) || seconds <= 0 || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Duration must be positive and fit in ticks");
        return Math.max(1, (int) value);
    }

    public static PlayerStateConfig fromJson(JsonObject root) {
        try {
            return new PlayerStateConfig(number(root, "revive_distance", DEFAULT.reviveDistance),
                    number(root, "revive_duration_seconds", DEFAULT.reviveDurationSeconds),
                    number(root, "downed_duration_seconds", DEFAULT.downedDurationSeconds),
                    number(root, "revive_health_percent", DEFAULT.reviveHealthPercent));
        } catch (RuntimeException error) {
            RuneLegendMod.LOGGER.error("Invalid player state config; using defaults", error);
            return DEFAULT;
        }
    }

    private static double number(JsonObject root, String key, double fallback) {
        if (!root.has(key)) return fallback;
        var value = root.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(key + " must be a number");
        return value.getAsDouble();
    }

    public JsonObject toJson() {
        var root = new JsonObject();
        root.addProperty("revive_distance", reviveDistance);
        root.addProperty("revive_duration_seconds", reviveDurationSeconds);
        root.addProperty("downed_duration_seconds", downedDurationSeconds);
        root.addProperty("revive_health_percent", reviveHealthPercent);
        return root;
    }
}
