package com.runelegend.test;

import com.runelegend.RuneLegendServer;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.token.TokenEngine;
import com.runelegend.token.TokenEvent;
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
            try (var _ = server.connect()) {
                context.waitFor(client -> client.player != null && cache.view() != null);
                server.runCommand("runelegend admin @p equip runelegend:immediate_counter");
                server.runCommand("runelegend game start");
                server.runOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayers().getFirst();
                    RuneLegendServer.find(minecraft).tokens().dispatch(new TokenEvent(TokenEvent.Kind.GUARD_SUCCESS,
                            player, player, player.level(), null, 6, 3, 3));
                });
                context.waitFor(client -> token(cache) != null && (token(cache).flags() & TokenEngine.ACTIVE) != 0);
                server.runCommand("kill @p");
                context.waitFor(client -> client.player != null && !client.player.isAlive()
                        && token(cache) != null && token(cache).flags() == 0);
                context.runOnClient(client -> client.player.respawn());
                context.waitFor(client -> client.player != null && client.player.isAlive() && ready(cache));
                server.runOnServer(minecraft -> {
                    var player = minecraft.getPlayerList().getPlayers().getFirst();
                    RuneLegendServer.find(minecraft).tokens().dispatch(new TokenEvent(TokenEvent.Kind.GUARD_SUCCESS,
                            player, player, player.level(), null, 6, 3, 3));
                });
                context.waitFor(client -> token(cache) != null && token(cache).flags() == TokenEngine.COOLDOWN);
            }
            server.waitFor(minecraft -> minecraft.getPlayerList().getPlayers().isEmpty());
            context.waitFor(client -> client.player == null && cache.view() == null);
            try (var _ = server.connect()) {
                context.waitFor(client -> client.player != null && ready(cache));
                server.runCommand("runelegend game stop");
                context.waitFor(client -> cache.rules() != null && !cache.rules().running());
                server.runCommand("runelegend game start");
                context.waitFor(client -> cache.rules().running() && ready(cache));
            }
        }
    }

    private static boolean ready(ClientStateCache cache) {
        var view = token(cache);
        return view != null && view.flags() == TokenEngine.READY && view.cooldownUntil() == 0;
    }

    private static TokenEngine.View token(ClientStateCache cache) {
        if (cache.view() == null) return null;
        return cache.view().tokens().stream().filter(view -> view.id().getPath().equals("immediate_counter"))
                .findFirst().orElse(null);
    }
}
