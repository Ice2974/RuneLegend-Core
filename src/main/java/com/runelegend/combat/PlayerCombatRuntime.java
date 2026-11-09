package com.runelegend.combat;

final class PlayerCombatRuntime {
    static final long NOT_USING = -1;
    int stamina;
    long startedAt = NOT_USING;
    long stoppedAt;
    boolean releaseRequired;

    PlayerCombatRuntime(GuardConfig config) {
        stamina = config.max();
        stoppedAt = -config.regenDelayTicks() - 1L;
    }
}
