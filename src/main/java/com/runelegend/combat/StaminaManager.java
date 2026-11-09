package com.runelegend.combat;

import java.util.Objects;

/** Integral guard stamina math; timing follows server ticks. */
public final class StaminaManager {
    private final GuardConfig config;

    public StaminaManager(GuardConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public GuardConfig config() { return config; }
    public int max() { return config.max(); }

    public boolean canStart(int value) { return value >= config.minToStart(); }
    public int drain(int value) { return Math.max(0, value - config.drainPerTick()); }
    public int recover(int value, long tick, long stoppedAt) {
        return tick - stoppedAt > config.regenDelayTicks()
                ? Math.min(config.max(), value + config.regenPerTick()) : value;
    }
}
