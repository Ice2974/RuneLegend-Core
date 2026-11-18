package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.token.TokenConfig.FullBlessingSettings;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

final class FullBlessingEffect implements TokenEffectHandler {
    static final Identifier ID = RuneLegendMod.id("full_blessing");
    private static final String COOLDOWN = "cooldown";
    private final FullBlessingSettings settings;

    FullBlessingEffect(FullBlessingSettings settings) {
        this.settings = settings;
    }

    @Override public boolean ready(ServerPlayer player, TokenRuntimeState state, long tick) {
        return tick >= state.cooldownUntil;
    }

    void settle(TokenEngine.Context context, TokenRuntimeState state, boolean equipped) {
        var owner = context.player();
        if (!context.canHeal(owner) || !atOrBelowThreshold(owner)) return;

        if (equipped && ready(owner, state, context.tick())) {
            for (var recipient : context.onlinePlayers()) bless(context, recipient);
            if (!context.valid()) return;
            long tick = context.tick();
            state.flash(tick);
            state.cooldownUntil = tick + Math.max(1, settings.cooldownTicks());
            context.at(COOLDOWN, state.cooldownUntil, context::recheckBlessings);
        }

        while (state.storedBlessings > 0) {
            var recipient = context.player();
            if (!context.canHeal(recipient) || recipient.getHealth() >= recipient.getMaxHealth()
                    || !atOrBelowThreshold(recipient)) break;
            state.storedBlessings--;
            recipient.heal(settings.healAmount());
        }
    }

    private void bless(TokenEngine.Context context, ServerPlayer recipient) {
        for (int remaining = settings.blessingsPerTrigger(); remaining > 0 && context.canHeal(recipient); remaining--) {
            if (!atOrBelowThreshold(recipient)) {
                context.storeBlessings(recipient, remaining, settings.maxStoredBlessings());
                break;
            }
            recipient.heal(settings.healAmount());
        }
    }

    private boolean atOrBelowThreshold(ServerPlayer player) {
        return player.getHealth() <= player.getMaxHealth() * settings.healthThreshold();
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{TokenConfig.percent(settings.healthThreshold()), settings.blessingsPerTrigger(),
                settings.healAmount(), settings.maxStoredBlessings(), settings.cooldownSeconds()};
    }
}
