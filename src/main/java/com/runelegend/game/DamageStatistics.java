package com.runelegend.game;

import com.runelegend.RuneLegendServer;
import com.runelegend.player.PlayerState;
import com.runelegend.mixin.ProjectileOwnerAccessor;
import com.runelegend.token.TokenTargeting;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Observes vanilla settlement independently of the token trigger bridge. */
public final class DamageStatistics {
    record SettledHit(long round, UUID attackerId, UUID targetId, UUID directEntityId, Identifier damageType,
                      float blocked, float absorbed, float healthLost) {}

    private static final ThreadLocal<PendingHit> CURRENT = new ThreadLocal<>();

    private DamageStatistics() {}

    public static boolean track(RuneLegendServer server, LivingEntity victim, DamageSource source, BooleanSupplier original) {
        var previous = CURRENT.get();
        var attacker = attackerId(source);
        var identity = attacker == null ? null : server.playerStates().state(attacker);
        boolean creditAttacker = identity != null && identity != PlayerState.SPECTATOR;
        boolean creditTarget = victim instanceof ServerPlayer player && server.tokenEligible(player);
        var hit = (creditAttacker || creditTarget) && victim.isAlive() && !TokenTargeting.substitute(victim)
                ? new PendingHit(server, victim, source, attacker, creditAttacker, creditTarget) : null;
        CURRENT.set(hit);
        try {
            return original.getAsBoolean();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    private static UUID attackerId(DamageSource source) {
        var cause = source.getEntity();
        if (source.getDirectEntity() instanceof Projectile projectile && (cause == null || cause == projectile)) {
            // Vanilla can no longer resolve an offline shooter, but the projectile retains its owner reference.
            var owner = ((ProjectileOwnerAccessor) projectile).runelegend$owner();
            return owner == null ? null : owner.getUUID();
        }
        return uuid(cause);
    }

    public static void blocked(LivingEntity victim, DamageSource source, float blocked) {
        var hit = CURRENT.get();
        if (hit != null && hit.matches(victim, source) && Float.isFinite(blocked)) hit.blocked = Math.max(0, blocked);
    }

    public static void absorptionChanged(LivingEntity victim, float before, float after) {
        var hit = CURRENT.get();
        if (hit != null && hit.victim == victim) {
            hit.reachedAbsorption = true;
            hit.absorbed = loss(before, after);
        }
    }

    public static void beginHealthWrite(LivingEntity victim) {
        var hit = CURRENT.get();
        if (hit != null && hit.victim == victim) {
            hit.healthBefore = victim.getHealth();
            hit.measuringHealth = true;
        }
    }

    /** Consume the damage write before HEALTH listeners can heal or cause another hit. */
    public static void finishHealthWrite(LivingEntity victim) {
        var hit = CURRENT.get();
        if (hit != null && hit.victim == victim && hit.measuringHealth) {
            hit.measuringHealth = false;
            hit.healthLost = loss(hit.healthBefore, victim.getHealth());
        }
    }

    public static void settled(LivingEntity victim, DamageSource source) {
        var hit = CURRENT.get();
        if (hit == null || !hit.matches(victim, source) || !hit.reachedAbsorption) return;
        var damageType = source.typeHolder().unwrapKey().map(key -> key.identifier()).orElse(null);
        var settled = new SettledHit(hit.round, hit.attacker, victim.getUUID(), uuid(source.getDirectEntity()),
                damageType, hit.blocked, hit.absorbed, hit.healthLost);
        hit.server.statistics().record(settled, hit.creditAttacker, hit.creditTarget);
    }

    private static UUID uuid(Entity entity) {
        return entity == null ? null : entity.getUUID();
    }

    private static float loss(float before, float after) {
        float difference = before - after;
        return Float.isFinite(difference) ? Math.max(0, difference) : 0;
    }

    private static final class PendingHit {
        private final RuneLegendServer server;
        private final long round;
        private final LivingEntity victim;
        private final DamageSource source;
        private final UUID attacker;
        private final boolean creditAttacker;
        private final boolean creditTarget;
        private float blocked;
        private float absorbed;
        private float healthBefore;
        private boolean measuringHealth;
        private float healthLost;
        private boolean reachedAbsorption;

        private PendingHit(RuneLegendServer server, LivingEntity victim, DamageSource source, UUID attacker,
                           boolean creditAttacker, boolean creditTarget) {
            this.server = server;
            this.round = server.game().getRules().round();
            this.victim = victim;
            this.source = source;
            this.attacker = attacker;
            this.creditAttacker = creditAttacker;
            this.creditTarget = creditTarget;
        }

        private boolean matches(LivingEntity victim, DamageSource source) {
            return this.victim == victim && this.source == source;
        }
    }
}
