package com.runelegend.api.token;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Consumer;

/** Server-thread operations scoped to the current token callback. */
public interface TokenContext {
    Identifier tokenId();
    ServerPlayer player();
    long tick();
    boolean roll(double chance);
    void flash();
    int cooldownRemainingTicks();
    void startCooldown(int ticks);

    boolean canHarm(LivingEntity target);

    /** A separate hit with vanilla player attack damage, including armor and protection. */
    boolean damage(LivingEntity target, float amount);

    /** Applies a harmful status effect using vanilla application, immunity and ticking rules. */
    boolean applyDebuff(LivingEntity target, MobEffectInstance effect);

    /** Heals a living participant in the current level. */
    boolean heal(ServerPlayer target, float amount);

    /** Installs cleanup for an owned effect, revoking the previous effect with the same key. */
    void trackEffect(String key, Runnable cleanup);

    /** Schedules work with a fresh context after a positive number of server ticks. */
    void schedule(String key, int delayTicks, Consumer<TokenContext> action);

    /** Revokes the named owned effect and cancels work with that key. */
    void finishEffect(String key);
}
