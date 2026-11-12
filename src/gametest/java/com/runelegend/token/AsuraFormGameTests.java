package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.game.GameController;
import com.runelegend.player.PlayerTokenSavedData;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.test.GuardGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class AsuraFormGameTests {
    private static final net.minecraft.resources.Identifier ASURA = RuneLegendMod.id("asura_form");

    @GameTest
    public void actualKillsChargeAndTheBonusPreservesVanillaMitigation(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var attacker = GuardGameTests.fixture(test);
        var second = GuardGameTests.fixture(test);
        var pvpVictim = GuardGameTests.fixture(test);
        var target = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 3, 2, 3);
        var arrow = test.spawn(EntityTypes.ARROW, 2, 3, 3);
        try {
            server.game().stopGame();
            for (var player : List.of(attacker, second)) {
                test.assertValueEqual(server.players().equip(player.getUUID(), ASURA),
                        PlayerTokenService.Result.CHANGED, "Equip Asura Form");
            }
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60);
            target.getAttribute(Attributes.ARMOR).setBaseValue(0);
            attacker.setPos(target.position().add(0, 0, 1));
            server.game().startGame();

            float plain = melee(attacker, target);
            target.getAttribute(Attributes.ARMOR).setBaseValue(12);
            float armored = melee(attacker, target);
            var chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
            chestplate.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.PROTECTION), 4);
            target.setItemSlot(EquipmentSlot.CHEST, chestplate);
            float protectedHit = melee(attacker, target);
            target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200));
            float resistant = melee(attacker, target);
            test.assertTrue(plain > armored && armored > protectedHit && protectedHit > resistant && resistant > 0,
                    "The base strike is affected by armor, Protection and Resistance");

            kill(attacker, test.spawnWithNoFreeWill(EntityTypes.COW, 2, 2, 2));
            kill(attacker, pvpVictim);
            test.assertTrue(!pvpVictim.isAlive(), "The PvP kill settled");
            var tokenVictim = zombie(test);
            tokenVictim.hurtServer(level, TokenEngine.directDamageSource(level, attacker), 100);
            test.assertTrue(!tokenVictim.isAlive(), "The separate token source killed the monster");
            test.assertValueEqual(view(server.tokens(), attacker).flags(), TokenEngine.READY,
                    "Passive, PvP and separate token kills give no charge");

            kill(attacker, zombie(test));
            kill(second, zombie(test));
            kill(attacker, zombie(test));
            test.assertValueEqual(view(server.tokens(), attacker).flags(), TokenEngine.READY | TokenEngine.PREPARING,
                    "Two kills charge without transforming");
            var arrowVictim = zombie(test);
            arrowVictim.hurtServer(level, level.damageSources().arrow(arrow, attacker), 100);
            test.assertTrue(!arrowVictim.isAlive(), "The arrow killed the third monster");
            test.assertTrue((view(server.tokens(), attacker).flags() & TokenEngine.ACTIVE) != 0, "The arrow completes the charge");
            test.assertValueEqual(view(server.tokens(), second).flags(), TokenEngine.READY | TokenEngine.PREPARING,
                    "The other player keeps only their own charge");
            near(test, attacker.getHealth(), 16, "The third kill starts the full temporary bar");

            near(test, melee(attacker, target), resistant + 3.2F, "Resistance reduces the added damage");
            target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200));
            near(test, melee(attacker, target), 0, "Absorption can cover the combined hit");
            near(test, target.getAbsorptionAmount(), 4 - resistant - 3.2F,
                    "Absorption pays for both damage portions");
            target.removeEffect(MobEffects.ABSORPTION);
            target.removeEffect(MobEffects.RESISTANCE);
            near(test, melee(attacker, target), protectedHit + 4F, "Armor and Protection never reduce Asura damage");
            target.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            near(test, melee(attacker, target), armored + 4F, "Armor-only target also takes the full bonus");
            target.getAttribute(Attributes.ARMOR).setBaseValue(0);
            near(test, melee(attacker, target), plain + 4F, "An unarmored target takes the same bonus");

            attacker.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            attacker.hurtServer(level, level.damageSources().magic(), 100);
            test.assertTrue(attacker.isAlive() && attacker.getOffhandItem().isEmpty(), "Vanilla totem prevents temporary bar death");
            near(test, attacker.getMaxHealth(), 16, "A totem save preserves the form");
            near(test, attacker.getHealth(), 1, "The totem sets only the temporary bar");
            near(test, savedHealth(attacker), 20, "The totem does not overwrite the normal bar");
            attacker.damageCooldownTime = 0;
            attacker.hurtServer(level, level.damageSources().magic(), 100);
            test.assertTrue(!attacker.isAlive(), "A second lethal hit kills normally");
            near(test, attacker.getHealth(), 0, "Death never restores the normal bar");
            near(test, attacker.getMaxHealth(), 20, "Death clears the temporary maximum");
            test.succeed();
        } finally {
            server.game().stopGame();
            for (var player : List.of(attacker, second, pvpVictim)) {
                server.players().clear(player.getUUID());
                server.leave(player);
            }
        }
    }

    @GameTest
    public void healingAndPersistenceUseTheNormalBarIndependently(GameTestHelper test) {
        var level = test.getLevel();
        var minecraft = level.getServer();
        var player = GuardGameTests.fixture(test);
        var server = RuneLegendServer.find(minecraft);
        var clock = new AtomicLong(100);
        var defaults = TokenConfig.DEFAULT;
        var registry = TokenRegistry.builtIn(new TokenConfig(defaults.sharpenedTip(), defaults.soulSiphon(), defaults.echo(),
                defaults.immediateCounter(), defaults.venomousTip(), defaults.poisonedArrow(),
                new TokenConfig.AsuraFormSettings(2, 2, 5.5F, 3, 16, 1)));
        var game = new GameController(() -> {}, (previous, current) -> {});
        var players = new PlayerTokenService(game, PlayerTokenSavedData.load(minecraft), registry);
        var tokens = new TokenEngine(game, players, registry, clock::get);
        var kill = new TokenEvent(TokenEvent.Kind.KILL, player, zombie(test), level, null, 20, 20, 0);
        try {
            players.equip(player.getUUID(), ASURA);
            game.startGame();
            player.setHealth(8);
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 200));
            float speed = (float) player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            tokens.dispatch(kill);
            near(test, player.getHealth(), 8, "A custom threshold of two does not fire on the first kill");
            tokens.dispatch(kill);
            near(test, tokens.melee(player, 1).bonusDamage(), 5.5F, "The configured bonus is used");
            near(test, (float) player.getAttributeValue(Attributes.MOVEMENT_SPEED), speed * 1.6F,
                    "The configured speed stacks with the existing potion");
            player.removeAllEffects();
            near(test, (float) player.getAttributeValue(Attributes.MOVEMENT_SPEED), speed / 1.2F * 1.6F,
                    "Clearing potion effects does not end the speed bonus");

            player.hurtServer(level, level.damageSources().magic(), 1);
            near(test, player.getHealth(), 15, "Damage applies to the temporary bar");
            near(test, savedHealth(player), 8, "Damage does not touch the normal bar or its saved value");
            player.heal(4);
            near(test, player.getHealth(), 16, "Temporary healing caps at 16");
            near(test, savedHealth(player), 12, "Normal healing gets the full amount, including overflow");

            MobEffects.REGENERATION.value().applyEffectTick(level, player, 0);
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(6);
            for (int i = 0; i < 100; i++) player.getFoodData().tick(player);
            near(test, savedHealth(player), 12, "Vanilla skips regeneration and food healing when the temporary bar is full");
            MobEffects.INSTANT_HEALTH.value().applyInstantaneousEffect(level, null, null, player, 0, 1);
            near(test, savedHealth(player), 16, "Instant Health still heals the normal bar at full temporary health");
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(22);
            player.heal(100);
            near(test, player.getMaxHealth(), 16, "Normal attribute changes never resize the temporary bar");
            near(test, savedHealth(player), 22, "Normal healing uses the current normal maximum");
            tokens.dispatch(kill);
            tokens.dispatch(kill);
            clock.set(140);
            tokens.tick(List.of(player));
            near(test, player.getHealth(), 22, "Expiry restores the healed normal bar even above 16");
            near(test, (float) player.getAttributeValue(Attributes.MOVEMENT_SPEED), speed / 1.2F,
                    "Expiry removes only the form's speed");
            test.assertValueEqual(view(tokens, player).flags(), TokenEngine.COOLDOWN, "Cooldown starts at expiry");
            tokens.dispatch(kill);
            clock.set(160);
            tokens.dispatch(kill);
            test.assertValueEqual(view(tokens, player).flags(), TokenEngine.READY | TokenEngine.PREPARING,
                    "Kills during the form and cooldown did not count");
            tokens.dispatch(kill);
            player.hurtServer(level, level.damageSources().magic(), 3);
            tokens.clearRound();
            near(test, player.getHealth(), 22, "Stopping the round also restores the normal bar");
            test.assertValueEqual(view(tokens, player).flags(), TokenEngine.READY, "Round cleanup discards the cooldown");
            test.succeed();
        } finally {
            tokens.clearRound();
            game.stopGame();
            players.clear(player.getUUID());
            server.leave(player);
        }
    }

    private static void kill(ServerPlayer player, LivingEntity target) {
        target.hurtServer(player.level(), player.damageSources().playerAttack(player), 100);
    }

    private static LivingEntity zombie(GameTestHelper test) {
        return test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
    }

    private static float melee(ServerPlayer attacker, LivingEntity target) {
        target.setHealth(target.getMaxHealth());
        target.damageCooldownTime = 0;
        attacker.resetAttackStrengthTicker();
        attacker.attack(target);
        return target.getMaxHealth() - target.getHealth();
    }

    private static TokenEngine.View view(TokenEngine tokens, ServerPlayer player) {
        return tokens.views(player).getFirst();
    }

    private static float savedHealth(ServerPlayer player) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.level().registryAccess());
        player.saveWithoutId(output);
        return output.buildResult().getFloatOr("Health", -1);
    }

    private static void near(GameTestHelper test, float actual, float expected, String description) {
        test.assertTrue(Math.abs(actual - expected) < 0.001F, description + ": expected " + expected + ", got " + actual);
    }
}
