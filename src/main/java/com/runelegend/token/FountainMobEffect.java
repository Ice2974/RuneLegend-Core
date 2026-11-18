package com.runelegend.token;

import com.runelegend.mixin.MobEffectDurationAccess;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.LongSupplier;

/** Projects the stronger effect into the vanilla slot while keeping ordinary potion state independent. */
public final class FountainMobEffect extends MobEffectInstance {
    record Grant(int amplifier, long until) {}

    private final LongSupplier clock;
    private MobEffectInstance ordinary;
    private Grant grant;

    FountainMobEffect(Holder<MobEffect> type, MobEffectInstance ordinary, Grant grant, LongSupplier clock) {
        super(type);
        this.ordinary = ordinary;
        this.grant = grant;
        this.clock = clock;
    }

    MobEffectInstance ordinary() { return ordinary; }

    boolean setGrant(Grant next) {
        if (grant.equals(next)) return false;
        grant = next;
        return true;
    }

    private boolean ordinaryWins() {
        return ordinary != null && (grant.until() <= clock.getAsLong() || ordinary.getAmplifier() >= grant.amplifier());
    }

    @Override public int getAmplifier() { return ordinaryWins() ? ordinary.getAmplifier() : grant.amplifier(); }
    @Override public int getDuration() {
        return ordinaryWins() ? ordinary.getDuration() : (int) Math.clamp(grant.until() - clock.getAsLong(), 0, Integer.MAX_VALUE);
    }
    @Override public boolean isAmbient() { return ordinaryWins() && ordinary.isAmbient(); }
    @Override public boolean isVisible() { return ordinaryWins() && ordinary.isVisible(); }
    @Override public boolean showIcon() { return !ordinaryWins() || ordinary.showIcon(); }
    @Override public boolean isInfiniteDuration() { return ordinaryWins() && ordinary.isInfiniteDuration(); }
    @Override public boolean endsWithin(int ticks) { return !isInfiniteDuration() && getDuration() <= ticks; }

    @Override public boolean update(MobEffectInstance incoming) {
        if (ordinary != null) return ordinary.update(incoming);
        ordinary = incoming;
        return true;
    }

    @Override public boolean tickServer(ServerLevel level, LivingEntity entity, Runnable onChanged) {
        if (ordinaryWins()) {
            if (!ordinary.tickServer(level, entity, onChanged)) {
                ordinary = null;
                if (getDuration() > 0) onChanged.run();
            }
        } else if (getDuration() > 0) {
            if (ordinary != null) {
                // Keep the hidden vanilla chain aging while the stronger fountain sets the tick cadence.
                var duration = (MobEffectDurationAccess) ordinary;
                duration.runelegend$tickDownDuration();
                duration.runelegend$downgradeToHiddenEffect();
                if (ordinary.getDuration() == 0) ordinary = null;
            }
            var effect = getEffect().value();
            if (effect.shouldApplyEffectTickThisTick(entity.tickCount, grant.amplifier())) {
                effect.applyEffectTick(level, entity, grant.amplifier());
            }
        }
        return ordinary != null || getDuration() > 0;
    }

    /** Entity saving and copying keep the original vanilla instance, including its hidden effects. */
    public static List<MobEffectInstance> ordinaryEffects(Collection<MobEffectInstance> effects) {
        var ordinaryEffects = new ArrayList<MobEffectInstance>(effects.size());
        for (var effect : effects) {
            if (effect instanceof FountainMobEffect fountain) {
                if (fountain.ordinary != null) ordinaryEffects.add(fountain.ordinary);
            } else {
                ordinaryEffects.add(effect);
            }
        }
        return ordinaryEffects;
    }
}
