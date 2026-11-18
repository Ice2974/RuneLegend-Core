package com.runelegend.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.runelegend.RuneLegendServer;
import com.runelegend.api.GameState;
import com.runelegend.api.PlayerSnapshot;
import com.runelegend.api.RuneLegendApi;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.player.PlayerTokenStore;
import com.runelegend.test.GuardGameTests;
import com.runelegend.token.TokenEvent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CommandOutputGameTests {
    private static final Identifier ECHO = Identifier.parse("runelegend:echo");
    private static final Identifier RESONANCE = Identifier.parse("runelegend:resonance_healing");
    private static final String QUERY_OBJECTIVE = "runelegend_query_test";
    private static final String ALIVE = "runelegend test player @s alive";
    private static final String ALIVE_COUNT = "runelegend query game alive_count";
    private static final String STAMINA = "runelegend query player @s stamina";
    private static final String COOLDOWN = "runelegend query player @s cooldown resonance_healing";

    @GameTest
    public void tokenListShowsOnlyDisplayNames(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var player = GuardGameTests.fixture(test);
        var messages = new ArrayList<String>();
        try {
            run(test, player, messages, "runelegend token list");
            test.assertValueEqual(messages.size(), server.registry().all().size(), "The list has one line per token");
            for (var definition : server.registry().all()) {
                var name = definition.name().getString();
                test.assertTrue(messages.stream().anyMatch(message -> message.contains(name)),
                        "The token list shows " + definition.id() + "'s display name");
            }
            test.assertTrue(messages.stream().noneMatch(message -> message.contains("runelegend:")),
                    "The token list does not show internal token IDs");
            test.succeed();
        } finally {
            server.leave(player);
        }
    }

    @GameTest
    public void tokenStatusShowsOnlyEquippedTokenNames(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var player = GuardGameTests.fixture(test);
        var messages = new ArrayList<String>();
        try {
            server.game().stopGame();
            test.assertValueEqual(server.players().equip(player.getUUID(), ECHO), PlayerTokenService.Result.CHANGED,
                    "The test equips a token while waiting");
            var persisted = PlayerTokenStore.load(PlayerTokenStore.defaultPath());
            test.assertValueEqual(persisted.get(player.getUUID()).equipped(), List.of(ECHO),
                    "The server writes equipment to the global player file before reporting success");
            run(test, player, messages, "runelegend token status");
            var name = server.registry().get(ECHO).name().getString();
            test.assertValueEqual(messages.size(), 1, "The status lists only the equipped token");
            test.assertValueEqual(messages.getFirst(), name, "The status shows only the equipped token's display name");
            test.assertFalse(messages.getFirst().contains("runelegend:"), "The status does not show internal token IDs");
            test.succeed();
        } finally {
            server.players().clear(player.getUUID());
            server.leave(player);
        }
    }

    @GameTest
    public void statusCommandMovedUnderToken(GameTestHelper test) {
        var dispatcher = test.getLevel().getServer().getCommands().getDispatcher();
        var runelegend = dispatcher.getRoot().getChild("runelegend");
        test.assertTrue(runelegend != null, "The runelegend command exists");
        test.assertTrue(runelegend.getChild("status") == null, "The root status command is removed");
        test.assertTrue(runelegend.getChild("token") != null && runelegend.getChild("token").getChild("status") != null,
                "The status command lives under token");
        test.succeed();
    }

    @GameTest
    public void queryResultsFollowTheRoundAndPreserveStatusCommands(GameTestHelper test) throws CommandSyntaxException {
        var level = test.getLevel();
        var minecraft = level.getServer();
        var server = RuneLegendServer.find(minecraft);
        var queries = RuneLegendApi.queries(minecraft);
        var scoreboard = minecraft.getScoreboard();
        var objective = scoreboard.addObjective(QUERY_OBJECTIVE, ObjectiveCriteria.DUMMY,
                Component.literal(QUERY_OBJECTIVE), ObjectiveCriteria.RenderType.INTEGER, false, null);
        var score = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#result"), objective);
        server.game().stopGame();
        var player = GuardGameTests.fixture(test);
        var teammate = GuardGameTests.fixture(test);
        var messages = new ArrayList<String>();
        var source = capturingSource(player, messages).withPermission(LevelBasedPermissionSet.GAMEMASTER);
        var id = player.getUUID();
        try {
            test.assertValueEqual(server.players().equip(id, RESONANCE), PlayerTokenService.Result.CHANGED, "Equip the test token");
            var waiting = queries.player(id);
            test.assertValueEqual(waiting.rules().state(), GameState.WAITING, "Waiting snapshot includes the rules");
            test.assertTrue(waiting.connected() && waiting.identity().isEmpty(), "Waiting has a connection and no identity");
            storeResult(test, source, score, ALIVE, 0);
            storeResult(test, source, score, ALIVE_COUNT, 0);
            storeResult(test, source, score, "runelegend test player @s equipped runelegend:resonance_healing", 1);
            storeResult(test, source, score, "runelegend test player @s equipped echo", 0);
            storeResult(test, source, score, "runelegend query player @s cooldown echo", 0);
            storeResult(test, source, score, "runelegend query player @s cooldown missing:token", 0);
            storeResult(test, source, score, STAMINA, server.stamina().config().max());
            storeResult(test, source, score, COOLDOWN, 0);
            test.assertTrue(messages.isEmpty(), "Valid queries only return numeric results");
            storeResult(test, source, score, "runelegend game status", 1);
            storeResult(test, source, score, "runelegend player status", 1);
            storeResult(test, source, score, "runelegend token status", 1);
            var tokenName = server.registry().get(RESONANCE).name().getString();
            test.assertValueEqual(messages, List.of("Game: WAITING", "Waiting: participating in the next round", tokenName),
                    "All three status commands keep their waiting text");
            messages.clear();

            server.game().startGame();
            var active = queries.player(id);
            var unrecorded = queries.player(UUID.randomUUID());
            test.assertTrue(active.alive(), "The participant is alive");
            test.assertTrue(unrecorded.rules().running() && !unrecorded.connected() && unrecorded.identity().isEmpty(),
                    "An unrecorded UUID is distinct from an offline participant");
            storeResult(test, source, score, ALIVE, 1);
            storeResult(test, source, score, ALIVE_COUNT, 2);

            var sword = new ItemParser(level.registryAccess()).parse(new StringReader(GuardGameTests.SWORD)).createItemStack(1);
            player.getInventory().setItem(player.getInventory().getSelectedSlot(), sword);
            sword.use(level, player, InteractionHand.MAIN_HAND);
            test.assertTrue(player.isBlocking(), "The player starts guarding");
            server.stamina().tick(player);
            var guard = server.stamina().config();
            storeResult(test, source, score, STAMINA, Math.max(0, guard.max() - guard.drainPerTick()));
            player.stopUsingItem();

            player.setHealth(1);
            server.tokens().dispatch(new TokenEvent(TokenEvent.Kind.GUARD_SUCCESS, player, null, player.level(), null, 4, 0, 4));
            long remaining = server.tokens().views(player).getFirst().cooldownUntil() - server.tick();
            test.assertTrue(remaining > 0, "A successful guard at low health starts the cooldown");
            storeResult(test, source, score, COOLDOWN, Math.toIntExact(remaining));
            storeResult(test, source, score, "runelegend token status", 1);
            test.assertValueEqual(messages, List.of(tokenName + " (" + ((remaining + 19) / 20) + "s)"),
                    "Token status still shows the rounded cooldown in seconds");
            messages.clear();

            player.die(player.damageSources().genericKill());
            test.assertValueEqual(queries.player(id).identity(), Optional.of(PlayerSnapshot.Identity.DOWNED), "Connected and downed");
            storeResult(test, source, score, ALIVE, 0);
            storeResult(test, source, score, ALIVE_COUNT, 1);
            storeResult(test, source, score, COOLDOWN, 0);
            storeResult(test, source, score, "runelegend player status", 1);
            test.assertValueEqual(messages, List.of("Player state: Downed"), "Player status still shows the downed identity");
            messages.clear();

            server.leave(player);
            var eliminated = queries.player(id);
            test.assertValueEqual(eliminated.identity(), Optional.of(PlayerSnapshot.Identity.ELIMINATED), "Offline and eliminated");
            test.assertTrue(!eliminated.connected() && eliminated.stamina().isEmpty() && eliminated.cooldownTicks(RESONANCE).isEmpty(),
                    "Offline players have no runtime readings");
            test.assertValueEqual(eliminated.equippedTokens(), List.of(RESONANCE), "Offline players keep their equipment");
            storeResult(test, source, score, "runelegend query player @a[tag=query_no_target,limit=1] stamina", 0);
            test.assertValueEqual(messages, List.of(EntityArgument.NO_PLAYERS_FOUND.create().getMessage()),
                    "A missing target produces the vanilla command failure");
            messages.clear();

            server.game().stopGame();
            storeResult(test, source, score, ALIVE_COUNT, 0);
            var teammateSource = capturingSource(teammate, messages).withPermission(LevelBasedPermissionSet.GAMEMASTER);
            storeResult(test, teammateSource, score, ALIVE, 0);
            storeResult(test, teammateSource, score, STAMINA, guard.max());
            test.assertTrue(queries.player(id).identity().isEmpty() && active.alive(),
                    "Stop clears the round identity while captured snapshots retain their original values");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.players().clear(id);
            server.leave(player);
            server.leave(teammate);
            scoreboard.removeObjective(objective);
        }
    }

    private static void storeResult(GameTestHelper test, CommandSourceStack source, ScoreAccess score,
                                    String command, int expected) {
        score.set(-1);
        source.getServer().getCommands().performPrefixedCommand(source,
                "execute store result score #result " + QUERY_OBJECTIVE + " run " + command);
        test.assertValueEqual(score.get(), expected, command);
    }

    private static void run(GameTestHelper test, ServerPlayer player, List<String> messages, String command) {
        test.getLevel().getServer().getCommands().performPrefixedCommand(capturingSource(player, messages), command);
    }

    private static CommandSourceStack capturingSource(ServerPlayer player, List<String> messages) {
        return player.createCommandSourceStack().withSource(new CommandSource() {
            @Override public void sendSystemMessage(Component message) { messages.add(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        });
    }
}
