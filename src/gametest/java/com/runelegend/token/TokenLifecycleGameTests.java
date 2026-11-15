package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.game.GameController;
import com.runelegend.player.PlayerTokenSavedData;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.test.GuardGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class TokenLifecycleGameTests {
    @GameTest
    public void cleanupDiscardsCooldownsAndCancelsAllPlayerTasks(GameTestHelper test) {
        var minecraft = test.getLevel().getServer();
        var server = RuneLegendServer.find(minecraft);
        var first = GuardGameTests.fixture(test);
        var second = GuardGameTests.fixture(test);
        var clock = new AtomicLong(100);
        Map<UUID, TokenRuntimeState> observed = new HashMap<>();
        Map<UUID, Integer> pulses = new HashMap<>();
        Map<UUID, Integer> removals = new HashMap<>();
        var id = RuneLegendMod.id("lifecycle_fixture");
        var registry = new TokenRegistry.Builder().register(new TokenDefinition(id,
                Set.of(TokenEvent.Kind.KILL, TokenEvent.Kind.HEALTH), new TokenEffectHandler() {
            @Override public boolean ready(ServerPlayer player, TokenRuntimeState state, long tick) {
                return tick >= state.cooldownUntil;
            }

            @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
                var uuid = event.player().getUUID();
                observed.put(uuid, state);
                if (event.kind() != TokenEvent.Kind.KILL) return;
                state.charge = 3;
                state.flash(context.tick());
                state.activeUntil = context.tick() + 60;
                state.cooldownUntil = context.tick() + 140;
                context.replaceEffect("owned", () -> removals.merge(uuid, 1, Integer::sum));
                context.at("owned", context.tick() + 80, () -> pulses.merge(uuid, 1, Integer::sum));
                // Scheduling without an owned effect must also be safe on disconnect/death.
                context.at("unowned", context.tick() + 70, () -> pulses.merge(uuid, 1, Integer::sum));
            }
        })).build();
        var game = new GameController(() -> {}, (previous, current) -> {});
        var players = new PlayerTokenService(game, PlayerTokenSavedData.load(minecraft), registry);
        var tokens = new TokenEngine(game, players, registry, clock::get);
        try {
            for (var player : List.of(first, second)) {
                test.assertValueEqual(players.equip(player.getUUID(), id), PlayerTokenService.Result.CHANGED, "Equip fixture");
            }
            game.startGame();
            for (var player : List.of(first, second)) tokens.dispatch(event(player, TokenEvent.Kind.KILL));
            var old = observed.get(first.getUUID());
            clock.set(110);
            tokens.clearEffects(first.getUUID());
            tokens.clearEffects(first.getUUID());
            tokens.clearEffects(first.getUUID()); // death, disconnect, join can all perform cleanup
            tokens.dispatch(event(first, TokenEvent.Kind.HEALTH));
            var restored = observed.get(first.getUUID());
            test.assertTrue(restored != old, "The old per-player runtime is released");
            test.assertTrue(restored.charge == 0 && restored.activeUntil == 0 && restored.triggeredUntil == 0,
                    "Charges, active effects and trigger flashes do not survive cleanup");
            test.assertValueEqual(restored.cooldownUntil, 0L, "Cleanup discards the unexpired cooldown");
            test.assertValueEqual(tokens.views(first).getFirst().flags(), TokenEngine.READY,
                    "The HUD reports the reset token as ready");
            test.assertValueEqual(removals.get(first.getUUID()), 1, "Owned effects are removed once");
            test.assertValueEqual(observed.get(second.getUUID()).charge, 3, "Another player's charge is untouched");
            clock.set(180);
            tokens.tick(List.of(first, second));
            test.assertValueEqual(pulses.getOrDefault(first.getUUID(), 0), 0, "All of the disconnected player's tasks are cancelled");
            test.assertValueEqual(pulses.getOrDefault(second.getUUID(), 0), 2, "The other player's tasks still run");

            tokens.dispatch(event(first, TokenEvent.Kind.KILL));
            tokens.clearRound();
            tokens.dispatch(event(first, TokenEvent.Kind.HEALTH));
            test.assertValueEqual(observed.get(first.getUUID()).cooldownUntil, 0L, "A round reset also clears cooldowns");
            test.succeed();
        } finally {
            tokens.clearRound();
            game.stopGame();
            for (var player : List.of(first, second)) {
                players.clear(player.getUUID());
                server.leave(player);
            }
        }
    }

    @GameTest
    public void soulSiphonSurvivesLivingTransferButNotSavingOrDeath(GameTestHelper test) {
        var minecraft = test.getLevel().getServer();
        var server = RuneLegendServer.find(minecraft);
        var original = GuardGameTests.fixture(test);
        var reloaded = GuardGameTests.fixture(test);
        var aliveCopy = GuardGameTests.fixture(test);
        var respawned = GuardGameTests.fixture(test);
        var corpse = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
        var game = new GameController(() -> {}, (previous, current) -> {});
        var players = new PlayerTokenService(game, PlayerTokenSavedData.load(minecraft), server.registry());
        var tokens = new TokenEngine(game, players, server.registry(), () -> 100, player -> 0);
        try {
            test.assertValueEqual(players.equip(original.getUUID(), RuneLegendMod.id("soul_siphon")),
                    PlayerTokenService.Result.CHANGED, "Equip Soul Siphon");
            original.getAttribute(Attributes.MAX_HEALTH).setBaseValue(24);
            game.startGame();
            tokens.dispatch(new TokenEvent(TokenEvent.Kind.KILL, original, corpse, test.getLevel(), null, 20, 20, 0));
            test.assertValueEqual(original.getMaxHealth(), 26F, "Soul Siphon adds its configured bonus");
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, minecraft.registryAccess());
            original.saveWithoutId(output);
            reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, minecraft.registryAccess(), output.buildResult()));
            test.assertValueEqual(reloaded.getMaxHealth(), 24F, "The saved player has no Soul Siphon bonus");
            original.setHealth(26F);
            aliveCopy.restoreFrom(original, true);
            test.assertValueEqual(aliveCopy.getMaxHealth(), 26F, "A living player transfer retains the modifier");
            test.assertValueEqual(aliveCopy.getHealth(), 26F, "Living transfer preserves health above the base maximum");
            server.died(original);
            test.assertValueEqual(original.getMaxHealth(), 24F, "Death cleanup removes the bonus");
            respawned.restoreFrom(original, false);
            test.assertValueEqual(respawned.getMaxHealth(), 24F, "Ordinary death respawn keeps base health but loses the bonus");
            test.succeed();
        } finally {
            tokens.clearRound();
            game.stopGame();
            players.clear(original.getUUID());
            for (var player : List.of(original, reloaded, aliveCopy, respawned)) server.leave(player);
        }
    }

    @GameTest
    public void soulSiphonLegacySaveClearsOnlyItsOwnModifierOnJoin(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var player = GuardGameTests.fixture(test);
        try {
            server.game().startGame();
            var maximum = player.getAttribute(Attributes.MAX_HEALTH);
            var other = RuneLegendMod.id("test_extra_health");
            maximum.setBaseValue(24);
            maximum.addPermanentModifier(new AttributeModifier(other, 3, AttributeModifier.Operation.ADD_VALUE));
            maximum.addPermanentModifier(new AttributeModifier(RuneLegendMod.id("soul_siphon"), 2,
                    AttributeModifier.Operation.ADD_VALUE));
            player.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(4);
            player.setAbsorptionAmount(4);
            player.setHealth(19);
            server.join(player);
            test.assertValueEqual(maximum.getBaseValue(), 24D, "Login preserves base health");
            test.assertValueEqual(player.getMaxHealth(), 27F, "Only the old Soul Siphon bonus is removed");
            test.assertValueEqual(player.getHealth(), 19F, "Login does not heal to the new maximum");
            test.assertValueEqual(player.getAbsorptionAmount(), 4F, "Login preserves absorption health");
            test.succeed();
        } finally {
            server.leave(player);
            server.game().stopGame();
        }
    }

    private static TokenEvent event(ServerPlayer player, TokenEvent.Kind kind) {
        return new TokenEvent(kind, player, player, player.level(), null, 0, 0, 0);
    }
}
