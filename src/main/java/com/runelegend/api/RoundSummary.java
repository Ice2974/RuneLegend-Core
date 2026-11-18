package com.runelegend.api;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Final round identities, including offline records, captured before teardown. */
public record RoundSummary(long round, Reason reason, Optional<Identifier> outcomeId,
                           Map<UUID, PlayerSnapshot.Identity> identities) {
    public enum Reason { FINISH, MANUAL_STOP, NO_ALIVE }

    public RoundSummary {
        Objects.requireNonNull(reason);
        if (outcomeId.isPresent() != (reason == Reason.FINISH)) {
            throw new IllegalArgumentException("Only FINISH has an outcome ID");
        }
        identities = Map.copyOf(identities);
    }
}
