package com.runelegend.client.hud;

import com.runelegend.RuneLegendMod;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.token.AddonTokenFixture;
import com.runelegend.token.TokenEngine;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Properties;

public final class AddonTokenClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var properties = new Properties();
        properties.setProperty("online-mode", "false");
        properties.setProperty("white-list", "false");
        var cache = ClientStateCache.INSTANCE;
        var addon = AddonTokenFixture.ID;
        var builtIn = RuneLegendMod.id("echo");
        var withoutIcon = AddonTokenFixture.WITHOUT_ICON;
        try (var server = context.worldBuilder().createServer(properties); var connection = server.connect()) {
            context.waitFor(client -> client.player != null && cache.view() != null);
            server.runCommand("runelegend admin @p clear");
            for (var id : List.of(addon, builtIn, withoutIcon)) server.runCommand("runelegend admin @p equip " + id);
            server.runCommand("runelegend game start");
            context.waitFor(client -> cache.rules().running() && cache.view().tokens().stream().map(TokenEngine.View::id)
                    .toList().equals(List.of(addon, builtIn, withoutIcon)));
            connection.waitForChunksRender();
            context.runOnClient(client -> {
                var atlas = client.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
                var slots = cache.view().tokens();
                expect(RuneLegendHud.icon(atlas, slots.get(0)), addon.withPrefix("token/"));
                expect(RuneLegendHud.icon(atlas, slots.get(1)), builtIn.withPrefix("token/"));
                expect(RuneLegendHud.icon(atlas, slots.get(2)), RuneLegendMod.id("token/unknown"));
                var removed = new TokenEngine.View(addon, TokenEngine.UNKNOWN, 0, 0, 0);
                expect(RuneLegendHud.icon(atlas, removed), RuneLegendMod.id("token/unknown"));
                if (Component.translatable("token.runelegend_test.echo").getString().equals("token.runelegend_test.echo")) {
                    throw new AssertionError("Addon token language resource did not load");
                }
            });
            context.takeScreenshot("runelegend-addon-token-icons");

            server.runCommand("damage @p 2 minecraft:magic");
            context.waitFor(client -> (cache.view().tokens().getFirst().flags() & TokenEngine.COOLDOWN) != 0);
            server.waitFor(minecraft -> minecraft.getPlayerList().getPlayers().getFirst().entityTags()
                    .contains("runelegend_test_delayed"));
            server.runCommand("tag @p remove runelegend_test_delayed");
            server.runCommand("runelegend game stop");
            server.runCommand("runelegend game start");
            context.waitFor(client -> cache.rules().running() && cache.view().tokens().getFirst().flags() == TokenEngine.READY);
            server.runCommand("damage @p 2 minecraft:magic");
            server.runCommand("runelegend game stop");
            context.waitFor(client -> !cache.rules().running());
            context.waitTicks(25);
            if (server.computeOnServer(minecraft -> minecraft.getPlayerList().getPlayers().getFirst().entityTags()
                    .contains("runelegend_test_delayed"))) {
                throw new AssertionError("Scheduled addon work survived stopping the round");
            }
            server.runCommand("runelegend admin @p clear");
        }
    }

    private static void expect(Identifier actual, Identifier expected) {
        if (!actual.equals(expected)) throw new AssertionError("Expected HUD sprite " + expected + ", got " + actual);
    }
}
