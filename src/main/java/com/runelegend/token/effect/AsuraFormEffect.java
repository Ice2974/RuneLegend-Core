package com.runelegend.token.effect;

import com.runelegend.token.*;
import com.runelegend.token.TokenConfig.AsuraFormSettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class AsuraFormEffect implements TokenEffectHandler {
    private static final String FORM = "form";
    private final AsuraFormSettings settings;

    public AsuraFormEffect(AsuraFormSettings settings) { this.settings = settings; }

    @Override public boolean ready(ServerPlayer player, TokenRuntimeState state, long tick) {
        return tick >= state.activeUntil && tick >= state.cooldownUntil;
    }

    @Override public boolean preparing(ServerPlayer player, TokenRuntimeState state, long tick) {
        return state.charge > 0 && ready(player, state, tick);
    }

    @Override public float addMeleeDamage(TokenEngine.Context context, TokenRuntimeState state) {
        return context.tick() < state.activeUntil ? settings.bonusDamage() : 0;
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        long tick = context.tick();
        if (event.kind() != TokenEvent.Kind.KILL || !ready(event.player(), state, tick)
                || event.target() == null || event.target() instanceof Player || !TokenTargeting.enemy(event.target())) return;
        if (++state.charge < settings.requiredKills()) return;

        var form = AsuraHealth.start(context.player(), settings.temporaryHealth(), settings.speedLevel());
        state.charge = 0;
        state.flash(tick);
        state.activeUntil = tick + settings.durationTicks();
        state.cooldownUntil = state.activeUntil + settings.cooldownTicks();
        context.replaceEffect(FORM, form::finish);
        context.at(FORM, state.activeUntil, () -> context.finish(FORM));
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{settings.requiredKills(), settings.durationSeconds(), settings.bonusDamage(),
                settings.speedLevel(), settings.temporaryHealth(), settings.cooldownSeconds()};
    }
}
