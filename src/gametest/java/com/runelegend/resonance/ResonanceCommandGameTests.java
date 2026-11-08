package com.runelegend.resonance;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.test.GuardGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;

public final class ResonanceCommandGameTests {
    private static final Identifier PROTECTION = Identifier.parse("runelegend:protection");
    private static final Identifier LOST = Identifier.parse("runelegend:lost");

    @GameTest
    public void commandsRespectWaitingGateAndSelectionRules(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var player = GuardGameTests.fixture(test);
        var commands = test.getLevel().getServer().getCommands();
        var source = player.createCommandSourceStack().withSuppressedOutput();
        var service = server.resonance();
        try {
            server.game().stopGame();
            service.unequip(player.getUUID());

            commands.performPrefixedCommand(source, "runelegend resonance list");
            test.assertTrue(service.guardSelection(player.getUUID()).isEmpty(), "Listing does not change the selection");

            commands.performPrefixedCommand(source, "runelegend resonance equip protection");
            test.assertValueEqual(service.guardSelection(player.getUUID()), java.util.Optional.of(PROTECTION),
                    "The command equips protection");

            commands.performPrefixedCommand(source, "runelegend resonance equip lost");
            test.assertValueEqual(service.guardSelection(player.getUUID()), java.util.Optional.of(LOST),
                    "A second equip replaces the single selection");

            commands.performPrefixedCommand(source, "runelegend resonance equip destruction");
            test.assertValueEqual(service.guardSelection(player.getUUID()), java.util.Optional.of(LOST),
                    "Destruction is rejected without clearing the current selection");

            commands.performPrefixedCommand(source, "runelegend resonance equip unknown_attribute");
            test.assertValueEqual(service.guardSelection(player.getUUID()), java.util.Optional.of(LOST),
                    "Unknown IDs are rejected without clearing the current selection");

            commands.performPrefixedCommand(source, "runelegend status");
            commands.performPrefixedCommand(source, "runelegend resonance unequip");
            test.assertTrue(service.guardSelection(player.getUUID()).isEmpty(), "Unequip clears the selection");

            server.game().startGame();
            commands.performPrefixedCommand(source, "runelegend resonance equip protection");
            test.assertTrue(service.guardSelection(player.getUUID()).isEmpty(),
                    "RUNNING rejects selection changes at the service layer");
            server.game().stopGame();

            commands.performPrefixedCommand(source, "runelegend resonance equip protection");
            test.assertValueEqual(service.guardSelection(player.getUUID()), java.util.Optional.of(PROTECTION),
                    "The same command works again after returning to WAITING");
            test.succeed();
        } finally {
            server.game().stopGame();
            service.unequip(player.getUUID());
            server.leave(player);
        }
    }

    @GameTest
    public void statusOnlyShowsTokensAndResonance(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var player = GuardGameTests.fixture(test);
        var commands = test.getLevel().getServer().getCommands();
        var messages = new ArrayList<String>();
        var source = player.createCommandSourceStack().withSource(new CommandSource() {
            @Override public void sendSystemMessage(Component message) { messages.add(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        });
        try {
            server.game().stopGame();
            server.resonance().unequip(player.getUUID());
            server.players().clear(player.getUUID());
            test.assertValueEqual(server.players().equip(player.getUUID(), RuneLegendMod.id("echo")),
                    PlayerTokenService.Result.CHANGED, "A token is equipped for the status line");
            test.assertValueEqual(server.resonance().equip(player.getUUID(), PROTECTION),
                    ResonanceService.Result.CHANGED, "A resonance is equipped for the status line");

            commands.performPrefixedCommand(source, "runelegend status");

            test.assertTrue(messages.stream().anyMatch(message -> message.contains("tokens:")),
                    "The status header names the token list");
            test.assertTrue(messages.stream().anyMatch(message -> message.contains("echo") && message.contains("Echo")),
                    "The equipped token is still shown");
            test.assertTrue(messages.stream().anyMatch(message -> message.contains("Protection")),
                    "The selected resonance is shown");
            test.assertTrue(messages.stream().noneMatch(message -> message.contains("Stamina")
                            || message.contains("体力") || message.contains("%") || message.contains("s)")),
                    "Status no longer shows stamina or cooldown information");
            test.succeed();
        } finally {
            server.players().clear(player.getUUID());
            server.resonance().unequip(player.getUUID());
            server.game().stopGame();
            server.leave(player);
        }
    }
}
