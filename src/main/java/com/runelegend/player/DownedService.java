package com.runelegend.player;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/** Tick-driven rescue bookkeeping. Call only on the owning server thread. */
public final class DownedService {
    public record DownedRecord(UUID playerUuid, long round, long sequence, UUID handleId, ResourceKey<Level> dimension,
                               Vec3 position, float yaw, float pitch, long expiresAtTick) {
        public int secondsRemaining(long now) { return (int) Math.max(0, (expiresAtTick - now + 19) / 20); }
    }
    public record Rescuer(UUID uuid, ResourceKey<Level> dimension, Vec3 position, boolean eligible, boolean sneaking) {}
    public record ReviveSession(UUID targetUuid, long sequence, UUID rescuerUuid, long startedAtTick) {}
    private record Candidate(DownedRecord target, Rescuer rescuer, double distance) {}

    private final PlayerStateConfig config;
    private final Map<UUID, DownedRecord> downed = new HashMap<>();
    private final Map<UUID, ReviveSession> rescues = new HashMap<>();
    private final Map<UUID, UUID> targetByRescuer = new HashMap<>();
    private long sequence;

    public DownedService(PlayerStateConfig config) { this.config = config; }
    public DownedRecord get(UUID uuid) { return downed.get(uuid); }
    public Collection<DownedRecord> records() { return List.copyOf(downed.values()); }
    public ReviveSession rescue(UUID target) { return rescues.get(target); }

    public DownedRecord add(UUID uuid, long round, long now, ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {
        cancelRescue(uuid);
        return downed.computeIfAbsent(uuid, _ -> new DownedRecord(uuid, round, ++sequence, UUID.randomUUID(), dimension,
                position, yaw, pitch, now + config.downedTicks()));
    }

    public void cancelRescue(UUID rescuer) {
        var target = targetByRescuer.remove(rescuer);
        if (target != null) rescues.remove(target);
    }

    private void cancelTarget(UUID target) {
        var session = rescues.remove(target);
        if (session != null) targetByRescuer.remove(session.rescuerUuid());
    }

    public void removeDowned(UUID uuid) {
        downed.remove(uuid);
        cancelTarget(uuid);
        cancelRescue(uuid);
    }

    public void tick(long now, long round, Collection<Rescuer> online,
                     Consumer<DownedRecord> expired, BiPredicate<DownedRecord, UUID> revive) {
        for (var record : records()) {
            if (record.round() != round || now >= record.expiresAtTick()) {
                removeDowned(record.playerUuid());
                expired.accept(record);
            }
        }
        var byId = new HashMap<UUID, Rescuer>();
        online.forEach(player -> byId.put(player.uuid(), player));
        for (var session : List.copyOf(rescues.values())) {
            var record = downed.get(session.targetUuid());
            if (record == null || record.sequence() != session.sequence() || !eligible(byId.get(session.rescuerUuid()), record))
                cancelTarget(session.targetUuid());
        }
        var candidates = new ArrayList<Candidate>();
        for (var record : downed.values()) {
            if (rescues.containsKey(record.playerUuid())) continue;
            for (var player : online) {
                if (!targetByRescuer.containsKey(player.uuid()) && eligible(player, record))
                    candidates.add(new Candidate(record, player, player.position().distanceToSqr(record.position())));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance)
                .thenComparing(candidate -> candidate.target().playerUuid()).thenComparing(candidate -> candidate.rescuer().uuid()));
        for (var candidate : candidates) {
            var target = candidate.target();
            var rescuer = candidate.rescuer();
            if (rescues.containsKey(target.playerUuid()) || targetByRescuer.containsKey(rescuer.uuid())) continue;
            rescues.put(target.playerUuid(), new ReviveSession(target.playerUuid(), target.sequence(), rescuer.uuid(), now));
            targetByRescuer.put(rescuer.uuid(), target.playerUuid());
        }
        for (var session : List.copyOf(rescues.values())) {
            if (now - session.startedAtTick() < config.reviveTicks()) continue;
            var record = downed.get(session.targetUuid());
            if (record != null && record.sequence() == session.sequence() && eligible(byId.get(session.rescuerUuid()), record)
                    && revive.test(record, session.rescuerUuid())) removeDowned(record.playerUuid());
            else cancelTarget(session.targetUuid());
        }
    }

    private boolean eligible(Rescuer player, DownedRecord target) {
        return player != null && player.eligible() && player.sneaking() && !player.uuid().equals(target.playerUuid())
                && player.dimension().equals(target.dimension()) && player.position().distanceToSqr(target.position()) <= config.distanceSquared();
    }

    public void clearRound() { downed.clear(); rescues.clear(); targetByRescuer.clear(); }
}
