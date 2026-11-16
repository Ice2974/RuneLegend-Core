package com.runelegend.player;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.runelegend.RuneLegendMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Immutable instance configuration, read once when the logical server starts. */
public record PlayerStateConfig(double reviveDistance, double reviveDurationSeconds,
                                double downedDurationSeconds, double reviveHealthPercent) {
    private static final com.google.gson.Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create();
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

    public static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("runelegend-core/player_states.json");
    }

    public static PlayerStateConfig loadOrGenerate(Path path) {
        path = path.toAbsolutePath();
        if (Files.notExists(path)) {
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(DEFAULT.toJson()) + "\n", StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException error) {
                RuneLegendMod.LOGGER.error("Could not write default player state config {}", path, error);
            }
            return DEFAULT;
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return fromJson(GSON.fromJson(reader, JsonObject.class));
        } catch (IOException | RuntimeException error) {
            RuneLegendMod.LOGGER.error("Could not read player state config {}; using defaults", path, error);
            return DEFAULT;
        }
    }

    public static PlayerStateConfig fromJson(JsonObject root) {
        try {
            if (root == null || !root.has("schemaVersion") || !"1".equals(root.get("schemaVersion").toString()))
                throw new IllegalArgumentException("Expected integer schemaVersion 1");
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
        root.addProperty("schemaVersion", 1);
        root.addProperty("revive_distance", reviveDistance);
        root.addProperty("revive_duration_seconds", reviveDurationSeconds);
        root.addProperty("downed_duration_seconds", downedDurationSeconds);
        root.addProperty("revive_health_percent", reviveHealthPercent);
        return root;
    }
}
