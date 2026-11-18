package com.runelegend.token.effect;

import com.runelegend.token.*;
import com.runelegend.token.TokenConfig.ResonanceHealingSettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

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
        var center = player.position();
        var area = new AABB(center, center).inflate(settings.radius());
        double radiusSquared = settings.radius() * settings.radius();
        int enemyCount = level.getEntitiesOfClass(LivingEntity.class, area,
                mob -> mob.isAlive() && mob.distanceToSqr(center) <= radiusSquared && TokenTargeting.enemy(mob)).size();
        float healAmount = enemyCount * settings.healPerEnemy();
        if (healAmount > 0.0F) {
            for (var recipient : level.players()) {
                if (!context.canHeal(recipient) || recipient.distanceToSqr(center) > radiusSquared) continue;
                recipient.heal(healAmount);
            }
        }
        state.flash(tick);
        state.cooldownUntil = tick + settings.cooldownTicks();
    }

    private boolean atOrBelowThreshold(ServerPlayer player) {
        return player.getHealth() <= player.getMaxHealth() * settings.healthThreshold();
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{TokenConfig.percent(settings.healthThreshold()), settings.radius(),
                settings.healPerEnemy(), settings.cooldownSeconds()};
    }
}
