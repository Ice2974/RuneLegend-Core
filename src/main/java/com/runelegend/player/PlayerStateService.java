package com.runelegend.player;

import com.runelegend.api.PlayerSnapshot;
import com.runelegend.game.GameController;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-thread, save-session-only round identities. Never stored with token equipment. */
public final class PlayerStateService {
    private final GameController game;
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private final Set<UUID> spectateNextRound = new HashSet<>();

    public PlayerStateService(GameController game) { this.game = game; }

    public PlayerState state(UUID player) { game.getRules(); return states.get(player); }
    public boolean alive(UUID player) { return game.getRules().running() && state(player) == PlayerState.ALIVE; }
    public boolean spectateNextRound(UUID player) { game.getRules(); return spectateNextRound.contains(player); }

    public boolean selectSpectator(UUID player, boolean spectate) {
        if (game.getRules().running()) return false;
        if (spectate) spectateNextRound.add(player);
        else spectateNextRound.remove(player);
        return true;
    }

    public void beginRound(Collection<UUID> online) {
        if (!game.getRules().running()) throw new IllegalStateException("Round has not started");
        states.clear();
        for (var uuid : online) states.put(uuid, spectateNextRound.contains(uuid) ? PlayerState.SPECTATOR : PlayerState.ALIVE);
        spectateNextRound.clear();
    }

    public void join(UUID player) {
        if (!game.getRules().running()) return;
        states.compute(player, (_, old) -> old == null ? PlayerState.SPECTATOR
                : old == PlayerState.ALIVE || old == PlayerState.DOWNED ? PlayerState.ELIMINATED : old);
    }

    public void leave(UUID player) { if (game.getRules().running()) eliminate(player); }
    public boolean enterDowned(UUID player) { return transition(player, PlayerState.ALIVE, PlayerState.DOWNED); }
    public boolean completeRevive(UUID player) { return transition(player, PlayerState.DOWNED, PlayerState.ALIVE); }

    public boolean eliminate(UUID player) {
        if (!game.getRules().running()) return false;
        var previous = states.get(player);
        if (previous != PlayerState.ALIVE && previous != PlayerState.DOWNED) return false;
        states.put(player, PlayerState.ELIMINATED);
        return true;
    }

    private boolean transition(UUID player, PlayerState expected, PlayerState next) {
        return game.getRules().running() && states.replace(player, expected, next);
    }

    public boolean hasAlive() { game.getRules(); return states.containsValue(PlayerState.ALIVE); }

    public int aliveCount() {
        if (!game.getRules().running()) return 0;
        int count = 0;
        for (var state : states.values()) {
            if (state == PlayerState.ALIVE) count++;
        }
        return count;
    }


    /** Captures even offline identities during the transition, before clearRound(). */
    public Map<UUID, PlayerSnapshot.Identity> snapshotRound() {
        game.getRules();
        var identities = new HashMap<UUID, PlayerSnapshot.Identity>();
        states.forEach((uuid, state) -> identities.put(uuid, state.identity()));
        return Map.copyOf(identities);
    }

    public void clearRound() {
        game.getRules();
        states.clear();
        spectateNextRound.clear();
    }
}
