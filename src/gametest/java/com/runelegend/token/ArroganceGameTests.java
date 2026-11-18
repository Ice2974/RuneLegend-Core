package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.game.GameController;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.player.PlayerTokenStore;
import com.runelegend.test.GuardGameTests;
import com.runelegend.token.effect.ArroganceEffect;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ArroganceGameTests {
    private static final net.minecraft.resources.Identifier ARROGANCE = RuneLegendMod.id("arrogance");

    @GameTest
    public void realMeleeKeepsDamageAndKillAttributionSeparate(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var attacker = GuardGameTests.fixture(test);
        var other = GuardGameTests.fixture(test);
        var playerTarget = GuardGameTests.fixture(test);
        var cow = test.spawnWithNoFreeWill(EntityTypes.COW, 2, 2, 2);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60);
        attacker.setPos(cow.position().add(0, 0, 1));
        playerTarget.setPos(attacker.position().add(1, 0, 0));
        try {
            server.game().stopGame();
            for (var player : List.of(attacker, other)) {
                test.assertValueEqual(server.players().equip(player.getUUID(), ARROGANCE),
                        PlayerTokenService.Result.CHANGED, "Equip Arrogance");
            }
            var engine = server.tokens();
            kill(attacker, zombie(test));
            server.game().startGame();
            flags(test, engine, attacker, TokenEngine.READY | TokenEngine.PREPARING);
            float basePvP = melee(attacker, playerTarget);
            float plain = melee(attacker, cow);
            cow.getAttribute(Attributes.ARMOR).setBaseValue(12);
            float armored = melee(attacker, cow);
            var chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
            chestplate.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.PROTECTION), 4);
            cow.setItemSlot(EquipmentSlot.CHEST, chestplate);
            float protectedHit = melee(attacker, cow);
            cow.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200));
            float resistant = melee(attacker, cow);
            test.assertTrue(plain > armored && armored > protectedHit && protectedHit > resistant && resistant > 0,
                    "The base hit respects armor, Protection and Resistance");

            attacker.setHealth(12);
            attacker.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200));
            kill(attacker, zombie(test));
            flags(test, engine, attacker, 0);
            attacker.setHealth(13);
            kill(attacker, test.spawnWithNoFreeWill(EntityTypes.COW, 2, 2, 3));
            var tokenVictim = zombie(test);
            tokenVictim.hurtServer(level, TokenEngine.directDamageSource(level, attacker), 100);
            test.assertTrue(!tokenVictim.isAlive(), "The independent token hit killed the enemy");
            bonus(test, engine, attacker, 0);
            var arrow = new Arrow(EntityTypes.ARROW, level);
            var arrowVictim = zombie(test);
            arrowVictim.hurtServer(level, level.damageSources().arrow(arrow, attacker), 100);
            test.assertTrue(!arrowVictim.isAlive(), "The arrow kill settled");
            flags(test, engine, attacker, TokenEngine.ACTIVE);
            bonus(test, engine, attacker, 1);
            bonus(test, engine, other, 0);

            near(test, melee(attacker, cow), resistant + 0.8F, "Resistance also reduces the bonus");
            cow.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200));
            near(test, melee(attacker, cow), 0, "Absorption covers the combined hit");
            near(test, cow.getAbsorptionAmount(), 4 - resistant - 0.8F, "Absorption pays for both components");
            cow.removeAllEffects();
            near(test, melee(attacker, cow), protectedHit + 1, "Armor and Protection do not reduce the bonus");
            cow.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            near(test, melee(attacker, cow), armored + 1, "Armor alone does not reduce the bonus");
            cow.getAttribute(Attributes.ARMOR).setBaseValue(0);
            near(test, melee(attacker, cow), plain + 1, "A passive target receives the full bonus");
            test.assertTrue(basePvP > 0, "The vanilla player hit was allowed");
            near(test, melee(attacker, playerTarget), basePvP, "Arrogance adds no damage to players");

            var zombie = zombie(test);
            float boosted = melee(attacker, zombie);
            zombie.setHealth(boosted - 0.5F);
            zombie.damageCooldownTime = 0;
            swing(attacker, zombie);
            test.assertTrue(!zombie.isAlive(), "The existing 1 HP bonus finished the enemy");
            bonus(test, engine, attacker, 2);
            kill(attacker, zombie(test));
            bonus(test, engine, attacker, 3);

            attacker.setHealth(12);
            attacker.heal(1);
            flags(test, engine, attacker, TokenEngine.READY | TokenEngine.PREPARING);
            bonus(test, engine, attacker, 0);
            kill(attacker, zombie(test));
            bonus(test, engine, attacker, 1);
            attacker.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
            engine.tick(List.of(attacker));
            flags(test, engine, attacker, 0);
            attacker.setHealth(40);
            flags(test, engine, attacker, TokenEngine.READY | TokenEngine.PREPARING);

            kill(other, zombie(test));
            bonus(test, engine, other, 1);
            server.leave(other);
            bonus(test, engine, other, 0);
            kill(attacker, zombie(test));
            bonus(test, engine, attacker, 1);
            attacker.damageCooldownTime = 0;
            attacker.hurtServer(level, level.damageSources().magic(), 100);
            test.assertValueEqual(server.playerStates().state(attacker.getUUID()),
                    com.runelegend.player.PlayerState.DOWNED, "The attacker is downed");
            flags(test, engine, attacker, 0);

            kill(other, zombie(test));
            kill(other, zombie(test));
            bonus(test, engine, other, 0);
            server.game().stopGame();
            flags(test, engine, other, 0);
            server.game().startGame();
            bonus(test, engine, other, 0);
            test.succeed();
        } finally {
            server.game().stopGame();
            for (var player : List.of(attacker, other, playerTarget)) {
                server.players().clear(player.getUUID());
                server.leave(player);
            }
        }
    }

    @GameTest
    public void configuredGroupsOnlyFlashOnActualIncreases(GameTestHelper test) {
        var player = GuardGameTests.fixture(test);
        var defaults = TokenConfig.DEFAULT;
        var registry = TokenRegistry.builtIn(new TokenConfig(defaults.soulSiphon(), defaults.echo(),
                defaults.immediateCounter(), defaults.venomousTip(), defaults.poisonedArrow(),
                defaults.asuraForm(), defaults.resonanceHealing(), defaults.vernalTide(),
                new TokenConfig.ArroganceSettings(0.7F, 3, 2, 5), defaults.executionEcho(), defaults.retribution()));
        var game = new GameController(() -> {}, (before, after) -> {});
        var players = new PlayerTokenService(game, PlayerTokenStore.inMemory(), registry);
        var flashes = new ArrayList<net.minecraft.resources.Identifier>();
        var engine = TokenTestEngine.forPlayers(player).create(game, players, registry, () -> 100, (recipient, id) -> flashes.add(id));
        var enemy = zombie(test);
        var kill = new TokenEvent(TokenEvent.Kind.KILL, player, enemy, player.level(), null, 1, 1, 0);
        try {
            players.equip(player.getUUID(), ARROGANCE);
            game.startGame();
            player.setHealth(14);
            engine.dispatch(kill);
            flags(test, engine, player, 0);
            player.setHealth(15);
            for (int i = 1; i <= 12; i++) {
                engine.dispatch(kill);
                bonus(test, engine, player, Math.min(5, (1 + (i - 1) / 3) * 2));
                test.assertValueEqual(flashes.size(), Math.min(3, 1 + (i - 1) / 3), "One flash per actual increase");
            }
            test.assertValueEqual(flashes, List.of(ARROGANCE, ARROGANCE, ARROGANCE),
                    "All three flashes were emitted in the same tick, including the partial increase at the cap");
            test.succeed();
        } finally {
            RuneLegendServer.find(player.level().getServer()).leave(player);
        }
    }

    @GameTest
    public void reachingZeroHealthResetsEvenWhenHealthIsImmediatelyRestored(GameTestHelper test) {
        var player = GuardGameTests.fixture(test);
        var registry = new TokenRegistry.Builder().register(new TokenDefinition(ARROGANCE,
                Set.of(TokenEvent.Kind.HEALTH, TokenEvent.Kind.KILL, TokenEvent.Kind.MELEE),
                new ArroganceEffect(new TokenConfig.ArroganceSettings(0, 1, 1, 5)))).build();
        var game = new GameController(() -> {}, (before, after) -> {});
        var players = new PlayerTokenService(game, PlayerTokenStore.inMemory(), registry);
        var engine = TokenTestEngine.forPlayers(player).create(game, players, registry, () -> 100);
        try {
            players.equip(player.getUUID(), ARROGANCE);
            game.startGame();
            engine.dispatch(new TokenEvent(TokenEvent.Kind.KILL, player, zombie(test), player.level(), null, 1, 1, 0));
            bonus(test, engine, player, 1);
            player.setHealth(0);
            engine.updateHealth(player);
            player.setHealth(1);
            engine.updateHealth(player);
            flags(test, engine, player, TokenEngine.READY | TokenEngine.PREPARING);
            bonus(test, engine, player, 0);
            test.succeed();
        } finally {
            RuneLegendServer.find(player.level().getServer()).leave(player);
        }
    }

    private static void kill(ServerPlayer attacker, LivingEntity victim) {
        victim.hurtServer(attacker.level(), attacker.damageSources().playerAttack(attacker), 100);
    }

    private static LivingEntity zombie(GameTestHelper test) {
        return test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
    }

    private static float melee(ServerPlayer attacker, LivingEntity target) {
        target.setHealth(target.getMaxHealth());
        target.damageCooldownTime = 0;
        swing(attacker, target);
        return target.getMaxHealth() - target.getHealth();
    }

    private static void swing(ServerPlayer attacker, LivingEntity target) {
        attacker.resetAttackStrengthTicker();
        attacker.attack(target);
    }

    private static void flags(GameTestHelper test, TokenEngine engine, ServerPlayer player, int expected) {
        test.assertValueEqual(engine.views(player).getFirst().flags(), expected, "Arrogance HUD flags");
    }

    private static void bonus(GameTestHelper test, TokenEngine engine, ServerPlayer player, float expected) {
        near(test, engine.melee(player, 1).bonusDamage(), expected, "Arrogance melee bonus");
    }

    private static void near(GameTestHelper test, float actual, float expected, String message) {
        test.assertTrue(Math.abs(actual - expected) < 0.001F, message + ": expected " + expected + ", got " + actual);
    }
}
