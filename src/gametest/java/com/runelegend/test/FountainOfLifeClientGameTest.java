package com.runelegend.test;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.particle.RuneLegendParticles;
import com.runelegend.token.TokenEngine;
import io.netty.channel.ChannelFutureListener;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;

import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class FountainOfLifeClientGameTest implements FabricClientGameTest {
    private static final net.minecraft.resources.Identifier FOUNTAIN = RuneLegendMod.id("fountain_of_life");

    @Override public void runTest(ClientGameTestContext context) {
        var properties = new Properties();
        properties.setProperty("online-mode", "false");
        properties.setProperty("level-type", "minecraft:flat");
        var expires = new AtomicLong();
        var scoutConnection = new ParticleConnection();
        try (var dedicated = context.worldBuilder().createServer(properties); var connection = dedicated.connect()) {
            context.waitFor(client -> client.player != null && ClientStateCache.INSTANCE.view() != null);
            dedicated.runCommand("runelegend admin @p clear");
            dedicated.runCommand("runelegend admin @p equip runelegend:fountain_of_life");
            dedicated.runCommand("difficulty peaceful");
            dedicated.runCommand("time set noon");
            var ally = dedicated.computeOnServer(server -> {
                var owner = connection.getServerPlayer();
                return PlayerStateFixtures.join(server, owner.level(), UUID.randomUUID(), "FountainScout", null, scoutConnection);
            });
            try {
                dedicated.runCommand("runelegend game start");
                context.waitFor(client -> hasFlag(TokenEngine.READY));
                dedicated.runCommand("execute as @p at @s run fill ~-4 ~ ~-4 ~4 ~ ~4 minecraft:smooth_stone");
                dedicated.runCommand("execute as @p at @s run tp @s ~ ~1 ~ 0 0");
                connection.waitForChunksRender();
                dedicated.runOnServer(server -> {
                    var player = connection.getServerPlayer();
                    ally.setPos(player.position().add(2, 0, 0));
                    player.level().players().add(ally);
                    player.setHealth(8);
                });
                context.waitFor(client -> hasFlag(TokenEngine.ACTIVE)
                        && client.player.getEffect(MobEffects.REGENERATION) != null
                        && client.player.getEffect(MobEffects.REGENERATION).getAmplifier() == 1
                        && client.player.getEffect(MobEffects.RESISTANCE) != null
                        && client.player.getEffect(MobEffects.RESISTANCE).getAmplifier() == 1);
                context.waitFor(client -> scoutConnection.bubbles.get() > 0);
                context.runOnClient(client -> expires.set(view().activeUntil()));
                dedicated.runOnServer(server -> {
                    if (!ally.hasEffect(MobEffects.RESISTANCE)) throw new AssertionError("Nearby unequipped ally missed the fountain");
                });
                dedicated.runCommand("execute as @p at @s run tp @s ~ ~-1 ~-6 0 3");
                context.waitTicks(8);
                context.takeScreenshot("runelegend-fountain-thin-bubble-layer");

                int beforeDimensionChange = dedicated.computeOnServer(server -> {
                    var nether = server.getLevel(Level.NETHER);
                    var position = ally.position();
                    if (!ally.teleportTo(nether, position.x(), position.y(), position.z(), Set.of(), 0, 0, false)) {
                        throw new AssertionError("Ally could not change dimension");
                    }
                    return scoutConnection.bubbles.get();
                });
                context.waitFor(client -> !client.player.hasEffect(MobEffects.REGENERATION)
                        && client.player.hasEffect(MobEffects.RESISTANCE));
                context.waitFor(client -> hasFlag(TokenEngine.COOLDOWN), 400);
                context.runOnClient(client -> {
                    if (view().cooldownUntil() - expires.get() != 200) throw new AssertionError("Unexpected fountain cooldown");
                });
                dedicated.runOnServer(server -> {
                    if (ally.hasEffect(MobEffects.RESISTANCE)) throw new AssertionError("Fountain refreshed the ally across dimensions");
                    if (scoutConnection.bubbles.get() != beforeDimensionChange) {
                        throw new AssertionError("Fountain particles reached the ally after changing dimension");
                    }
                });
            } finally {
                dedicated.runOnServer(server -> {
                    ally.level().players().remove(ally);
                    RuneLegendServer.find(server).leave(ally);
                });
            }
        }
    }

    private static TokenEngine.View view() {
        var current = ClientStateCache.INSTANCE.view();
        return current == null ? null : current.tokens().stream().filter(token -> token.id().equals(FOUNTAIN))
                .findFirst().orElse(null);
    }

    private static boolean hasFlag(int flag) {
        var token = view();
        return token != null && (token.flags() & flag) != 0;
    }

    private static final class ParticleConnection extends Connection {
        private final AtomicInteger bubbles = new AtomicInteger();

        ParticleConnection() { super(PacketFlow.SERVERBOUND); }

        @Override public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
            if (packet instanceof ClientboundLevelParticlesPacket particles
                    && particles.particle() == RuneLegendParticles.FOUNTAIN_BUBBLE) bubbles.incrementAndGet();
        }
    }
}
