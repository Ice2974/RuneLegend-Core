package com.runelegend.token.effect;

import com.runelegend.token.*;
import com.runelegend.token.TokenConfig.VenomousTipSettings;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class VenomousTipEffect implements TokenEffectHandler {
    /** Fixed at Poison II; the token's poison level is intentionally not configurable. */
    private static final int POISON_LEVEL = 2;
    private final VenomousTipSettings settings;

    public VenomousTipEffect(VenomousTipSettings settings) {
        this.settings = settings;
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (!TokenTargeting.enemy(event.target()) || !event.target().isAlive() || !context.roll(settings.chance())) return;
        var poison = new MobEffectInstance(MobEffects.POISON, settings.durationTicks(), POISON_LEVEL - 1);
        // Vanilla immunity decides whether the debuff lands; an unapplied effect is not a trigger.
        if (event.target().addEffect(poison, context.player())) state.flash(context.tick());
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{TokenConfig.percent(settings.chance()), settings.durationSeconds()};
    }
}
