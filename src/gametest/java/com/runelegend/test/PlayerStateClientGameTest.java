package com.runelegend.test;

import com.runelegend.RuneLegendServer;
import com.runelegend.api.GameState;
import com.runelegend.api.RuleSnapshot;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.network.RuneLegendPayloads;
import com.runelegend.player.PlayerState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import static com.runelegend.network.RuneLegendPayloads.*;

public final class PlayerStateClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(_ -> checkStalePackets());
        var props = new Properties();
        props.setProperty("online-mode", "false");
        props.setProperty("white-list", "false");
        var cache = ClientStateCache.INSTANCE;
        try (var server = context.worldBuilder().createServer(props)) {
            try (var ignored = server.connect()) {
                context.waitFor(client -> client.player != null && cache.view() != null);
                server.runCommand("execute as @p run runelegend player spectate true");
                context.waitFor(client -> cache.view() != null && cache.view().spectateNextRound());
            }
            server.waitFor(minecraft -> minecraft.getPlayerList().getPlayers().isEmpty());
            try (var connection = server.connect()) {
                context.waitFor(client -> client.player != null && cache.view() != null && cache.view().spectateNextRound());
                server.runCommand("runelegend game start");
                context.waitFor(client -> cache.rules() != null && cache.rules().round() == 1 && !cache.rules().running()
                        && cache.view() != null && !cache.view().spectateNextRound() && cache.view().playerState() == null);
                var helper = server.computeOnServer(minecraft -> PlayerStateFixtures.join(minecraft,
                        connection.getServerPlayer().level(), UUID.randomUUID()));
                server.runCommand("kill @p");
                context.waitFor(client -> client.player != null && !client.player.isAlive());
                server.runCommand("time set day");
                server.runCommand("weather clear");
                server.runCommand("tp @p ~ ~ ~ 0 -5");
                server.runCommand("runelegend game start");
                context.waitFor(client -> cache.view() != null && cache.view().playerState() == PlayerState.ALIVE
                        && client.player != null && client.player.isAlive()
                        && !(client.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen));
                server.runOnServer(minecraft -> {
                    helper.setPos(connection.getServerPlayer().position().add(0, 0, 4));
                    helper.die(helper.damageSources().genericKill());
                });
                context.waitFor(client -> cache.view() != null && cache.view().downed().size() == 1);
                connection.waitForChunksRender();
                context.takeScreenshot("runelegend-downed-marker-en");
                // The helper can fly elsewhere without moving the authoritative marker.
                var anchor = server.computeOnServer(minecraft -> RuneLegendServer.find(minecraft).downed().get(helper.getUUID()).position());
                server.runOnServer(minecraft -> {
                    helper.setPos(anchor.add(50, 40, 50));
                    var player = connection.getServerPlayer();
                    player.teleportTo(player.level(), anchor.x, anchor.y, anchor.z - 1, java.util.Set.of(), 0, 0, false);
                });
                context.getInput().holdKey(options -> options.keyShift);
                long began = server.computeOnServer(minecraft -> RuneLegendServer.find(minecraft).tick());
                server.waitFor(minecraft -> RuneLegendServer.find(minecraft).tick() >= began + 20);
                context.getInput().releaseKey(options -> options.keyShift);
                connection.waitForServerboundPackets();
                server.waitFor(minecraft -> RuneLegendServer.find(minecraft).downed().rescue(helper.getUUID()) == null);
                context.getInput().holdKey(options -> options.keyShift);
                try {
                    context.waitFor(client -> cache.view() != null && cache.view().downed().isEmpty(), 200);
                    server.runOnServer(minecraft -> {
                        if (RuneLegendServer.find(minecraft).playerStates().state(helper.getUUID()) != PlayerState.ALIVE
                                || helper.getHealth() != helper.getMaxHealth() / 2)
                            throw new AssertionError("Real sneak input did not revive at configured health");
                    });
                } finally { context.getInput().releaseKey(options -> options.keyShift); }

                var originalLanguage = context.computeOnClient(client -> client.getLanguageManager().getSelected());
                var reload = context.computeOnClient(client -> {
                    client.getLanguageManager().setSelected("zh_cn");
                    return client.reloadResourcePacks();
                });
                context.waitFor(client -> reload.isDone() && client.gui.overlay() == null);
                server.runCommand("tp @p ~ ~ ~ 0 -5");
                server.runOnServer(minecraft -> {
                    helper.setPos(connection.getServerPlayer().position().add(0, 0, 4));
                    helper.die(helper.damageSources().genericKill());
                });
                context.waitFor(client -> cache.view() != null && !cache.view().downed().isEmpty());
                context.waitTicks(2);
                context.takeScreenshot("runelegend-downed-marker-zh");
                server.runCommand("kill @p");
                context.waitFor(client -> cache.rules() != null && !cache.rules().running() && cache.view() != null
                        && cache.view().downed().isEmpty() && cache.view().playerState() == null && client.player != null
                        && client.player.isAlive() && !client.player.isSpectator() && client.player.getHealth() == client.player.getMaxHealth());
                context.takeScreenshot("runelegend-auto-stop-clears-markers");
                var restore = context.computeOnClient(client -> {
                    client.getLanguageManager().setSelected(originalLanguage);
                    return client.reloadResourcePacks();
                });
                context.waitFor(client -> restore.isDone() && client.gui.overlay() == null);
                server.runOnServer(minecraft -> RuneLegendServer.find(minecraft).leave(helper));
            }
        }
        // A second save/server in the same client process starts with a fresh session.
        try (var server = context.worldBuilder().createServer(props); var ignored = server.connect()) {
            context.waitFor(client -> client.player != null && cache.rules() != null && cache.rules().round() == 0
                    && !cache.rules().running() && cache.view() != null && cache.view().downed().isEmpty()
                    && cache.view().playerState() == null && !cache.view().spectateNextRound());
        }
    }

    private static void checkStalePackets() {
        var cache = ClientStateCache.INSTANCE;
        var marker = new DownedMarker(UUID.randomUUID(), 1, 1, Identifier.parse("minecraft:overworld"), 0, 64, 0, 15);
        var state = new View(List.of(), 20000, 20000, PlayerState.ALIVE, false, List.of(marker));
        try {
            cache.clear();
            cache.apply(new Rules(new RuleSnapshot(GameState.RUNNING, 1, 1)));
            cache.apply(new PlayerSnapshot(1, 20, state, 1, 1));
            cache.apply(new PlayerDelta(2, 3, 40, DOWNED, state, List.of(), 1, 1));
            if (!cache.desynced() || cache.view() != null) throw new AssertionError("Missing delta did not hide untrusted markers");
            cache.apply(new PlayerSnapshot(4, 40, state, 1, 1));
            if (cache.view() == null || cache.desynced()) throw new AssertionError("Snapshot did not recover the cache");
            cache.apply(new Rules(new RuleSnapshot(GameState.WAITING, 1, 2)));
            cache.apply(new PlayerSnapshot(999, 40, state, 1, 1));
            if (cache.view() != null) throw new AssertionError("Old round data restored a stopped marker");
            cache.apply(new Rules(new RuleSnapshot(GameState.RUNNING, 2, 3)));
            cache.apply(new PlayerSnapshot(1000, 40, state, 1, 1));
            if (cache.view() != null) throw new AssertionError("Old round data crossed into a new round");
        } finally { cache.clear(); }
    }
}
