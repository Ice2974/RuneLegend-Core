package com.runelegend.api;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Final round identities and statistics, including offline records, captured before teardown. */
public record RoundSummary(long round, Reason reason, Optional<Identifier> outcomeId,
                           Map<UUID, PlayerSnapshot.Identity> identities, Map<UUID, PlayerRoundStats> stats) {
    public enum Reason { FINISH, MANUAL_STOP, NO_ALIVE }

    public RoundSummary(long round, Reason reason, Optional<Identifier> outcomeId,
                        Map<UUID, PlayerSnapshot.Identity> identities) {
        this(round, reason, outcomeId, identities, Map.of());
    }

    public RoundSummary {
        Objects.requireNonNull(reason);
        if (outcomeId.isPresent() != (reason == Reason.FINISH)) {
            throw new IllegalArgumentException("Only FINISH has an outcome ID");
        }
        identities = Map.copyOf(identities);
        stats = Map.copyOf(stats);
    }
}
