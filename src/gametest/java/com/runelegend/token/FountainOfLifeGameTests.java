package com.runelegend.token;

import com.google.gson.JsonParser;
import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.game.GameController;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.player.PlayerTokenStore;
import com.runelegend.test.PlayerStateFixtures;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class FountainOfLifeGameTests {
    @GameTest
    public void usesAnAnchoredCylinderAndRestartsAfterCooldown(GameTestHelper test) {
        try (var fixture = new Fixture(test, "{}")) {
            var caster = fixture.player(Vec3.ZERO);
            var ally = fixture.player(new Vec3(3, 1, 0));
            var tall = fixture.player(new Vec3(0, 1.01, 0));
            var far = fixture.player(new Vec3(3.01, 0, 0));
            var spectator = fixture.player(Vec3.ZERO);
            var remote = fixture.player(Vec3.ZERO);
            fixture.start(caster);
            spectator.setGameMode(GameType.SPECTATOR);
            var nether = caster.level().getServer().getLevel(Level.NETHER);
            var center = caster.position();
            test.assertTrue(remote.teleportTo(nether, center.x(), center.y(), center.z(), Set.of(), 0, 0, false), "Enter Nether");
            caster.setHealth(8.01F);
            fixture.tick(100);
            test.assertValueEqual(fixture.view(caster).flags(), TokenEngine.READY, "Above 40 percent");
            caster.setHealth(8F);
            fixture.tick(101);
            test.assertValueEqual(fixture.view(caster).flags(), TokenEngine.TRIGGERED | TokenEngine.ACTIVE, "Exactly 40 percent");
            amplifier(test, caster, MobEffects.REGENERATION, 1);
            amplifier(test, ally, MobEffects.REGENERATION, 1);
            amplifier(test, ally, MobEffects.RESISTANCE, 1);
            test.assertFalse(tall.hasEffect(MobEffects.RESISTANCE), "Above the height limit");
            test.assertFalse(far.hasEffect(MobEffects.RESISTANCE), "Beyond the radius");
            test.assertFalse(spectator.hasEffect(MobEffects.RESISTANCE), "Spectators do not receive the effect");
            test.assertFalse(remote.hasEffect(MobEffects.RESISTANCE), "No effect across dimensions");

            var nextCenter = center.add(10, 0, 0);
            caster.setPos(nextCenter);
            fixture.tick(102);
            test.assertValueEqual(caster.getEffect(MobEffects.REGENERATION).getDuration(), 20, "Leaving starts the regeneration tail");
            fixture.tick(121);
            test.assertTrue(caster.hasEffect(MobEffects.REGENERATION), "Regeneration lasts until its deadline");
            fixture.tick(122);
            test.assertFalse(caster.hasEffect(MobEffects.REGENERATION), "Regeneration ends after 20 ticks");
            fixture.tick(201);
            test.assertTrue(caster.hasEffect(MobEffects.RESISTANCE), "Resistance lasts until its deadline");
            fixture.tick(202);
            test.assertFalse(caster.hasEffect(MobEffects.RESISTANCE), "Resistance ends after 100 ticks");
            fixture.tick(300);
            amplifier(test, ally, MobEffects.REGENERATION, 1);
            fixture.tick(301);
            test.assertValueEqual(fixture.view(caster).flags(), TokenEngine.COOLDOWN, "Cooldown starts at expiration");
            test.assertValueEqual(fixture.engine.cooldownTicks(caster, Fixture.ID), 200, "Ten seconds of cooldown");
            test.assertValueEqual(ally.getEffect(MobEffects.REGENERATION).getDuration(), 20, "Natural expiry counts as leaving");
            test.assertValueEqual(ally.getEffect(MobEffects.RESISTANCE).getDuration(), 100, "Natural expiry grants resistance tail");
            fixture.tick(500);
            test.assertFalse(caster.hasEffect(MobEffects.REGENERATION), "No early retrigger");
            fixture.tick(501);
            amplifier(test, caster, MobEffects.REGENERATION, 1);
            test.assertFalse(ally.hasEffect(MobEffects.REGENERATION), "New fountain uses the new cast position");
            test.succeed();
        }
    }

    @GameTest
    public void overlapReentryAndClearDoNotChangeOrdinaryEffects(GameTestHelper test) {
        try (var fixture = new Fixture(test, """
                {"runelegend:fountain_of_life":{"regeneration_level":3,"resistance_level":4,
                 "duration_seconds":3,"regeneration_linger_seconds":2,"resistance_linger_seconds":6,"cooldown_seconds":1}}
                """)) {
            var first = fixture.player(Vec3.ZERO);
            var second = fixture.player(new Vec3(3, 0, 0));
            var ally = fixture.player(new Vec3(1.5, 0, 0));
            var copy = fixture.player(new Vec3(8, 0, 0));
            var reloaded = fixture.player(new Vec3(8, 0, 0));
            var ordinary = new MobEffectInstance(MobEffects.REGENERATION, 400, 0);
            ally.addEffect(ordinary);
            fixture.start(first, second);
            first.setHealth(7);
            second.setHealth(7);
            fixture.tick(100);
            amplifier(test, ally, MobEffects.REGENERATION, 2);
            amplifier(test, ally, MobEffects.RESISTANCE, 3);
            var position = ally.position();
            ally.setPos(position.add(0, 2, 0));
            fixture.tick(110);
            test.assertValueEqual(ally.getEffect(MobEffects.REGENERATION).getDuration(), 40, "Configured regeneration tail");
            test.assertValueEqual(ally.getEffect(MobEffects.RESISTANCE).getDuration(), 120, "Configured resistance tail");
            ally.setPos(position);
            fixture.tick(120);
            test.assertValueEqual(ally.getEffect(MobEffects.REGENERATION).getDuration(), 80, "Reentry keeps regeneration past the old tail");
            test.assertValueEqual(fixture.view(first).activeUntil(), 160L, "Configured fountain lifetime");

            var registryAccess = ally.level().registryAccess();
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registryAccess);
            ally.saveWithoutId(output);
            reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess, output.buildResult()));
            copy.restoreFrom(ally, true);
            for (var player : List.of(reloaded, copy)) {
                amplifier(test, player, MobEffects.REGENERATION, 0);
                test.assertFalse(player.hasEffect(MobEffects.RESISTANCE), "Fountain contribution is neither saved nor copied");
            }

            fixture.engine.clearEffects(first.getUUID());
            amplifier(test, ally, MobEffects.RESISTANCE, 3);
            ally.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 70, 4));
            amplifier(test, ally, MobEffects.RESISTANCE, 4);
            ally.setHealth(12);
            ally.tickCount = 24;
            ally.getEffect(MobEffects.REGENERATION).tickServer(ally.level(), ally, () -> {});
            test.assertValueEqual(ally.getHealth(), 13F, "Vanilla Regeneration III heals once with overlapping contributions");
            fixture.engine.clearEffects(second.getUUID());
            test.assertTrue(ally.getEffect(MobEffects.REGENERATION) == ordinary, "Restore the ordinary effect instance");
            test.assertValueEqual(ordinary.getDuration(), 399, "The weaker ordinary potion aged while covered");
            amplifier(test, ally, MobEffects.RESISTANCE, 4);
            fixture.engine.clearRound();
            amplifier(test, ally, MobEffects.RESISTANCE, 4);
            test.succeed();
        }
    }

    @GameTest
    public void grantsVanillaResistanceAndClearsRecipientAndRound(GameTestHelper test) {
        try (var fixture = new Fixture(test, "{}")) {
            var caster = fixture.player(Vec3.ZERO);
            var ally = fixture.player(new Vec3(1, 0, 0));
            fixture.start(caster);
            caster.setHealth(6);
            ally.setHealth(14);
            fixture.tick(100);
            test.assertTrue(ally.hurtServer(ally.level(), ally.damageSources().magic(), 5), "Incoming damage is accepted");
            test.assertValueEqual(ally.getHealth(), 11F, "Vanilla Resistance II reduces damage by 40 percent");
            ally.setGameMode(GameType.SPECTATOR);
            fixture.engine.clearEffects(ally.getUUID());
            test.assertFalse(ally.hasEffect(MobEffects.RESISTANCE), "Recipient cleanup clears the incoming contribution");
            test.assertTrue(caster.hasEffect(MobEffects.RESISTANCE), "The source is still active");
            fixture.engine.clearRound();
            test.assertFalse(caster.hasEffect(MobEffects.RESISTANCE), "Round cleanup revokes the active grant");
            caster.setHealth(20);
            fixture.tick(500);
            test.assertValueEqual(fixture.view(caster).flags(), TokenEngine.READY, "No old active or cooldown state after clearing");
            test.succeed();
        }
    }

    private static void amplifier(GameTestHelper test, ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        var instance = player.getEffect(effect);
        test.assertTrue(instance != null && instance.getAmplifier() == amplifier, "Expected amplifier " + amplifier + " for " + effect);
    }

    private static final class Fixture implements AutoCloseable {
        static final net.minecraft.resources.Identifier ID = RuneLegendMod.id("fountain_of_life");
        private final GameTestHelper test;
        private final GameController game = new GameController(() -> {}, (before, after) -> {});
        private final AtomicLong clock = new AtomicLong(100);
        private final List<ServerPlayer> online = new ArrayList<>();
        private final TokenRegistry registry;
        private final PlayerTokenService players;
        private TokenEngine engine;

        Fixture(GameTestHelper test, String config) {
            this.test = test;
            this.registry = TokenRegistry.builtIn(TokenConfig.fromJson(JsonParser.parseString(config).getAsJsonObject()));
            this.players = new PlayerTokenService(game, PlayerTokenStore.inMemory(), registry);
        }

        ServerPlayer player(Vec3 offset) {
            var level = test.getLevel();
            var player = PlayerStateFixtures.join(level.getServer(), level, UUID.randomUUID());
            player.setGameMode(GameType.ADVENTURE);
            player.setPos(test.absoluteVec(new Vec3(3.5, 2, 3.5)).add(offset));
            online.add(player);
            return player;
        }

        void start(ServerPlayer... owners) {
            engine = TokenTestEngine.forPlayers(online.toArray(ServerPlayer[]::new)).create(game, players, registry, clock::get);
            for (var owner : owners) {
                test.assertValueEqual(players.equip(owner.getUUID(), ID), PlayerTokenService.Result.CHANGED, "Equip the fountain");
                owner.setHealth(6);
            }
            tick(99);
            for (var owner : owners) {
                test.assertFalse(owner.hasEffect(MobEffects.REGENERATION), "Waiting suppresses the fountain");
                owner.setHealth(20);
            }
            game.startGame();
        }

        void tick(long tick) {
            clock.set(tick);
            engine.tick(online);
        }

        TokenEngine.View view(ServerPlayer owner) { return engine.views(owner).getFirst(); }

        @Override public void close() {
            if (engine != null) engine.clearRound();
            var server = RuneLegendServer.find(test.getLevel().getServer());
            online.forEach(server::leave);
        }
    }
}
