package com.runelegend.token.effect;

import com.runelegend.token.*;
import com.runelegend.token.TokenConfig.ExecutionEchoSettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * A melee kill records the damage of that swing after every token addition; each later melee hit adds the
 * remaining record as true damage. The record decays in steps until it reaches zero, then the cooldown starts.
 */
public final class ExecutionEchoEffect implements TokenEffectHandler {
    /** Absorbs floating-point noise so a whole-number damage is not rounded up one point too far. */
    private static final double ROUNDING_TOLERANCE = 1.0E-4;
    private final ExecutionEchoSettings settings;

    public ExecutionEchoEffect(ExecutionEchoSettings settings) {
        this.settings = settings;
    }

    @Override public boolean ready(ServerPlayer player, TokenRuntimeState state, long tick) {
        return tick >= state.cooldownUntil;
    }

    @Override public float addMeleeDamage(TokenEngine.Context context, TokenRuntimeState state) {
        long now = context.tick();
        return now < state.activeUntil
                ? remainingDamage(state.recordedDamage, settings.decayPerInterval(), settings.decayIntervalTicks(),
                now - state.decayStartedAt)
                : 0;
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        long tick = context.tick();
        if (event.kind() != TokenEvent.Kind.MELEE || event.target() == null || event.target() instanceof Player
                || event.target().isAlive() || !TokenTargeting.enemy(event.target()) || !ready(event.player(), state, tick)) return;
        int recorded = recordedDamage(event.damageBeforeAbsorption(), settings.maxRecordedDamage());
        if (recorded <= 0) return;
        state.recordedDamage = recorded;
        state.decayStartedAt = tick;
        state.activeUntil = tick + durationTicks(recorded, settings.decayPerInterval(), settings.decayIntervalTicks());
        state.cooldownUntil = state.activeUntil + settings.cooldownTicks();
        state.flash(tick);
    }

    /** Rounds the damage up to whole points and applies the configured cap; zero means nothing is recorded. */
    static int recordedDamage(double damageBeforeAbsorption, int maxRecordedDamage) {
        double rounded = Math.ceil(damageBeforeAbsorption - ROUNDING_TOLERANCE);
        if (!(rounded >= 1)) return 0;
        return (int) Math.min(rounded, maxRecordedDamage);
    }

    /** The record stays whole until the first decay step, then loses decayPerInterval at each step, never below zero. */
    static float remainingDamage(int recorded, float decayPerInterval, int intervalTicks, long elapsedTicks) {
        long steps = elapsedTicks / intervalTicks;
        return (float) Math.max(0, recorded - steps * (double) decayPerInterval);
    }

    /** Ticks until the record decays to zero: one interval per decay step needed to remove it. */
    static long durationTicks(int recorded, float decayPerInterval, int intervalTicks) {
        long steps = (long) Math.ceil(recorded / (double) decayPerInterval);
        return steps * intervalTicks;
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{settings.decayPerInterval(), settings.decayIntervalSeconds(),
                settings.cooldownSeconds(), settings.maxRecordedDamage()};
    }
}
