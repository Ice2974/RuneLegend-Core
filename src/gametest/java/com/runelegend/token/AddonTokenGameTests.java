package com.runelegend.token;

import com.google.gson.JsonParser;
import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.api.RoundMetric;
import com.runelegend.game.GameController;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.player.PlayerTokenStore;
import com.runelegend.test.GuardGameTests;
import com.runelegend.test.PlayerStateFixtures;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static com.runelegend.token.AddonTokenFixture.*;

public final class AddonTokenGameTests {
    @GameTest
    public void fabricAddonEquipsSurvivesLivingReplacementAndCleansUp(GameTestHelper test) {
        var minecraft = test.getLevel().getServer();
        var server = RuneLegendServer.find(minecraft);
        server.game().stopGame();
        var player = PlayerStateFixtures.join(minecraft, test.getLevel(), UUID.randomUUID());
        var ally = PlayerStateFixtures.join(minecraft, test.getLevel(), UUID.randomUUID());
        var uuid = player.getUUID();
        var replacement = new ServerPlayer(minecraft, player.level(), player.getGameProfile(), ClientInformation.createDefault());
        try {
            var builtIn = RuneLegendMod.id("echo");
            test.assertTrue(server.registry().get(ID) != null && server.registry().get(builtIn) != null,
                    "Fabric registered an addon with the same path as a built-in token");
            test.assertValueEqual(server.registry().get(ID).name().getString(), "Addon Echo",
                    "The addon resolves its own translation");
            test.assertValueEqual(server.registry().get(ID).description().getString(), "Tests addon token lifecycle and damage.",
                    "The addon resolves its own description");
            minecraft.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "runelegend token equip " + ID);
            minecraft.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "runelegend token equip echo");
            var installed = List.of(ID, builtIn);
            var loaded = PlayerTokenStore.load(PlayerTokenStore.defaultPath());
            test.assertValueEqual(loaded.get(uuid).equipped(), installed, "Both command IDs are saved separately");

            var missingRegistry = TokenRegistry.builtIn();
            var missingPlayers = new PlayerTokenService(server.game(), loaded, missingRegistry);
            var missingEngine = new TokenEngine(server.game(), missingPlayers, missingRegistry, server::tick);
            test.assertValueEqual(missingEngine.views(player).getFirst().flags(), TokenEngine.UNKNOWN,
                    "Removing the addon leaves its saved slot marked unknown");
            test.assertValueEqual(missingPlayers.unequip(uuid, ID), PlayerTokenService.Result.CHANGED,
                    "A saved unknown token remains removable");
            test.assertValueEqual(missingPlayers.get(uuid).equipped(), List.of(builtIn),
                    "Removing the addon slot preserves the built-in with the same path");

            test.assertValueEqual(server.tokens().cooldownTicks(player, ID), 0, "Waiting has no token cooldown");
            server.game().startGame();
            hurt(player);
            test.assertTrue(hasSpeed(player), "A real accepted hit triggers the equipped addon");
            test.assertFalse(hasSpeed(ally), "Transient effects belong to one player");
            test.assertValueEqual(server.tokens().cooldownTicks(player, ID), DEFAULT.cooldownTicks(), "The addon starts its cooldown");
            replacement.connection = player.connection;
            replacement.restoreFrom(player, true);
            replacement.connection.player = replacement;
            server.respawned(player, replacement, true);
            test.assertTrue(!hasSpeed(player) && hasSpeed(replacement), "The replacement callback moves the modifier");
            test.assertValueEqual(server.tokens().cooldownTicks(replacement, ID), DEFAULT.cooldownTicks(),
                    "The replacement keeps the cooldown deadline");
            replacement.addTag(FINISH_ON_CLEANUP);
            replacement.die(replacement.damageSources().genericKill());
            test.assertTrue(!hasSpeed(replacement) && replacement.entityTags().contains(CLEANED),
                    "Downing cleans up the managed modifier and the effect instance");
            test.assertValueEqual(server.statistics().player(uuid).value(RoundMetric.DOWNS), 1.0, "The downing is committed");
            var downed = server.downed().get(uuid);
            test.assertTrue(server.revive(uuid, downed.sequence()), "The current player can be revived");
            test.assertValueEqual(server.tokens().cooldownTicks(server.currentPlayer(uuid), ID), 0, "Revival has a fresh runtime");
            hurt(server.currentPlayer(uuid));
            test.assertTrue(hasSpeed(server.currentPlayer(uuid)), "A new runtime can apply the effect");
            server.game().stopGame();
            test.assertFalse(hasSpeed(server.currentPlayer(uuid)), "Stopping clears the new runtime");
            test.assertValueEqual(server.players().get(uuid).equipped(), installed, "Cleanup does not change equipped IDs");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.players().clear(uuid);
            server.leave(server.currentPlayer(uuid));
            server.leave(ally);
        }
    }

    @GameTest
    public void addonDamageUsesTheDeclaredVanillaOrPenetratingChannel(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        server.game().stopGame();
        var attacker = GuardGameTests.fixture(test);
        var target = test.spawnWithNoFreeWill(EntityTypes.PIG, 2, 2, 2);
        var level = attacker.level();
        try {
            server.players().equip(attacker.getUUID(), ID);
            server.game().startGame();
            var position = test.absoluteVec(new Vec3(2.5, 2, 2.5));
            attacker.setPos(position.add(1, 0, 0));
            target.setPos(position);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40);
            closeTo(test, ordinary(server, attacker, target), 4, "Unarmored ordinary damage");
            target.getAttribute(Attributes.ARMOR).setBaseValue(12);
            float armored = ordinary(server, attacker, target);
            test.assertTrue(armored < 4, "Armor reduces ordinary direct damage");
            var armor = new ItemStack(Items.LEATHER_CHESTPLATE);
            armor.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
            target.setItemSlot(EquipmentSlot.CHEST, armor);
            float protectedDamage = ordinary(server, attacker, target);
            test.assertTrue(protectedDamage < armored, "Protection further reduces ordinary direct damage");
            float base = swing(attacker, target);
            target.addTag(PENETRATING);
            closeTo(test, swing(attacker, target) - base, 4, "Penetrating melee bonus bypasses armor and Protection");
            target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400));
            closeTo(test, ordinary(server, attacker, target), protectedDamage * 0.8F, "Resistance reduces ordinary damage");
            float boosted = swing(attacker, target);
            target.removeTag(PENETRATING);
            closeTo(test, boosted - swing(attacker, target), 3.2F, "Resistance reduces the bonus and leaves the base hit intact");

            target.addTag(DEBUFF);
            closeTo(test, ordinary(server, attacker, target), 0, "Applying vanilla poison has no direct damage");
            test.assertTrue(target.hasEffect(MobEffects.POISON), "The target gets the vanilla debuff");
            target.removeTag(DEBUFF);
            target.setHealth(1);
            target.damageCooldownTime = 0;
            double hitsBefore = server.statistics().player(attacker.getUUID()).value(RoundMetric.HITS_DEALT);
            fireGuard(server.tokens(), attacker, target);
            test.assertFalse(target.isAlive(), "The ordinary addon hit can kill");
            test.assertValueEqual(server.tokens().cooldownTicks(attacker, ID), 0, "The addon kill cannot recurse into a token KILL event");
            test.assertValueEqual(server.statistics().player(attacker.getUUID()).value(RoundMetric.HITS_DEALT),
                    hitsBefore + 1, "The addon hit remains attributed in round statistics");
            var finisherTarget = test.spawnWithNoFreeWill(EntityTypes.PIG, 2, 2, 2);
            finisherTarget.addTag(FINISHER);
            hitsBefore = server.statistics().player(attacker.getUUID()).value(RoundMetric.HITS_DEALT);
            attacker.resetAttackStrengthTicker();
            attacker.attack(finisherTarget);
            test.assertFalse(finisherTarget.isAlive(), "The addon finishes a nonlethal swing");
            test.assertValueEqual(server.tokens().cooldownTicks(attacker, ID), 0, "The outer swing earns no KILL");
            test.assertValueEqual(server.statistics().player(attacker.getUUID()).value(RoundMetric.HITS_DEALT),
                    hitsBefore + 2, "The swing and finisher are separate settled hits");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.players().clear(attacker.getUUID());
            server.leave(attacker);
        }
    }

    @GameTest
    public void synchronousHealingAndAsuraReserveAreMeasuredSeparately(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        server.game().stopGame();
        var player = GuardGameTests.fixture(test);
        try {
            server.players().equip(player.getUUID(), ID);
            server.players().equip(player.getUUID(), RuneLegendMod.id("asura_form"));
            server.game().startGame();
            player.setHealth(14);
            player.addTag(HEAL_ON_CHANGE);
            player.hurtServer(player.level(), player.damageSources().magic(), 4);
            closeTo(test, player.getHealth(), 12, "The HEALTH callback already restored two points");
            test.assertValueEqual(server.statistics().player(player.getUUID()).value(RoundMetric.DAMAGE_TAKEN),
                    4.0, "The whole hit is credited independently of healing");

            var enemy = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
            for (int i = 0; i < TokenConfig.AsuraFormSettings.DEFAULT.requiredKills(); i++) {
                server.tokens().dispatch(new TokenEvent(TokenEvent.Kind.KILL, player, enemy, player.level(), null, 1, 1, 0));
            }
            closeTo(test, player.getHealth(), TokenConfig.AsuraFormSettings.DEFAULT.temporaryHealth(), "Full temporary bar");
            player.addTag(HEAL_ON_GUARD);
            fireGuard(server.tokens(), player, player);
            test.assertTrue(player.removeTag(HEALED), "Healing only the reserve succeeds");
            fireGuard(server.tokens(), player, player);
            test.assertFalse(player.entityTags().contains(HEALED), "Two full bars report no healing");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.players().clear(player.getUUID());
            server.leave(player);
        }
    }

    @GameTest
    public void addonCodecUsesConfiguredValuesAndPerTokenFallback(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        server.game().stopGame();
        var player = PlayerStateFixtures.join(test.getLevel().getServer(), test.getLevel(), UUID.randomUUID());
        try {
            var extensions = TokenExtensions.freeze(new AddonTokenFixture()::registerTokens);
            var target = test.spawnWithNoFreeWill(EntityTypes.PIG, 2, 2, 2);
            server.game().startGame();
            checkSettings(test, server, player, target, extensions, """
                    {"runelegend_test:echo":{"damage":7,"cooldown_ticks":12,"effect_ticks":2}}
                    """, 7);
            checkSettings(test, server, player, target, extensions, """
                    {"runelegend_test:echo":{"damage":7,"cooldown_ticks":-1,"effect_ticks":2}}
                    """, DEFAULT.damage());
            checkSettings(test, server, player, target, extensions, """
                    {"runelegend_test:echo":[]}
                    """, DEFAULT.damage());
            test.succeed();
        } finally {
            server.game().stopGame();
            server.leave(player);
        }
    }

    private static void checkSettings(GameTestHelper test, RuneLegendServer server, ServerPlayer player, LivingEntity target,
                                      TokenExtensions extensions, String json, float damage) {
        var sections = JsonParser.parseString(json).getAsJsonObject();
        var registry = TokenRegistry.create(TokenConfig.DEFAULT, extensions, sections);
        var game = new GameController(() -> {}, (before, after) -> {});
        var players = new PlayerTokenService(game, PlayerTokenStore.inMemory(), registry);
        players.equip(player.getUUID(), ID);
        var clock = new AtomicLong(1);
        var engine = new TokenEngine(game, players, registry, clock::get, (owner, id) -> {},
                server::tokenEligible, server::currentPlayer);
        game.startGame();
        float before = reset(target);
        fireGuard(engine, player, target);
        closeTo(test, before - target.getHealth(), damage, "The effect factory receives validated server settings");
        engine.clearRound();
    }

    static boolean hasSpeed(ServerPlayer player) {
        return player.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ID) != null;
    }

    static void hurt(ServerPlayer player) {
        player.damageCooldownTime = 0;
        player.hurtServer(player.level(), player.damageSources().magic(), 2);
    }

    private static void fireGuard(TokenEngine tokens, ServerPlayer player, LivingEntity target) {
        tokens.dispatch(new TokenEvent(TokenEvent.Kind.GUARD_SUCCESS, player, target, player.level(), null, 2, 0, 2));
    }

    private static float ordinary(RuneLegendServer server, ServerPlayer attacker, LivingEntity target) {
        float before = reset(target);
        fireGuard(server.tokens(), attacker, target);
        return before - target.getHealth();
    }

    private static float swing(ServerPlayer attacker, LivingEntity target) {
        float before = reset(target);
        attacker.resetAttackStrengthTicker();
        attacker.attack(target);
        return before - target.getHealth();
    }

    private static float reset(LivingEntity target) {
        target.damageCooldownTime = 0;
        target.setHealth(target.getMaxHealth());
        return target.getHealth();
    }

    private static void closeTo(GameTestHelper test, float actual, float expected, String message) {
        test.assertTrue(Math.abs(actual - expected) < 0.01F, message + " (expected " + expected + ", got " + actual + ")");
    }
}
