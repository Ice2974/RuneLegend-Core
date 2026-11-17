package com.runelegend.test;

import com.runelegend.RuneLegendServer;
import com.runelegend.player.PlayerState;
import com.runelegend.player.RevivePositionResolver;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public final class PlayerStateGameTests {
    @GameTest
    public void waitingJoinUsesWorldSpawnInsteadOfSavedPosition(GameTestHelper test) {
        var level = test.getLevel();
        var minecraft = level.getServer();
        var server = RuneLegendServer.find(minecraft);
        server.game().stopGame();
        var spawn = minecraft.getRespawnData();
        var saved = Vec3.atBottomCenterOf(spawn.pos()).add(500.5, 0, 0);
        var player = PlayerStateFixtures.join(minecraft, level, UUID.randomUUID(), saved);
        ServerPlayer spectator = null;
        try {
            test.assertTrue(player.position().distanceTo(saved) > 100.0, "Waiting join ignores the saved logout position");
            assertAtWorldSpawn(test, player, spawn, "Waiting join");

            server.game().startGame();
            var spectatorSaved = Vec3.atBottomCenterOf(spawn.pos()).add(-300.5, 0, 0);
            spectator = PlayerStateFixtures.join(minecraft, level, UUID.randomUUID(), spectatorSaved);
            test.assertValueEqual(spectator.gameMode(), GameType.SPECTATOR, "A running join is still a spectator");
            test.assertTrue(spectator.position().distanceToSqr(spectatorSaved) < 1.0E-4,
                    "A running join keeps its saved position");
            test.succeed();
        } finally {
            if (spectator != null) server.leave(spectator);
            server.game().stopGame();
            server.leave(player);
        }
    }

    @GameTest
    public void stopGameTeleportsEveryOnlinePlayerToWorldSpawn(GameTestHelper test) {
        var level = test.getLevel();
        var minecraft = level.getServer();
        var server = RuneLegendServer.find(minecraft);
        server.game().stopGame();
        var spawn = minecraft.getRespawnData();
        var first = PlayerStateFixtures.join(minecraft, level, UUID.randomUUID());
        var second = PlayerStateFixtures.join(minecraft, level, UUID.randomUUID());
        try {
            server.game().startGame();
            var firstSaved = Vec3.atBottomCenterOf(spawn.pos()).add(500.5, 0, 0);
            first.setPos(firstSaved);
            var nether = minecraft.getLevel(net.minecraft.world.level.Level.NETHER);
            test.assertTrue(nether != null, "The test server exposes the nether for the cross-dimension check");
            test.assertTrue(second.teleportTo(nether, 0.5, 64, 0.5, java.util.Set.of(), 0, 0, false),
                    "The second player can leave the world spawn dimension");
            server.game().stopGame();
            assertAtWorldSpawn(test, first, spawn, "First stopped player");
            assertAtWorldSpawn(test, second, spawn, "Second stopped player");
            test.assertTrue(first.position().distanceTo(firstSaved) > 100.0, "Stop ignores the round position");
            test.assertTrue(second.position().distanceTo(firstSaved) > 100.0, "Stop returns other dimensions to spawn");
            test.assertTrue(server.playerStates().state(first.getUUID()) == null, "Stop clears round identities");
            test.assertValueEqual(first.gameMode(), GameType.ADVENTURE, "Stop restores every player to adventure");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.leave(first);
            server.leave(second);
        }
    }

    private static void assertAtWorldSpawn(GameTestHelper test, ServerPlayer player, LevelData.RespawnData spawn,
                                           String description) {
        var expected = Vec3.atBottomCenterOf(spawn.pos());
        test.assertValueEqual(player.level().dimension(), spawn.dimension(), description + " uses the world spawn dimension");
        double horizontal = Math.hypot(player.getX() - expected.x, player.getZ() - expected.z);
        test.assertTrue(horizontal <= 16.0, description + " is placed within the shared spawn radius");
    }

    @GameTest
    public void bothTotemHandsPrecedeDowningAndInventoryIsPreserved(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        server.game().stopGame();
        var player = PlayerStateFixtures.join(level.getServer(), level, UUID.randomUUID());
        try {
            player.setPos(test.absoluteVec(new Vec3(2.5, 2, 2.5)));
            player.getInventory().setItem(8, new ItemStack(Items.DIAMOND, 5));
            player.giveExperiencePoints(30);
            int xp = player.totalExperience;
            int deaths = player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
            server.game().startGame();
            for (var hand : InteractionHand.values()) {
                player.setHealth(20); player.setInvulnerableTime(0); player.damageCooldownTime = 0;
                player.setItemInHand(hand, new ItemStack(Items.TOTEM_OF_UNDYING));
                player.hurtServer(level, player.damageSources().generic(), 100);
                test.assertValueEqual(server.playerStates().state(player.getUUID()), PlayerState.ALIVE, "A usable totem wins before downing");
                test.assertTrue(server.downed().get(player.getUUID()) == null, "Totem creates no death marker");
                test.assertTrue(player.getItemInHand(hand).isEmpty(), "Totem is consumed by vanilla");
                player.removeAllEffects();
            }
            player.setInvulnerableTime(0); player.damageCooldownTime = 0;
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            var origin = player.position();
            player.hurtServer(level, player.damageSources().generic(), 100);
            var record = server.downed().get(player.getUUID());
            test.assertValueEqual(server.playerStates().state(player.getUUID()), PlayerState.DOWNED, "No totem enters DOWNED");
            test.assertTrue(player.isAlive() && player.isSpectator(), "Downing keeps a living spectator instead of a death screen");
            test.assertValueEqual(record.position(), origin, "Exact death position is captured");
            test.assertTrue(player.getActiveEffects().isEmpty(), "Downing removes potion effects");
            test.assertValueEqual(player.getInventory().getItem(8).getCount(), 5, "Items are retained");
            test.assertValueEqual(player.totalExperience, xp, "Experience is retained");
            test.assertValueEqual(player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)), deaths, "No vanilla death statistic");
            player.die(player.damageSources().genericKill());
            test.assertValueEqual(server.downed().get(player.getUUID()), record, "Repeated death cannot extend the timer");
            test.succeed();
        } finally { server.game().stopGame(); server.leave(player); }
    }

    @GameTest
    public void reconnectRetainsIdentityAndOldDisconnectCannotEvictNewConnection(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        server.game().stopGame();
        var uuid = UUID.randomUUID();
        var first = PlayerStateFixtures.join(level.getServer(), level, uuid);
        net.minecraft.server.level.ServerPlayer replacement = null, late = null;
        try {
            server.game().startGame();
            first.die(first.damageSources().genericKill());
            server.leave(first);
            test.assertValueEqual(server.playerStates().state(uuid), PlayerState.ELIMINATED, "Disconnected downed identity remains");
            test.assertTrue(server.downed().get(uuid) == null, "Disconnected downed marker is removed");
            replacement = PlayerStateFixtures.join(level.getServer(), level, uuid);
            test.assertValueEqual(server.playerStates().state(uuid), PlayerState.ELIMINATED, "Reconnect cannot revive");
            test.assertTrue(replacement.isSpectator(), "Reconnect uses spectator mode");
            server.leave(first);
            test.assertTrue(server.isCurrent(replacement), "Old disconnect does not evict new transport");
            late = PlayerStateFixtures.join(level.getServer(), level, UUID.randomUUID());
            test.assertValueEqual(server.playerStates().state(late.getUUID()), PlayerState.SPECTATOR, "Late join is a spectator");
            server.game().stopGame();
            test.assertTrue(server.playerStates().state(uuid) == null, "Stop removes offline and online round identities");
            test.assertValueEqual(replacement.gameMode(), GameType.ADVENTURE, "Waiting restores adventure mode");
            server.game().startGame();
            test.assertValueEqual(server.playerStates().state(uuid), PlayerState.ALIVE, "Next round can participate again");
            test.succeed();
        } finally {
            server.game().stopGame(); server.leave(first);
            if (replacement != null) server.leave(replacement);
            if (late != null) server.leave(late);
        }
    }

    @GameTest
    public void revivalUsesRecordedAnchorAndClearsTheMarker(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        server.game().stopGame();
        var player = PlayerStateFixtures.join(level.getServer(), level, UUID.randomUUID());
        try {
            var position = test.absoluteVec(new Vec3(2.5, 2, 2.5));
            level.setBlockAndUpdate(net.minecraft.core.BlockPos.containing(position).below(), Blocks.STONE.defaultBlockState());
            player.setPos(position);
            server.game().startGame();
            player.die(player.damageSources().genericKill());
            var record = server.downed().get(player.getUUID());
            player.setPos(position.add(20, 20, 20));
            test.assertTrue(!server.revive(player.getUUID(), record.sequence() + 1), "Wrong downed generation is rejected");
            test.assertTrue(server.revive(player.getUUID(), record.sequence()), "Valid rescue completes");
            test.assertValueEqual(player.position(), position, "Revival uses the death anchor, not spectator position");
            test.assertValueEqual(player.getHealth(), player.getMaxHealth() * 0.5F, "Configured revival health");
            test.assertValueEqual(player.gameMode(), GameType.ADVENTURE, "Revived player is an adventurer");
            test.assertTrue(server.downed().get(player.getUUID()) == null, "Revival removes marker");
            var unsafe = new Vec3(position.x, level.getMinY() - 100, position.z);
            test.assertValueEqual(RevivePositionResolver.resolve(level, player, unsafe), unsafe, "No safe point falls back to original coordinates");
            test.succeed();
        } finally { server.game().stopGame(); server.leave(player); }
    }
}
