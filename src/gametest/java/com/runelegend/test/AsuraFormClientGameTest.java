package com.runelegend.test;

import com.mojang.authlib.GameProfile;
import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.token.TokenEngine;
import com.runelegend.token.TokenEvent;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Properties;
import java.util.UUID;

public final class AsuraFormClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var props = new Properties();
        props.setProperty("online-mode", "false");
        props.setProperty("white-list", "false");
        var cache = ClientStateCache.INSTANCE;
        try (var server = context.worldBuilder().createServer(props)) {
            try (var connection = server.connect()) {
                context.waitFor(client -> client.player != null && cache.view() != null);
                server.runCommand("gamemode survival @p");
                server.runCommand("runelegend admin @p equip runelegend:asura_form");
                var second = server.computeOnServer(minecraft -> {
                    var player = new ServerPlayer(minecraft, connection.getServerPlayer().level(),
                            new GameProfile(UUID.randomUUID(), "AsuraFixture"), ClientInformation.createDefault());
                    if (RuneLegendServer.find(minecraft).players().equip(player.getUUID(), RuneLegendMod.id("asura_form"))
                            != PlayerTokenService.Result.CHANGED) throw new AssertionError("Could not equip the second player");
                    return player;
                });
                server.runCommand("runelegend game start");
                server.runOnServer(minecraft -> {
                    var player = connection.getServerPlayer();
                    player.getFoodData().setFoodLevel(10);
                    player.getFoodData().setSaturation(0);
                    player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(24);
                    player.setHealth(18);
                    charge(player, 2);
                    var zombie = EntityTypes.ZOMBIE.create(second.level(), EntitySpawnReason.COMMAND);
                    RuneLegendServer.find(minecraft).tokens().dispatch(
                            new TokenEvent(TokenEvent.Kind.KILL, second, zombie, second.level(), null, 20, 20, 0));
                });
                context.waitFor(client -> client.player != null && client.player.getMaxHealth() == 24
                        && client.player.getHealth() == 18 && hasFlag(cache, TokenEngine.PREPARING));
                server.runOnServer(minecraft -> {
                    charge(connection.getServerPlayer(), 1);
                    var view = RuneLegendServer.find(minecraft).tokens().views(second).getFirst();
                    if (view.flags() != (TokenEngine.READY | TokenEngine.PREPARING) || second.getMaxHealth() != 20) {
                        throw new AssertionError("The connected player's form changed the second player's state");
                    }
                });
                context.waitFor(client -> client.player != null && client.player.getMaxHealth() == 16
                        && client.player.getHealth() == 16 && hasFlag(cache, TokenEngine.ACTIVE));
                server.runOnServer(minecraft -> connection.getServerPlayer().heal(4));
                server.runCommand("runelegend game stop");
                context.waitFor(client -> client.player != null && client.player.getMaxHealth() == 24
                        && client.player.getHealth() == 22 && cache.rules() != null && !cache.rules().running());

                server.runCommand("runelegend game start");
                server.runOnServer(minecraft -> {
                    var player = connection.getServerPlayer();
                    player.setHealth(17);
                    charge(player, 3);
                    player.heal(5);
                });
                context.waitFor(client -> client.player != null && client.player.getMaxHealth() == 16
                        && client.player.getHealth() == 16 && hasFlag(cache, TokenEngine.ACTIVE));
            }
            server.waitFor(minecraft -> minecraft.getPlayerList().getPlayers().isEmpty());
            context.waitFor(client -> client.player == null && cache.view() == null);
            try (var ignored = server.connect()) {
                context.waitFor(client -> client.player != null && client.player.getMaxHealth() == 24
                        && client.player.getHealth() == 22 && hasFlag(cache, TokenEngine.COOLDOWN));
            }
        }
    }

    private static void charge(ServerPlayer player, int kills) {
        var level = player.level();
        for (int i = 0; i < kills; i++) {
            var zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            if (zombie == null) throw new AssertionError("Could not create a zombie");
            zombie.setPos(player.position().add(2, 0, 0));
            level.addFreshEntity(zombie);
            zombie.hurtServer(level, level.damageSources().playerAttack(player), 100);
            if (zombie.isAlive()) throw new AssertionError("The test kill did not settle");
        }
    }

    private static boolean hasFlag(ClientStateCache cache, int flag) {
        return cache.view() != null && cache.view().tokens().stream()
                .anyMatch(token -> token.id().getPath().equals("asura_form") && (token.flags() & flag) != 0);
    }
}
