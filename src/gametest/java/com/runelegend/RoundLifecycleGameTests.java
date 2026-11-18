package com.runelegend;

import com.runelegend.api.PlayerSnapshot.Identity;
import com.runelegend.api.RoundEvents;
import com.runelegend.api.RoundSummary;
import com.runelegend.api.RuneLegendApi;
import com.runelegend.test.PlayerStateFixtures;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RoundLifecycleGameTests {
    private static final Identifier OUTCOME = Identifier.parse("test_map:victory");
    private static final Identifier STORAGE = Identifier.parse("runelegend_test:round_probe");
    private static final String OBJECTIVE = "rl_round_probe";
    private static Probe active;

    static {
        RoundEvents.STARTED.register((minecraft, round, identities) -> {
            if (active != null && active.minecraft == minecraft) active.started(round, identities);
        });
        RoundEvents.ENDED.register((minecraft, summary) -> {
            if (active != null && active.minecraft == minecraft) active.ended(summary);
        });
    }

    @GameTest
    public void finishAndStopPublishOneSettlementAfterCleanup(GameTestHelper test) {
        var minecraft = test.getLevel().getServer();
        var server = RuneLegendServer.find(minecraft);
        server.game().stopGame();
        var survivor = join(test);
        var disconnected = join(test);
        var downed = join(test);
        var probe = new Probe(test);
        try {
            var nether = minecraft.getLevel(Level.NETHER);
            test.assertTrue(nether != null, "The finish command can originate in another dimension");
            var source = minecraft.createCommandSourceStack().withLevel(nether);
            long round = server.game().getRules().round() + 1;
            probe.storeResult(source, "runelegend game start", 1);
            probe.storeResult(source, "runelegend game start", 0);
            test.assertValueEqual(probe.startRounds, List.of(round), "One Java start notification");
            test.assertValueEqual(probe.initialIdentities.getFirst(), Map.of(survivor.getUUID(), Identity.ALIVE,
                    disconnected.getUUID(), Identity.ALIVE, downed.getUUID(), Identity.ALIVE), "Start captures assigned identities");
            probe.assertMacroSnapshot("started", round, probe.initialIdentities.getFirst());
            test.assertValueEqual(probe.score("#alive_on_start"), 3, "The start function can query initialized identities");
            test.assertValueEqual(probe.score("#blocked_finish"), 0, "A start function cannot reentrantly finish");
            minecraft.getCommands().performPrefixedCommand(source.withPermission(PermissionSet.NO_PERMISSIONS),
                    "runelegend game finish " + OUTCOME);
            test.assertTrue(server.game().getRules().running(), "Finishing requires the game permission");

            server.leave(disconnected);
            downed.die(downed.damageSources().genericKill());
            test.assertTrue(survivor.teleportTo(nether, 0.5, 64, 0.5, java.util.Set.of(), 0, 0, false),
                    "The survivor can leave the world spawn dimension");
            survivor.setHealth(2);
            survivor.getFoodData().setFoodLevel(4);

            probe.storeResult(source, "runelegend game finish " + OUTCOME, 1);
            var finish = probe.assertEnd(1, round, RoundSummary.Reason.FINISH, Optional.of(OUTCOME),
                    Map.of(survivor.getUUID(), Identity.ALIVE, disconnected.getUUID(), Identity.ELIMINATED,
                            downed.getUUID(), Identity.DOWNED));
            test.assertValueEqual(probe.score("#alive_on_end"), 0, "The end function queries the cleared round");
            test.assertValueEqual(probe.score("#blocked_start"), 0, "An end function cannot reentrantly start");
            test.assertValueEqual(probe.score("#spawn_dimension"), 1, "Functions use the shared spawn dimension");
            test.assertValueEqual(probe.score("#has_executor"), 0, "Functions have no executing entity");
            probe.storeResult(source, "runelegend game finish test_map:duplicate", 0);
            probe.storeResult(source, "runelegend game stop", 0);
            probe.assertCounts(1, 1);

            probe.storeResult(source, "runelegend game start", 1);
            probe.storeResult(source, "runelegend game stop", 1);
            probe.assertEnd(2, round + 1, RoundSummary.Reason.MANUAL_STOP, Optional.empty(),
                    Map.of(survivor.getUUID(), Identity.ALIVE, downed.getUUID(), Identity.ALIVE));
            probe.assertCounts(2, 2);
            test.assertValueEqual(finish.identities().get(disconnected.getUUID()), Identity.ELIMINATED,
                    "The finished summary remains usable after a subsequent round");
            test.succeed();
        } finally {
            probe.close();
            server.game().stopGame();
            server.leave(survivor);
            server.leave(disconnected);
            server.leave(downed);
        }
    }

    @GameTest
    public void noAliveSettlesSpectatorsAndLastDisconnectOnTheServerTick(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        server.game().stopGame();
        var player = join(test);
        var uuid = player.getUUID();
        var probe = new Probe(test);
        try {
            test.assertTrue(server.playerStates().selectSpectator(uuid, true), "The player can choose to spectate");
            test.assertTrue(server.game().startGame(), "A round with only spectators still starts");
            long round = server.game().getRules().round();
            probe.assertCounts(1, 0);
            test.assertValueEqual(probe.initialIdentities.getFirst(), Map.of(uuid, Identity.SPECTATOR), "Spectator at start");
            server.endTick();
            probe.assertEnd(1, round, RoundSummary.Reason.NO_ALIVE, Optional.empty(), Map.of(uuid, Identity.SPECTATOR));

            test.assertTrue(server.game().startGame(), "The spectator choice has been consumed");
            server.leave(player);
            probe.assertCounts(2, 1);
            server.endTick();
            probe.assertEnd(2, round + 1, RoundSummary.Reason.NO_ALIVE, Optional.empty(), Map.of(uuid, Identity.ELIMINATED));
            server.endTick();
            probe.assertCounts(2, 2);
            test.succeed();
        } finally {
            probe.close();
            server.game().stopGame();
            server.leave(player);
        }
    }

    @GameTest
    public void participantListsHonorSpectatorChoiceAndApplyOnlyAtStart(GameTestHelper test) {
        var minecraft = test.getLevel().getServer();
        var server = RuneLegendServer.find(minecraft);
        var queries = RuneLegendApi.queries(minecraft);
        server.game().stopGame();
        var included = join(test);
        var excluded = join(test);
        var optOut = join(test);
        var lateId = UUID.randomUUID();
        ServerPlayer late = null;
        ServerPlayer rejoined = null;
        var probe = new Probe(test);
        var source = minecraft.createCommandSourceStack().withEntity(included);
        var start = "runelegend game start participants @s";
        try {
            minecraft.getCommands().performPrefixedCommand(source.withPermission(PermissionSet.NO_PERMISSIONS), start);
            test.assertFalse(server.game().getRules().running(), "Participant selection requires the game permission");
            server.playerStates().selectSpectator(optOut.getUUID(), true);
            test.assertTrue(RuneLegendApi.startGame(minecraft, List.of(included.getUUID(), optOut.getUUID(), lateId)),
                    "The explicit online candidates start a round");
            long round = server.game().getRules().round();
            var identities = Map.of(included.getUUID(), Identity.ALIVE, excluded.getUUID(), Identity.SPECTATOR,
                    optOut.getUUID(), Identity.SPECTATOR);
            test.assertValueEqual(probe.initialIdentities.getFirst(), identities, "The initial roster honors both the list and the opt-out");
            probe.assertMacroSnapshot("started", round, identities);
            test.assertTrue(included.gameMode() == GameType.ADVENTURE && excluded.isSpectator() && optOut.isSpectator(),
                    "Initial game modes reflect assigned identities");
            probe.storeResult(source.withEntity(excluded), start, 0);
            test.assertValueEqual(queries.player(excluded.getUUID()).identity(), Optional.of(Identity.SPECTATOR),
                    "A rejected start does not change the roster");

            late = PlayerStateFixtures.join(minecraft, test.getLevel(), lateId);
            test.assertTrue(late.isSpectator() && queries.player(lateId).identity().orElseThrow() == Identity.SPECTATOR,
                    "A listed player arriving late still spectates");
            server.leave(included);
            rejoined = PlayerStateFixtures.join(minecraft, test.getLevel(), included.getUUID());
            test.assertTrue(rejoined.isSpectator() && queries.player(included.getUUID()).identity().orElseThrow() == Identity.ELIMINATED,
                    "Rejoining does not restore a disconnected participant");
            server.endTick();
            probe.assertEnd(1, round, RoundSummary.Reason.NO_ALIVE, Optional.empty(), Map.of(included.getUUID(), Identity.ELIMINATED,
                    excluded.getUUID(), Identity.SPECTATOR, optOut.getUUID(), Identity.SPECTATOR, lateId, Identity.SPECTATOR));

            probe.storeResult(source.withEntity(excluded), start, 1);
            test.assertValueEqual(queries.aliveCount(), 1, "The selector's only match is the only participant");
            test.assertTrue(queries.player(excluded.getUUID()).alive(), "The command selected its executing player");
            server.game().stopGame();
            probe.storeResult(source, "runelegend game start participants @a[tag=rl_probe_no_match]", 1);
            test.assertValueEqual(queries.aliveCount(), 0, "An empty selector starts with all players spectating");
            server.endTick();

            probe.storeResult(source, "runelegend game start", 1);
            test.assertValueEqual(queries.aliveCount(), 4, "Plain start uses all online players and the old opt-out has been consumed");
            test.succeed();
        } finally {
            probe.close();
            server.game().stopGame();
            server.leave(included);
            server.leave(excluded);
            server.leave(optOut);
            if (late != null) server.leave(late);
            if (rejoined != null) server.leave(rejoined);
        }
    }

    private static ServerPlayer join(GameTestHelper test) {
        return PlayerStateFixtures.join(test.getLevel().getServer(), test.getLevel(), UUID.randomUUID());
    }

    private static final class Probe {
        private final GameTestHelper test;
        private final MinecraftServer minecraft;
        private final List<Long> startRounds = new ArrayList<>();
        private final List<Map<UUID, Identity>> initialIdentities = new ArrayList<>();
        private final List<RoundSummary> summaries = new ArrayList<>();

        Probe(GameTestHelper test) {
            this.test = test;
            minecraft = test.getLevel().getServer();
            minecraft.getScoreboard().addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY, Component.literal(OBJECTIVE),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
            var data = new CompoundTag();
            data.putBoolean("active", true);
            minecraft.getCommandStorage().set(STORAGE, data);
            active = this;
        }

        void started(long round, Map<UUID, Identity> identities) {
            var api = RuneLegendApi.get(minecraft);
            var queries = RuneLegendApi.queries(minecraft);
            test.assertTrue(api.getRules().running() && api.getRules().round() == round, "Java start sees the new rules");
            identities.forEach((uuid, identity) -> test.assertValueEqual(queries.player(uuid).identity(),
                    Optional.of(identity), "Java start sees initialized identities"));
            test.assertFalse(api.stopGame(), "Java start rejects a reentrant stop");
            test.assertFalse(RuneLegendApi.finishGame(minecraft, OUTCOME), "Java start rejects a reentrant finish");
            startRounds.add(round);
            initialIdentities.add(identities);
        }

        void ended(RoundSummary summary) {
            var api = RuneLegendApi.get(minecraft);
            var queries = RuneLegendApi.queries(minecraft);
            var server = RuneLegendServer.find(minecraft);
            var spawn = minecraft.getRespawnData();
            test.assertTrue(!api.getRules().running() && api.getRules().round() == summary.round(), "Java end sees waiting rules");
            test.assertFalse(api.startGame(), "Java end rejects a reentrant start");
            for (var uuid : summary.identities().keySet()) {
                var current = queries.player(uuid);
                test.assertTrue(current.identity().isEmpty() && server.downed().get(uuid) == null, "Java end sees cleared round state");
                var player = server.currentPlayer(uuid);
                if (player == null) continue;
                test.assertValueEqual(player.level().dimension(), spawn.dimension(), "Java end runs after return to spawn");
                test.assertTrue(player.gameMode() == GameType.ADVENTURE && player.getHealth() == player.getMaxHealth()
                        && player.getFoodData().getFoodLevel() == 20 && current.stamina().orElseThrow() == server.stamina().config().max(),
                        "Java end sees restored player state");
            }
            summaries.add(summary);
        }

        int score(String holder) {
            var scoreboard = minecraft.getScoreboard();
            return scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), scoreboard.getObjective(OBJECTIVE)).get();
        }

        void storeResult(CommandSourceStack source, String command, int expected) {
            minecraft.getCommands().performPrefixedCommand(source, "execute store result score #result " + OBJECTIVE + " run " + command);
            test.assertValueEqual(score("#result"), expected, command);
        }

        void assertCounts(int starts, int ends) {
            test.assertValueEqual(startRounds.size(), starts, "Java start count");
            test.assertValueEqual(score("#started"), starts, "Function start count");
            test.assertValueEqual(summaries.size(), ends, "Java end count");
            test.assertValueEqual(score("#ended"), ends, "Function end count");
        }

        RoundSummary assertEnd(int count, long round, RoundSummary.Reason reason, Optional<Identifier> outcome,
                               Map<UUID, Identity> identities) {
            test.assertValueEqual(summaries.size(), count, "Expected one additional Java end notification");
            var summary = summaries.getLast();
            test.assertValueEqual(summary, new RoundSummary(round, reason, outcome, identities), "Final Java snapshot");
            var data = assertMacroSnapshot("ended", round, identities);
            test.assertValueEqual(data.getStringOr("reason", "missing"), reason.name().toLowerCase(Locale.ROOT), "Reason macro");
            test.assertValueEqual(data.getStringOr("outcome_id", "missing"), outcome.map(Identifier::toString).orElse(""), "Outcome macro");
            return summary;
        }

        CompoundTag assertMacroSnapshot(String event, long round, Map<UUID, Identity> identities) {
            var data = minecraft.getCommandStorage().get(STORAGE).getCompoundOrEmpty(event);
            test.assertValueEqual(data.getLongOr("round", -1), round, "Round macro");
            var players = data.getCompoundOrEmpty("identities");
            var expected = new CompoundTag();
            identities.forEach((uuid, identity) -> expected.putString(uuid.toString(), identity.name()));
            test.assertValueEqual(players, expected, "Identity macro matches the snapshot");
            return data;
        }

        void close() {
            active = null;
            minecraft.getCommandStorage().set(STORAGE, new CompoundTag());
            var scoreboard = minecraft.getScoreboard();
            scoreboard.removeObjective(scoreboard.getObjective(OBJECTIVE));
        }
    }
}
