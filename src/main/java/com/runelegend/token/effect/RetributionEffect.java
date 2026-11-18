package com.runelegend.token.effect;

import com.runelegend.token.TokenConfig.RetributionSettings;
import com.runelegend.token.TokenEffectHandler;
import com.runelegend.token.TokenEngine;
import com.runelegend.token.TokenEvent;
import com.runelegend.token.TokenRuntimeState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

public final class RetributionEffect implements TokenEffectHandler {
    private static final String ACCUMULATION = "accumulation";
    private final RetributionSettings settings;

    public RetributionEffect(RetributionSettings settings) { this.settings = settings; }

    @Override public boolean ready(ServerPlayer player, TokenRuntimeState state, long tick) {
        return tick >= state.cooldownUntil;
    }

    @Override public float addMeleeDamage(TokenEngine.Context context, TokenRuntimeState state) {
        return context.tick() < state.activeUntil ? state.retributionDamage : 0;
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        if (event.kind() != TokenEvent.Kind.HURT) return;
        float damage = event.damageBeforeAbsorption();
        if (!Float.isFinite(damage) || damage <= 0) return;

        long tick = context.tick();
        if (tick < state.activeUntil) {
            state.retributionDamage = Math.min(Float.MAX_VALUE, state.retributionDamage + damage);
            return;
        }
        if (!ready(event.player(), state, tick) || !isAttack(event.player(), event.source())) return;

        context.replaceEffect(ACCUMULATION, () -> state.retributionDamage = 0);
        state.retributionDamage = damage;
        state.activeUntil = tick + settings.durationTicks();
        state.cooldownUntil = state.activeUntil + settings.cooldownTicks();
        state.flash(tick);
        context.at(ACCUMULATION, state.activeUntil, () -> context.finish(ACCUMULATION));
    }

    private static boolean isAttack(ServerPlayer victim, DamageSource source) {
        if (source == null || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) return false;
        return source.is(DamageTypeTags.IS_PLAYER_ATTACK) || source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.is(DamageTypes.STING) || source.is(DamageTypes.SPIT) || source.is(DamageTypes.FIREWORKS)
                || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.SONIC_BOOM)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                && (source.getDirectEntity() == attacker || source.getDirectEntity() instanceof Projectile);
    }

    @Override public Object[] descriptionArguments() {
        return new Object[]{settings.durationSeconds(), settings.cooldownSeconds()};
    }
}
