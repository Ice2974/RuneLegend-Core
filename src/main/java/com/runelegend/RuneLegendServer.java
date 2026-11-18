package com.runelegend;

import com.runelegend.api.DownedHandle;
import com.runelegend.api.PlayerStateEvents;
import com.runelegend.api.PlayerStateEvents.EliminationReason;
import com.runelegend.api.RoundSummary;
import com.runelegend.api.RuleSnapshot;
import com.runelegend.combat.GuardItems;
import com.runelegend.combat.GuardStaminaService;
import com.runelegend.game.GameController;
import com.runelegend.game.PlayerStateNotifications;
import com.runelegend.game.RoundNotifications;
import com.runelegend.game.RoundStatistics;
import com.runelegend.network.PlayerStateSync;
import com.runelegend.player.*;
import com.runelegend.token.TokenEngine;
import com.runelegend.token.TokenExtensions;
import com.runelegend.token.TokenRegistry;
import com.runelegend.token.effect.SoulSiphonEffect;
import com.runelegend.token.effect.AsuraHealth;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public final class RuneLegendServer {
    private static final Map<MinecraftServer, RuneLegendServer> INSTANCES = new ConcurrentHashMap<>();
    private final MinecraftServer minecraft;
    private final GameController game;
    private final RoundStatistics statistics;
    private final TokenRegistry registry;
    private final PlayerTokenService players;
    private final TokenEngine tokens;
    private final PlayerStateSync sync;
    private final GuardStaminaService stamina;
    private final PlayerStateConfig playerStateConfig;
    private final PlayerStateService playerStates;
    private final DownedService downed;
    private final Set<Long> reviving = new HashSet<>();
    private final Map<UUID, ServerGamePacketListenerImpl> transports = new HashMap<>();
    private long tick;
    private boolean noAliveSignalled;
    private final Set<UUID> downing = new HashSet<>();

    private RuneLegendServer(MinecraftServer minecraft, TokenExtensions extensions) {
        this.minecraft = minecraft;
        var config = ServerConfig.load(extensions);
        game = new GameController(this::checkThread, this::rulesChanged);
        statistics = new RoundStatistics(game);
        playerStateConfig = config.playerStates();
        playerStates = new PlayerStateService(game);
        downed = new DownedService(playerStateConfig);
        registry = config.tokenRegistry(extensions);
        players = new PlayerTokenService(game, PlayerTokenStore.load(PlayerTokenStore.defaultPath()), registry);
        sync = new PlayerStateSync(this);
        tokens = new TokenEngine(game, players, registry, this::tick, sync::flash, this::tokenEligible, this::currentPlayer);
        stamina = new GuardStaminaService(game, this::tick, config.guard());
    }

    public static RuneLegendServer find(MinecraftServer minecraft) { return INSTANCES.get(minecraft); }
    static void open(MinecraftServer minecraft, TokenExtensions extensions) {
        INSTANCES.put(minecraft, new RuneLegendServer(minecraft, extensions));
    }
    static void remove(MinecraftServer minecraft) { INSTANCES.remove(minecraft); }

    public long tick() { return tick; }
    public GameController game() { return game; }
    public RoundStatistics statistics() { return statistics; }
    public TokenRegistry registry() { return registry; }
    public PlayerTokenService players() { return players; }
    public TokenEngine tokens() { return tokens; }
    public GuardStaminaService stamina() { return stamina; }
    public PlayerStateSync sync() { return sync; }
    public PlayerStateService playerStates() { return playerStates; }
    public DownedService downed() { return downed; }
    public PlayerStateConfig playerStateConfig() { return playerStateConfig; }

    private List<ServerPlayer> online() {
        return transports.values().stream().map(connection -> connection.player).toList();
    }

    public ServerPlayer currentPlayer(UUID uuid) {
        var connection = transports.get(uuid);
        return connection == null ? null : connection.player;
    }

    public boolean tokenEligible(ServerPlayer player) {
        return isCurrent(player) && playerStates.alive(player.getUUID()) && !player.isSpectator();
    }

    public void join(ServerPlayer player) {
        checkThread();
        var previous = currentPlayer(player.getUUID());
        if (previous != null && previous != player) leave(previous);
        transports.put(player.getUUID(), player.connection);
        playerStates.join(player.getUUID());
        downed.removeDowned(player.getUUID());
        // Also remove the permanent modifier left by older saves.
        clearPlayerEffects(player);
        stamina.join(player);
        applyIdentity(player);
        if (!game.getRules().running()) teleportToWorldSpawn(currentPlayer(player.getUUID()));
        sync.refreshTab(online());
        sync.join(currentPlayer(player.getUUID()));
    }

    public void leave(ServerPlayer player) {
        checkThread();
        var current = transports.get(player.getUUID());
        if (current == null || current != player.connection || current.player != player) return;
        var uuid = player.getUUID();
        game.withDeferredNotifications(() -> {
            var elimination = commitElimination(uuid, downed.get(uuid), EliminationReason.DISCONNECT, Optional.empty());
            transports.remove(uuid);
            sync.leave(player);
            stamina.leave(player);
            clearPlayerEffects(player);
            publishElimination(elimination);
        });
    }

    public boolean isCurrent(ServerPlayer player) {
        return player.connection != null && transports.get(player.getUUID()) == player.connection
                && player.connection.player == player;
    }

    /** Called at vanilla die(), after vanilla has already attempted totem protection. */
    public boolean interceptDeath(ServerPlayer player) {
        checkThread();
        if (!isCurrent(player) || !game.getRules().running() || playerStates.state(player.getUUID()) == null) return false;
        var uuid = player.getUUID();
        game.withDeferredNotifications(() -> {
            if (!downing.add(uuid)) return;
            try {
                applyDowning(player);
            } finally {
                downing.remove(uuid);
            }
        });
        return true;
    }

    private void applyDowning(ServerPlayer player) {
        if (playerStates.enterDowned(player.getUUID())) {
            var record = downed.add(player.getUUID(), game.getRules().round(), tick, player.level().dimension(),
                    player.position(), player.getYRot(), player.getXRot());
            // Queue in commit order. Delivery waits until cleanup and mode changes are complete.
            var event = new PlayerStateEvents.Downed(record.round(), record.playerUuid(), anchor(record));
            var handleId = record.handleId();
            statistics.record(event);
            game.publishNotification(() -> PlayerStateNotifications.downed(minecraft, event, handleId));
            player.stopUsingItem();
            clearPlayerEffects(player);
            player.removeAllEffects();
        }
        resetHazards(player);
        player.setHealth(Math.min(1F, player.getMaxHealth()));
        player.setGameMode(GameType.SPECTATOR);
        player.setCamera(player);
    }

    public void releasedSneak(ServerPlayer player) {
        checkThread();
        if (isCurrent(player)) downed.cancelRescue(player.getUUID());
    }

    public void changedLevel(ServerPlayer player) {
        checkThread();
        if (!isCurrent(player)) return;
        downed.cancelRescue(player.getUUID());
        sync.update(player);
    }

    public void respawned(ServerPlayer oldPlayer, ServerPlayer player, boolean alive) {
        checkThread();
        if (!isCurrent(player)) return;
        downed.cancelRescue(player.getUUID());
        if (!alive) clearPlayerEffects(oldPlayer);
        applyIdentity(player);
        sync.update(player);
    }

    public void copyPlayer(ServerPlayer oldPlayer, ServerPlayer player, boolean alive) {
        checkThread();
        if (alive && isCurrent(oldPlayer) && playerStates.alive(oldPlayer.getUUID())) {
            SoulSiphonEffect.transferBonus(oldPlayer, player, true);
            AsuraHealth.transfer(oldPlayer, player);
            tokens.playerReplaced(oldPlayer, player);
        }
    }

    private PlayerStateEvents.Eliminated commitElimination(UUID uuid, DownedService.DownedRecord record,
                                                          EliminationReason reason, Optional<Identifier> reasonId) {
        var previous = playerStates.state(uuid);
        boolean changed = playerStates.eliminate(uuid);
        downed.removeDowned(uuid);
        if (!changed) return null;
        return new PlayerStateEvents.Eliminated(game.getRules().round(), uuid, previous.identity(), reason,
                Optional.ofNullable(record).map(RuneLegendServer::anchor), reasonId);
    }

    private void expire(DownedService.DownedRecord record) {
        var elimination = commitElimination(record.playerUuid(), record, EliminationReason.TIMEOUT, Optional.empty());
        var player = currentPlayer(record.playerUuid());
        if (player != null) applyElimination(player);
        publishElimination(elimination);
    }

    public boolean tryEliminate(UUID uuid, long round, Identifier reasonId) {
        var rules = game.getRules();
        var reason = Optional.of(reasonId);
        var player = currentPlayer(uuid);
        if (!rules.running() || rules.round() != round || downing.contains(uuid) || player == null || !isCurrent(player)) return false;
        var elimination = commitElimination(uuid, downed.get(uuid), EliminationReason.SCRIPTED, reason);
        if (elimination == null) return false;
        game.withDeferredNotifications(() -> {
            applyElimination(player);
            publishElimination(elimination);
        });
        return true;
    }

    private void applyElimination(ServerPlayer player) {
        player.stopUsingItem();
        clearPlayerEffects(player);
        player.setGameMode(GameType.SPECTATOR);
    }

    private void publishElimination(PlayerStateEvents.Eliminated event) {
        if (event == null) return;
        statistics.record(event);
        game.publishNotification(() -> PlayerStateNotifications.eliminated(minecraft, event));
    }

    private static PlayerStateEvents.DownedAnchor anchor(DownedService.DownedRecord record) {
        return new PlayerStateEvents.DownedAnchor(record.sequence(), record.dimension(), record.position(),
                record.yaw(), record.pitch());
    }

    public Optional<DownedHandle> downedHandle(UUID uuid) {
        var record = currentDowning(uuid);
        return record == null ? Optional.empty() : Optional.of(new DownedHandle(uuid, record.handleId()));
    }

    public boolean tryRevive(DownedHandle handle) {
        checkThread();
        Objects.requireNonNull(handle);
        var record = downed.get(handle.playerId());
        return record != null && record.handleId().equals(handle.id()) && revive(handle.playerId(), record.sequence());
    }

    private DownedService.DownedRecord currentDowning(UUID uuid) {
        var rules = game.getRules();
        var record = downed.get(uuid);
        var player = currentPlayer(uuid);
        if (!rules.running() || downing.contains(uuid) || record == null || record.round() != rules.round() || tick >= record.expiresAtTick()
                || playerStates.state(uuid) != PlayerState.DOWNED || player == null || !isCurrent(player)) return null;
        return record;
    }

    /** Shared authoritative completion point for a rescue without a player rescuer. */
    public boolean revive(UUID uuid, long sequence) {
        return revive(uuid, sequence, Optional.empty());
    }

    private boolean revive(UUID uuid, long sequence, Optional<UUID> rescuerId) {
        var record = currentDowning(uuid);
        if (record == null || record.sequence() != sequence || !reviving.add(sequence)) return false;
        try {
            if (!completeRevive(currentPlayer(uuid), record)) return false;
        } finally {
            reviving.remove(sequence);
        }
        noAliveSignalled = false;
        var event = new PlayerStateEvents.Revived(record.round(), uuid, anchor(record), rescuerId);
        statistics.record(event);
        game.publishNotification(() -> PlayerStateNotifications.revived(minecraft, event));
        return true;
    }

    private boolean completeRevive(ServerPlayer player, DownedService.DownedRecord record) {
        var uuid = record.playerUuid();
        var level = minecraft.getLevel(record.dimension());
        if (level == null) {
            RuneLegendMod.LOGGER.error("Cannot revive {}: death dimension {} is unavailable", uuid, record.dimension());
            return false;
        }
        var position = RevivePositionResolver.resolve(level, player, record.position());
        player.setCamera(player);
        if (!player.teleportTo(level, position.x, position.y, position.z, Set.of(), record.yaw(), record.pitch(), false)) {
            RuneLegendMod.LOGGER.error("Cannot teleport {} to revive position {}", uuid, position);
            return false;
        }
        // A cross-dimension teleport can trigger other mods' callbacks before it returns.
        if (!isCurrent(player) || currentDowning(uuid) != record) return false;
        resetHazards(player);
        float maximum = player.getMaxHealth();
        player.setHealth(Math.min(maximum, Math.max(Float.MIN_NORMAL,
                (float) (maximum * playerStateConfig.reviveHealthPercent() / 100))));
        player.setGameMode(GameType.ADVENTURE);
        if (!playerStates.completeRevive(uuid)) return false;
        downed.removeDowned(uuid);
        return true;
    }

    private static void resetHazards(ServerPlayer player) {
        player.clearFire();
        player.setTicksFrozen(0);
        player.setAirSupply(player.getMaxAirSupply());
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
    }

    private void applyIdentity(ServerPlayer player) {
        boolean waiting = !game.getRules().running();
        var state = playerStates.state(player.getUUID());
        if (!player.isAlive()) {
            player.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            player = player.connection.player;
        }
        player.setGameMode(waiting || state == PlayerState.ALIVE ? GameType.ADVENTURE : GameType.SPECTATOR);
        if (waiting) {
            resetHazards(player);
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
        }
    }

    /**
     * Uses vanilla's shared-spawn placement, so WAITING always starts and resumes at the
     * world spawn instead of the position saved when the player last disconnected.
     */
    private void teleportToWorldSpawn(ServerPlayer player) {
        if (player == null) return;
        if (player.teleport(TeleportTransition.createDefault(player, TeleportTransition.DO_NOTHING)) == null) {
            RuneLegendMod.LOGGER.warn("Cannot teleport {} to the world spawn", player.getUUID());
        }
    }

    public void died(ServerPlayer player) {
        checkThread();
        if (!isCurrent(player)) return;
        var uuid = player.getUUID();
        game.withDeferredNotifications(() -> {
            var elimination = commitElimination(uuid, downed.get(uuid), EliminationReason.DEATH, Optional.empty());
            if (GuardItems.using(player)) player.stopUsingItem();
            clearPlayerEffects(player);
            publishElimination(elimination);
        });
    }

    private void clearPlayerEffects(ServerPlayer player) {
        tokens.clearEffects(player.getUUID());
        SoulSiphonEffect.clearBonus(player);
    }

    void endTick() {
        tick++;
        if (game.getRules().running()) {
            var rescuers = online().stream().map(player -> new DownedService.Rescuer(
                    player.getUUID(), player.level().dimension(), player.position(), tokenEligible(player) && player.isAlive(),
                    player.getLastClientInput().shift())).toList();
            game.withDeferredNotifications(() -> downed.tick(tick, game.getRules().round(), rescuers, this::expire,
                    (record, rescuer) -> revive(record.playerUuid(), record.sequence(), Optional.of(rescuer))));
        }
        var online = online();
        online.forEach(stamina::tick);
        tokens.tick(online);
        var rules = game.getRules();
        if (rules.running() && !noAliveSignalled && !playerStates.hasAlive()) {
            noAliveSignalled = true;
            var identities = playerStates.snapshotRound();
            game.publishNotification(() -> RoundNotifications.noAlive(minecraft, rules.round(), identities));
        }
        var current = online();
        sync.refreshTab(current);
        current.forEach(sync::update);
    }

    void close() {
        statistics.clear();
        playerStates.clearRound();
        tokens.clearRound();
        online().forEach(SoulSiphonEffect::clearBonus);
        downed.clearRound();
        sync.clear();
        transports.clear();
    }

    private void rulesChanged(RuleSnapshot before, RuleSnapshot after, RoundSummary.Reason reason, Identifier outcomeId,
                              Predicate<UUID> participates) {
        noAliveSignalled = false;
        var summary = after.running() ? null : new RoundSummary(before.round(), reason,
                Optional.ofNullable(outcomeId), playerStates.snapshotRound(), statistics.finish());
        if (after.running()) statistics.clear();
        var online = online();
        downed.clearRound();
        if (after.running()) playerStates.beginRound(online.stream().map(ServerPlayer::getUUID).toList(), participates);
        else playerStates.clearRound();
        tokens.clearRound();
        if (!after.running()) online.forEach(SoulSiphonEffect::clearBonus);
        online.forEach(this::applyIdentity);
        online.forEach(stamina::resetRound);
        var current = online();
        sync.refreshTab(current);
        current.forEach(sync::rules);
        current.forEach(sync::update);
        // Run after the rules/state sync so a cross-dimension spawn check does not
        // make the client reject a delta that arrives before the new rules packet.
        if (summary != null) {
            online().forEach(this::teleportToWorldSpawn);
            game.publishNotification(() -> RoundNotifications.ended(minecraft, summary));
        } else {
            var identities = playerStates.snapshotRound();
            game.publishNotification(() -> RoundNotifications.started(minecraft, after.round(), identities));
        }
    }

    private void checkThread() {
        if (!minecraft.isSameThread()) throw new IllegalStateException("RuneLegend requires the server thread");
    }
}
