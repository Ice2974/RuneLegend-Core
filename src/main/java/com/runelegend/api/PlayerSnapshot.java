package com.runelegend.api;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Immutable player data captured on the server thread. An empty identity means there is no
 * round identity in WAITING, or the UUID has no record in the running round.
 * Connection status is independent of identity. Stamina is present only while connected.
 */
public record PlayerSnapshot(UUID playerId, RuleSnapshot rules, Optional<Identity> identity,
                             boolean connected, List<Identifier> equippedTokens, OptionalInt stamina,
                             Map<Identifier, Integer> cooldowns) {
    public enum Identity { ALIVE, DOWNED, ELIMINATED, SPECTATOR }

    public PlayerSnapshot {
        equippedTokens = List.copyOf(equippedTokens);
        cooldowns = Map.copyOf(cooldowns);
    }

    public boolean alive() {
        return identity.orElse(null) == Identity.ALIVE;
    }

    /**
     * Remaining server ticks in the cooldown phase. Zero means there is no current cooldown.
     * Empty means the player is offline or the token is unregistered or unequipped.
     */
    public OptionalInt cooldownTicks(Identifier tokenId) {
        var ticks = cooldowns.get(tokenId);
        return ticks == null ? OptionalInt.empty() : OptionalInt.of(ticks);
    }
}
