package com.runelegend.token.effect;

import com.runelegend.token.*;
import com.runelegend.token.TokenConfig.ImmediateCounterSettings;
import net.minecraft.server.level.ServerPlayer;

public final class ImmediateCounterEffect implements TokenEffectHandler {
    private final ImmediateCounterSettings settings;

    public ImmediateCounterEffect(ImmediateCounterSettings settings) {
        this.settings = settings;
    }

    @Override public boolean ready(ServerPlayer player, TokenRuntimeState state, long tick) {
        return tick >= state.cooldownUntil;
    }

    @Override public float addMeleeDamage(TokenEngine.Context context, TokenRuntimeState state) {
        return context.tick() < state.activeUntil ? settings.bonusDamage() : 0;
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (event.kind() != TokenEvent.Kind.GUARD_SUCCESS || !ready(event.player(), state, context.tick())) return;
        state.flash(context.tick());
        state.activeUntil = context.tick() + settings.durationTicks();
        state.cooldownUntil = state.activeUntil + settings.cooldownAfterEffectTicks();
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{settings.bonusDamage(), TokenConfig.seconds(settings.durationTicks()),
                TokenConfig.seconds(settings.cooldownAfterEffectTicks())};
    }
}
