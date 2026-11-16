package com.runelegend.token;

import com.runelegend.RuneLegendServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import java.util.function.BooleanSupplier;
import java.util.function.BiFunction;

/** Scoped to the original call, so nested damage never inherits an unrelated hit's metadata. */
public final class TokenEventBridge {
    public static final class Hit {
        private final LivingEntity victim;
        private final DamageSource source;
        private final float incoming;
        private final boolean aliveBefore;
        private float beforeMitigation;
        private float blocked;
        private boolean settled;

        private Hit(LivingEntity victim, DamageSource source, float incoming) {
            this.victim = victim;
            this.source = source;
            this.incoming = incoming;
            this.aliveBefore = victim.isAlive();
        }
    }

    private record Melee(ServerPlayer player, Entity victim, DamageSource source, float bonus) {}
    private static final ThreadLocal<Hit> HITS = new ThreadLocal<>();
    private static final ThreadLocal<Melee> MELEE = new ThreadLocal<>();

    private TokenEventBridge() {}

    public static boolean trackHit(RuneLegendServer server, LivingEntity victim, ServerLevel level,
                                   DamageSource source, float amount, BooleanSupplier original) {
        if (source.is(TokenEngine.TOKEN_DAMAGE)) return original.getAsBoolean();
        var previous = HITS.get();
        var hit = new Hit(victim, source, amount);
        HITS.set(hit);
        try {
            boolean accepted = original.getAsBoolean();
            if (hit.aliveBefore && hit.settled) dispatch(server, hit, level, accepted && hit.beforeMitigation > 0);
            return accepted;
        } finally {
            restore(HITS, previous);
        }
    }

    /** Observes entry into actuallyHurt, before equipment and other damage reductions. */
    public static void applied(LivingEntity victim, DamageSource source, float beforeMitigation) {
        var hit = HITS.get();
        if (hit != null && hit.victim == victim && hit.source == source) {
            hit.beforeMitigation += beforeMitigation;
            hit.settled = true;
        }
    }

    public static void blocked(LivingEntity victim, DamageSource source, float blocked) {
        var hit = HITS.get();
        if (hit != null && hit.victim == victim && hit.source == source) hit.blocked = blocked;
    }

    public static boolean melee(ServerPlayer attacker, Entity victim, DamageSource source, float bonus, BooleanSupplier original) {
        var previous = MELEE.get();
        MELEE.set(new Melee(attacker, victim, source, bonus));
        try {
            return original.getAsBoolean();
        } finally {
            restore(MELEE, previous);
        }
    }

    /** Joins the two damage portions before absorption without sending a second hit. */
    public static float afterMagic(LivingEntity victim, DamageSource source, float base,
                                   BiFunction<DamageSource, Float, Float> applyMagic) {
        var hit = HITS.get();
        var melee = MELEE.get();
        if (hit == null || hit.victim != victim || hit.source != source || hit.beforeMitigation <= 0
                || melee == null || melee.victim != victim || melee.source != source || melee.bonus <= 0) return base;
        var server = RuneLegendServer.find(melee.player.level().getServer());
        if (victim instanceof net.minecraft.world.entity.player.Player || server == null || !server.tokenEligible(melee.player)) return base;
        // This portion never enters the armor calculation. The token source lets vanilla
        // apply Resistance but skip protection enchantments.
        return base + applyMagic.apply(TokenEngine.directDamageSource(melee.player.level(), melee.player), melee.bonus);
    }

    private static void dispatch(RuneLegendServer server, Hit hit, ServerLevel level, boolean acceptedDamage) {
        var tokens = server.tokens();
        var victim = hit.victim;
        var source = hit.source;
        if (victim instanceof ServerPlayer player && tokens.eligible(player) && player.isAlive()) {
            if (acceptedDamage) tokens.dispatch(event(TokenEvent.Kind.HURT, player, victim, level, hit));
            if (hit.blocked > 0) tokens.dispatch(event(TokenEvent.Kind.GUARD_SUCCESS, player, victim, level, hit));
        }
        if (!acceptedDamage || !(source.getEntity() instanceof ServerPlayer player) || player == victim || !tokens.eligible(player)) return;
        var melee = MELEE.get();
        if (melee != null && melee.player == player && melee.victim == victim && melee.source == source) {
            tokens.dispatch(event(TokenEvent.Kind.MELEE, player, victim, level, hit));
        } else if (source.getDirectEntity() instanceof AbstractArrow) {
            tokens.dispatch(event(TokenEvent.Kind.ARROW, player, victim, level, hit));
        }
        if (!victim.isAlive()) tokens.dispatch(event(TokenEvent.Kind.KILL, player, victim, level, hit));
    }

    private static TokenEvent event(TokenEvent.Kind kind, ServerPlayer player, LivingEntity victim, ServerLevel level, Hit hit) {
        return new TokenEvent(kind, player, victim, level, hit.source, hit.incoming, hit.beforeMitigation, hit.blocked);
    }

    private static <T> void restore(ThreadLocal<T> local, T previous) {
        if (previous == null) local.remove();
        else local.set(previous);
    }
}
