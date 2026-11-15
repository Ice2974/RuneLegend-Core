package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.client.network.ClientStateCache;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Properties;

public final class SoulSiphonClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var props = new Properties();
        props.setProperty("online-mode", "false");
        props.setProperty("white-list", "false");
        try (var server = context.worldBuilder().createServer(props)) {
            try (var connection = server.connect()) {
                awaitHealth(context, false, 20, 20);
                server.runCommand("runelegend admin @p equip runelegend:soul_siphon");
                server.runCommand("runelegend game start");
                server.runOnServer(minecraft -> {
                    var player = connection.getServerPlayer();
                    player.getFoodData().setFoodLevel(10);
                    player.getFoodData().setSaturation(0);
                    var maximum = player.getAttribute(Attributes.MAX_HEALTH);
                    maximum.setBaseValue(24);
                    maximum.addPermanentModifier(new AttributeModifier(RuneLegendMod.id("test_extra_health"), 3,
                            AttributeModifier.Operation.ADD_VALUE));
                    grantBonus(player);
                });
                awaitHealth(context, true, 29, 29);
            }
            server.waitFor(minecraft -> minecraft.getPlayerList().getPlayers().isEmpty());
            try (var connection = server.connect()) {
                awaitHealth(context, true, 29, 29);
                server.runCommand("runelegend game stop");
                awaitHealth(context, false, 27, 27);
                server.runCommand("runelegend game start");
                server.runOnServer(minecraft -> grantBonus(connection.getServerPlayer()));
                awaitHealth(context, true, 29, 29);
            }
            server.waitFor(minecraft -> minecraft.getPlayerList().getPlayers().isEmpty());
            server.runCommand("runelegend game stop");
            try (var connection = server.connect()) {
                awaitHealth(context, false, 27, 27);
                server.runCommand("runelegend game start");
                server.runOnServer(minecraft -> grantBonus(connection.getServerPlayer()));
                awaitHealth(context, true, 29, 29);
            }
            server.waitFor(minecraft -> minecraft.getPlayerList().getPlayers().isEmpty());
            server.runCommand("runelegend game stop");
            server.runCommand("runelegend game start");
            try (var _ = server.connect()) {
                awaitHealth(context, true, 27, 27);
            }
        }
    }

    private static void grantBonus(ServerPlayer player) {
        var level = player.level();
        var server = RuneLegendServer.find(level.getServer());
        var tokens = new TokenEngine(server.game(), server.players(), server.registry(), server::tick, ignored -> 0);
        var target = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        tokens.dispatch(new TokenEvent(TokenEvent.Kind.KILL, player, target, level, null, 20, 20, 0));
        player.setHealth(player.getMaxHealth());
    }

    private static void awaitHealth(ClientGameTestContext context, boolean running, float maximum, float health) {
        context.waitFor(client -> {
            var rules = ClientStateCache.INSTANCE.rules();
            return rules != null && rules.running() == running && client.player != null
                    && client.player.getMaxHealth() == maximum && client.player.getHealth() == health;
        });
    }
}
