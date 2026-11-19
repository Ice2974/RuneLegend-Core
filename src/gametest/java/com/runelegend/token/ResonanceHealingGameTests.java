package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.game.GameController;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.player.PlayerTokenStore;
import com.runelegend.test.GuardGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class ResonanceHealingGameTests {
    private static final net.minecraft.resources.Identifier RESONANCE =
            RuneLegendMod.id("resonance_healing");

    @GameTest
    public void defaultSettingsHealNearbyNonSpectatorsAndStartCooldown(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var healer = GuardGameTests.fixture(test);
        var nearAlly = GuardGameTests.fixture(test);
        var farAlly = GuardGameTests.fixture(test);
        var spectator = (ServerPlayer) test.makeMockServerPlayer(GameType.SPECTATOR);
        var clock = new AtomicLong(100);
        var game = new GameController(() -> {}, (previous, current) -> {});
        var registry = TokenRegistry.builtIn(TokenConfig.DEFAULT);
        var service = new PlayerTokenService(game, PlayerTokenStore.inMemory(), registry);
        var tokens = TokenTestEngine.forPlayers(healer, nearAlly, farAlly).create(game, service, registry, clock::get);
        var players = List.of(healer, nearAlly, farAlly, spectator);
        var origin = test.absoluteVec(new Vec3(2.5, 2, 2.5));
        var nearbyEnemy = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 1);
        var boundaryEnemy = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        var distantEnemy = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 3, 2, 2);
        var deadEnemy = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 3, 2, 3);
        var passiveMob = test.spawnWithNoFreeWill(EntityTypes.PIG, 1, 2, 2);
        try {
            boundaryEnemy.setPos(origin.add(5.0, 0, 0));
            distantEnemy.setPos(origin.add(4.0, 0, 4.0));
            deadEnemy.setHealth(0);
            // Mock players are not ticked through the normal player list, so place them directly.
            players.forEach(level.players()::add);
            healer.setPos(origin);
            nearAlly.setPos(origin.add(5.0, 0, 0));
            farAlly.setPos(origin.add(5.5, 0, 0));
            spectator.setPos(origin.add(1.0, 0, 0));
            healer.setHealth(10.5F);
            nearAlly.setHealth(18);
            farAlly.setHealth(10);
            spectator.setHealth(10);

            test.assertValueEqual(service.equip(healer.getUUID(), RESONANCE),
                    PlayerTokenService.Result.CHANGED, "Equip Resonance Healing");
            test.assertValueEqual(service.equip(farAlly.getUUID(), RESONANCE),
                    PlayerTokenService.Result.CHANGED, "Equip an independent Resonance Healing");
            game.startGame();

            test.assertValueEqual(tokens.views(healer).getFirst().flags(), TokenEngine.READY,
                    "Above the default 50% threshold the token is ready but not preparing");
            tokens.dispatch(guard(healer, level));
            test.assertValueEqual(healer.getHealth(), 10.5F,
                    "Above the threshold a successful block does not heal");
            test.assertTrue((tokens.views(healer).getFirst().flags() & TokenEngine.COOLDOWN) == 0,
                    "Above the threshold a successful block does not start the cooldown");

            healer.setHealth(10);
            test.assertValueEqual(tokens.views(healer).getFirst().flags(),
                    TokenEngine.READY | TokenEngine.PREPARING,
                    "At exactly 50% health the token enters preparation");
            tokens.dispatch(guard(healer, level));
            test.assertValueEqual(healer.getHealth(), 12F,
                    "The healer gains one HP per living enemy within five blocks, including the boundary");
            test.assertValueEqual(nearAlly.getHealth(), 20F,
                    "An ally inside the default five-block radius is healed and capped at max health");
            test.assertValueEqual(farAlly.getHealth(), 10F,
                    "A player outside the radius is not healed");
            test.assertValueEqual(spectator.getHealth(), 10F, "A spectator is not healed");
            test.assertValueEqual(tokens.views(farAlly).getFirst().flags(),
                    TokenEngine.READY | TokenEngine.PREPARING,
                    "Another player's token state is independent of the trigger");
            var view = tokens.views(healer).getFirst();
            test.assertValueEqual(view.flags(), TokenEngine.TRIGGERED | TokenEngine.COOLDOWN,
                    "A successful trigger flashes and starts the cooldown");
            test.assertValueEqual(view.cooldownUntil(), 300L,
                    "The default 10-second cooldown is 200 ticks");

            nearAlly.setHealth(10);
            tokens.dispatch(guard(healer, level));
            test.assertValueEqual(nearAlly.getHealth(), 10F,
                    "A second block during the cooldown does not heal");

            clock.set(300);
            healer.setHealth(10);
            test.assertValueEqual(tokens.views(healer).getFirst().flags(),
                    TokenEngine.READY | TokenEngine.PREPARING,
                    "The cooldown expires after 200 ticks");
            tokens.dispatch(guard(healer, level));
            test.assertValueEqual(healer.getHealth(), 12F,
                    "The token triggers again after the cooldown expires");

            nearbyEnemy.discard();
            boundaryEnemy.discard();
            clock.set(500);
            healer.setHealth(10);
            tokens.dispatch(guard(healer, level));
            test.assertValueEqual(healer.getHealth(), 10F, "No eligible enemies means zero healing");
            test.assertValueEqual(tokens.views(healer).getFirst().cooldownUntil(), 700L,
                    "A successful trigger starts the cooldown even with zero healing");

            game.stopGame();
            healer.setHealth(10);
            tokens.dispatch(guard(healer, level));
            test.assertValueEqual(healer.getHealth(), 10F, "WAITING disables the token");
            test.succeed();
        } finally {
            nearbyEnemy.discard();
            boundaryEnemy.discard();
            distantEnemy.discard();
            deadEnemy.discard();
            passiveMob.discard();
            cleanup(level, server, tokens, game, service, players);
        }
    }

    @GameTest
    public void configuredSettingsDriveThresholdRadiusHealAndCooldown(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var healer = GuardGameTests.fixture(test);
        var nearAlly = GuardGameTests.fixture(test);
        var farAlly = GuardGameTests.fixture(test);
        var clock = new AtomicLong(100);
        var game = new GameController(() -> {}, (previous, current) -> {});
        var defaults = TokenConfig.DEFAULT;
        var registry = TokenRegistry.builtIn(new TokenConfig(defaults.soulSiphon(), defaults.echo(),
                defaults.venomousTip(), defaults.poisonedArrow(),
                defaults.asuraForm(), new TokenConfig.ResonanceHealingSettings(0.6F, 3.0, 4.0F, 2),
                defaults.vernalTide(), defaults.arrogance(), defaults.executionEcho(), defaults.retribution(),
                defaults.substitute(), defaults.fullBlessing(), defaults.fountainOfLife()));
        var service = new PlayerTokenService(game, PlayerTokenStore.inMemory(), registry);
        var tokens = TokenTestEngine.forPlayers(healer, nearAlly, farAlly).create(game, service, registry, clock::get);
        var players = List.of(healer, nearAlly, farAlly);
        var origin = test.absoluteVec(new Vec3(2.5, 2, 2.5));
        var nearbyEnemy = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 1);
        var distantEnemy = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        try {
            distantEnemy.setPos(origin.add(3.1, 0, 0));
            players.forEach(level.players()::add);
            healer.setPos(origin);
            nearAlly.setPos(origin.add(3.0, 0, 0));
            farAlly.setPos(origin.add(3.1, 0, 0));
            healer.setHealth(12.5F);
            nearAlly.setHealth(10);
            farAlly.setHealth(10);

            test.assertValueEqual(service.equip(healer.getUUID(), RESONANCE),
                    PlayerTokenService.Result.CHANGED, "Equip configured Resonance Healing");
            game.startGame();

            test.assertValueEqual(tokens.views(healer).getFirst().flags(), TokenEngine.READY,
                    "The configured 60% threshold is not met above it");
            tokens.dispatch(guard(healer, level));
            test.assertValueEqual(healer.getHealth(), 12.5F,
                    "No healing occurs above the configured threshold");

            healer.setHealth(12);
            test.assertValueEqual(tokens.views(healer).getFirst().flags(),
                    TokenEngine.READY | TokenEngine.PREPARING,
                    "The configured 60% threshold prepares at exactly 60%");
            tokens.dispatch(guard(healer, level));
            test.assertValueEqual(healer.getHealth(), 16F,
                    "One enemy within the configured radius heals the healer by the configured four HP");
            test.assertValueEqual(nearAlly.getHealth(), 14F,
                    "The configured three-block radius includes an ally at exactly 3.0 blocks");
            test.assertValueEqual(farAlly.getHealth(), 10F,
                    "The configured three-block radius excludes an ally at 3.1 blocks");
            var view = tokens.views(healer).getFirst();
            test.assertValueEqual(view.flags(), TokenEngine.TRIGGERED | TokenEngine.COOLDOWN,
                    "The configured cooldown starts immediately");
            test.assertValueEqual(view.cooldownUntil(), 140L,
                    "The configured two-second cooldown is 40 ticks");
            test.succeed();
        } finally {
            nearbyEnemy.discard();
            distantEnemy.discard();
            cleanup(level, server, tokens, game, service, players);
        }
    }

    private static TokenEvent guard(ServerPlayer player, ServerLevel level) {
        return new TokenEvent(TokenEvent.Kind.GUARD_SUCCESS, player, player, level, null, 6, 3, 3);
    }

    private static void cleanup(ServerLevel level, RuneLegendServer server, TokenEngine tokens,
                                GameController game, PlayerTokenService service, List<ServerPlayer> players) {
        tokens.clearRound();
        game.stopGame();
        for (var player : players) {
            service.clear(player.getUUID());
            level.players().remove(player);
            server.leave(player);
        }
    }
}
