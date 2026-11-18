package com.runelegend;

import com.runelegend.api.RuleSnapshot;
import com.runelegend.combat.GuardItems;
import com.runelegend.combat.GuardStaminaService;
import com.runelegend.game.GameController;
import com.runelegend.network.PlayerStateSync;
import com.runelegend.player.*;
import com.runelegend.token.TokenEngine;
import com.runelegend.token.TokenRegistry;
import com.runelegend.token.effect.SoulSiphonEffect;
import com.runelegend.token.effect.AsuraHealth;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public final class RuneLegendServer {
    private static final Map<MinecraftServer, RuneLegendServer> INSTANCES = new ConcurrentHashMap<>();
    private final MinecraftServer minecraft;
    private final GameController game;
    private final TokenRegistry registry;
    private final PlayerTokenService players;
    private final TokenEngine tokens;
    private final PlayerStateSync sync;
    private final GuardStaminaService stamina;
    private final PlayerStateConfig playerStateConfig;
    private final PlayerStateService playerStates;
    private final DownedService downed;
    private final Map<UUID, ServerGamePacketListenerImpl> transports = new HashMap<>();
    private long tick;

    private RuneLegendServer(MinecraftServer minecraft) {
        this.minecraft = minecraft;
        var config = ServerConfig.load();
        game = new GameController(this::checkThread, this::rulesChanged);
        playerStateConfig = config.playerStates();
        playerStates = new PlayerStateService(game);
        downed = new DownedService(playerStateConfig);
        registry = TokenRegistry.builtIn(config.tokens());
        players = new PlayerTokenService(game, PlayerTokenStore.load(PlayerTokenStore.defaultPath()), registry);
        sync = new PlayerStateSync(this);
        tokens = new TokenEngine(game, players, registry, this::tick, sync::flash, this::tokenEligible, this::currentPlayer);
        stamina = new GuardStaminaService(game, this::tick, config.guard());
    }

    public static RuneLegendServer find(MinecraftServer minecraft) { return INSTANCES.get(minecraft); }
    static void open(MinecraftServer minecraft) { INSTANCES.put(minecraft, new RuneLegendServer(minecraft)); }
    static void remove(MinecraftServer minecraft) { INSTANCES.remove(minecraft); }

    public long tick() { return tick; }
    public GameController game() { return game; }
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
        playerStates.leave(player.getUUID());
        downed.removeDowned(player.getUUID());
        transports.remove(player.getUUID());
        sync.leave(player);
        stamina.leave(player);
        clearPlayerEffects(player);
    }

    public boolean isCurrent(ServerPlayer player) {
        return player.connection != null && transports.get(player.getUUID()) == player.connection
                && player.connection.player == player;
    }

    /** Called at vanilla die(), after vanilla has already attempted totem protection. */
    public boolean interceptDeath(ServerPlayer player) {
        checkThread();
        if (!isCurrent(player) || !game.getRules().running() || playerStates.state(player.getUUID()) == null) return false;
        if (playerStates.enterDowned(player.getUUID())) {
            downed.add(player.getUUID(), game.getRules().round(), tick, player.level().dimension(),
                    player.position(), player.getYRot(), player.getXRot());
            player.stopUsingItem();
            clearPlayerEffects(player);
            player.removeAllEffects();
        }
        resetHazards(player);
        player.setHealth(Math.min(1F, player.getMaxHealth()));
        player.setGameMode(GameType.SPECTATOR);
        player.setCamera(player);
        return true;
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
        }
    }

    private void eliminate(UUID uuid) {
        playerStates.eliminate(uuid);
        downed.removeDowned(uuid);
        var player = currentPlayer(uuid);
        if (player != null) {
            player.stopUsingItem();
            clearPlayerEffects(player);
            player.setGameMode(GameType.SPECTATOR);
        }
    }

    /** Shared authoritative completion point for future rescue effects. */
    public boolean revive(UUID uuid, long sequence) {
        checkThread();
        var record = downed.get(uuid);
        var player = currentPlayer(uuid);
        if (!game.getRules().running() || record == null || record.sequence() != sequence
                || record.round() != game.getRules().round() || tick >= record.expiresAtTick()
                || playerStates.state(uuid) != PlayerState.DOWNED || player == null || !isCurrent(player)) return false;
        var level = minecraft.getLevel(record.dimension());
        if (level == null) {
            RuneLegendMod.LOGGER.error("Cannot revive {}: death dimension {} is unavailable", uuid, record.dimension());
            return false;
        }
        var position = RevivePositionResolver.resolve(level, player, record.position());
        player.setCamera(player);
        if (!player.teleportTo(level, position.x, position.y, position.z, java.util.Set.of(), record.yaw(), record.pitch(), false)) {
            RuneLegendMod.LOGGER.error("Cannot teleport {} to revive position {}", uuid, position);
            return false;
        }
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
        playerStates.eliminate(player.getUUID());
        downed.removeDowned(player.getUUID());
        if (GuardItems.using(player)) player.stopUsingItem();
        clearPlayerEffects(player);
    }

    private void clearPlayerEffects(ServerPlayer player) {
        tokens.clearEffects(player.getUUID());
        SoulSiphonEffect.clearBonus(player);
    }

    void endTick() {
        tick++;
        var online = online();
        if (game.getRules().running()) {
            downed.tick(tick, game.getRules().round(), online.stream().map(player -> new DownedService.Rescuer(
                    player.getUUID(), player.level().dimension(), player.position(), tokenEligible(player) && player.isAlive(),
                    player.getLastClientInput().shift())).toList(), record -> eliminate(record.playerUuid()),
                    (record, rescuer) -> revive(record.playerUuid(), record.sequence()));
        }
        online.forEach(stamina::tick);
        tokens.tick(online);
        if (game.getRules().running() && !playerStates.hasAlive()) game.stopGame();
        var current = online();
        sync.refreshTab(current);
        current.forEach(sync::update);
    }

    void close() {
        playerStates.clearRound();
        tokens.clearRound();
        online().forEach(SoulSiphonEffect::clearBonus);
        downed.clearRound();
        sync.clear();
        transports.clear();
    }

    private void rulesChanged(RuleSnapshot before, RuleSnapshot after) {
        var online = online();
        if (before.state() != after.state()) {
            downed.clearRound();
            if (after.running()) playerStates.beginRound(online.stream().map(ServerPlayer::getUUID).toList());
            else playerStates.clearRound();
            tokens.clearRound();
            if (!after.running()) online.forEach(SoulSiphonEffect::clearBonus);
            online.forEach(this::applyIdentity);
            online.forEach(stamina::resetRound);
        }
        var current = online();
        sync.refreshTab(current);
        current.forEach(sync::rules);
        current.forEach(sync::update);
        // Run after the rules/state sync so a cross-dimension spawn check does not
        // make the client reject a delta that arrives before the new rules packet.
        if (!after.running()) online().forEach(this::teleportToWorldSpawn);
    }

    private void checkThread() {
        if (!minecraft.isSameThread()) throw new IllegalStateException("RuneLegend requires the server thread");
    }
}
