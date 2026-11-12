package com.runelegend.token.effect;

import com.runelegend.token.*;
import com.runelegend.token.TokenConfig.EchoSettings;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

public final class EchoEffect implements TokenEffectHandler {
    private final EchoSettings settings;

    public EchoEffect(EchoSettings settings) {
        this.settings = settings;
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (!TokenTargeting.enemy(event.target()) || !context.roll(settings.chance())) return;
        var center = event.target().position();
        var level = event.level();
        long start = context.tick();
        context.replaceEffect("echo", () -> {});
        if (start >= state.activeUntil) state.flash(start);
        state.activeUntil = start + settings.durationTicks();
        for (int elapsed = settings.intervalTicks(); elapsed <= settings.durationTicks(); elapsed += settings.intervalTicks()) {
            context.at("echo", start + elapsed, () -> {
                var area = new AABB(center, center).inflate(settings.radius());
                for (var enemy : level.getEntitiesOfClass(LivingEntity.class, area,
                        mob -> mob.isAlive() && TokenTargeting.enemy(mob) && mob.distanceToSqr(center) <= settings.radius() * settings.radius())) {
                    context.damage(level, enemy, settings.damage());
                }
            });
        }
        context.at("echo", state.activeUntil, () -> context.finish("echo"));
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{TokenConfig.percent(settings.chance()), settings.radius(), settings.damage(),
                TokenConfig.seconds(settings.durationTicks()), TokenConfig.seconds(settings.intervalTicks())};
    }
}
