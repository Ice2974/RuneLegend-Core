package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.api.token.TokenContext;
import com.runelegend.api.token.TokenEffect;
import com.runelegend.token.effect.AsuraHealth;
import com.runelegend.api.token.TokenTrigger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class ExtensionTokenEffect implements TokenEffectHandler {
    private static final TokenEffect DISABLED = new TokenEffect() {};
    private final Identifier id;
    private final Supplier<? extends TokenEffect> factory;

    ExtensionTokenEffect(Identifier id, Supplier<? extends TokenEffect> factory) {
        this.id = id;
        this.factory = factory;
    }

    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
        withContext(context, state, "event", scope -> {
            var trigger = new TokenTrigger(TokenTrigger.Kind.valueOf(event.kind().name()), event.target(), event.source(),
                    event.incomingDamage(), event.damageBeforeMitigation(), event.blockedDamage(), event.damageBeforeAbsorption());
            instance(state).onEvent(scope, trigger);
            return null;
        });
    }

    @Override public float addMeleeDamage(TokenEngine.Context context, TokenRuntimeState state) {
        var target = context.meleeTarget();
        if (target == null) return 0;
        var bonus = withContext(context, state, "melee bonus",
                scope -> instance(state).penetratingMeleeBonus(scope, target));
        return bonus != null && context.valid() ? bonus : 0;
    }

    @Override public boolean ready(ServerPlayer player, TokenRuntimeState state, long tick) {
        return state.extensionEffect != DISABLED && state.cooldownUntil <= tick;
    }

    void playerReplaced(TokenRuntimeState state, ServerPlayer previous, ServerPlayer replacement) {
        var effect = state.extensionEffect;
        if (effect != null) run("player replacement", () -> effect.onPlayerReplaced(previous, replacement));
    }

    void cleanup(TokenRuntimeState state) {
        var effect = state.extensionEffect;
        state.extensionEffect = null;
        if (effect != null) run("cleanup", effect::onCleanup);
    }

    private TokenEffect instance(TokenRuntimeState state) {
        if (state.extensionEffect == null) {
            state.extensionEffect = DISABLED;
            run("effect factory", () -> state.extensionEffect = Objects.requireNonNull(factory.get(), "token effect"));
        }
        return state.extensionEffect;
    }

    private <T> T withContext(TokenEngine.Context context, TokenRuntimeState state, String callback,
                              Function<TokenContext, T> action) {
        if (!context.valid()) return null;
        var scope = new Scope(context, state);
        try {
            return action.apply(scope);
        } catch (RuntimeException error) {
            log(callback, error);
            return null;
        } finally {
            scope.open = false;
        }
    }

    private void run(String callback, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            log(callback, error);
        }
    }

    private void log(String callback, RuntimeException error) {
        RuneLegendMod.LOGGER.error("RuneLegend token {} failed during {}", id, callback, error);
    }

    private final class Scope implements TokenContext {
        private final TokenEngine.Context context;
        private final TokenRuntimeState state;
        private final Thread thread = Thread.currentThread();
        private boolean open = true;

        Scope(TokenEngine.Context context, TokenRuntimeState state) {
            this.context = context;
            this.state = state;
        }

        private void checkScope() {
            if (Thread.currentThread() != thread || !open || !context.valid()) {
                throw new IllegalStateException("Token context is outside its active server callback");
            }
        }

        @Override public Identifier tokenId() { checkScope(); return id; }
        @Override public ServerPlayer player() { checkScope(); return context.player(); }
        @Override public long tick() { checkScope(); return context.tick(); }
        @Override public boolean roll(double chance) {
            checkScope();
            if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
                throw new IllegalArgumentException("Chance must be within [0, 1]");
            }
            return context.roll(chance);
        }

        @Override public void flash() {
            checkScope();
            state.flash(context.tick());
        }

        @Override public int cooldownRemainingTicks() {
            return (int) Math.max(0, state.cooldownUntil - tick());
        }

        @Override public void startCooldown(int ticks) {
            if (ticks < 0) throw new IllegalArgumentException("Cooldown cannot be negative");
            state.cooldownUntil = tick() + ticks;
        }

        @Override public boolean canHarm(LivingEntity target) {
            checkScope();
            return context.canHarm(target) && target.level() == context.player().level();
        }

        @Override public boolean damage(LivingEntity target, float amount) {
            checkScope();
            return Float.isFinite(amount) && amount > 0 && context.normalDamage(target, amount);
        }

        @Override public boolean applyDebuff(LivingEntity target, MobEffectInstance effect) {
            checkScope();
            return canHarm(target) && effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL
                    && target.addEffect(new MobEffectInstance(effect), context.player());
        }

        @Override public boolean heal(ServerPlayer target, float amount) {
            checkScope();
            if (target == null || !Float.isFinite(amount) || amount <= 0
                    || target.level() != context.player().level() || !context.canHeal(target)) return false;
            var form = AsuraHealth.on(target.getAttribute(Attributes.MAX_HEALTH));
            float normalBefore = form == null ? 0 : form.healthForSave();
            float before = target.getHealth();
            target.heal(amount);
            return target.getHealth() > before || form != null && form.healthForSave() > normalBefore;
        }

        @Override public void trackEffect(String key, Runnable cleanup) {
            checkScope();
            Objects.requireNonNull(cleanup, "cleanup");
            context.replaceEffect(Objects.requireNonNull(key, "key"), () -> run("owned effect cleanup", cleanup));
        }

        @Override public void schedule(String key, int delayTicks, Consumer<TokenContext> action) {
            checkScope();
            if (delayTicks <= 0) throw new IllegalArgumentException("Delay must be positive");
            Objects.requireNonNull(action, "action");
            context.at(Objects.requireNonNull(key, "key"), context.tick() + delayTicks, () -> {
                withContext(context, state, "scheduled callback", fresh -> {
                    action.accept(fresh);
                    return null;
                });
            });
        }

        @Override public void finishEffect(String key) {
            checkScope();
            context.finish(Objects.requireNonNull(key, "key"));
        }
    }
}
