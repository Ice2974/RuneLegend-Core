package com.runelegend.token;

import com.runelegend.token.TokenConfig.FountainSettings;
import net.minecraft.server.level.ServerPlayer;

final class FountainOfLifeEffect implements TokenEffectHandler {
    private static final String COOLDOWN = "fountain-cooldown";
    private final FountainSettings settings;

    FountainOfLifeEffect(FountainSettings settings) {
        this.settings = settings;
    }

    @Override public boolean ready(ServerPlayer player, TokenRuntimeState state, long tick) {
        return state.activeUntil == 0 && tick >= state.cooldownUntil;
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        tryActivate(context, state);
    }

    private void tryActivate(TokenEngine.Context context, TokenRuntimeState state) {
        long now = context.tick();
        var player = context.player();
        if (!context.valid() || !ready(player, state, now)
                || player.getHealth() > player.getMaxHealth() * settings.healthThreshold()) return;

        long expiresAt = now + settings.durationTicks();
        var source = new FountainEffects.Source(context, player.level(), player.position(), settings, now, expiresAt);
        var fountains = context.fountains();
        String key = "fountain-" + now;
        state.activeUntil = expiresAt;
        state.flash(now);
        fountains.start(source);
        context.replaceEffect(key, () -> fountains.remove(source));
        context.at(key, expiresAt, () -> {
            fountains.expire(source);
            context.at(key, expiresAt + settings.maxLingerTicks(), () -> context.finish(key));
            state.activeUntil = 0;
            state.triggeredUntil = 0;
            state.cooldownUntil = expiresAt + settings.cooldownTicks();
            context.at(COOLDOWN, state.cooldownUntil, () -> tryActivate(context, state));
        });
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{TokenConfig.percent(settings.healthThreshold()), settings.radius(), settings.durationSeconds(),
                settings.regenerationLevel(), settings.resistanceLevel(), settings.regenerationLingerSeconds(),
                settings.resistanceLingerSeconds(), settings.cooldownSeconds()};
    }
}
