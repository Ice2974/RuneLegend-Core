package com.runelegend.test;

import com.runelegend.RuneLegendServer;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.network.TabStatus;
import com.runelegend.player.PlayerState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class TabStatusClientGameTest implements FabricClientGameTest {
    private static final ClientStateCache CACHE = ClientStateCache.INSTANCE;
    private static final String RANGER = "RangerWithLongNa";

    @Override public void runTest(ClientGameTestContext context) {
        var props = new Properties();
        props.setProperty("online-mode", "false");
        props.setProperty("white-list", "false");
        try (var server = context.worldBuilder().createServer(props); var connection = server.connect()) {
            context.waitFor(client -> client.player != null && CACHE.view() != null && CACHE.view().tab().isEmpty());
            var party = server.computeOnServer(minecraft -> {
                var viewer = connection.getServerPlayer();
                RuneLegendServer.find(minecraft).playerStates().selectSpectator(viewer.getUUID(), true);
                var ranger = PlayerStateFixtures.join(minecraft, viewer.level(), UUID.randomUUID(), RANGER, null);
                var scout = PlayerStateFixtures.join(minecraft, viewer.level(), UUID.randomUUID(), "RemoteScout", null);
                var runner = PlayerStateFixtures.join(minecraft, viewer.level(), UUID.randomUUID(), "ReturningRunner", null);
                var rows = List.of(ranger, scout, runner);
                viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(rows));
                return rows;
            });
            var ranger = party.get(0);
            var scout = party.get(1);
            var runner = party.get(2);
            server.runCommand("runelegend game start");
            context.waitFor(client -> CACHE.view() != null && CACHE.view().playerState() == PlayerState.SPECTATOR
                    && !CACHE.view().tab().containsKey(client.player.getUUID())
                    && new TabStatus(TabStatus.Kind.HIGH_HEALTH, 20).equals(status(scout)));

            server.runOnServer(minecraft -> {
                ranger.setHealth(15.5F);
                ranger.setAbsorptionAmount(7);
            });
            context.waitFor(client -> new TabStatus(TabStatus.Kind.MEDIUM_HEALTH, 16).equals(status(ranger)));
            server.runOnServer(minecraft -> ranger.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60));
            context.waitFor(client -> new TabStatus(TabStatus.Kind.LOW_HEALTH, 16).equals(status(ranger)));

            server.runOnServer(minecraft -> {
                RuneLegendServer.find(minecraft).leave(runner);
                connection.getServerPlayer().connection.send(new ClientboundPlayerInfoRemovePacket(List.of(runner.getUUID())));
            });
            context.waitFor(client -> CACHE.view() != null && status(runner) == null
                    && client.getConnection().getPlayerInfo(runner.getUUID()) == null);
            server.runOnServer(minecraft -> {
                var viewer = connection.getServerPlayer();
                var returning = PlayerStateFixtures.join(minecraft, viewer.level(), runner.getUUID(), "ReturningRunner", null);
                viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(returning)));
            });
            context.waitFor(client -> TabStatus.ELIMINATED.equals(status(runner))
                    && client.getConnection().getPlayerInfo(runner.getUUID()) != null);

            server.runCommand("scoreboard objectives add TabPoints dummy");
            server.runCommand("scoreboard players set " + RANGER + " TabPoints 14");
            server.runCommand("scoreboard objectives setdisplay list TabPoints");
            context.waitFor(client -> client.level.getScoreboard().getDisplayObjective(DisplaySlot.LIST) != null);

            server.runOnServer(minecraft -> {
                var nether = minecraft.getLevel(Level.NETHER);
                if (!scout.teleportTo(nether, 1.5, 80, 1.5, Set.of(), 0, 0, false) || scout.level() != nether) {
                    throw new AssertionError("Scout did not reach the other dimension");
                }
                scout.die(scout.damageSources().genericKill());
            });
            context.waitFor(client -> status(scout) != null && status(scout).kind() == TabStatus.Kind.DOWNED
                    && CACHE.view().downed().isEmpty());
            int first = context.computeOnClient(client -> status(scout).value());
            context.waitFor(client -> status(scout) != null && status(scout).kind() == TabStatus.Kind.DOWNED
                    && status(scout).value() < first);
            connection.waitForChunksRender();
            context.getInput().holdKey(options -> options.keyPlayerList);
            int previousScale = context.computeOnClient(client -> client.options.guiScale().get());
            try {
                setScale(context, 1);
                context.takeScreenshot("runelegend-tab-states-numeric-score");
                server.runCommand("scoreboard objectives modify TabPoints rendertype hearts");
                context.waitFor(client -> client.level.getScoreboard().getDisplayObjective(DisplaySlot.LIST).getRenderType()
                        == ObjectiveCriteria.RenderType.HEARTS);
                server.runOnServer(minecraft -> {
                    var viewer = connection.getServerPlayer();
                    var extras = new ArrayList<ServerPlayer>();
                    for (int i = 0; i < 18; i++) {
                        extras.add(PlayerStateFixtures.join(minecraft, viewer.level(), UUID.randomUUID(), "Spectator" + i, null));
                    }
                    viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(extras));
                });
                context.waitFor(client -> client.getConnection().getListedOnlinePlayers().size() == 22);
                context.takeScreenshot("runelegend-tab-multiple-columns-hearts-score");
                setScale(context, 0);
                context.takeScreenshot("runelegend-tab-narrow-columns");
            } finally {
                context.getInput().releaseKey(options -> options.keyPlayerList);
                setScale(context, previousScale);
            }

            var revivedHealth = server.computeOnServer(minecraft -> {
                var runtime = RuneLegendServer.find(minecraft);
                var record = runtime.downed().get(scout.getUUID());
                if (record == null || !runtime.revive(scout.getUUID(), record.sequence())) {
                    throw new AssertionError("Remote player was not revived");
                }
                return (int) Math.ceil(scout.getHealth());
            });
            context.waitFor(client -> status(scout) != null && status(scout).kind() != TabStatus.Kind.DOWNED
                    && status(scout).kind() != TabStatus.Kind.ELIMINATED && status(scout).value() == revivedHealth);

            int downedTicks = server.computeOnServer(minecraft -> RuneLegendServer.find(minecraft).playerStateConfig().downedTicks());
            server.runOnServer(minecraft -> scout.die(scout.damageSources().genericKill()));
            context.waitFor(client -> status(scout) != null && status(scout).kind() == TabStatus.Kind.DOWNED);
            context.waitFor(client -> TabStatus.ELIMINATED.equals(status(scout)), downedTicks + 120);
            server.runCommand("runelegend game stop");
            context.waitFor(client -> CACHE.rules() != null && !CACHE.rules().running()
                    && CACHE.view() != null && CACHE.view().tab().isEmpty());
            context.getInput().holdKey(options -> options.keyPlayerList);
            try {
                context.waitTicks(2);
                context.takeScreenshot("runelegend-tab-waiting-vanilla");
            } finally {
                context.getInput().releaseKey(options -> options.keyPlayerList);
            }
        }
    }

    private static TabStatus status(ServerPlayer player) {
        var view = CACHE.view();
        return view == null ? null : view.tab().get(player.getUUID());
    }

    private static void setScale(ClientGameTestContext context, int scale) {
        context.runOnClient(client -> {
            client.options.guiScale().set(scale);
            client.resizeGui();
        });
        context.waitTicks(2);
    }
}
