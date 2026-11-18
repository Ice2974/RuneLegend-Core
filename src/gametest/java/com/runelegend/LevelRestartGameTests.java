package com.runelegend;

import com.runelegend.test.PlayerStateFixtures;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

public final class LevelRestartGameTests {
    private static final BlockPos GATE = new BlockPos(1, 2, 1);
    private static final Identifier STORAGE = Identifier.parse("runelegend_test:restart");
    private static final String FUNCTIONS = "function runelegend_test:restart/";
    private static final String TIMER = "runelegend_test:restart/wave_due";
    private static final String OBJECTIVE = "rl_restart_phase";

    @GameTest
    public void mapLoadStopAndStartClearStaleWaves(GameTestHelper test) {
        var level = test.getLevel();
        var minecraft = level.getServer();
        var server = RuneLegendServer.find(minecraft);
        server.game().stopGame();
        var player = PlayerStateFixtures.join(minecraft, level, UUID.randomUUID());
        var arena = test.spawn(EntityTypes.MARKER, GATE);
        arena.addTag("runelegend_test.restart_arena");
        var otherMapMob = test.spawnWithNoFreeWill(EntityTypes.HUSK, 1, 2, 2);
        otherMapMob.addTag("other_map.round_mob");
        var source = minecraft.getFunctions().getGameLoopSender();
        var timers = minecraft.getScheduledEvents();
        long due = source.getLevel().getGameTime() + 100;
        long firstRound = server.game().getRules().round() + 1;
        var commands = minecraft.getCommands();
        try {
            commands.performPrefixedCommand(source, "function #minecraft:load");
            commands.performPrefixedCommand(source, FUNCTIONS + "start {round:" + firstRound + "L}");
            level.setBlockAndUpdate(test.absolutePos(GATE), Blocks.REDSTONE_BLOCK.defaultBlockState());
            test.assertTrue(timers.getEventsIds().contains(TIMER), "A persisted task can have the upcoming round number");
            commands.performPrefixedCommand(source, "function #minecraft:load");
            test.assertFalse(timers.getEventsIds().contains(TIMER), "Load clears the persisted vanilla timer");
            test.assertTrue(minecraft.getCommandStorage().get(STORAGE).isEmpty(), "Load clears both round records");
            test.assertBlockPresent(Blocks.AIR, GATE);

            test.assertTrue(server.game().startGame(), "The first round begins after loading");
            test.assertTrue(timers.getEventsIds().contains(TIMER), "The start event schedules a fresh wave");
            test.assertTrue(server.game().stopGame(), "Manual stop ends the first round");
            test.assertFalse(timers.getEventsIds().contains(TIMER), "The end event cancels the old wave");
            timers.tick(minecraft, due);
            test.assertBlockPresent(Blocks.AIR, GATE);

            var staleMob = test.spawnWithNoFreeWill(EntityTypes.HUSK, 2, 2, 1);
            staleMob.addTag("runelegend_test.round_mob");
            test.assertTrue(server.game().startGame(), "The map can start a new round");
            test.assertFalse(staleMob.isAlive(), "Start also cleans up a leftover map mob");
            commands.performPrefixedCommand(source, FUNCTIONS + "check_wave {round:" + firstRound + "L}");
            test.assertBlockPresent(Blocks.AIR, GATE);
            timers.tick(minecraft, due);
            test.assertBlockPresent(Blocks.REDSTONE_BLOCK, GATE);
            var wave = level.getEntitiesOfClass(Husk.class, arena.getBoundingBox().inflate(3),
                    mob -> mob.isAlive() && mob.entityTags().contains("runelegend_test.round_mob"));
            test.assertValueEqual(wave.size(), 1, "Only the current round spawns a wave");
            server.game().stopGame();
            test.assertBlockPresent(Blocks.AIR, GATE);
            test.assertFalse(wave.getFirst().isAlive(), "Ending the current round removes its wave");
            test.assertTrue(otherMapMob.isAlive(), "Map cleanup preserves entities in another namespace");
            test.succeed();
        } finally {
            server.game().stopGame();
            commands.performPrefixedCommand(source, FUNCTIONS + "reset");
            arena.discard();
            server.leave(player);
            var scoreboard = minecraft.getScoreboard();
            var objective = scoreboard.getObjective(OBJECTIVE);
            if (objective != null) scoreboard.removeObjective(objective);
        }
    }
}
