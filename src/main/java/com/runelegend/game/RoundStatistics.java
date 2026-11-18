package com.runelegend.game;

import com.runelegend.api.PlayerRoundStats;
import com.runelegend.api.PlayerStateEvents;
import com.runelegend.api.RoundMetric;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.runelegend.api.RoundMetric.*;

/** Server-thread totals. Only the active or most recently ended round can be queried. */
public final class RoundStatistics {
    private final GameController game;
    private final Map<UUID, EnumMap<RoundMetric, Double>> live = new HashMap<>();
    private Map<UUID, PlayerRoundStats> last = Map.of();

    public RoundStatistics(GameController game) {
        this.game = game;
    }

    public PlayerRoundStats player(UUID playerId) {
        if (!game.getRules().running()) return last.getOrDefault(playerId, PlayerRoundStats.EMPTY);
        var values = live.get(playerId);
        return values == null ? PlayerRoundStats.EMPTY : new PlayerRoundStats(values);
    }

    public void clear() {
        live.clear();
        last = Map.of();
    }

    public Map<UUID, PlayerRoundStats> finish() {
        Map<UUID, PlayerRoundStats> snapshot = new HashMap<>();
        live.forEach((uuid, values) -> snapshot.put(uuid, new PlayerRoundStats(values)));
        live.clear();
        last = Map.copyOf(snapshot);
        return last;
    }

    public void record(PlayerStateEvents.Downed event) {
        if (accepts(event.round())) add(event.playerId(), DOWNS, 1);
    }

    public void record(PlayerStateEvents.Revived event) {
        if (!accepts(event.round())) return;
        add(event.playerId(), REVIVED, 1);
        event.rescuerId().ifPresent(uuid -> add(uuid, RESCUES, 1));
    }

    public void record(PlayerStateEvents.Eliminated event) {
        if (accepts(event.round())) add(event.playerId(), ELIMINATIONS, 1);
    }

    void record(DamageStatistics.SettledHit hit, boolean creditAttacker, boolean creditTarget) {
        if (!accepts(hit.round())) return;
        if (creditAttacker) {
            add(hit.attackerId(), HITS_DEALT, 1);
            add(hit.attackerId(), DAMAGE_DEALT, hit.healthLost());
        }
        if (creditTarget) {
            add(hit.targetId(), HITS_TAKEN, 1);
            add(hit.targetId(), DAMAGE_TAKEN, hit.healthLost());
            add(hit.targetId(), DAMAGE_BLOCKED, hit.blocked());
            add(hit.targetId(), DAMAGE_ABSORBED, hit.absorbed());
            if (hit.attackerId() == null) {
                add(hit.targetId(), UNATTRIBUTED_HITS_TAKEN, 1);
                add(hit.targetId(), UNATTRIBUTED_DAMAGE_TAKEN, hit.healthLost());
            }
        }
    }

    private boolean accepts(long round) {
        var rules = game.getRules();
        return rules.running() && rules.round() == round;
    }

    private void add(UUID player, RoundMetric metric, double value) {
        if (value > 0) live.computeIfAbsent(player, _ -> new EnumMap<>(RoundMetric.class)).merge(metric, value, Double::sum);
    }
}
