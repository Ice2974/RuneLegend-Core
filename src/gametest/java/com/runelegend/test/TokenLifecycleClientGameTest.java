package com.runelegend.test;

import com.runelegend.RuneLegendServer;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.token.TokenEngine;
import com.runelegend.token.TokenEvent;
import com.runelegend.player.PlayerState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.Properties;

public final class TokenLifecycleClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var props = new Properties();
        props.setProperty("online-mode", "false");
        props.setProperty("white-list", "false");
        var cache = ClientStateCache.INSTANCE;
        try (var server = context.worldBuilder().createServer(props)) {
            var helper = new java.util.concurrent.atomic.AtomicReference<net.minecraft.server.level.ServerPlayer>();
            try (var _ = server.connect()) {
                context.waitFor(client -> client.player != null && cache.view() != null);
                server.runCommand("runelegend admin @p clear");
                server.runCommand("runelegend admin @p equip runelegend:retribution");
                server.runOnServer(minecraft -> helper.set(PlayerStateFixtures.join(minecraft, minecraft.overworld(), java.util.UUID.randomUUID())));
                server.runCommand("runelegend game start");
                server.runOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayers().getFirst();
                    // The joined helper stands in as the living attacker a HURT event is attributed to.
                    RuneLegendServer.find(minecraft).tokens().dispatch(new TokenEvent(TokenEvent.Kind.HURT,
                            player, player, player.level(), player.damageSources().mobAttack(helper.get()), 6, 6, 0));
                });
                context.waitFor(client -> token(cache) != null && (token(cache).flags() & TokenEngine.ACTIVE) != 0);
                server.runCommand("kill @p");
                context.waitFor(client -> client.player != null && client.player.isAlive() && client.player.isSpectator()
                        && cache.view() != null && cache.view().playerState() == PlayerState.DOWNED && !cache.view().downed().isEmpty()
                        && token(cache) != null && token(cache).flags() == 0);
                server.runOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayers().getFirst();
                    var record = RuneLegendServer.find(minecraft).downed().get(player.getUUID());
                    helper.get().setPos(record.position());
                    PlayerStateFixtures.sneak(helper.get(), true);
                });
                context.waitFor(client -> client.player != null && !client.player.isSpectator() && ready(cache)
                        && cache.view().playerState() == PlayerState.ALIVE && cache.view().downed().isEmpty());
                server.runOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayers().getFirst();
                    RuneLegendServer.find(minecraft).tokens().dispatch(new TokenEvent(TokenEvent.Kind.HURT,
                            player, player, player.level(), player.damageSources().mobAttack(helper.get()), 6, 6, 0));
                });
                context.waitFor(client -> token(cache) != null && token(cache).flags() == TokenEngine.COOLDOWN);
            }
            server.waitFor(minecraft -> minecraft.getPlayerList().getPlayers().isEmpty());
            context.waitFor(client -> client.player == null && cache.view() == null);
            try (var _ = server.connect()) {
                context.waitFor(client -> client.player != null && cache.view() != null
                        && cache.view().playerState() == PlayerState.ELIMINATED && client.player.isSpectator()
                        && token(cache) != null && token(cache).flags() == 0);
                server.runCommand("runelegend game stop");
                context.waitFor(client -> cache.rules() != null && !cache.rules().running());
                server.runCommand("runelegend game start");
                context.waitFor(client -> cache.rules().running() && ready(cache));
            }
            server.runOnServer(minecraft -> RuneLegendServer.find(minecraft).leave(helper.get()));
        }
    }

    private static boolean ready(ClientStateCache cache) {
        var view = token(cache);
        return view != null && view.flags() == TokenEngine.READY && view.cooldownUntil() == 0;
    }

    private static TokenEngine.View token(ClientStateCache cache) {
        if (cache.view() == null) return null;
        return cache.view().tokens().stream().filter(view -> view.id().getPath().equals("retribution"))
                .findFirst().orElse(null);
    }
}
