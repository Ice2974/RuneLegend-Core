package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.game.GameController;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.player.PlayerTokenStore;
import com.runelegend.test.GuardGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.concurrent.atomic.AtomicLong;

public final class ExecutionEchoGameTests {
    private static final Identifier ECHO = RuneLegendMod.id("execution_echo");
    private static final Identifier ARROGANCE = RuneLegendMod.id("arrogance");

    @GameTest
    public void swingKillRecordsNothingWhileWaiting(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var attacker = GuardGameTests.fixture(test);
        var cow = test.spawnWithNoFreeWill(EntityTypes.COW, 2, 2, 2);
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        try {
            server.game().stopGame();
            test.assertValueEqual(server.players().equip(attacker.getUUID(), ECHO),
                    PlayerTokenService.Result.CHANGED, "Equip Execution Echo");
            attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3.0);
            zombie.setHealth(1);
            swing(attacker, zombie);
            test.assertTrue(!zombie.isAlive(), "The waiting swing killed the zombie");
            server.game().startGame();
            near(test, server.tokens().melee(attacker, cow, 1).bonusDamage(), 0,
                    "A kill made while waiting records nothing");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.players().clear(attacker.getUUID());
            server.leave(attacker);
        }
    }

    @GameTest
    public void swingKillRecordsTheSwingDamageAndAppendsItAsTrueDamage(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var attacker = GuardGameTests.fixture(test);
        var cow = test.spawnWithNoFreeWill(EntityTypes.COW, 2, 2, 2);
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        try {
            server.game().stopGame();
            test.assertValueEqual(server.players().equip(attacker.getUUID(), ECHO),
                    PlayerTokenService.Result.CHANGED, "Equip Execution Echo");
            server.game().startGame();
            attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3.0);
            zombie.setHealth(1);
            swing(attacker, zombie);
            test.assertTrue(!zombie.isAlive(), "The swing killed the zombie");
            near(test, server.tokens().melee(attacker, cow, 1).bonusDamage(), 3,
                    "The record is the swing's damage");
            near(test, melee(attacker, cow), 6, "A plain cow takes the swing plus the appended true damage");
            cow.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200));
            near(test, melee(attacker, cow), 4.8F, "Resistance reduces the appended damage as well");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.players().clear(attacker.getUUID());
            server.leave(attacker);
        }
    }

    @GameTest
    public void recordUsesTheDamageAfterTheTargetsArmor(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var attacker = GuardGameTests.fixture(test);
        var probe = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
        probe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
        probe.getAttribute(Attributes.ARMOR).setBaseValue(12);
        var target = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        target.getAttribute(Attributes.ARMOR).setBaseValue(12);
        var cow = test.spawnWithNoFreeWill(EntityTypes.COW, 2, 2, 4);
        try {
            server.game().stopGame();
            test.assertValueEqual(server.players().equip(attacker.getUUID(), ECHO),
                    PlayerTokenService.Result.CHANGED, "Equip Execution Echo");
            server.game().startGame();
            attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3.0);
            float armored = melee(attacker, probe);
            test.assertTrue(armored > 0 && armored < 3, "Armor reduces the swing before the record is taken");
            target.setHealth(1);
            swing(attacker, target);
            test.assertTrue(!target.isAlive(), "The armored swing killed the zombie");
            near(test, server.tokens().melee(attacker, cow, 1).bonusDamage(),
                    (float) Math.ceil(armored - 0.0001F), "The record rounds up the post-armor damage");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.players().clear(attacker.getUUID());
            server.leave(attacker);
        }
    }

    @GameTest
    public void recordIncludesOtherTokenBonusesOfTheSameSwing(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var attacker = GuardGameTests.fixture(test);
        var cow = test.spawnWithNoFreeWill(EntityTypes.COW, 2, 2, 2);
        var first = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        var second = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 4);
        try {
            server.game().stopGame();
            test.assertValueEqual(server.players().equip(attacker.getUUID(), ARROGANCE),
                    PlayerTokenService.Result.CHANGED, "Equip Arrogance");
            test.assertValueEqual(server.players().equip(attacker.getUUID(), ECHO),
                    PlayerTokenService.Result.CHANGED, "Equip Execution Echo");
            server.game().startGame();
            attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3.0);
            kill(attacker, first);
            near(test, server.tokens().melee(attacker, cow, 1).bonusDamage(), 1,
                    "A non-melee kill gives Arrogance its first step and leaves Execution Echo idle");
            second.setHealth(1);
            swing(attacker, second);
            test.assertTrue(!second.isAlive(), "The swing killed the second zombie");
            near(test, server.tokens().melee(attacker, cow, 1).bonusDamage(), 6,
                    "The record is 4 (3 plus Arrogance's 1 on that swing), and Arrogance now adds 2");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.players().clear(attacker.getUUID());
            server.leave(attacker);
        }
    }

    @GameTest
    public void recordDecaysOncePerIntervalThenCoolsDownAfterTheEffectEnds(GameTestHelper test) {
        var player = GuardGameTests.fixture(test);
        var clock = new AtomicLong(100);
        var local = local(player, TokenConfig.ExecutionEchoSettings.DEFAULT, clock);
        var cow = test.spawnWithNoFreeWill(EntityTypes.COW, 2, 2, 2);
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        try {
            local.players().equip(player.getUUID(), ECHO);
            local.game().startGame();
            zombie.setHealth(0);
            local.engine().dispatch(meleeKill(player, zombie, 10));
            near(test, bonus(local.engine(), player, cow), 10, "The record starts at the kill's damage");
            flags(test, local.engine(), player, TokenEngine.ACTIVE | TokenEngine.TRIGGERED, "Triggered and active");
            clock.set(119);
            near(test, bonus(local.engine(), player, cow), 10, "The first second keeps the full record");
            clock.set(120);
            near(test, bonus(local.engine(), player, cow), 8, "Each interval removes 2 HP");
            clock.set(140);
            near(test, bonus(local.engine(), player, cow), 6, "Two intervals leave 6 HP");
            clock.set(199);
            near(test, bonus(local.engine(), player, cow), 2, "The last second before the end still has 2 HP");
            clock.set(200);
            near(test, bonus(local.engine(), player, cow), 0, "The record reaches zero and ends");
            flags(test, local.engine(), player, TokenEngine.COOLDOWN, "The cooldown starts when the effect ends");
            clock.set(399);
            flags(test, local.engine(), player, TokenEngine.COOLDOWN, "Still cooling down");
            clock.set(400);
            flags(test, local.engine(), player, TokenEngine.READY, "Ready after the default 10 second cooldown");
            test.succeed();
        } finally {
            RuneLegendServer.find(player.level().getServer()).leave(player);
        }
    }

    @GameTest
    public void killsDuringActiveOrCooldownDoNotOverwriteTheRecord(GameTestHelper test) {
        var player = GuardGameTests.fixture(test);
        var clock = new AtomicLong(100);
        var local = local(player, TokenConfig.ExecutionEchoSettings.DEFAULT, clock);
        var cow = test.spawnWithNoFreeWill(EntityTypes.COW, 2, 2, 2);
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        try {
            local.players().equip(player.getUUID(), ECHO);
            local.game().startGame();
            zombie.setHealth(0);
            local.engine().dispatch(meleeKill(player, zombie, 10));
            clock.set(110);
            local.engine().dispatch(meleeKill(player, zombie, 30));
            near(test, bonus(local.engine(), player, cow), 10, "A kill during the effect is ignored");
            clock.set(250);
            local.engine().dispatch(meleeKill(player, zombie, 7));
            near(test, bonus(local.engine(), player, cow), 0, "A kill during the cooldown is ignored");
            clock.set(500);
            local.engine().dispatch(meleeKill(player, zombie, 7));
            near(test, bonus(local.engine(), player, cow), 7, "After the cooldown the next kill records again");
            test.succeed();
        } finally {
            RuneLegendServer.find(player.level().getServer()).leave(player);
        }
    }

    @GameTest
    public void recordIsCappedByTheConfiguredMaximum(GameTestHelper test) {
        var player = GuardGameTests.fixture(test);
        var clock = new AtomicLong(100);
        var local = local(player, TokenConfig.ExecutionEchoSettings.DEFAULT, clock);
        var cow = test.spawnWithNoFreeWill(EntityTypes.COW, 2, 2, 2);
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        try {
            local.players().equip(player.getUUID(), ECHO);
            local.game().startGame();
            zombie.setHealth(0);
            local.engine().dispatch(meleeKill(player, zombie, 50));
            near(test, bonus(local.engine(), player, cow), 10, "The default cap is 10 HP");
            clock.set(199);
            near(test, bonus(local.engine(), player, cow), 2, "Five intervals of 2 HP remove the capped record");
            clock.set(200);
            near(test, bonus(local.engine(), player, cow), 0, "The capped record ends after five seconds");
            test.succeed();
        } finally {
            RuneLegendServer.find(player.level().getServer()).leave(player);
        }
    }

    @GameTest
    public void configuredDecayAndCooldownAreHonored(GameTestHelper test) {
        var player = GuardGameTests.fixture(test);
        var clock = new AtomicLong(100);
        var local = local(player, new TokenConfig.ExecutionEchoSettings(5.0F, 2, 3, 12), clock);
        var cow = test.spawnWithNoFreeWill(EntityTypes.COW, 2, 2, 2);
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        try {
            local.players().equip(player.getUUID(), ECHO);
            local.game().startGame();
            zombie.setHealth(0);
            local.engine().dispatch(meleeKill(player, zombie, 11.2F));
            near(test, bonus(local.engine(), player, cow), 12, "Rounding up and the cap give 12 HP");
            clock.set(139);
            near(test, bonus(local.engine(), player, cow), 12, "The first two-second interval keeps 12 HP");
            clock.set(140);
            near(test, bonus(local.engine(), player, cow), 7, "Each two-second interval removes 5 HP");
            clock.set(180);
            near(test, bonus(local.engine(), player, cow), 2, "The third interval leaves 2 HP");
            clock.set(220);
            near(test, bonus(local.engine(), player, cow), 0, "The record ends after three intervals");
            flags(test, local.engine(), player, TokenEngine.COOLDOWN, "The configured 3 second cooldown starts");
            clock.set(280);
            flags(test, local.engine(), player, TokenEngine.READY, "Ready after the configured cooldown");
            test.succeed();
        } finally {
            RuneLegendServer.find(player.level().getServer()).leave(player);
        }
    }

    @GameTest
    public void clearingEffectsEndsTheRecord(GameTestHelper test) {
        var player = GuardGameTests.fixture(test);
        var clock = new AtomicLong(100);
        var local = local(player, TokenConfig.ExecutionEchoSettings.DEFAULT, clock);
        var cow = test.spawnWithNoFreeWill(EntityTypes.COW, 2, 2, 2);
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        try {
            local.players().equip(player.getUUID(), ECHO);
            local.game().startGame();
            zombie.setHealth(0);
            local.engine().dispatch(meleeKill(player, zombie, 10));
            near(test, bonus(local.engine(), player, cow), 10, "The record is active");
            local.engine().clearEffects(player.getUUID());
            near(test, bonus(local.engine(), player, cow), 0, "Death, disconnect and round end drop the record");
            flags(test, local.engine(), player, TokenEngine.READY, "The cooldown is cleared with the record");
            test.succeed();
        } finally {
            RuneLegendServer.find(player.level().getServer()).leave(player);
        }
    }

    @GameTest
    public void nonEnemiesAndEmptyRecordsAreIgnored(GameTestHelper test) {
        var player = GuardGameTests.fixture(test);
        var clock = new AtomicLong(100);
        var local = local(player, TokenConfig.ExecutionEchoSettings.DEFAULT, clock);
        var cow = test.spawnWithNoFreeWill(EntityTypes.COW, 2, 2, 2);
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        try {
            local.players().equip(player.getUUID(), ECHO);
            local.game().startGame();
            cow.setHealth(0);
            local.engine().dispatch(meleeKill(player, cow, 9));
            near(test, bonus(local.engine(), player, cow), 0, "A killed passive animal is not an enemy");
            zombie.setHealth(0);
            local.engine().dispatch(meleeKill(player, zombie, 0));
            near(test, bonus(local.engine(), player, cow), 0, "A zero-damage kill records nothing");
            flags(test, local.engine(), player, TokenEngine.READY, "Nothing was recorded, so the token stays ready");
            test.succeed();
        } finally {
            RuneLegendServer.find(player.level().getServer()).leave(player);
        }
    }

    private static TokenRegistry registry(TokenConfig.ExecutionEchoSettings settings) {
        var defaults = TokenConfig.DEFAULT;
        return TokenRegistry.builtIn(new TokenConfig(defaults.soulSiphon(), defaults.echo(),
                defaults.immediateCounter(), defaults.venomousTip(), defaults.poisonedArrow(),
                defaults.asuraForm(), defaults.resonanceHealing(), defaults.vernalTide(), defaults.arrogance(),
                settings, defaults.retribution(), defaults.substitute(), defaults.fullBlessing(), defaults.fountainOfLife()));
    }

    private record Local(GameController game, PlayerTokenService players, TokenEngine engine) {}

    private static Local local(ServerPlayer player, TokenConfig.ExecutionEchoSettings settings, AtomicLong clock) {
        var registry = registry(settings);
        var game = new GameController(() -> {}, (before, after) -> {});
        var players = new PlayerTokenService(game, PlayerTokenStore.inMemory(), registry);
        var engine = TokenTestEngine.forPlayers(player).create(game, players, registry, clock::get);
        return new Local(game, players, engine);
    }

    private static TokenEvent meleeKill(ServerPlayer player, LivingEntity victim, float damage) {
        return new TokenEvent(TokenEvent.Kind.MELEE, player, victim, player.level(), null, damage, damage, 0, damage);
    }

    private static float bonus(TokenEngine engine, ServerPlayer player, LivingEntity target) {
        return engine.melee(player, target, 1).bonusDamage();
    }

    private static void kill(ServerPlayer attacker, LivingEntity victim) {
        victim.hurtServer(attacker.level(), attacker.damageSources().playerAttack(attacker), 100);
    }

    private static float melee(ServerPlayer attacker, LivingEntity target) {
        target.setHealth(target.getMaxHealth());
        target.damageCooldownTime = 0;
        swing(attacker, target);
        return target.getMaxHealth() - target.getHealth();
    }

    private static void swing(ServerPlayer attacker, LivingEntity target) {
        attacker.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024);
        attacker.resetAttackStrengthTicker();
        attacker.attack(target);
    }

    private static void near(GameTestHelper test, float actual, float expected, String message) {
        test.assertTrue(Math.abs(actual - expected) < 0.001F, message + ": expected " + expected + " but was " + actual);
    }

    private static void flags(GameTestHelper test, TokenEngine engine, ServerPlayer player, int expected, String message) {
        test.assertValueEqual(engine.views(player).getFirst().flags(), expected, message);
    }
}
