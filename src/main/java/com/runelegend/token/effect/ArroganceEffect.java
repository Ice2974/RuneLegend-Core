package com.runelegend.token.effect;

import com.runelegend.token.TokenConfig;
import com.runelegend.token.TokenConfig.ArroganceSettings;
import com.runelegend.token.TokenEffectHandler;
import com.runelegend.token.TokenEngine;
import com.runelegend.token.TokenEvent;
import com.runelegend.token.TokenRuntimeState;
import com.runelegend.token.TokenTargeting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class ArroganceEffect implements TokenEffectHandler {
    private final ArroganceSettings settings;

    public ArroganceEffect(ArroganceSettings settings) {
        this.settings = settings;
    }

    @Override public boolean ready(ServerPlayer player, TokenRuntimeState state, long tick) {
        return state.damageSteps == 0 && aboveThreshold(player);
    }

    @Override public boolean preparing(ServerPlayer player, TokenRuntimeState state, long tick) {
        return ready(player, state, tick);
    }

    @Override public float addMeleeDamage(TokenEngine.Context context, TokenRuntimeState state) {
        return retainProgress(context.player(), state) && !(context.meleeTarget() instanceof Player)
                ? bonusDamage(state) : 0;
    }

    @Override public boolean flashOnMeleeBoost() { return false; }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (!retainProgress(context.player(), state) || event.kind() != TokenEvent.Kind.KILL
                || event.target() == null || event.target() instanceof Player || !TokenTargeting.enemy(event.target())) return;
        float previous = bonusDamage(state);
        if (previous >= settings.maxBonusDamage() || ++state.charge < settings.killsPerIncrease()) return;
        state.charge = 0;
        state.damageSteps++;
        state.activeUntil = Long.MAX_VALUE; // Ends on a health change, not on a deadline.
        if (bonusDamage(state) > previous) context.flash();
    }

    private boolean retainProgress(ServerPlayer player, TokenRuntimeState state) {
        if (aboveThreshold(player)) return true;
        state.charge = 0;
        state.damageSteps = 0;
        state.activeUntil = 0;
        return false;
    }

    private boolean aboveThreshold(ServerPlayer player) {
        return player.getHealth() / player.getMaxHealth() > settings.healthThreshold();
    }

    private float bonusDamage(TokenRuntimeState state) {
        return (float) Math.min(settings.maxBonusDamage(), state.damageSteps * (double) settings.damagePerIncrease());
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{TokenConfig.percent(settings.healthThreshold()), settings.killsPerIncrease(),
                settings.damagePerIncrease(), settings.maxBonusDamage()};
    }
}
