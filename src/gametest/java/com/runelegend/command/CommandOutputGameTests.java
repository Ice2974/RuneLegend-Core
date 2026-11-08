package com.runelegend.command;

import com.runelegend.RuneLegendServer;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.test.GuardGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class CommandOutputGameTests {
    private static final Identifier ECHO = Identifier.parse("runelegend:echo");

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
