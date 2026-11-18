package com.runelegend.token;

import com.google.gson.JsonParser;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.api.RoundMetric;
import com.runelegend.game.GameController;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.player.PlayerTokenStore;
import com.runelegend.test.GuardGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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

public final class RetributionGameTests {
    private static final Identifier RETRIBUTION = RuneLegendMod.id("retribution");

    @GameTest
    public void countsDamageBeforeAbsorptionAfterVanillaReductions(GameTestHelper test) throws CommandSyntaxException {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var player = GuardGameTests.fixture(test);
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        var attack = level.damageSources().mobAttack(zombie);
        var difficulty = level.getDifficulty();
        try {
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            equip(test, server, player);
            hurt(player, attack, 4);
            stat(test, server, player, RoundMetric.HITS_TAKEN, 0);
            near(test, bonus(server.tokens(), player, zombie), 0, "A waiting player cannot trigger Retribution");
            server.game().startGame();
            player.setHealth(player.getMaxHealth());
            hurt(player, level.damageSources().fall(), 2);
            poison(player);
            near(test, bonus(server.tokens(), player, zombie), 0, "Fall damage and poison cannot start the effect");

            var origin = test.absoluteVec(new Vec3(2.5, 2, 2.5));
            player.setPos(origin);
            player.setYRot(0);
            player.setYHeadRot(0);
            zombie.setPos(origin.add(0, 0, 1));
            var fullSword = GuardGameTests.SWORD.replace("factor:0.5f", "factor:1.0f");
            var item = new ItemParser(level.registryAccess()).parse(new StringReader(fullSword));
            player.getInventory().setItem(0, item.createItemStack(1));
            player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
            test.assertTrue(player.isBlocking(), "Full-block sword is in use");
            test.assertTrue(!hurt(player, attack, 4), "Vanilla rejects a completely blocked hit");
            near(test, bonus(server.tokens(), player, zombie), 0, "A full block cannot start the effect");
            stat(test, server, player, RoundMetric.HITS_TAKEN, 3);
            stat(test, server, player, RoundMetric.DAMAGE_BLOCKED, 4);
            stat(test, server, player, RoundMetric.DAMAGE_TAKEN, 3);
            stat(test, server, player, RoundMetric.UNATTRIBUTED_HITS_TAKEN, 2);
            stat(test, server, player, RoundMetric.UNATTRIBUTED_DAMAGE_TAKEN, 3);
            releaseGuard(player);

            float health = player.getHealth();
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200));
            test.assertTrue(hurt(player, attack, 4), "Vanilla accepts an entirely absorbed hit");
            near(test, player.getHealth(), health, "The triggering hit took no red hearts");
            near(test, bonus(server.tokens(), player, zombie), 4, "All four points of the triggering hit count");
            stat(test, server, player, RoundMetric.DAMAGE_ABSORBED, 4);
            stat(test, server, player, RoundMetric.DAMAGE_TAKEN, 3);

            test.assertTrue(!player.hurtServer(level, attack, 3), "The hurt cooldown rejects a weaker hit");
            near(test, bonus(server.tokens(), player, zombie), 4, "A rejected hit adds nothing");
            stat(test, server, player, RoundMetric.HITS_TAKEN, 4);
            player.hurtServer(level, attack, 6);
            near(test, bonus(server.tokens(), player, zombie), 6, "A stronger hit during the hurt cooldown adds only its excess");

            player.setAbsorptionAmount(2);
            health = player.getHealth();
            hurt(player, attack, 3);
            near(test, health - player.getHealth(), 1, "The next hit is partially absorbed");
            near(test, bonus(server.tokens(), player, zombie), 9, "Partial absorption still counts the entire settled hit");

            player.setAbsorptionAmount(4);
            health = player.getHealth();
            hurt(player, level.damageSources().fall(), 2);
            poison(player);
            near(test, player.getHealth(), health, "Absorption covers the fall and poison damage");
            near(test, bonus(server.tokens(), player, zombie), 12, "Environmental and poison damage count once active");

            player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
            test.assertTrue(player.isBlocking(), "Releasing Use allows the next block");
            test.assertTrue(!hurt(player, attack, 4), "A second full block is rejected");
            near(test, bonus(server.tokens(), player, zombie), 12, "A full block also adds nothing once active");
            releaseGuard(player);
            player.setAbsorptionAmount(0);
            player.setItemSlot(EquipmentSlot.CHEST, protectionChestplate(player));
            player.getAttribute(Attributes.ARMOR).setBaseValue(12);
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200));
            health = player.getHealth();
            hurt(player, attack, 4);
            float mitigated = health - player.getHealth();
            test.assertTrue(mitigated > 0 && mitigated < 4, "Incoming equipment and Resistance reduce the hit");
            near(test, bonus(server.tokens(), player, zombie), 12 + mitigated, "The accumulation uses the mitigated amount");
            player.heal(4);
            near(test, bonus(server.tokens(), player, zombie), 12 + mitigated, "Healing leaves the accumulation intact");
            test.succeed();
        } finally {
            level.getServer().setDifficulty(difficulty, true);
            cleanup(server, player);
        }
    }

    @GameTest
    public void onlyAttributedActiveAttacksStartRetribution(GameTestHelper test) {
        var player = GuardGameTests.fixture(test);
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        var creeper = test.spawnWithNoFreeWill(EntityTypes.CREEPER, 3, 2, 3);
        var arrow = test.spawn(EntityTypes.ARROW, 3, 2, 2);
        var tnt = test.spawn(EntityTypes.TNT, 3, 2, 4);
        var damage = test.getLevel().damageSources();
        var registry = TokenRegistry.builtIn();
        var game = new GameController(() -> {}, (before, after) -> {});
        var players = new PlayerTokenService(game, PlayerTokenStore.inMemory(), registry);
        var engine = TokenTestEngine.forPlayers(player).create(game, players, registry, () -> 100);
        try {
            players.equip(player.getUUID(), RETRIBUTION);
            game.startGame();
            for (var source : List.of(damage.fall(), damage.magic(), damage.thorns(zombie), damage.playerAttack(player),
                    damage.arrow(arrow, null), damage.explosion(tnt, zombie), damage.explosion(null, null),
                    damage.source(DamageTypes.FALL, zombie), TokenEngine.directDamageSource(player.level(), player))) {
                settle(engine, player, source, 2);
                near(test, bonus(engine, player, zombie), 0, source + " cannot start Retribution");
            }
            settle(engine, player, damage.mobAttack(zombie), 0);
            near(test, bonus(engine, player, zombie), 0, "A zero-damage attack cannot start Retribution");
            for (var source : List.of(damage.mobAttack(zombie), damage.arrow(arrow, zombie),
                    damage.indirectMagic(arrow, zombie), damage.sonicBoom(zombie), damage.explosion(creeper, creeper))) {
                settle(engine, player, source, 0.75F);
                near(test, bonus(engine, player, zombie), 0.75F, source + " starts the effect with fractional damage");
                engine.clearEffects(player.getUUID());
            }
            test.succeed();
        } finally {
            server.leave(player);
        }
    }

    @GameTest
    public void configuredTimingsClearTheRecordAndKeepPlayersIndependent(GameTestHelper test) {
        var first = GuardGameTests.fixture(test);
        var second = GuardGameTests.fixture(test);
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        var attack = test.getLevel().damageSources().mobAttack(zombie);
        var clock = new AtomicLong(100);
        var config = TokenConfig.fromJson(JsonParser.parseString("""
                {"runelegend:retribution":{"duration_seconds":2,"cooldown_seconds":3}}
                """).getAsJsonObject());
        var registry = TokenRegistry.builtIn(config);
        var game = new GameController(() -> {}, (before, after) -> {});
        var players = new PlayerTokenService(game, PlayerTokenStore.inMemory(), registry);
        var engine = TokenTestEngine.forPlayers(first, second).create(game, players, registry, clock::get);
        try {
            players.equip(first.getUUID(), RETRIBUTION);
            players.equip(second.getUUID(), RETRIBUTION);
            game.startGame();
            settle(engine, first, attack, 4);
            var active = engine.views(first).getFirst();
            test.assertValueEqual(active.flags(), TokenEngine.TRIGGERED | TokenEngine.ACTIVE, "Trigger is visible");
            test.assertValueEqual(active.activeUntil(), 140L, "Configured duration is two seconds");
            near(test, bonus(engine, second, zombie), 0, "The other player has no record");
            clock.set(139);
            settle(engine, second, attack, 6);
            near(test, bonus(engine, first, zombie), 4, "First player is still active on its last tick");
            clock.set(140);
            engine.tick(List.of(first, second));
            settle(engine, first, attack, 3);
            near(test, bonus(engine, first, zombie), 0, "At expiry the bonus is gone and the hit is ignored");
            near(test, bonus(engine, second, zombie), 6, "The other player's accumulation survives");
            var cooldown = engine.views(first).getFirst();
            test.assertValueEqual(cooldown.flags(), TokenEngine.COOLDOWN, "The effect transitions to cooldown");
            test.assertValueEqual(cooldown.cooldownUntil(), 200L, "Configured cooldown begins at expiry");
            clock.set(199);
            settle(engine, first, attack, 3);
            near(test, bonus(engine, first, zombie), 0, "The last cooldown tick still rejects a hit");
            clock.set(200);
            engine.tick(List.of(first, second));
            settle(engine, first, attack, 2);
            near(test, bonus(engine, first, zombie), 2, "The first eligible hit starts a fresh record");
            engine.clearEffects(first.getUUID());
            near(test, bonus(engine, first, zombie), 0, "Player cleanup removes the active record");
            settle(engine, first, attack, 3);
            engine.clearRound();
            test.assertValueEqual(engine.views(first).getFirst().flags(), TokenEngine.READY, "Round cleanup removes the timers");
            near(test, bonus(engine, first, zombie), 0, "Round cleanup removes the bonus");
            test.succeed();
        } finally {
            server.leave(first);
            server.leave(second);
        }
    }

    @GameTest
    public void meleeAddsTrueDamageInTheSameHitOnlyToNonPlayers(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var attacker = GuardGameTests.fixture(test);
        var teammate = GuardGameTests.fixture(test);
        var zombie = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        try {
            equip(test, server, attacker);
            server.game().startGame();
            attacker.getInventory().setItem(0, ItemStack.EMPTY);
            attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
            attacker.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024);

            float plain = swing(attacker, zombie);
            zombie.getAttribute(Attributes.ARMOR).setBaseValue(12);
            float armored = swing(attacker, zombie);
            zombie.setItemSlot(EquipmentSlot.CHEST, protectionChestplate(zombie));
            float protectedDamage = swing(attacker, zombie);
            test.assertTrue(protectedDamage > 0 && protectedDamage < armored && armored < plain,
                    "Armor and Protection independently reduce the unboosted melee hit");
            stat(test, server, attacker, RoundMetric.DAMAGE_DEALT, plain + armored + protectedDamage);
            stat(test, server, attacker, RoundMetric.HITS_DEALT, 3);
            float teammateDamage = swing(attacker, teammate);
            test.assertTrue(teammateDamage > 0, "The mock teammate takes base melee damage");

            hurt(attacker, level.damageSources().mobAttack(zombie), 4);
            float bonus = bonus(server.tokens(), attacker, zombie);
            test.assertTrue(bonus > 0, "The incoming attack activates Retribution");
            float boosted = swing(attacker, zombie);
            near(test, boosted, protectedDamage + bonus, "The bonus bypasses armor and Protection");
            stat(test, server, attacker, RoundMetric.DAMAGE_DEALT, plain + armored + protectedDamage + teammateDamage + boosted);
            stat(test, server, attacker, RoundMetric.HITS_DEALT, 5);
            test.assertValueEqual(zombie.damageCooldownTime, 20, "The boosted hit uses the vanilla hurt cooldown");
            near(test, swing(attacker, teammate), teammateDamage, "Player targets take only the base damage");
            zombie.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200));
            zombie.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200));
            near(test, swing(attacker, zombie), (protectedDamage + bonus) * 0.8F - 4,
                    "Resistance and then absorption apply to the combined hit");
            stat(test, server, attacker, RoundMetric.DAMAGE_DEALT,
                    plain + armored + protectedDamage + 2 * teammateDamage + boosted + (protectedDamage + bonus) * 0.8F - 4);
            stat(test, server, attacker, RoundMetric.HITS_DEALT, 7);
            near(test, bonus(server.tokens(), attacker, zombie), bonus, "Swings do not spend the accumulated damage");

            hurt(attacker, level.damageSources().mobAttack(zombie), 100);
            var downed = server.downed().get(attacker.getUUID());
            test.assertTrue(downed != null && server.revive(attacker.getUUID(), downed.sequence()), "The attacker is downed and revived");
            near(test, bonus(server.tokens(), attacker, zombie), 0, "A revive cannot restore the old record");
            test.succeed();
        } finally {
            cleanup(server, attacker);
            server.leave(teammate);
        }
    }

    private static void equip(GameTestHelper test, RuneLegendServer server, ServerPlayer player) {
        server.game().stopGame();
        test.assertValueEqual(server.players().equip(player.getUUID(), RETRIBUTION),
                PlayerTokenService.Result.CHANGED, "Equip Retribution");
    }

    private static void cleanup(RuneLegendServer server, ServerPlayer player) {
        server.game().stopGame();
        server.players().clear(player.getUUID());
        server.leave(player);
    }

    private static void settle(TokenEngine engine, ServerPlayer player, DamageSource source, float damage) {
        engine.dispatch(new TokenEvent(TokenEvent.Kind.HURT, player, player, player.level(), source, 8, 8, 0, damage));
    }

    private static boolean hurt(ServerPlayer player, DamageSource source, float amount) {
        player.damageCooldownTime = 0;
        return player.hurtServer(player.level(), source, amount);
    }

    private static void poison(ServerPlayer player) {
        player.damageCooldownTime = 0;
        MobEffects.POISON.value().applyEffectTick(player.level(), player, 0);
    }

    private static void releaseGuard(ServerPlayer player) {
        player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
    }

    private static ItemStack protectionChestplate(LivingEntity entity) {
        var armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
        armor.enchant(entity.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.PROTECTION), 4);
        return armor;
    }

    private static float swing(ServerPlayer attacker, LivingEntity target) {
        target.setHealth(target.getMaxHealth());
        target.damageCooldownTime = 0;
        attacker.resetAttackStrengthTicker();
        attacker.attack(target);
        return target.getMaxHealth() - target.getHealth();
    }

    private static float bonus(TokenEngine engine, ServerPlayer player, LivingEntity target) {
        return engine.melee(player, target, 1).bonusDamage();
    }

    private static void near(GameTestHelper test, float actual, float expected, String message) {
        test.assertTrue(Math.abs(actual - expected) < 0.001F, message + ": expected " + expected + ", got " + actual);
    }

    private static void stat(GameTestHelper test, RuneLegendServer server, ServerPlayer player, RoundMetric metric, float expected) {
        float actual = (float) server.statistics().player(player.getUUID()).value(metric);
        near(test, actual, expected, metric.id());
    }
}
