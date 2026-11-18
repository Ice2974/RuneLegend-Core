package com.runelegend.token;

import com.google.gson.JsonParser;
import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.api.PlayerRoundStats;
import com.runelegend.game.GameController;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.player.PlayerTokenStore;
import com.runelegend.test.GuardGameTests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class SubstituteGameTests {
    private static final net.minecraft.resources.Identifier SUBSTITUTE = RuneLegendMod.id("substitute");

    @GameTest
    public void inclusiveThresholdCopiesThePlayerAndCooldownRechecksHealth(GameTestHelper test) {
        var owner = GuardGameTests.fixture(test);
        var other = GuardGameTests.fixture(test);
        place(test, owner, 3.5, 4.5);
        place(test, other, 6.5, 4.5);
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var game = new GameController(() -> {}, (before, after) -> {});
        var registry = TokenRegistry.builtIn();
        var players = new PlayerTokenService(game, PlayerTokenStore.inMemory(), registry);
        var clock = new AtomicLong(100);
        var engine = TokenTestEngine.forPlayers(owner, other).create(game, players, registry, clock::get);
        try {
            equip(test, players, owner, other);
            var healthBonus = new AttributeModifier(RuneLegendMod.id("substitute_test_helmet"), 4,
                    AttributeModifier.Operation.ADD_VALUE);
            var helmet = new ItemStack(Items.DIAMOND_HELMET);
            helmet.set(DataComponents.ATTRIBUTE_MODIFIERS, helmet.get(DataComponents.ATTRIBUTE_MODIFIERS)
                    .withModifierAdded(Attributes.MAX_HEALTH, healthBonus, EquipmentSlotGroup.HEAD));
            owner.setItemSlot(EquipmentSlot.HEAD, helmet);
            var maximum = owner.getAttribute(Attributes.MAX_HEALTH);
            maximum.setBaseValue(36);
            maximum.addTransientModifier(healthBonus);
            owner.setHealth(20);
            engine.updateHealth(owner);
            test.assertTrue(liveModels(owner).isEmpty(), "Waiting suppresses the token");

            game.startGame();
            owner.setHealth(21);
            engine.updateHealth(owner);
            test.assertTrue(liveModels(owner).isEmpty(), "Just above half health");
            owner.setHealth(20);
            engine.updateHealth(owner);
            var model = liveModels(owner).getFirst();
            test.assertValueEqual(model.getType(), EntityTypes.MANNEQUIN, "Vanilla entity type");
            test.assertValueEqual(model.getProfile().partialProfile(), owner.getGameProfile(), "Skin profile");
            var armor = model.getItemBySlot(EquipmentSlot.HEAD);
            test.assertTrue(ItemStack.matches(helmet, armor) && armor != helmet, "Independent armor copy");
            model.tick();
            test.assertValueEqual(model.getMaxHealth(), 40F, "Equipment does not double-count the maximum");
            test.assertValueEqual(model.getHealth(), 40F, "Starts at full health");
            test.assertTrue(!model.hurtServer(level, level.damageSources().playerAttack(other), 4),
                    "Players cannot damage the substitute");
            test.assertValueEqual(view(engine, owner).flags(), TokenEngine.ACTIVE | TokenEngine.TRIGGERED, "Active HUD");
            test.assertValueEqual(view(engine, owner).activeUntil(), 500L, "Default lifetime is 20 seconds");

            owner.setHealth(15);
            engine.updateHealth(owner);
            test.assertValueEqual(liveModels(owner).size(), 1, "One model per owner");
            other.setHealth(other.getMaxHealth() / 2);
            engine.updateHealth(other);
            var otherModel = liveModels(other).getFirst();
            model.hurtServer(level, level.damageSources().genericKill(), 1000);
            test.assertValueEqual(view(engine, owner).flags(), TokenEngine.COOLDOWN, "Death starts cooldown");
            test.assertValueEqual(view(engine, owner).cooldownUntil(), 900L, "40 seconds from death");
            test.assertTrue(otherModel.isAlive(), "The other player is unaffected");
            clock.set(899);
            engine.tick(List.of(owner, other));
            test.assertTrue(liveModels(owner).isEmpty(), "No early respawn");
            clock.set(900);
            engine.tick(List.of(owner, other));
            var replacement = liveModels(owner).getFirst();
            test.assertTrue(replacement != model, "Continued low health automatically creates another model");

            replacement.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            test.assertValueEqual(view(engine, owner).cooldownUntil(), 1700L, "Unload starts cooldown");
            owner.setHealth(30);
            clock.set(1700);
            engine.tick(List.of(owner, other));
            test.assertTrue(liveModels(owner).isEmpty(), "Recovery before expiry prevents resummoning");
            test.succeed();
        } finally {
            engine.clearRound();
            server.leave(owner);
            server.leave(other);
        }
    }

    @GameTest
    public void substituteDiesAtConfiguredDeadlineEvenAfterAnEarlyKill(GameTestHelper test) {
        var owner = GuardGameTests.fixture(test);
        place(test, owner, 3.5, 4.5);
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var game = new GameController(() -> {}, (before, after) -> {});
        var config = TokenConfig.fromJson(JsonParser.parseString("""
                {"runelegend:substitute":{"duration_seconds":3,"cooldown_seconds":1}}
                """).getAsJsonObject());
        var registry = TokenRegistry.builtIn(config);
        var players = new PlayerTokenService(game, PlayerTokenStore.inMemory(), registry);
        var clock = new AtomicLong(100);
        var online = List.of(owner);
        var engine = TokenTestEngine.forPlayers(owner).create(game, players, registry, clock::get);
        try {
            equip(test, players, owner);
            game.startGame();
            owner.setHealth(owner.getMaxHealth() / 2);
            engine.tick(online);
            var original = liveModels(owner).getFirst();
            clock.set(110);
            original.kill(level);
            test.assertValueEqual(view(engine, owner).cooldownUntil(), 130L, "Early death starts cooldown immediately");

            clock.set(130);
            engine.tick(online);
            var replacement = liveModels(owner).getFirst();
            test.assertTrue(replacement != original, "Cooldown summons a new substitute");
            clock.set(160);
            engine.tick(online);
            test.assertTrue(replacement.isAlive(), "The first deadline does not affect the replacement");
            clock.set(189);
            engine.tick(online);
            test.assertTrue(replacement.isAlive(), "Still alive one tick before its deadline");
            test.assertValueEqual(replacement.getHealth(), replacement.getMaxHealth(), "No monsters have damaged it");

            clock.set(190);
            engine.tick(online);
            test.assertTrue(replacement.isDeadOrDying() && !replacement.isRemoved(), "Expiry runs normal death");
            test.assertValueEqual(view(engine, owner).flags(), TokenEngine.COOLDOWN, "Expiry ends the active state");
            test.assertValueEqual(view(engine, owner).cooldownUntil(), 210L, "Cooldown starts at expiry");
            test.succeed();
        } finally {
            engine.clearRound();
            server.leave(owner);
        }
    }

    @GameTest
    public void hostileGoalBrainAndWitherHeadsTargetTheSubstitute(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var owner = GuardGameTests.fixture(test);
        var teammate = GuardGameTests.fixture(test);
        var zombie = test.spawn(EntityTypes.ZOMBIE, new Vec3(1.5, 2, 4.5));
        var skeleton = test.spawn(EntityTypes.SKELETON, new Vec3(1.5, 2, 3.5));
        var hoglin = test.spawn(EntityTypes.HOGLIN, new Vec3(2.5, 2, 5.5));
        var piglin = test.spawn(EntityTypes.PIGLIN, new Vec3(2.5, 2, 3.5));
        var warden = test.spawn(EntityTypes.WARDEN, new Vec3(2.5, 2, 2.5));
        var wither = test.spawn(EntityTypes.WITHER, new Vec3(1.5, 2, 2.5));
        try {
            server.game().stopGame();
            equip(test, server.players(), owner);
            server.game().startGame();
            level.addNewPlayer(teammate);
            place(test, owner, 4.5, 4.5);
            place(test, teammate, 7.5, 4.5);
            zombie.setTarget(teammate);
            wither.setAlternativeTarget(1, teammate.getId());
            test.assertValueEqual(zombie.getTarget(), teammate, "Initial player target");
            owner.setHealth(owner.getMaxHealth() / 2);
            var model = liveModels(owner).getFirst();
            zombie.tick();
            test.assertValueEqual(zombie.getTarget(), model, "Existing target is redirected before attack goals");
            float health = model.getHealth();
            zombie.doHurtTarget(level, zombie.getTarget());
            test.assertTrue(model.getHealth() < health, "The redirected attack damages the model");
            test.assertValueEqual(server.statistics().player(owner.getUUID()), PlayerRoundStats.EMPTY,
                    "The substitute's damage does not count against its owner");
            test.assertValueEqual(server.statistics().player(teammate.getUUID()), PlayerRoundStats.EMPTY,
                    "The substitute's damage does not count against the original target");

            skeleton.setTarget(teammate);
            test.assertValueEqual(skeleton.getTarget(), model, "New ranged target is redirected");
            skeleton.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1);
            skeleton.setTarget(teammate);
            test.assertValueEqual(skeleton.getTarget(), teammate, "Out-of-range model is ignored");
            skeleton.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(16);
            test.setBlock(3, 2, 4, Blocks.STONE);
            test.setBlock(3, 3, 4, Blocks.STONE);
            zombie.getSensing().tick();
            zombie.setTarget(teammate);
            test.assertValueEqual(zombie.getTarget(), teammate, "Occluded model is ignored");
            test.setBlock(3, 2, 4, Blocks.AIR);
            test.setBlock(3, 3, 4, Blocks.AIR);
            zombie.getSensing().tick();

            var attack = MemoryModuleType.ATTACK_TARGET;
            hoglin.getBrain().setMemoryWithExpiry(attack, teammate, 30);
            hoglin.getBrain().tick(level, hoglin);
            test.assertValueEqual(hoglin.getTarget(), model, "Existing Brain target");
            test.assertValueEqual(hoglin.getBrain().getTimeUntilExpiry(attack), 29L, "Brain expiry is preserved");
            piglin.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, teammate);
            piglin.getBrain().setMemory(attack, teammate);
            piglin.getBrain().tick(level, piglin);
            test.assertValueEqual(piglin.getTarget(), model, "Piglin retains the redirected target");

            warden.increaseAngerAt(teammate, 100, false);
            warden.setAttackTarget(teammate);
            warden.getBrain().tick(level, warden);
            test.assertValueEqual(warden.getTarget(), model, "Warden combat uses the substitute");
            test.assertTrue(warden.getAngerLevel().isAngry(), "Warden anger continues to use its original suspect");

            wither.tick();
            test.assertValueEqual(wither.getAlternativeTarget(1), model.getId(), "Existing Wither side-head target");
            wither.setAlternativeTarget(2, teammate.getId());
            test.assertValueEqual(wither.getAlternativeTarget(2), model.getId(), "New Wither side-head target");
            model.hurtServer(level, level.damageSources().genericKill(), 1000);
            skeleton.setTarget(teammate);
            test.assertValueEqual(skeleton.getTarget(), teammate, "Normal targeting resumes after death");
            wither.setAlternativeTarget(1, teammate.getId());
            test.assertValueEqual(wither.getAlternativeTarget(1), teammate.getId(), "Wither side head can reacquire a player");
            test.succeed();
        } finally {
            cleanup(server, owner, teammate);
            level.removePlayerImmediately(teammate, Entity.RemovalReason.DISCARDED);
        }
    }

    @GameTest
    public void downDisconnectAndStopRevokeOwnedModels(GameTestHelper test) {
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var first = GuardGameTests.fixture(test);
        var second = GuardGameTests.fixture(test);
        var third = GuardGameTests.fixture(test);
        try {
            server.game().stopGame();
            equip(test, server.players(), first, second, third);
            server.game().startGame();
            place(test, first, 2.5, 4.5);
            place(test, second, 4.5, 4.5);
            place(test, third, 6.5, 4.5);
            for (var player : List.of(first, second, third)) player.setHealth(player.getMaxHealth() / 2);
            var firstModel = liveModels(first).getFirst();
            var secondModel = liveModels(second).getFirst();
            var thirdModel = liveModels(third).getFirst();
            first.hurtServer(level, level.damageSources().magic(), 100);
            test.assertTrue(firstModel.isRemoved() && secondModel.isAlive(), "Downing clears only that player's model");
            server.leave(second);
            test.assertTrue(secondModel.isRemoved() && thirdModel.isAlive(), "Disconnect revokes the owned model");
            server.game().stopGame();
            test.assertTrue(thirdModel.isRemoved(), "Stopping the round removes any remaining models");
            test.succeed();
        } finally {
            cleanup(server, first, second, third);
        }
    }

    private static void equip(GameTestHelper test, PlayerTokenService service, ServerPlayer... players) {
        for (var player : players) {
            test.assertValueEqual(service.equip(player.getUUID(), SUBSTITUTE), PlayerTokenService.Result.CHANGED,
                    "Equip Substitute");
        }
    }

    private static void cleanup(RuneLegendServer server, ServerPlayer... players) {
        server.game().stopGame();
        for (var player : players) {
            server.players().clear(player.getUUID());
            server.leave(player);
        }
    }

    private static void place(GameTestHelper test, ServerPlayer player, double x, double z) {
        player.setPos(test.absoluteVec(new Vec3(x, 2, z)));
    }

    private static List<SubstituteMannequin> liveModels(ServerPlayer player) {
        return player.level().getEntitiesOfClass(SubstituteMannequin.class, player.getBoundingBox().inflate(0.25),
                SubstituteMannequin::isAlive);
    }

    private static TokenEngine.View view(TokenEngine engine, ServerPlayer player) {
        return engine.views(player).getFirst();
    }
}
