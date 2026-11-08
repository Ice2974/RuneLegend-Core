package com.runelegend;

import com.runelegend.api.RuleSnapshot;
import com.runelegend.combat.GuardItems;
import com.runelegend.combat.GuardStaminaService;
import com.runelegend.game.GameRulesSavedData;
import com.runelegend.game.GameController;
import com.runelegend.network.PlayerStateSync;
import com.runelegend.player.PlayerTokenSavedData;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.token.TokenEngine;
import com.runelegend.token.TokenConfig;
import com.runelegend.token.TokenRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RuneLegendServer {
    private static final Map<MinecraftServer, RuneLegendServer> INSTANCES = new ConcurrentHashMap<>();
    private final MinecraftServer minecraft;
    private final GameController game;
    private final TokenRegistry registry;
    private final PlayerTokenService players;
    private final TokenEngine tokens;
    private final PlayerStateSync sync;
    private final GuardStaminaService stamina;
    private final Map<UUID, ServerGamePacketListenerImpl> transports = new HashMap<>();
    private long tick;

    private RuneLegendServer(MinecraftServer minecraft) {
        this.minecraft = minecraft;
        var savedRules = GameRulesSavedData.load(minecraft);
        game = new GameController(this::checkThread, this::rulesChanged, savedRules.rules(), savedRules::update);
        registry = TokenRegistry.builtIn(TokenConfig.loadOrGenerate(TokenConfig.defaultPath()));
        players = new PlayerTokenService(game, PlayerTokenSavedData.load(minecraft), registry);
        tokens = new TokenEngine(game, players, registry, this::tick);
        sync = new PlayerStateSync(this);
        stamina = new GuardStaminaService(game, this::tick);
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

    public void join(ServerPlayer player) {
        checkThread();
        transports.put(player.getUUID(), player.connection);
        tokens.clearEffects(player.getUUID());
        stamina.join(player);
        sync.join(player);
    }

    public void leave(ServerPlayer player) {
        checkThread();
        var current = transports.get(player.getUUID());
        if (current != null && current != player.connection) return;
        transports.remove(player.getUUID());
        sync.leave(player);
        stamina.leave(player);
        tokens.clearEffects(player.getUUID());
    }

    public boolean isCurrent(ServerPlayer player) {
        return transports.get(player.getUUID()) == player.connection;
    }

    public void died(ServerPlayer player) {
        checkThread();
        if (GuardItems.using(player)) player.stopUsingItem();
        tokens.clearEffects(player.getUUID());
    }

    void endTick() {
        tick++;
        var online = minecraft.getPlayerList().getPlayers();
        online.forEach(stamina::tick);
        tokens.tick(online);
        online.forEach(sync::update);
    }

    void close() {
        tokens.clearRound();
    }

    private void rulesChanged(RuleSnapshot before, RuleSnapshot after) {
        var online = minecraft.getPlayerList().getPlayers();
        if (before.state() != after.state()) {
            tokens.clearRound();
            online.forEach(stamina::resetRound);
        }
        online.forEach(sync::rules);
        online.forEach(sync::update);
    }

    private void checkThread() {
        if (!minecraft.isSameThread()) throw new IllegalStateException("RuneLegend requires the server thread");
    }
}
