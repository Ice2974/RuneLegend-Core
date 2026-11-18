package com.runelegend.api;

import java.util.Map;

/** Immutable, sparse statistics for one player in one round. */
public record PlayerRoundStats(Map<RoundMetric, Double> values) {
    public static final PlayerRoundStats EMPTY = new PlayerRoundStats(Map.of());

    public PlayerRoundStats {
        values = Map.copyOf(values);
    }

    public double value(RoundMetric metric) {
        return values.getOrDefault(metric, 0.0);
    }
}
