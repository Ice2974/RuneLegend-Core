package com.runelegend.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.client.hud.TokenHudAnimation;
import com.runelegend.combat.GuardConfig;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.network.RuneLegendPayloads.PlayerSnapshot;
import com.runelegend.network.RuneLegendPayloads.View;
import com.runelegend.token.TokenEngine;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Properties;

public final class HudClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        checkAnimationTimeline();
        var props = new Properties();
        props.setProperty("online-mode", "false");
        props.setProperty("white-list", "false");
        try (var server = context.worldBuilder().createServer(props);
             var connection = server.connect()) {
            var cache = ClientStateCache.INSTANCE;
            context.waitFor(client -> client.level != null && client.player != null && cache.view() != null);
            var guard = server.computeOnServer(minecraft -> RuneLegendServer.find(minecraft).stamina().config());
            context.waitFor(client -> cache.view() != null && cache.view().staminaMax() == guard.max());
            server.runCommand("runelegend admin @p equip runelegend:echo");
            server.runCommand("item replace entity @p weapon.mainhand with " + GuardGameTests.SWORD);
            context.waitFor(client -> cache.rules() != null && !cache.rules().running() && cache.view() != null
                    && cache.view().tokens().stream().anyMatch(token -> token.id().getPath().equals("echo"))
                    && client.player.getMainHandItem().is(Items.DIAMOND_SWORD));
            connection.waitForChunksRender();
            server.runCommand("tp @p ~ ~ ~ 0 -35");
            context.getInput().holdKey(options -> options.keyUse);
            try {
                assertWaitingGuard(context, server, connection, cache, guard,
                        Math.ceilDiv(guard.max(), guard.drainPerTick()) + guard.regenDelayTicks());
                server.runCommand("title @a actionbar {text:'Vanilla action bar'}");
                context.takeScreenshot("runelegend-waiting-stamina-only");
                server.runCommand("runelegend game start");
                context.waitFor(client -> cache.rules().running() && client.player.isBlocking()
                        && cache.view() != null && cache.view().stamina() < guard.max());
                context.takeScreenshot("runelegend-hud-and-action-bar");
                context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
                context.waitTick();
                context.takeScreenshot("runelegend-native-third-person-block");
                context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
                context.waitFor(client -> cache.view() != null && cache.view().stamina() == 0 && !client.player.isUsingItem(), 240);
                context.waitFor(client -> cache.view() != null && cache.view().stamina() >= guard.minToStart(), 100);
                context.waitTicks(12);
                if (context.computeOnClient(client -> client.player.isUsingItem())) {
                    throw new AssertionError("Held Use automatically restarted blocking after exhaustion");
                }
            } finally {
                context.getInput().releaseKey(options -> options.keyUse);
            }
            context.waitTick();
            connection.waitForServerboundPackets();
            context.getInput().holdKey(options -> options.keyUse);
            try {
                context.waitFor(client -> cache.rules().running() && client.player.isBlocking());
                server.waitFor(minecraft -> connection.getServerPlayer().isBlocking());
                long round = context.computeOnClient(client -> cache.rules().round());
                // Keep Use held while an active guard crosses both round transitions.
                server.runCommand("runelegend game stop");
                assertWaitingGuard(context, server, connection, cache, guard, guard.regenDelayTicks() + 20);
                server.runCommand("runelegend game start");
                context.waitFor(client -> cache.rules().running() && cache.rules().round() == round + 1
                        && client.player.isBlocking() && cache.view() != null
                        && cache.view().stamina() > 0 && cache.view().stamina() < guard.max());
                server.runOnServer(minecraft -> {
                    var runtime = RuneLegendServer.find(minecraft);
                    var player = connection.getServerPlayer();
                    if (!player.isBlocking() || runtime.stamina().stamina(player) >= guard.max()) {
                        throw new AssertionError("Held guard did not resume draining on the next round");
                    }
                });
            } finally {
                context.getInput().releaseKey(options -> options.keyUse);
            }
            context.waitFor(client -> !client.player.isUsingItem());
            context.waitFor(client -> cache.view() != null && cache.view().stamina() == guard.max(), 240);
            server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
            server.runCommand("item replace entity @p weapon.offhand with minecraft:shield");
            context.waitFor(client -> client.player.getOffhandItem().is(Items.SHIELD));
            context.runOnClient(client -> {
                client.options.keyUse.setKey(InputConstants.getKey("key.keyboard.r"));
                KeyMapping.resetMapping();
            });
            context.getInput().holdKey(options -> options.keyUse);
            try {
                context.waitFor(client -> client.player.isBlocking() && client.player.getUsedItemHand() == InteractionHand.OFF_HAND
                        && cache.view() != null && cache.view().stamina() < guard.max());
            } finally {
                context.getInput().releaseKey(options -> options.keyUse);
            }
            context.waitFor(client -> !client.player.isUsingItem());
            captureThreeGlows(context, cache, guard);
        }
    }

    private static void assertWaitingGuard(ClientGameTestContext context, TestDedicatedServerContext server,
                                           TestDedicatedServerConnection connection, ClientStateCache cache,
                                           GuardConfig guard, int ticks) {
        context.waitFor(client -> cache.rules() != null && !cache.rules().running() && client.player.isBlocking()
                && cache.view() != null && cache.view().stamina() == guard.max());
        server.waitFor(minecraft -> connection.getServerPlayer().isBlocking());
        long until = server.computeOnServer(minecraft -> RuneLegendServer.find(minecraft).tick()) + ticks;
        // Observe real server ticks so a fast client cannot skip the stamina calculation window.
        server.waitFor(minecraft -> {
            var runtime = RuneLegendServer.find(minecraft);
            var player = connection.getServerPlayer();
            if (runtime.game().getRules().running() || !player.isBlocking()
                    || runtime.stamina().stamina(player) != guard.max()) {
                throw new AssertionError("WAITING changed stamina or interrupted the held guard");
            }
            return runtime.tick() >= until;
        }, ticks + 100);
        connection.waitForClientboundPackets();
        if (!context.computeOnClient(client -> !cache.rules().running() && client.options.keyUse.isDown()
                && client.player.isBlocking() && cache.view() != null && cache.view().stamina() == guard.max()
                && cache.view().staminaMax() == guard.max())) {
            throw new AssertionError("WAITING guard and full stamina were not preserved on the client");
        }
    }

    private static void checkAnimationTimeline() {
        var id = RuneLegendMod.id("preparation_fixture");
        var animation = new TokenHudAnimation();
        var preparing = new TokenEngine.View(id, TokenEngine.PREPARING, 0, 0, 0);
        var triggered = new TokenEngine.View(id, TokenEngine.PREPARING | TokenEngine.TRIGGERED, 12, 0, 0);
        animation.update(List.of(preparing), 0);
        assertDisplay(animation, preparing, 999, 0.5F, -1);
        assertDisplay(animation, preparing, 1_000, 1F, 0);
        assertDisplay(animation, preparing, 1_350, 1F, 7);
        assertDisplay(animation, preparing, 1_400, 1F, -1);
        assertDisplay(animation, preparing, 2_000, 0.5F, -1);
        animation.update(List.of(triggered), 3_200);
        assertDisplay(animation, triggered, 3_200, 1F, 0);
        animation.update(List.of(triggered), 3_300);
        assertDisplay(animation, triggered, 3_300, 1F, 2);
        animation.update(List.of(preparing), 3_800);
        assertDisplay(animation, preparing, 4_199, 1F, -1);
        assertDisplay(animation, preparing, 4_200, 0.5F, -1);
        assertDisplay(animation, preparing, 5_200, 1F, 0);
        var active = new TokenEngine.View(id, TokenEngine.PREPARING | TokenEngine.TRIGGERED | TokenEngine.ACTIVE, 45, 60, 0);
        animation.update(List.of(active), 5_250);
        assertDisplay(animation, active, 5_250, 1F, 0);
        assertDisplay(animation, active, 6_900, 1F, -1);
        animation.update(List.of(preparing), 7_000);
        assertDisplay(animation, preparing, 7_999, 0.5F, -1);
        assertDisplay(animation, preparing, 8_000, 1F, 0);
        var idle = new TokenEngine.View(id, TokenEngine.READY | TokenEngine.COOLDOWN, 0, 0, 90);
        animation.update(List.of(idle), 8_010);
        assertDisplay(animation, idle, 8_010, 0.5F, -1);
        animation.update(List.of(), 8_020);
        assertDisplay(animation, idle, 8_020, 0.5F, -1);
    }

    private static void assertDisplay(TokenHudAnimation animation, TokenEngine.View token, long now, float alpha, int frame) {
        var expected = new TokenHudAnimation.Display(alpha, frame);
        var actual = animation.display(token.id(), now);
        if (!expected.equals(actual)) throw new AssertionError("HUD at " + now + "ms: expected " + expected + ", got " + actual);
    }

    private static void captureThreeGlows(ClientGameTestContext context, ClientStateCache cache, GuardConfig guard) {
        var fixture = RuneLegendMod.id("hud_screenshot_fixture");
        var ids = List.of(RuneLegendMod.id("vernal_tide"), RuneLegendMod.id("asura_form"),
                RuneLegendMod.id("resonance_healing"));
        long cooldownUntil = cache.serverTick() + 600;
        var trigger = new java.util.concurrent.atomic.AtomicLong(1_000_000);
        var frameAge = new java.util.concurrent.atomic.AtomicLong();
        int originalScale = context.computeOnClient(client -> client.options.guiScale().get());
        context.runOnClient(client -> {
            cache.apply(new PlayerSnapshot(Long.MAX_VALUE, cache.serverTick(), new View(
                    ids.stream().map(id -> new TokenEngine.View(id, TokenEngine.COOLDOWN, 0, 0, cooldownUntil)).toList(),
                    guard.max(), guard.max())));
            HudElementRegistry.attachElementBefore(RuneLegendMod.id("hud"), fixture, (graphics, delta) -> {
                // Pin a visual frame with synthetic triggers so screenshot timing does not depend on FPS.
                long stamp = trigger.incrementAndGet();
                var views = ids.stream().map(id -> new TokenEngine.View(id, TokenEngine.TRIGGERED | TokenEngine.COOLDOWN,
                        stamp, 0, cooldownUntil)).toList();
                cache.tokenAnimation().update(views, Util.getMillis() - frameAge.get());
            });
        });
        try {
            for (int scale : new int[] {1, 2}) {
                context.runOnClient(client -> client.options.guiScale().set(scale));
                context.waitFor(client -> client.getWindow().getGuiScale() == scale);
                frameAge.set(0);
                context.takeScreenshot("token-glow-first-frame-scale-" + scale);
                frameAge.set(300);
                context.takeScreenshot("token-glow-expanded-scale-" + scale);
                frameAge.set(350);
                context.takeScreenshot("token-glow-transparent-last-frame-scale-" + scale);
            }
        } finally {
            context.runOnClient(client -> {
                HudElementRegistry.removeElement(fixture);
                client.options.guiScale().set(originalScale);
            });
        }
    }
}
