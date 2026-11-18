package com.runelegend.token.effect;

import com.runelegend.token.TokenConfig;
import com.runelegend.token.TokenConfig.SubstituteSettings;
import com.runelegend.token.TokenEffectHandler;
import com.runelegend.token.TokenEngine;
import com.runelegend.token.TokenEvent;
import com.runelegend.token.TokenRuntimeState;
import net.minecraft.server.level.ServerPlayer;

public final class SubstituteEffect implements TokenEffectHandler {
    private static final String EFFECT = "mannequin";
    private final SubstituteSettings settings;

    public SubstituteEffect(SubstituteSettings settings) {
        this.settings = settings;
    }

    @Override public boolean ready(ServerPlayer player, TokenRuntimeState state, long tick) {
        return state.activeUntil == 0 && tick >= state.cooldownUntil;
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        trySummon(context, state);
    }

    private void trySummon(TokenEngine.Context context, TokenRuntimeState state) {
        var player = context.player();
        long tick = context.tick();
        if (!context.valid() || !ready(player, state, tick)
                || player.getHealth() > player.getMaxHealth() * settings.healthThreshold()) return;
        state.activeUntil = Long.MAX_VALUE;
        if (!context.summonSubstitute(EFFECT, () -> end(context, state))) {
            state.activeUntil = 0;
            return;
        }
        state.flash(tick);
    }

    private void end(TokenEngine.Context context, TokenRuntimeState state) {
        context.finish(EFFECT);
        state.activeUntil = 0;
        state.triggeredUntil = 0;
        if (!context.valid()) return;
        long tick = context.tick();
        state.cooldownUntil = tick + Math.max(1, settings.cooldownTicks());
        context.at(EFFECT, state.cooldownUntil, () -> trySummon(context, state));
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{TokenConfig.percent(settings.healthThreshold()), settings.cooldownSeconds()};
    }
}
