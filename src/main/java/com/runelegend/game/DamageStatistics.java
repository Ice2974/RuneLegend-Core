package com.runelegend.game;

import com.runelegend.RuneLegendServer;
import com.runelegend.player.PlayerState;
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
        var attacker = source.getEntity();
        // Some projectile sources use the ownerless projectile itself as the causing entity.
        if (attacker instanceof Projectile && attacker == source.getDirectEntity()) attacker = null;
        var identity = attacker instanceof ServerPlayer ? server.playerStates().state(attacker.getUUID()) : null;
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

    public static void healthChanged(LivingEntity victim, float before, float after) {
        var hit = CURRENT.get();
        if (hit != null && hit.victim == victim) hit.healthLost = loss(before, after);
    }

    public static void settled(LivingEntity victim, DamageSource source) {
        var hit = CURRENT.get();
        if (hit == null || !hit.matches(victim, source) || !hit.reachedAbsorption) return;
        var damageType = source.typeHolder().unwrapKey().map(key -> key.identifier()).orElse(null);
        var settled = new SettledHit(hit.round, uuid(hit.attacker), victim.getUUID(), uuid(source.getDirectEntity()),
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
        private final Entity attacker;
        private final boolean creditAttacker;
        private final boolean creditTarget;
        private float blocked;
        private float absorbed;
        private float healthLost;
        private boolean reachedAbsorption;

        private PendingHit(RuneLegendServer server, LivingEntity victim, DamageSource source, Entity attacker,
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
