package com.runelegend.token;

public final class TokenRuntimeState {
    public int charge;
    public long cooldownUntil;
    public long triggeredUntil;
    public long activeUntil;
    /** Set by addMeleeDamage for the swing in progress; consumed by that swing's MELEE event. */
    public boolean meleeProcPending;

    public void flash(long tick) { triggeredUntil = tick + 12; }
}
