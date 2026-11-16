package com.runelegend.token.effect;

import com.runelegend.token.*;
import com.runelegend.token.TokenConfig.ResonanceHealingSettings;
import net.minecraft.server.level.ServerPlayer;

public final class ResonanceHealingEffect implements TokenEffectHandler {
    private final ResonanceHealingSettings settings;

    public ResonanceHealingEffect(ResonanceHealingSettings settings) {
        this.settings = settings;
    }

    @Override public boolean ready(ServerPlayer player, TokenRuntimeState state, long tick) {
        return tick >= state.cooldownUntil;
    }

    @Override public boolean preparing(ServerPlayer player, TokenRuntimeState state, long tick) {
        return ready(player, state, tick) && atOrBelowThreshold(player);
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (event.kind() != TokenEvent.Kind.GUARD_SUCCESS) return;
        var player = event.player();
        long tick = context.tick();
        if (!ready(player, state, tick) || !atOrBelowThreshold(player)) return;

        var level = event.level();
        double radiusSquared = settings.radius() * settings.radius();
        for (var recipient : level.players()) {
            if (!context.canHeal(recipient)) continue;
            if (recipient.distanceToSqr(player) > radiusSquared) continue;
            recipient.heal(settings.healAmount());
        }
        state.flash(tick);
        state.cooldownUntil = tick + settings.cooldownTicks();
    }

    private boolean atOrBelowThreshold(ServerPlayer player) {
        return player.getHealth() <= player.getMaxHealth() * settings.healthThreshold();
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{TokenConfig.percent(settings.healthThreshold()), settings.radius(),
                settings.healAmount(), settings.cooldownSeconds()};
    }
}
