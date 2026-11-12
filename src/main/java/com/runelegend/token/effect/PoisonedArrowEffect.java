package com.runelegend.token.effect;

import com.runelegend.token.*;
import com.runelegend.token.TokenConfig.PoisonedArrowSettings;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** A successful roll on an arrow hit applies Poison II to the token enemy that was hit. */
public final class PoisonedArrowEffect implements TokenEffectHandler {
    /** Fixed by design: level 2 is Poison II, so the vanilla amplifier is 1. */
    private static final int POISON_AMPLIFIER = 1;
    private final PoisonedArrowSettings settings;

    public PoisonedArrowEffect(PoisonedArrowSettings settings) {
        this.settings = settings;
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (!TokenTargeting.enemy(event.target()) || !event.target().isAlive() || !context.roll(settings.chance())) return;
        var poison = new MobEffectInstance(MobEffects.POISON, settings.durationTicks(), POISON_AMPLIFIER);
        // Vanilla immunity decides whether the debuff lands; an unapplied effect is not a trigger.
        if (event.target().addEffect(poison, context.player())) state.flash(context.tick());
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{TokenConfig.percent(settings.chance()), settings.durationSeconds()};
    }
}
