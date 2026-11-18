package com.runelegend.token;

import com.runelegend.api.token.TokenEffect;

public final class TokenRuntimeState {
    TokenEffect extensionEffect;
    public int charge;
    public long damageSteps;
    public long cooldownUntil;
    public long triggeredUntil;
    public long activeUntil;
    /** Set by addMeleeDamage for the swing in progress; consumed by that swing's MELEE event. */
    public boolean meleeProcPending;
    /** Execution Echo: the damage recorded by the last killing blow, and the tick its decay started. */
    public int recordedDamage;
    public long decayStartedAt;
    public float retributionDamage;
    int storedBlessings;

    public void flash(long tick) { triggeredUntil = tick + 12; }
}
