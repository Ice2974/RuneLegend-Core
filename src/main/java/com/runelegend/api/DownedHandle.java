package com.runelegend.api;

import java.util.Objects;
import java.util.UUID;

/** Immutable credential for one downing of one player in a logical server session. */
public record DownedHandle(UUID playerId, UUID id) {
    public DownedHandle {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(id);
    }
}
