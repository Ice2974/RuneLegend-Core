package com.runelegend.test;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.token.TokenEngine;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;

import java.util.Properties;

public final class ArroganceClientGameTest implements FabricClientGameTest {
    private static final net.minecraft.resources.Identifier ARROGANCE = RuneLegendMod.id("arrogance");

    @Override public void runTest(ClientGameTestContext context) {
        var props = new Properties();
        props.setProperty("online-mode", "false");
        var cache = ClientStateCache.INSTANCE;
        try (var dedicated = context.worldBuilder().createServer(props); var connection = dedicated.connect()) {
            context.waitFor(client -> client.player != null && cache.view() != null);
            dedicated.runCommand("gamemode survival @p");
            dedicated.runCommand("runelegend admin @p clear");
            dedicated.runCommand("runelegend admin @p equip runelegend:arrogance");
            dedicated.runCommand("runelegend game start");
            context.waitFor(client -> flags(cache) == (TokenEngine.READY | TokenEngine.PREPARING));
            connection.waitForChunksRender();
            // Keep the regular HUD renderer from draining the queue before we inspect it at exact times.
            context.runOnClient(client -> client.gui.hud.toggle());
            try {
                dedicated.runOnServer(mc -> killEnemies(connection.getServerPlayer(), 12));
                context.waitFor(client -> flags(cache) == TokenEngine.ACTIVE);
                connection.waitForClientboundPackets();
                context.runOnClient(client -> {
                    long now = Util.getMillis();
                    // The default cap is reached after four increases, so four flashes are queued.
                    for (int i = 0; i < 4; i++) {
                        frame(cache, now + i * 400L, 0);
                        frame(cache, now + i * 400L + 350, 7);
                    }
                    frame(cache, now + 1600, -1);
                });
                dedicated.runOnServer(mc -> RuneLegendServer.find(mc).sync().resync(connection.getServerPlayer()));
                connection.waitForClientboundPackets();
                context.runOnClient(client -> frame(cache, Util.getMillis(), -1));

                dedicated.runOnServer(mc -> {
                    var player = connection.getServerPlayer();
                    player.getFoodData().setFoodLevel(10);
                    player.setHealth(12);
                });
                context.waitFor(client -> flags(cache) == 0);
                dedicated.runOnServer(mc -> {
                    var player = connection.getServerPlayer();
                    player.setHealth(20);
                });
                context.waitFor(client -> flags(cache) == (TokenEngine.READY | TokenEngine.PREPARING));
                dedicated.runOnServer(mc -> killEnemies(connection.getServerPlayer(), 1));
                context.waitFor(client -> flags(cache) == TokenEngine.ACTIVE);
            } finally {
                context.runOnClient(client -> client.gui.hud.toggle());
            }
            context.takeScreenshot("runelegend-arrogance-increase-flash");

            context.runOnClient(client -> client.gui.hud.toggle());
            try {
                dedicated.runOnServer(mc -> killEnemies(connection.getServerPlayer(), 4));
                connection.waitForClientboundPackets();
                dedicated.runCommand("runelegend game stop");
                context.waitFor(client -> !cache.rules().running());
                dedicated.runCommand("runelegend game start");
                context.waitFor(client -> flags(cache) == (TokenEngine.READY | TokenEngine.PREPARING));
                dedicated.runOnServer(mc -> killEnemies(connection.getServerPlayer(), 1));
                context.waitFor(client -> flags(cache) == TokenEngine.ACTIVE);
                context.runOnClient(client -> {
                    long now = Util.getMillis();
                    frame(cache, now, 0);
                    frame(cache, now + 400, -1);
                });
            } finally {
                context.runOnClient(client -> client.gui.hud.toggle());
            }
        }
    }

    private static int flags(ClientStateCache cache) {
        var view = cache.view();
        return view == null ? -1 : view.tokens().stream().filter(token -> token.id().equals(ARROGANCE))
                .mapToInt(TokenEngine.View::flags).findFirst().orElse(-1);
    }

    private static void frame(ClientStateCache cache, long now, int expected) {
        var display = cache.tokenAnimation().display(ARROGANCE, now);
        if (display.glowFrame() != expected) {
            throw new AssertionError("Expected glow frame " + expected + ", got " + display.glowFrame());
        }
    }

    private static void killEnemies(ServerPlayer player, int count) {
        var level = player.level();
        for (int i = 0; i < count; i++) {
            var zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            if (zombie == null) throw new AssertionError("Could not create a zombie");
            zombie.setPos(player.position().add(2, 0, 0));
            level.addFreshEntity(zombie);
            zombie.hurtServer(level, level.damageSources().playerAttack(player), 100);
            if (zombie.isAlive()) throw new AssertionError("The test kill did not settle");
        }
    }
}
