package com.runelegend.network;

import net.minecraft.util.Mth;

import java.util.Objects;

/** The server's display value for a single player-list entry. */
public record TabStatus(Kind kind, int value) {
    public enum Kind { HIGH_HEALTH, MEDIUM_HEALTH, LOW_HEALTH, DOWNED, ELIMINATED }

    public static final TabStatus ELIMINATED = new TabStatus(Kind.ELIMINATED, 0);

    public TabStatus {
        Objects.requireNonNull(kind);
        if (value < 0 || (kind == Kind.ELIMINATED && value != 0) || (kind == Kind.DOWNED && value == 0)) {
            throw new IllegalArgumentException("Invalid Tab status value");
        }
    }

    static TabStatus health(float health, float maximum) {
        double scaled = health * 100.0;
        var kind = scaled >= maximum * 80.0 ? Kind.HIGH_HEALTH
                : scaled > maximum * 30.0 ? Kind.MEDIUM_HEALTH : Kind.LOW_HEALTH;
        return new TabStatus(kind, Mth.ceil(health));
    }
}
