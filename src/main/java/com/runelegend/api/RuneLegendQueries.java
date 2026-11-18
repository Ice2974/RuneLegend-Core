package com.runelegend.api;

import com.runelegend.RuneLegendServer;
import com.runelegend.player.PlayerState;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/** Read-only queries for one logical server session. Call on the server thread. */
public final class RuneLegendQueries {
    private final RuneLegendServer server;

    RuneLegendQueries(RuneLegendServer server) {
        this.server = server;
    }

    /** Number of players with the ALIVE round identity. Returns zero in WAITING. */
    public int aliveCount() {
        return server.playerStates().aliveCount();
    }

    /** Captures the credential for a connected player with a current, unexpired downing. */
    public Optional<DownedHandle> downedHandle(UUID playerId) {
        return server.downedHandle(playerId);
    }

    /** Captures the UUID's state, including stored equipment when the player is offline. */
    public PlayerSnapshot player(UUID playerId) {
        var rules = server.game().getRules();
        var state = rules.running() ? server.playerStates().state(playerId) : null;
        var player = server.currentPlayer(playerId);
        var equipped = server.players().get(playerId).equipped();
        var cooldowns = new HashMap<Identifier, Integer>();
        if (player != null) {
            for (var id : equipped) {
                if (server.registry().get(id) != null) cooldowns.put(id, server.tokens().cooldownTicks(player, id));
            }
        }
        return new PlayerSnapshot(playerId, rules, Optional.ofNullable(state).map(PlayerState::identity),
                player != null, equipped,
                player == null ? OptionalInt.empty() : OptionalInt.of(server.stamina().stamina(player)), cooldowns);
    }
}
