package com.runelegend.api;

import java.util.Locale;

/** Round counters and damage totals. Damage values in Java are measured in health points. */
public enum RoundMetric {
    DOWNS, REVIVED, RESCUES, ELIMINATIONS,
    HITS_DEALT, HITS_TAKEN, UNATTRIBUTED_HITS_TAKEN,
    DAMAGE_DEALT, DAMAGE_TAKEN, DAMAGE_BLOCKED, DAMAGE_ABSORBED, UNATTRIBUTED_DAMAGE_TAKEN;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isDamage() {
        return switch (this) {
            case DAMAGE_DEALT, DAMAGE_TAKEN, DAMAGE_BLOCKED, DAMAGE_ABSORBED, UNATTRIBUTED_DAMAGE_TAKEN -> true;
            default -> false;
        };
    }
}
