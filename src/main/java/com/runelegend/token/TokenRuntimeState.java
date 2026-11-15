package com.runelegend.token;

public final class TokenRuntimeState {
    public int charge;
    public long cooldownUntil;
    public long triggeredUntil;
    public long activeUntil;

    public void flash(long tick) { triggeredUntil = tick + 12; }
}
