package com.runelegend.token;

import com.runelegend.particle.RuneLegendParticles;
import com.runelegend.token.TokenConfig.FountainSettings;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** One contribution per cast and recipient, including the time since leaving that particular fountain. */
final class FountainEffects {
    private static final long INSIDE = Long.MAX_VALUE;
    private static final double VERTICAL_REACH = 1.0;
    private static final int PARTICLE_INTERVAL = 4;
    private static final int PARTICLES_PER_SAMPLE = 9;
    private static final double PARTICLE_HEIGHT = 0.22;
    private final Set<Source> active = new HashSet<>();
    private final Map<UUID, Recipient> recipients = new HashMap<>();
    private final LongSupplier clock;

    FountainEffects(LongSupplier clock) {
        this.clock = clock;
    }

    record Source(TokenEngine.Context context, ServerLevel level, Vec3 center, FountainSettings settings,
                  long startedAt, long expiresAt) {
        boolean covers(ServerPlayer player, long tick) {
            return tick < expiresAt && player.level() == level && Math.abs(player.getY() - center.y()) <= VERTICAL_REACH
                    && Math.hypot(player.getX() - center.x(), player.getZ() - center.z()) <= settings.radius()
                    && context.canHeal(player);
        }

        void emit(long tick) {
            if (tick >= expiresAt || (tick - startedAt) % PARTICLE_INTERVAL != 0 || !context.valid()) return;
            var random = ThreadLocalRandom.current();
            for (int particle = 0; particle < PARTICLES_PER_SAMPLE; particle++) {
                double angle = random.nextDouble() * Math.TAU;
                double radius = Math.sqrt(random.nextDouble()) * settings.radius();
                double x = center.x() + Math.cos(angle) * radius;
                double z = center.z() + Math.sin(angle) * radius;
                level.sendParticles(RuneLegendParticles.FOUNTAIN_BUBBLE, x, center.y() + PARTICLE_HEIGHT, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private static final class Recipient {
        private final Map<Source, Long> sources = new HashMap<>();
        private final Slot regeneration = new Slot(MobEffects.REGENERATION);
        private final Slot resistance = new Slot(MobEffects.RESISTANCE);
        private ServerPlayer player;

        Recipient(ServerPlayer player) {
            this.player = player;
        }

        void refresh(long now, LongSupplier clock) {
            refresh(regeneration, now, clock);
            refresh(resistance, now, clock);
        }

        private void refresh(Slot slot, long now, LongSupplier clock) {
            boolean isRegeneration = slot == regeneration;
            FountainMobEffect.Grant grant = null;
            boolean inside = false;
            for (var entry : sources.entrySet()) {
                var source = entry.getKey();
                long leftAt = entry.getValue();
                inside |= leftAt == INSIDE;
                long base = Math.min(leftAt, source.expiresAt());
                var settings = source.settings();
                int level = isRegeneration ? settings.regenerationLevel() : settings.resistanceLevel();
                int linger = isRegeneration ? settings.regenerationLingerTicks() : settings.resistanceLingerTicks();
                grant = best(grant, level - 1, base + linger, now);
            }
            slot.refresh(player, grant, inside, clock);
        }

        private static FountainMobEffect.Grant best(FountainMobEffect.Grant current, int amplifier, long until, long now) {
            if (now >= until || current != null && (current.amplifier() > amplifier
                    || current.amplifier() == amplifier && current.until() >= until)) return current;
            return new FountainMobEffect.Grant(amplifier, until);
        }
    }

    private static final class Slot {
        private final Holder<MobEffect> type;
        private FountainMobEffect applied;
        private boolean cleansed;

        Slot(Holder<MobEffect> type) { this.type = type; }

        void refresh(ServerPlayer player, FountainMobEffect.Grant grant, boolean inside, LongSupplier clock) {
            var current = player.getEffect(type);
            if (grant == null) {
                var previous = applied;
                applied = null;
                cleansed = false;
                if (previous != null && current == previous) {
                    var ordinary = previous.ordinary();
                    if (ordinary == null) player.removeEffect(type);
                    else player.forceAddEffect(ordinary, null);
                }
            } else if (current == applied && applied != null) {
                if (applied.setGrant(grant)) player.forceAddEffect(applied, null);
            } else {
                // A cleared effect can be regained inside a fountain. A cleared tail stays cleared.
                cleansed = !inside && (cleansed || applied != null && current == null);
                if (cleansed) return;
                applied = new FountainMobEffect(type, current, grant, clock);
                player.forceAddEffect(applied, null);
            }
        }

        void playerReplaced() { applied = null; }
    }

    void start(Source source) { active.add(source); }

    void expire(Source source) {
        active.remove(source);
    }

    void remove(Source source) {
        active.remove(source);
        for (var recipient : List.copyOf(recipients.values())) {
            if (recipient.sources.remove(source) != null) recipient.refresh(clock.getAsLong(), clock);
        }
    }

    void clear(UUID id) {
        var recipient = recipients.remove(id);
        if (recipient == null) return;
        recipient.sources.clear();
        recipient.refresh(clock.getAsLong(), clock);
    }

    void clear() {
        active.clear();
        for (var id : List.copyOf(recipients.keySet())) clear(id);
    }

    void tick(long now, Iterable<ServerPlayer> online, Predicate<ServerPlayer> eligible) {
        if (active.isEmpty() && recipients.isEmpty()) return;
        var seen = new HashSet<UUID>();
        var sources = List.copyOf(active);
        for (var player : online) {
            if (!eligible.test(player) || !player.isAlive() || player.isSpectator()) continue;
            var id = player.getUUID();
            seen.add(id);
            for (var source : sources) {
                if (source.covers(player, now)) {
                    recipients.computeIfAbsent(id, ignored -> new Recipient(player)).sources.put(source, INSIDE);
                }
            }
            var recipient = recipients.get(id);
            if (recipient == null) continue;
            if (recipient.player != player) {
                recipient.player = player;
                recipient.regeneration.playerReplaced();
                recipient.resistance.playerReplaced();
            }
            recipient.sources.replaceAll((source, leftAt) -> leftAt == INSIDE && !source.covers(player, now)
                    ? Math.min(now, source.expiresAt()) : leftAt);
            recipient.sources.entrySet().removeIf(entry -> !entry.getKey().context().valid()
                    || now >= Math.min(entry.getValue(), entry.getKey().expiresAt()) + entry.getKey().settings().maxLingerTicks());
            recipient.refresh(now, clock);
            if (recipient.sources.isEmpty()) recipients.remove(id, recipient);
        }
        for (var id : List.copyOf(recipients.keySet())) {
            if (!seen.contains(id)) clear(id);
        }
        for (var source : active) source.emit(now);
    }
}
