package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.game.GameController;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.player.PlayerTokenStore;
import com.runelegend.test.GuardGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class VernalTideGameTests {
    private static final net.minecraft.resources.Identifier VERNAL =
            RuneLegendMod.id("vernal_tide");

    @GameTest
    public void meleeBonusMergesIntoTheDirectHit(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var attacker = GuardGameTests.fixture(test);
        var target = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        var bystander = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        try {
            server.game().stopGame();
            test.assertValueEqual(server.players().equip(attacker.getUUID(), VERNAL),
                    PlayerTokenService.Result.CHANGED, "Equip Vernal Tide");
            server.game().startGame();
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60);
            bystander.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60);
            reset(target);
            reset(bystander);
            attacker.setPos(target.position().add(0, 0, 1));
            // Stay outside the vanilla sweep box so only the splash can reach the bystander.
            bystander.setPos(attacker.position().add(4, 0, 0));
            long procSeed = seed(true);

            attacker.removeEffect(MobEffects.REGENERATION);
            var plain = strike(attacker, target, bystander, procSeed);
            test.assertTrue(plain.target() > 0, "The base melee hit still lands");
            test.assertValueEqual(plain.bystander(), 0F, "Without Regeneration there is no splash");

            attacker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400));
            var procced = strike(attacker, target, bystander, procSeed);
            closeTo(test, procced.target() - plain.target(), 3.0F,
                    "The direct hit gains the configured true damage");
            closeTo(test, procced.bystander(), 3.0F, "A nearby enemy takes the splash");
            test.assertValueEqual(target.damageCooldownTime, 20,
                    "The merged bonus does not create a second hit");

            target.getAttribute(Attributes.ARMOR).setBaseValue(12);
            var chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
            chestplate.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.PROTECTION), 4);
            target.setItemSlot(EquipmentSlot.CHEST, chestplate);
            attacker.removeEffect(MobEffects.REGENERATION);
            var armoredPlain = strike(attacker, target, bystander, procSeed);
            attacker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400));
            var armored = strike(attacker, target, bystander, procSeed);
            closeTo(test, armored.target() - armoredPlain.target(), 3.0F,
                    "Armor and Protection never reduce the merged bonus");

            target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400));
            attacker.removeEffect(MobEffects.REGENERATION);
            var resistantPlain = strike(attacker, target, bystander, procSeed);
            attacker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400));
            var resistant = strike(attacker, target, bystander, procSeed);
            closeTo(test, resistant.target() - resistantPlain.target(), 2.4F,
                    "Resistance still reduces the merged bonus");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.players().clear(attacker.getUUID());
            server.leave(attacker);
        }
    }

    @GameTest
    public void splashExcludesTheStruckTargetAndConsumesTheProc(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var attacker = GuardGameTests.fixture(test);
        var primary = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        var bystander = test.spawnWithNoFreeWill(EntityTypes.SPIDER, 2, 2, 3);
        var far = test.spawnWithNoFreeWill(EntityTypes.PILLAGER, 2, 2, 3);
        var ally = GuardGameTests.fixture(test);
        var clock = new AtomicLong(100);
        var defaults = TokenConfig.DEFAULT;
        var registry = TokenRegistry.builtIn(new TokenConfig(defaults.soulSiphon(), defaults.echo(),
                defaults.immediateCounter(), defaults.venomousTip(), defaults.poisonedArrow(),
                defaults.asuraForm(), defaults.resonanceHealing(),
                new TokenConfig.VernalTideSettings(0.5F, 5.0, 3.0F), defaults.arrogance(), defaults.executionEcho(),
                defaults.retribution(), defaults.substitute()));
        var game = new GameController(() -> {}, (previous, current) -> {});
        var players = new PlayerTokenService(game, PlayerTokenStore.inMemory(), registry);
        double[] roll = {0.1};
        var tokens = TokenTestEngine.forPlayers(attacker).create(game, players, registry, clock::get, player -> roll[0]);
        var origin = test.absoluteVec(new Vec3(2.5, 2, 2.5));
        try {
            game.stopGame();
            test.assertValueEqual(players.equip(attacker.getUUID(), VERNAL),
                    PlayerTokenService.Result.CHANGED, "Equip Vernal Tide");
            game.startGame();
            attacker.setPos(origin);
            primary.setPos(origin.add(1, 0, 0));
            bystander.setPos(origin.add(4.0, 0, 0));
            far.setPos(origin.add(5.1, 0, 0));
            ally.setPos(origin.add(2, 0, 0));
            for (var mob : List.<LivingEntity>of(primary, bystander, far)) {
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60);
                reset(mob);
            }
            ally.setHealth(20);
            attacker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400));

            roll[0] = 0.1;
            test.assertValueEqual(tokens.melee(attacker, 6.0F).bonusDamage(), 3.0F,
                    "Regeneration and a successful roll add the configured damage");
            tokens.dispatch(melee(attacker, primary, level));
            test.assertValueEqual(primary.getHealth(), 60F, "The struck target is excluded from the splash");
            test.assertValueEqual(bystander.getHealth(), 57F, "An enemy inside the radius takes the splash");
            test.assertValueEqual(far.getHealth(), 60F, "An enemy outside the radius is untouched");
            test.assertValueEqual(ally.getHealth(), 20F, "Players are never damaged");

            tokens.dispatch(melee(attacker, primary, level));
            test.assertValueEqual(bystander.getHealth(), 57F, "The proc is consumed by one swing");

            reset(bystander);
            bystander.getAttribute(Attributes.ARMOR).setBaseValue(12);
            var chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
            chestplate.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.PROTECTION), 4);
            bystander.setItemSlot(EquipmentSlot.CHEST, chestplate);
            tokens.melee(attacker, 6.0F);
            tokens.dispatch(melee(attacker, primary, level));
            test.assertValueEqual(bystander.getHealth(), 57F,
                    "Armor and Protection never reduce the splash");

            bystander.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400));
            reset(bystander);
            tokens.melee(attacker, 6.0F);
            tokens.dispatch(melee(attacker, primary, level));
            closeTo(test, 60F - bystander.getHealth(), 2.4F, "Resistance still reduces the splash");

            bystander.removeEffect(MobEffects.RESISTANCE);
            bystander.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            bystander.getAttribute(Attributes.ARMOR).setBaseValue(0);
            bystander.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 3));
            bystander.setAbsorptionAmount(16);
            reset(bystander);
            tokens.melee(attacker, 6.0F);
            tokens.dispatch(melee(attacker, primary, level));
            test.assertValueEqual(bystander.getHealth(), 60F, "Absorption covers the splash");
            closeTo(test, bystander.getAbsorptionAmount(), 13F, "Absorption pays for the splash");

            attacker.removeEffect(MobEffects.REGENERATION);
            roll[0] = 0.1;
            test.assertValueEqual(tokens.melee(attacker, 6.0F).bonusDamage(), 0F,
                    "Regeneration gates the token");
            reset(bystander);
            tokens.dispatch(melee(attacker, primary, level));
            test.assertValueEqual(bystander.getHealth(), 60F, "Without Regeneration nothing splashes");

            attacker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400));
            roll[0] = 0.9;
            test.assertValueEqual(tokens.melee(attacker, 6.0F).bonusDamage(), 0F,
                    "A failed roll adds nothing to the hit");
            tokens.dispatch(melee(attacker, primary, level));
            test.assertValueEqual(bystander.getHealth(), 60F, "A failed roll never splashes");

            game.stopGame();
            roll[0] = 0.1;
            test.assertValueEqual(tokens.melee(attacker, 6.0F).bonusDamage(), 0F,
                    "WAITING disables the token");
            tokens.dispatch(melee(attacker, primary, level));
            test.assertValueEqual(bystander.getHealth(), 60F, "WAITING never splashes");
            test.succeed();
        } finally {
            tokens.clearRound();
            game.stopGame();
            players.clear(attacker.getUUID());
            server.leave(attacker);
            server.leave(ally);
        }
    }

    @GameTest
    public void configuredRadiusAndDamageDriveTheSplash(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var attacker = GuardGameTests.fixture(test);
        var primary = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        var nearSpider = test.spawnWithNoFreeWill(EntityTypes.SPIDER, 2, 2, 3);
        var farSpider = test.spawnWithNoFreeWill(EntityTypes.SPIDER, 2, 2, 3);
        var clock = new AtomicLong(100);
        var defaults = TokenConfig.DEFAULT;
        var registry = TokenRegistry.builtIn(new TokenConfig(defaults.soulSiphon(), defaults.echo(),
                defaults.immediateCounter(), defaults.venomousTip(), defaults.poisonedArrow(),
                defaults.asuraForm(), defaults.resonanceHealing(),
                new TokenConfig.VernalTideSettings(1.0F, 2.5, 5.0F), defaults.arrogance(), defaults.executionEcho(),
                defaults.retribution(), defaults.substitute()));
        var game = new GameController(() -> {}, (previous, current) -> {});
        var players = new PlayerTokenService(game, PlayerTokenStore.inMemory(), registry);
        var tokens = TokenTestEngine.forPlayers(attacker).create(game, players, registry, clock::get, player -> 0.0);
        var origin = test.absoluteVec(new Vec3(2.5, 2, 2.5));
        try {
            game.stopGame();
            test.assertValueEqual(players.equip(attacker.getUUID(), VERNAL),
                    PlayerTokenService.Result.CHANGED, "Equip configured Vernal Tide");
            game.startGame();
            attacker.setPos(origin);
            primary.setPos(origin.add(1, 0, 0));
            nearSpider.setPos(origin.add(2.5, 0, 0));
            farSpider.setPos(origin.add(2.6, 0, 0));
            for (var mob : List.<LivingEntity>of(primary, nearSpider, farSpider)) {
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60);
                reset(mob);
            }
            attacker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400));

            test.assertValueEqual(tokens.melee(attacker, 6.0F).bonusDamage(), 5.0F,
                    "The configured damage drives the melee bonus");
            tokens.dispatch(melee(attacker, primary, level));
            test.assertValueEqual(nearSpider.getHealth(), 55F,
                    "The configured radius includes an enemy at exactly 2.5 blocks from the player");
            test.assertValueEqual(farSpider.getHealth(), 60F,
                    "The radius is centred on the player, not on the struck target");
            test.assertValueEqual(primary.getHealth(), 60F,
                    "The struck target is never part of the splash");
            test.succeed();
        } finally {
            tokens.clearRound();
            game.stopGame();
            players.clear(attacker.getUUID());
            server.leave(attacker);
        }
    }

    @GameTest
    public void splashKillsDoNotTriggerOtherTokens(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var attacker = GuardGameTests.fixture(test);
        var primary = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        var victim = test.spawnWithNoFreeWill(EntityTypes.SPIDER, 2, 2, 3);
        var clock = new AtomicLong(100);
        var defaults = TokenConfig.DEFAULT;
        var registry = TokenRegistry.builtIn(defaults);
        var game = new GameController(() -> {}, (previous, current) -> {});
        var players = new PlayerTokenService(game, PlayerTokenStore.inMemory(), registry);
        var tokens = TokenTestEngine.forPlayers(attacker).create(game, players, registry, clock::get, player -> 0.0);
        var origin = test.absoluteVec(new Vec3(2.5, 2, 2.5));
        try {
            game.stopGame();
            for (String id : List.of("soul_siphon", "vernal_tide")) {
                test.assertValueEqual(players.equip(attacker.getUUID(), RuneLegendMod.id(id)),
                        PlayerTokenService.Result.CHANGED, "Equip " + id);
            }
            game.startGame();
            attacker.setPos(origin);
            primary.setPos(origin.add(1, 0, 0));
            victim.setPos(origin.add(2, 0, 0));
            reset(primary);
            victim.setHealth(2);
            victim.damageCooldownTime = 0;
            attacker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400));
            float maximumHealth = attacker.getMaxHealth();

            test.assertValueEqual(tokens.melee(attacker, 6.0F).bonusDamage(), 3.0F,
                    "The default settings still add the melee bonus");
            tokens.dispatch(melee(attacker, primary, level));
            test.assertTrue(!victim.isAlive(), "The splash killed the low-health enemy");
            test.assertValueEqual(attacker.getMaxHealth(), maximumHealth,
                    "Token damage never re-enters the token event pipeline");
            test.succeed();
        } finally {
            tokens.clearRound();
            game.stopGame();
            players.clear(attacker.getUUID());
            server.leave(attacker);
        }
    }

    private record Hit(float target, float bystander) {}

    private static Hit strike(ServerPlayer attacker, LivingEntity target, LivingEntity bystander, long seed) {
        reset(target);
        reset(bystander);
        attacker.getRandom().setSeed(seed);
        attacker.resetAttackStrengthTicker();
        float targetBefore = target.getHealth();
        float bystanderBefore = bystander.getHealth();
        attacker.attack(target);
        return new Hit(targetBefore - target.getHealth(), bystanderBefore - bystander.getHealth());
    }

    private static void reset(LivingEntity entity) {
        entity.setHealth(entity.getMaxHealth());
        entity.damageCooldownTime = 0;
    }

    private static TokenEvent melee(ServerPlayer attacker, LivingEntity target, ServerLevel level) {
        return new TokenEvent(TokenEvent.Kind.MELEE, attacker, target, level,
                attacker.damageSources().playerAttack(attacker), 6, 6, 0);
    }

    private static long seed(boolean proc) {
        long seed = 0;
        while ((RandomSource.create(seed).nextDouble() < 0.50) != proc) seed++;
        return seed;
    }

    private static void closeTo(GameTestHelper test, float actual, float expected, String message) {
        test.assertTrue(Math.abs(actual - expected) < 0.001F,
                message + ": expected " + expected + ", got " + actual);
    }
}
