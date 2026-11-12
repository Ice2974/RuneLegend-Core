package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.game.GameController;
import com.runelegend.player.PlayerTokenSavedData;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.test.GuardGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class TokenEffectsGameTests {
    @GameTest
    public void directAndMeleeBonusDamageBypassArmorAndProtectionOnly(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var attacker = GuardGameTests.fixture(test);
        var defender = GuardGameTests.fixture(test);
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        attacker.setPos(test.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 2.5)));
        defender.setPos(test.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 4.5)));
        var targets = List.of(zombie, defender);
        try {
            server.game().stopGame();
            test.assertValueEqual(server.players().equip(attacker.getUUID(), RuneLegendMod.id("immediate_counter")),
                    PlayerTokenService.Result.CHANGED, "Equip Immediate Counter");
            test.assertValueEqual(server.players().equip(attacker.getUUID(), RuneLegendMod.id("sharpened_tip")),
                    PlayerTokenService.Result.CHANGED, "Equip Sharpened Tip");
            float[] normal = new float[targets.size()];
            for (int i = 0; i < targets.size(); i++) {
                var target = targets.get(i);
                target.getAttribute(Attributes.ARMOR).setBaseValue(12);
                var armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
                armor.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
                target.setItemSlot(EquipmentSlot.CHEST, armor);
                float direct = hit(target, () -> target.hurtServer(level, TokenEngine.directDamageSource(level, attacker), 6));
                near(test, direct, 6F, "The direct source ignores both armor and Protection");
                float ordinary = hit(target, () -> target.hurtServer(level, level.damageSources().magic(), 6));
                test.assertTrue(ordinary > 0 && ordinary < 6, "Vanilla magic still uses Protection");
                float poison = hit(target, () -> MobEffects.POISON.value().applyEffectTick(level, target, 0));
                test.assertTrue(poison > 0 && poison < 1, "Vanilla poison still uses Protection");
                target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100));
                near(test, hit(target, () -> target.hurtServer(level, TokenEngine.directDamageSource(level, attacker), 6)),
                        4.8F, "Resistance still reduces direct token damage");
                normal[i] = hit(target, () -> {
                    attacker.resetAttackStrengthTicker();
                    attacker.attack(target);
                });
                test.assertTrue(normal[i] > 0, "The base melee hit settles");
            }

            server.game().startGame();
            server.tokens().dispatch(new TokenEvent(TokenEvent.Kind.GUARD_SUCCESS, attacker, attacker, level, null, 6, 3, 3));
            long seed = 0;
            while (RandomSource.create(seed).nextDouble() >= 0.50) seed++;
            for (int i = 0; i < targets.size(); i++) {
                var target = targets.get(i);
                attacker.getRandom().setSeed(seed);
                float boosted = hit(target, () -> {
                    attacker.resetAttackStrengthTicker();
                    attacker.attack(target);
                });
                near(test, boosted, normal[i] + 4F, "Base damage is unchanged; only the combined 5 HP bonus bypasses equipment");
            }
            test.succeed();
        } finally {
            server.game().stopGame();
            server.players().clear(attacker.getUUID());
            server.leave(attacker);
            server.leave(defender);
        }
    }

    private static float hit(LivingEntity target, Runnable damage) {
        target.setHealth(target.getMaxHealth());
        target.damageCooldownTime = 0;
        float before = target.getHealth();
        damage.run();
        return before - target.getHealth();
    }

    private static void near(GameTestHelper test, float actual, float expected, String message) {
        test.assertTrue(Math.abs(actual - expected) < 0.001F, message + ": expected " + expected + ", got " + actual);
    }

    @GameTest
    public void configuredTokenSettingsDriveEveryBuiltInEffect(GameTestHelper test) {
        var minecraft = test.getLevel().getServer();
        var server = RuneLegendServer.find(minecraft);
        var player = GuardGameTests.fixture(test);
        var corpse = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
        var nearSpider = test.spawnWithNoFreeWill(EntityTypes.SPIDER, 2, 2, 3);
        var farSpider = test.spawnWithNoFreeWill(EntityTypes.SPIDER, 2, 2, 4);
        var venomTarget = test.spawnWithNoFreeWill(EntityTypes.PILLAGER, 2, 2, 5);
        var clock = new AtomicLong(100);
        var config = new TokenConfig(
                new TokenConfig.SharpenedTipSettings(0.25F, 4.0F),
                new TokenConfig.SoulSiphonSettings(0.75F, 3.0F),
                new TokenConfig.EchoSettings(1.0F, 1.5, 4.0F, 40, 20),
                new TokenConfig.ImmediateCounterSettings(5.0F, 20, 30),
                new TokenConfig.VenomousTipSettings(0.25F, 3),
                new TokenConfig.PoisonedArrowSettings(0.25F, 4),
                TokenConfig.AsuraFormSettings.DEFAULT);
        var registry = TokenRegistry.builtIn(config);
        var game = new GameController(() -> {}, (previous, current) -> {});
        var players = new PlayerTokenService(game, PlayerTokenSavedData.load(minecraft), registry);
        var tokens = new TokenEngine(game, players, registry, clock::get, ignored -> 0.10);
        var kill = new TokenEvent(TokenEvent.Kind.KILL, player, corpse, test.getLevel(),
                player.damageSources().playerAttack(player), 20, 20, 0);
        try {
            game.stopGame();
            test.assertValueEqual(players.equip(player.getUUID(), RuneLegendMod.id("sharpened_tip")),
                    PlayerTokenService.Result.CHANGED, "Equip configured Sharpened Tip");
            test.assertValueEqual(players.equip(player.getUUID(), RuneLegendMod.id("soul_siphon")),
                    PlayerTokenService.Result.CHANGED, "Equip configured Soul Siphon");
            game.startGame();
            test.assertValueEqual(tokens.melee(player, 6).bonusDamage(), 4.0F,
                    "Configured probability and damage drive Sharpened Tip");
            float maximumHealth = player.getMaxHealth();
            tokens.dispatch(kill);
            test.assertValueEqual(player.getMaxHealth(), maximumHealth + 3.0F,
                    "Configured probability and health bonus drive Soul Siphon");

            game.stopGame();
            players.clear(player.getUUID());
            test.assertValueEqual(players.equip(player.getUUID(), RuneLegendMod.id("echo")),
                    PlayerTokenService.Result.CHANGED, "Equip configured Echo");
            game.startGame();
            tokens.dispatch(kill);
            float nearHealth = nearSpider.getHealth();
            float farHealth = farSpider.getHealth();
            clock.set(120);
            nearSpider.damageCooldownTime = 0;
            tokens.tick(List.of(player));
            test.assertValueEqual(nearSpider.getHealth(), nearHealth - 4.0F, "Configured Echo damages inside its radius");
            test.assertValueEqual(farSpider.getHealth(), farHealth, "Configured Echo excludes targets outside its radius");
            clock.set(140);
            nearSpider.damageCooldownTime = 0;
            tokens.tick(List.of(player));
            test.assertValueEqual(nearSpider.getHealth(), nearHealth - 8.0F, "Configured interval produces two pulses");
            clock.set(160);
            nearSpider.damageCooldownTime = 0;
            tokens.tick(List.of(player));
            test.assertValueEqual(nearSpider.getHealth(), nearHealth - 8.0F, "Configured duration stops after two pulses");

            game.stopGame();
            players.clear(player.getUUID());
            test.assertValueEqual(players.equip(player.getUUID(), RuneLegendMod.id("immediate_counter")),
                    PlayerTokenService.Result.CHANGED, "Equip configured Immediate Counter");
            game.startGame();
            clock.set(180);
            tokens.dispatch(new TokenEvent(TokenEvent.Kind.GUARD_SUCCESS, player, player, test.getLevel(), null, 6, 3, 3));
            clock.set(190);
            test.assertValueEqual(tokens.melee(player, 6).bonusDamage(), 5.0F, "Configured damage drives Immediate Counter");
            test.assertTrue((tokens.views(player).getFirst().flags() & TokenEngine.ACTIVE) != 0,
                    "Configured duration drives Immediate Counter");
            clock.set(200);
            var view = tokens.views(player).getFirst();
            test.assertTrue((view.flags() & TokenEngine.COOLDOWN) != 0 && view.cooldownUntil() == 230,
                    "Configured cooldown starts when the configured effect ends");

            game.stopGame();
            players.clear(player.getUUID());
            test.assertValueEqual(players.equip(player.getUUID(), RuneLegendMod.id("venomous_tip")),
                    PlayerTokenService.Result.CHANGED, "Equip configured Venomous Tip");
            game.startGame();
            tokens.dispatch(new TokenEvent(TokenEvent.Kind.MELEE, player, venomTarget, test.getLevel(),
                    player.damageSources().playerAttack(player), 6, 6, 0));
            var poison = venomTarget.getEffect(MobEffects.POISON);
            test.assertTrue(poison != null, "Configured Venomous Tip applies Poison");
            test.assertValueEqual(poison.getAmplifier(), 1, "Venomous Tip always applies Poison II");
            test.assertValueEqual(poison.getDuration(), 60, "Configured three seconds become sixty ticks");

            game.stopGame();
            players.clear(player.getUUID());
            test.assertValueEqual(players.equip(player.getUUID(), RuneLegendMod.id("poisoned_arrow")),
                    PlayerTokenService.Result.CHANGED, "Equip configured Poisoned Arrow");
            game.startGame();
            venomTarget.removeEffect(MobEffects.POISON);
            var arrow = test.spawn(EntityTypes.ARROW, 2, 3, 3);
            tokens.dispatch(new TokenEvent(TokenEvent.Kind.ARROW, player, venomTarget, test.getLevel(),
                    test.getLevel().damageSources().arrow(arrow, player), 6, 6, 0));
            var arrowPoison = venomTarget.getEffect(MobEffects.POISON);
            test.assertTrue(arrowPoison != null, "Configured Poisoned Arrow applies Poison to an arrow hit");
            test.assertValueEqual(arrowPoison.getAmplifier(), 1, "Poisoned Arrow always applies Poison II");
            test.assertValueEqual(arrowPoison.getDuration(), 80, "Configured four seconds become eighty ticks");
            test.succeed();
        } finally {
            tokens.clearRound();
            game.stopGame();
            players.clear(player.getUUID());
            server.leave(player);
        }
    }

    @GameTest
    public void meleeAttackAppliesDefaultPoisonOnlyToEnemies(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var attacker = GuardGameTests.fixture(test);
        var enemy = test.spawnWithNoFreeWill(EntityTypes.PILLAGER, 2, 2, 3);
        var ally = GuardGameTests.fixture(test);
        attacker.setPos(test.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 2.5)));
        ally.setPos(test.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 4.5)));
        try {
            server.game().stopGame();
            test.assertValueEqual(server.players().equip(attacker.getUUID(), RuneLegendMod.id("venomous_tip")),
                    PlayerTokenService.Result.CHANGED, "Equip Venomous Tip");
            server.game().startGame();

            long procSeed = 0;
            while (RandomSource.create(procSeed).nextDouble() >= 0.50) procSeed++;
            attacker.getRandom().setSeed(procSeed);
            attacker.resetAttackStrengthTicker();
            attacker.attack(enemy);
            var poison = enemy.getEffect(MobEffects.POISON);
            test.assertTrue(poison != null, "A successful roll applies Poison to the melee target");
            test.assertValueEqual(poison.getAmplifier(), 1, "Default poison level 2 is amplifier 1");
            test.assertValueEqual(poison.getDuration(), 120, "Default six seconds is 120 ticks");

            enemy.removeEffect(MobEffects.POISON);
            enemy.setHealth(enemy.getMaxHealth());
            enemy.damageCooldownTime = 0;
            long missSeed = 0;
            while (RandomSource.create(missSeed).nextDouble() < 0.50) missSeed++;
            attacker.getRandom().setSeed(missSeed);
            attacker.resetAttackStrengthTicker();
            attacker.attack(enemy);
            test.assertTrue(enemy.getEffect(MobEffects.POISON) == null, "A failed roll does not apply Poison");

            attacker.getRandom().setSeed(procSeed);
            attacker.resetAttackStrengthTicker();
            attacker.attack(ally);
            test.assertTrue(ally.getEffect(MobEffects.POISON) == null,
                    "A player target is not a token enemy and is not poisoned");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.players().clear(attacker.getUUID());
            server.leave(attacker);
            server.leave(ally);
        }
    }

    @GameTest
    public void arrowHitAppliesDefaultPoisonOnlyToEnemies(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var attacker = GuardGameTests.fixture(test);
        var enemy = test.spawnWithNoFreeWill(EntityTypes.PILLAGER, 2, 2, 3);
        var ally = GuardGameTests.fixture(test);
        var arrow = test.spawn(EntityTypes.ARROW, 2, 3, 3);
        try {
            server.game().stopGame();
            test.assertValueEqual(server.players().equip(attacker.getUUID(), RuneLegendMod.id("poisoned_arrow")),
                    PlayerTokenService.Result.CHANGED, "Equip Poisoned Arrow");
            server.game().startGame();

            long procSeed = 0;
            while (RandomSource.create(procSeed).nextDouble() >= 0.50) procSeed++;
            attacker.getRandom().setSeed(procSeed);
            enemy.damageCooldownTime = 0;
            enemy.hurtServer(level, level.damageSources().arrow(arrow, attacker), 2);
            var poison = enemy.getEffect(MobEffects.POISON);
            test.assertTrue(poison != null, "A successful arrow hit applies Poison");
            test.assertValueEqual(poison.getAmplifier(), 1, "Default poison level 2 is amplifier 1");
            test.assertValueEqual(poison.getDuration(), 240, "Default twelve seconds is 240 ticks");

            enemy.removeEffect(MobEffects.POISON);
            enemy.setHealth(enemy.getMaxHealth());
            enemy.damageCooldownTime = 0;
            long missSeed = 0;
            while (RandomSource.create(missSeed).nextDouble() < 0.50) missSeed++;
            attacker.getRandom().setSeed(missSeed);
            enemy.hurtServer(level, level.damageSources().arrow(arrow, attacker), 2);
            test.assertTrue(enemy.getEffect(MobEffects.POISON) == null, "A failed roll does not apply Poison");

            ally.setHealth(ally.getMaxHealth());
            ally.damageCooldownTime = 0;
            attacker.getRandom().setSeed(procSeed);
            ally.hurtServer(level, level.damageSources().arrow(arrow, attacker), 2);
            test.assertTrue(ally.getEffect(MobEffects.POISON) == null,
                    "A player target is not a token enemy and is not poisoned");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.players().clear(attacker.getUUID());
            server.leave(attacker);
            server.leave(ally);
        }
    }

    @GameTest
    public void onlyAnOptedInTokenPublishesPreparation(GameTestHelper test) {
        var minecraft = test.getLevel().getServer();
        var server = RuneLegendServer.find(minecraft);
        var player = GuardGameTests.fixture(test);
        var id = RuneLegendMod.id("preparation_fixture");
        var registry = new TokenRegistry.Builder().register(new TokenDefinition(id, Set.of(TokenEvent.Kind.KILL),
                new TokenEffectHandler() {
                    @Override public boolean preparing(net.minecraft.server.level.ServerPlayer player,
                                                       TokenRuntimeState state, long tick) { return state.charge == 1; }
                    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
                        state.charge++;
                    }
                })).build();
        var game = new GameController(() -> {}, (previous, current) -> {});
        var players = new PlayerTokenService(game, PlayerTokenSavedData.load(minecraft), registry);
        var tokens = new TokenEngine(game, players, registry, () -> 100);
        try {
            test.assertValueEqual(players.equip(player.getUUID(), id), PlayerTokenService.Result.CHANGED, "Equip fixture");
            game.startGame();
            var kill = new TokenEvent(TokenEvent.Kind.KILL, player, player, test.getLevel(), null, 0, 0, 0);
            test.assertValueEqual(tokens.views(player).getFirst().flags(), TokenEngine.READY, "Readiness is not preparation");
            tokens.dispatch(kill);
            test.assertValueEqual(tokens.views(player).getFirst().flags(), TokenEngine.READY | TokenEngine.PREPARING,
                    "The fixture enters preparation after its first kill");
            tokens.dispatch(kill);
            test.assertValueEqual(tokens.views(player).getFirst().flags(), TokenEngine.READY, "Preparation ends independently");
            for (var definition : server.registry().all()) {
                test.assertTrue(!definition.effect().preparing(player, new TokenRuntimeState(), 100),
                        definition.id() + " does not opt into preparation");
            }
            test.succeed();
        } finally {
            game.stopGame();
            players.clear(player.getUUID());
            server.leave(player);
        }
    }

    @GameTest
    public void lethalArrowKillStillReachesTokens(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var player = GuardGameTests.fixture(test);
        var arrowTarget = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 4);
        var arrow = test.spawn(EntityTypes.ARROW, 2, 3, 3);
        player.setPos(test.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 2.5)));
        try {
            server.game().stopGame();
            test.assertValueEqual(server.players().equip(player.getUUID(), RuneLegendMod.id("soul_siphon")),
                    PlayerTokenService.Result.CHANGED, "Equip Soul Siphon");
            server.game().startGame();
            long seed = 0;
            while (RandomSource.create(seed).nextDouble() >= 0.05) seed++;
            player.getRandom().setSeed(seed);
            float maxHealth = player.getMaxHealth();
            arrowTarget.hurtServer(level, level.damageSources().arrow(arrow, player), 100);
            test.assertTrue(!arrowTarget.isAlive(), "Arrow damage was lethal");
            test.assertValueEqual(player.getMaxHealth(), maxHealth + 2, "The lethal arrow is credited to its shooter");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.players().clear(player.getUUID());
            server.leave(player);
        }
    }

    @GameTest
    public void effectsUseIndependentPlayersAndOwnedTasks(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var first = GuardGameTests.fixture(test);
        var second = GuardGameTests.fixture(test);
        var corpse = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
        var spider = test.spawnWithNoFreeWill(EntityTypes.SPIDER, 2, 2, 3);
        var clock = new AtomicLong(100);
        spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        spider.setHealth(40.0F);
        var tokens = new TokenEngine(server.game(), server.players(), server.registry(), clock::get, player -> 0.01);
        float firstMax = first.getMaxHealth();
        float firstHealth = first.getHealth();
        float spiderHealth = spider.getHealth();
        try {
            server.game().stopGame();
            for (var player : List.of(first, second)) {
                for (String id : List.of("sharpened_tip", "soul_siphon", "echo")) {
                    test.assertValueEqual(server.players().equip(player.getUUID(), RuneLegendMod.id(id)),
                            PlayerTokenService.Result.CHANGED, "Each player can equip the same three tokens");
                }
            }
            var kill = new TokenEvent(TokenEvent.Kind.KILL, first, corpse, level, first.damageSources().playerAttack(first), 25, 25, 0);
            tokens.dispatch(kill);
            test.assertValueEqual(first.getMaxHealth(), firstMax, "WAITING disables token effects");
            server.game().startGame();
            var boost = tokens.melee(first, 6);
            test.assertValueEqual(boost.bonusDamage(), 2F, "Sharpened Tip contributes only the separate bonus");
            tokens.confirmBoost(first, boost.applied());
            tokens.dispatch(kill);
            tokens.dispatch(kill);
            test.assertValueEqual(first.getMaxHealth(), firstMax + 2, "Soul Siphon refreshes without stacking");
            test.assertValueEqual(first.getHealth(), firstHealth, "Soul Siphon does not heal");
            test.assertValueEqual(second.getMaxHealth(), firstMax, "An identical token does not buff the other player");
            test.assertTrue(tokens.views(second).stream().noneMatch(view -> (view.flags() & (TokenEngine.ACTIVE | TokenEngine.TRIGGERED)) != 0),
                    "Token HUD state is separate for each player");
            for (int elapsed = 20; elapsed <= 160; elapsed += 20) {
                spider.damageCooldownTime = 0;
                clock.set(100 + elapsed);
                tokens.tick(List.of(first, second));
                test.assertValueEqual(spider.getHealth(), spiderHealth - 3F * (elapsed / 20), "Echo applies one pulse every twenty ticks");
            }
            tokens.clearEffects(first.getUUID());
            test.assertValueEqual(first.getMaxHealth(), firstMax + 2, "Runtime cleanup keeps the saved max-health modifier");
            test.succeed();
        } finally {
            tokens.clearRound();
            server.game().stopGame();
            for (var player : List.of(first, second)) {
                server.players().clear(player.getUUID());
                server.leave(player);
            }
        }
    }
}
