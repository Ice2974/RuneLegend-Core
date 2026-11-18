package com.runelegend;

import com.runelegend.api.PlayerSnapshot.Identity;
import com.runelegend.api.PlayerStateEvents;
import com.runelegend.api.PlayerStateEvents.Downed;
import com.runelegend.api.PlayerStateEvents.DownedAnchor;
import com.runelegend.api.PlayerStateEvents.Eliminated;
import com.runelegend.api.PlayerStateEvents.EliminationReason;
import com.runelegend.api.PlayerStateEvents.Revived;
import com.runelegend.api.RoundEvents;
import com.runelegend.api.RoundSummary;
import com.runelegend.api.RuneLegendApi;
import com.runelegend.player.DownedService.DownedRecord;
import com.runelegend.test.PlayerStateFixtures;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlayerStateEventGameTests {
    private static final Identifier STORAGE = Identifier.parse("runelegend_test:player_probe");
    private static Probe active;

    static {
        PlayerStateEvents.DOWNED.register((minecraft, event) -> {
            if (active == null || active.minecraft != minecraft) return;
            active.observe(event, event.playerId(), Identity.DOWNED, true, true);
            active.afterDowned.run();
        });
        PlayerStateEvents.REVIVED.register((minecraft, event) -> {
            if (active != null && active.minecraft == minecraft)
                active.observe(event, event.playerId(), Identity.ALIVE, true, false);
        });
        PlayerStateEvents.ELIMINATED.register((minecraft, event) -> {
            if (active != null && active.minecraft == minecraft)
                active.observe(event, event.playerId(), Identity.ELIMINATED, event.reason() != EliminationReason.DISCONNECT, false);
        });
        RoundEvents.ENDED.register((minecraft, summary) -> {
            if (active != null && active.minecraft == minecraft) active.add(summary);
        });
    }

    @GameTest
    public void totemSkipsDowningAndLastDownIsDeliveredBeforeSettlement(GameTestHelper test) {
        var server = runtime(test);
        var first = join(test, UUID.randomUUID());
        var last = join(test, UUID.randomUUID());
        var probe = new Probe(test);
        try {
            server.game().startGame();
            first.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            first.hurtServer(first.level(), first.damageSources().generic(), 100);
            test.assertTrue(first.getItemInHand(InteractionHand.OFF_HAND).isEmpty(), "Vanilla consumed the totem");
            probe.assertCount(0);

            // A nested transition must wait until the previous event reaches both subscription channels.
            probe.afterDowned = () -> {
                probe.afterDowned = () -> {};
                last.die(last.damageSources().genericKill());
            };
            first.die(first.damageSources().genericKill());
            first.die(first.damageSources().genericKill());
            var firstRecord = server.downed().get(first.getUUID());
            var lastRecord = server.downed().get(last.getUUID());
            probe.assertCount(2);
            probe.assertDowned(0, firstRecord);
            probe.assertDowned(1, lastRecord);
            test.assertValueEqual(probe.data().getIntOr("blocked_finish", -1), 0, "A function cannot settle during an event");

            server.endTick();
            probe.assertCount(3);
            var identities = Map.of(first.getUUID(), Identity.DOWNED, last.getUUID(), Identity.DOWNED);
            test.assertValueEqual(probe.javaEvents.get(2), new RoundSummary(lastRecord.round(), RoundSummary.Reason.NO_ALIVE,
                    Optional.empty(), identities), "Settlement sees both committed identities");
            var ended = probe.functionEvent(2);
            test.assertValueEqual(ended.getStringOr("type", ""), "round_ended", "Function settlement follows both downs");
            test.assertValueEqual(ended.getLongOr("round", -1), lastRecord.round(), "Settlement round");
            test.assertValueEqual(ended.getStringOr("reason", ""), "no_alive", "Settlement reason");
            test.assertValueEqual(ended.getCompoundOrEmpty("identities").getStringOr(last.getUUID().toString(), ""),
                    "DOWNED", "Function settlement includes the last downed player");
            test.succeed();
        } finally {
            probe.close();
            server.game().stopGame();
            server.leave(first);
            server.leave(last);
        }
    }

    @GameTest
    public void rescuePublishesTheMatchedRescuerAndTheOriginalDimension(GameTestHelper test) {
        var server = runtime(test);
        var target = join(test, UUID.randomUUID());
        var helper = join(test, UUID.randomUUID());
        var probe = new Probe(test);
        try {
            server.game().startGame();
            var nether = probe.minecraft.getLevel(Level.NETHER);
            var position = new Vec3(32.25, 80.5, 32.75);
            test.assertTrue(target.teleportTo(nether, position.x, position.y, position.z, Set.of(), 63, -17, false),
                    "The target dies outside the function source dimension");
            test.assertTrue(helper.teleportTo(nether, position.x, position.y, position.z, Set.of(), 0, 0, false),
                    "The rescuer travels to the death dimension");
            target.die(target.damageSources().genericKill());
            var record = server.downed().get(target.getUUID());
            probe.assertDowned(0, record);
            test.assertTrue(probe.data().getBooleanOr("overworld_source", false), "Function uses the world spawn dimension");
            test.assertFalse(probe.data().getBooleanOr("has_executor", true), "The function has no executing entity");
            test.assertTrue(target.teleportTo(test.getLevel(), 0.5, 70, 0.5, Set.of(), 0, 0, false),
                    "The target's spectator position can be in a different dimension");

            PlayerStateFixtures.sneak(helper, true);
            advanceTo(server, server.tick() + 1 + server.playerStateConfig().reviveTicks());
            probe.assertCount(2);
            test.assertValueEqual(probe.javaEvents.get(1), new Revived(record.round(), target.getUUID(),
                    anchor(record), Optional.of(helper.getUUID())), "Java revival carries the actual rescuer and original anchor");
            var function = probe.assertFunction(1, "revived", record.round(), target.getUUID(), anchor(record));
            test.assertValueEqual(function.getStringOr("rescuer_uuid", ""), helper.getUUID().toString(), "Rescuer macro");
            test.assertValueEqual(target.level().dimension(), Level.NETHER, "Revival returns to the death dimension");

            target.die(target.damageSources().genericKill());
            var next = server.downed().get(target.getUUID());
            test.assertTrue(next.sequence() > record.sequence(), "A new downing has a fresh sequence");
            test.assertTrue(server.revive(target.getUUID(), next.sequence()), "The unattributed completion follows the same commit path");
            probe.assertCount(4);
            test.assertValueEqual(probe.javaEvents.get(3), new Revived(next.round(), target.getUUID(), anchor(next), Optional.empty()),
                    "No rescuer is attributed to a direct completion");
            var unattributed = probe.assertFunction(3, "revived", next.round(), target.getUUID(), anchor(next));
            test.assertValueEqual(unattributed.getStringOr("rescuer_uuid", "missing"), "", "Unattributed rescuer macro");
            test.succeed();
        } finally {
            probe.close();
            server.game().stopGame();
            server.leave(target);
            server.leave(helper);
        }
    }

    @GameTest
    public void timeoutPublishesOnlyEliminationWhenRescueCompletesOnTheDeadline(GameTestHelper test) {
        var server = runtime(test);
        var target = join(test, UUID.randomUUID());
        var helper = join(test, UUID.randomUUID());
        var probe = new Probe(test);
        try {
            server.game().startGame();
            target.setPos(test.absoluteVec(new Vec3(2.5, 2, 2.5)));
            helper.setPos(target.position());
            target.die(target.damageSources().genericKill());
            var record = server.downed().get(target.getUUID());
            long start = record.expiresAtTick() - server.playerStateConfig().reviveTicks();
            advanceTo(server, start - 1);
            PlayerStateFixtures.sneak(helper, true);
            advanceTo(server, start);
            var session = server.downed().rescue(target.getUUID());
            test.assertTrue(session != null && session.startedAtTick() == start, "Rescue would finish exactly at expiry");
            advanceTo(server, record.expiresAtTick() - 1);
            probe.assertCount(1);
            server.endTick();
            probe.assertCount(2);
            probe.assertEliminated(1, record.round(), target.getUUID(), Identity.DOWNED, EliminationReason.TIMEOUT, anchor(record));
            test.succeed();
        } finally {
            probe.close();
            server.game().stopGame();
            server.leave(target);
            server.leave(helper);
        }
    }

    @GameTest
    public void failedTeleportAndReconnectDoNotDuplicateNotifications(GameTestHelper test) {
        var server = runtime(test);
        var uuid = UUID.randomUUID();
        var target = join(test, uuid);
        var alive = join(test, UUID.randomUUID());
        ServerPlayer replacement = null;
        var probe = new Probe(test);
        try {
            server.game().startGame();
            target.die(target.damageSources().genericKill());
            var record = server.downed().get(uuid);
            target.discard(); // Vanilla teleport rejects a removed entity that still has a fixture connection.
            test.assertFalse(server.revive(uuid, record.sequence()), "The rescue teleport fails");
            test.assertValueEqual(server.downed().get(uuid), record, "Failed teleport preserves the pending downing");
            probe.assertCount(1);

            server.leave(target);
            probe.assertCount(2);
            probe.assertEliminated(1, record.round(), uuid, Identity.DOWNED, EliminationReason.DISCONNECT, anchor(record));
            replacement = join(test, uuid);
            server.leave(target);
            test.assertTrue(server.isCurrent(replacement) && replacement.isSpectator(), "The reconnected player stays eliminated");
            server.leave(replacement);
            probe.assertCount(2);

            server.leave(alive);
            probe.assertCount(3);
            probe.assertEliminated(2, record.round(), alive.getUUID(), Identity.ALIVE, EliminationReason.DISCONNECT, null);
            test.succeed();
        } finally {
            probe.close();
            server.leave(target);
            if (replacement != null) server.leave(replacement);
            server.game().stopGame();
            server.leave(alive);
        }
    }

    private static RuneLegendServer runtime(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        server.game().stopGame();
        return server;
    }

    private static ServerPlayer join(GameTestHelper test, UUID uuid) {
        return PlayerStateFixtures.join(test.getLevel().getServer(), test.getLevel(), uuid);
    }

    private static void advanceTo(RuneLegendServer server, long tick) {
        while (server.tick() < tick) server.endTick();
    }

    private static DownedAnchor anchor(DownedRecord record) {
        return new DownedAnchor(record.sequence(), record.dimension(), record.position(), record.yaw(), record.pitch());
    }

    private static final class Probe {
        private final GameTestHelper test;
        private final MinecraftServer minecraft;
        private final List<Object> javaEvents = new ArrayList<>();
        private Runnable afterDowned = () -> {};

        Probe(GameTestHelper test) {
            this.test = test;
            minecraft = test.getLevel().getServer();
            var data = new CompoundTag();
            data.putBoolean("active", true);
            minecraft.getCommandStorage().set(STORAGE, data);
            active = this;
        }

        void observe(Object event, UUID uuid, Identity identity, boolean connected, boolean hasMarker) {
            var player = RuneLegendApi.queries(minecraft).player(uuid);
            var server = RuneLegendServer.find(minecraft);
            test.assertValueEqual(player.identity(), Optional.of(identity), "Java callback sees committed identity");
            test.assertValueEqual(player.connected(), connected, "Java callback sees committed connection state");
            test.assertValueEqual(server.downed().get(uuid) != null, hasMarker, "Java callback sees committed marker state");
            var online = server.currentPlayer(uuid);
            if (online != null) test.assertValueEqual(online.isSpectator(), identity != Identity.ALIVE, "Player mode is committed");
            test.assertFalse(server.game().stopGame(), "A Java listener cannot settle during an event");
            add(event);
        }

        void add(Object event) {
            test.assertValueEqual(data().getListOrEmpty("events").size(), javaEvents.size(), "Java runs before the matching function");
            javaEvents.add(event);
        }

        CompoundTag data() {
            return minecraft.getCommandStorage().get(STORAGE);
        }

        CompoundTag functionEvent(int index) {
            return data().getListOrEmpty("events").getCompoundOrEmpty(index);
        }

        void assertCount(int count) {
            test.assertValueEqual(javaEvents.size(), count, "Java notification count");
            test.assertValueEqual(data().getListOrEmpty("events").size(), count, "Function notification count");
        }

        void assertDowned(int index, DownedRecord record) {
            test.assertValueEqual(javaEvents.get(index), new Downed(record.round(), record.playerUuid(), anchor(record)),
                    "Java downed event");
            assertFunction(index, "downed", record.round(), record.playerUuid(), anchor(record));
        }

        void assertEliminated(int index, long round, UUID uuid, Identity previous, EliminationReason reason, DownedAnchor anchor) {
            test.assertValueEqual(javaEvents.get(index), new Eliminated(round, uuid, previous, reason, Optional.ofNullable(anchor)),
                    "Java elimination event");
            var function = assertFunction(index, "eliminated", round, uuid, anchor);
            test.assertValueEqual(function.getStringOr("reason", ""), reason == EliminationReason.TIMEOUT ? "timeout" : "disconnect",
                    "Elimination reason macro");
            test.assertValueEqual(function.getStringOr("previous_identity", ""), previous.name(), "Previous identity macro");
        }

        CompoundTag assertFunction(int index, String type, long round, UUID uuid, DownedAnchor anchor) {
            var function = functionEvent(index);
            test.assertValueEqual(function.getStringOr("type", ""), type, "Function event order");
            test.assertValueEqual(function.getLongOr("round", -1), round, "Round macro");
            test.assertValueEqual(function.getStringOr("player_uuid", ""), uuid.toString(), "Target UUID macro");
            var location = function.getCompoundOrEmpty("anchor");
            var expected = new CompoundTag();
            if (anchor != null) {
                expected.putLong("sequence", anchor.sequence());
                expected.putString("dimension", anchor.dimension().identifier().toString());
                expected.putDouble("x", anchor.position().x);
                expected.putDouble("y", anchor.position().y);
                expected.putDouble("z", anchor.position().z);
                expected.putFloat("yaw", anchor.yaw());
                expected.putFloat("pitch", anchor.pitch());
            }
            test.assertValueEqual(location, expected, "Function anchor macro");
            return function;
        }

        void close() {
            active = null;
            minecraft.getCommandStorage().set(STORAGE, new CompoundTag());
        }
    }
}
