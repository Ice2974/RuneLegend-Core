package com.runelegend.token.effect;

import com.runelegend.token.*;
import com.runelegend.token.TokenConfig.VernalTideSettings;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * While Regeneration is active, a melee hit rolls once: the struck target takes the damage as part
 * of that hit (so no second hit and no vanilla damage cooldown involved), and every other enemy
 * within the configured radius takes it as a separate token hit.
 */
public final class VernalTideEffect implements TokenEffectHandler {
    private final VernalTideSettings settings;

    public VernalTideEffect(VernalTideSettings settings) {
        this.settings = settings;
    }

    /** The roll belongs to the swing that also delivers the melee event, so it is shared via state. */
    @Override public float addMeleeDamage(TokenEngine.Context context, TokenRuntimeState state) {
        boolean proc = context.player().hasEffect(MobEffects.REGENERATION) && context.roll(settings.chance());
        state.meleeProcPending = proc;
        return proc ? settings.damage() : 0;
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (!state.meleeProcPending) return;
        state.meleeProcPending = false;
        var player = context.player();
        var level = event.level();
        var center = player.position();
        var directTarget = event.target();
        double radiusSquared = settings.radius() * settings.radius();
        for (var mob : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(settings.radius()))) {
            // The struck target already received the bonus as part of its own hit.
            if (mob == player || mob == directTarget || mob instanceof Player || !mob.isAlive()
                    || !TokenTargeting.enemy(mob) || mob.distanceToSqr(center) > radiusSquared) continue;
            context.damage(level, mob, settings.damage());
        }
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{TokenConfig.percent(settings.chance()), settings.radius(), settings.damage()};
    }
}
