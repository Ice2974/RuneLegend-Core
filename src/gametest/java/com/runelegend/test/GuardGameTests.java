package com.runelegend.test;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.token.TokenEngine;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.UpdateInterval;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.function.Predicate;

public final class GuardGameTests {
    public static final String SWORD = "minecraft:diamond_sword[minecraft:blocks_attacks={block_delay_seconds:0.0f,"
            + "disable_cooldown_scale:0.0f,damage_reductions:[{"
            + "type:\"#runelegend:sword_guardable\",base:0.0f,factor:0.5f}],bypassed_by:\"#runelegend:sword_guard_bypasses\"},"
            + "minecraft:use_effects={can_sprint:false,speed_multiplier:0.2f}]";
    private static final String FULL_SWORD = SWORD.replace("factor:0.5f", "factor:1.0f");

    @GameTest
    public void componentSwordUsesVanillaReductionAndDurability(GameTestHelper test) {
        var player = fixture(test);
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var difficulty = test.getLevel().getDifficulty();
        test.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        var origin = test.absoluteVec(new Vec3(2.5, 2, 2.5));
        var mob = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        try {
            server.game().stopGame();
            reset(player, origin, 20);
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
            player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
            test.assertTrue(!player.isUsingItem(), "An ordinary diamond sword cannot block");

            start(test, player, SWORD);
            player.setXRot(-85);
            mob.setPos(origin.add(0, 0, 1));
            hit(test, player, player.damageSources().mobAttack(mob), 17, "Front hit is halved in WAITING");
            test.assertTrue(player.getMainHandItem().getDamageValue() > 0, "Vanilla charges durability");
            reset(player, origin, 20);
            mob.setPos(origin.add(1, 0, 0));
            hit(test, player, player.damageSources().mobAttack(mob), 17, "Vanilla includes the exact side");
            reset(player, origin, 20);
            mob.setPos(origin.add(0, 0, -1));
            hit(test, player, player.damageSources().mobAttack(mob), 14, "Back hit is not reduced");
            reset(player, origin, 20);
            player.setYHeadRot(180);
            hit(test, player, player.damageSources().mobAttack(mob), 17, "Turning the head changes the blocking direction");
            reset(player, origin, 20);
            hit(test, player, player.damageSources().magic(), 14, "Damage outside the allow tag is not reduced");

            release(player);
            start(test, player, SWORD.substring(0, SWORD.length() - 1) + ",minecraft:unbreakable={}]");
            reset(player, origin, 20);
            mob.setPos(origin.add(0, 0, 1));
            hit(test, player, player.damageSources().mobAttack(mob), 17, "An unbreakable sword still blocks");
            test.assertValueEqual(player.getMainHandItem().getDamageValue(), 0, "Vanilla unbreakable prevents durability loss");
            test.succeed();
        } finally {
            test.getLevel().getServer().setDifficulty(difficulty, true);
            server.leave(player);
        }
    }

    @GameTest
    public void projectileAndExplosionUseTheirOwnOrigins(GameTestHelper test) {
        var player = fixture(test);
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var difficulty = level.getDifficulty();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        var origin = test.absoluteVec(new Vec3(2.5, 2, 2.5));
        var shooter = test.spawnWithNoFreeWill(EntityTypes.SKELETON, 2, 2, 1);
        var arrow = test.spawn(EntityTypes.ARROW, 2, 2, 2);
        reset(player, origin, 20);
        level.addNewPlayer(player);
        try {
            start(test, player, SWORD);
            shooter.setPos(origin.add(0, 0, -3));
            arrow.setDeltaMovement(0, 0, -2);
            hit(test, player, player.damageSources().arrow(arrow, shooter), 17, "Front arrow despite shooter behind");
            reset(player, origin, 20);
            shooter.setPos(origin.add(0, 0, 3));
            arrow.setDeltaMovement(0, 0, 2);
            hit(test, player, player.damageSources().arrow(arrow, shooter), 14, "Rear arrow despite shooter in front");
            reset(player, origin, 20);
            arrow.setDeltaMovement(0, -1, 0);
            hit(test, player, player.damageSources().arrow(arrow, shooter), 14, "A vertical arrow has no horizontal direction");
            reset(player, origin, 20);
            var crossbow = new ItemStack(Items.CROSSBOW);
            crossbow.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PIERCING), 1);
            var piercingArrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), crossbow);
            piercingArrow.setDeltaMovement(0, 0, -2);
            test.assertValueEqual((int) piercingArrow.getPierceLevel(), 1, "Piercing is active on the arrow");
            hit(test, player, player.damageSources().arrow(piercingArrow, shooter), 14, "Vanilla piercing bypasses the component");
            reset(player, origin, 20);
            var fixedDamage = new ExplosionDamageCalculator() {
                @Override public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) { return 6; }
            };
            level.explode(null, null, fixedDamage, origin.add(0, 0, 1), 1, false, Level.ExplosionInteraction.NONE);
            test.assertValueEqual(player.getHealth(), 17F, "A front explosion without an entity uses its center");
            reset(player, origin, 20);
            level.explode(shooter, null, fixedDamage, origin.add(0, 0, -1), 1, false, Level.ExplosionInteraction.NONE);
            test.assertValueEqual(player.getHealth(), 14F, "A rear explosion is not blocked even when the source entity is in front");
            reset(player, origin, 20);
            hit(test, player, player.damageSources().explosion(shooter, null), 14, "An explosion without a known center is not blocked");
            test.succeed();
        } finally {
            level.getServer().setDifficulty(difficulty, true);
            server.leave(player);
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }

    @GameTest
    public void guardSuccessUsesSettledVanillaBlocksOnly(GameTestHelper test) {
        var player = fixture(test);
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        var difficulty = level.getDifficulty();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        var origin = test.absoluteVec(new Vec3(2.5, 2, 2.5));
        var mob = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 3);
        try {
            server.game().stopGame();
            test.assertValueEqual(server.players().equip(player.getUUID(), RuneLegendMod.id("immediate_counter")),
                    PlayerTokenService.Result.CHANGED, "Equip Immediate Counter");
            start(test, player, SWORD);
            reset(player, origin, 20);
            mob.setPos(origin.add(0, 0, 1));
            hit(test, player, player.damageSources().mobAttack(mob), 17, "Guard in WAITING");
            test.assertTrue(!counterActive(server, player), "WAITING does not dispatch GUARD_SUCCESS");

            server.game().startGame();
            test.assertTrue(player.isBlocking(), "Starting a game does not stop vanilla item use");
            reset(player, origin, 20);
            mob.setPos(origin.add(0, 0, -1));
            var source = player.damageSources().mobAttack(mob);
            hit(test, player, source, 14, "Rear hit");
            test.assertTrue(!counterActive(server, player), "A zero block cannot activate the token");
            mob.setPos(origin.add(0, 0, 1));
            test.assertTrue(!player.hurtServer(level, source, 6), "A weaker hit in vanilla cooldown is ignored");
            test.assertTrue(!counterActive(server, player), "An unsettled hit cannot activate the token");
            reset(player, origin, 20);
            hit(test, player, source, 17, "Front half block");
            test.assertTrue(counterActive(server, player), "Partial vanilla block activates Immediate Counter");
            test.assertValueEqual(server.tokens().melee(player, 6).bonusDamage(), 3F, "Counter adds 3 HP to melee");

            server.game().stopGame();
            test.assertTrue(player.isBlocking(), "Stopping a game does not lower a valid block");
            test.assertTrue(!counterActive(server, player), "Stopping clears the token");
            release(player);
            start(test, player, FULL_SWORD);
            server.game().startGame();
            reset(player, origin, 20);
            test.assertTrue(!player.hurtServer(level, source, 6), "Vanilla returns false for a full block");
            test.assertValueEqual(player.getHealth(), 20F, "Full block removes all incoming damage");
            test.assertTrue(counterActive(server, player), "A settled full block can activate the token");
            test.succeed();
        } finally {
            level.getServer().setDifficulty(difficulty, true);
            server.game().stopGame();
            server.players().clear(player.getUUID());
            server.leave(player);
        }
    }

    @GameTest
    public void staminaOnlyAppliesWhileRunning(GameTestHelper test) {
        var player = fixture(test);
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var stamina = server.stamina();
        var guard = stamina.config();
        int exhaustionTicks = Math.ceilDiv(guard.max(), guard.drainPerTick());
        try {
            server.game().stopGame();
            start(test, player, SWORD);
            for (int i = 0; i <= exhaustionTicks; i++) stamina.tick(player);
            test.assertValueEqual(stamina.stamina(player), guard.max(), "WAITING never drains stamina");
            test.assertTrue(player.isBlocking(), "WAITING can block beyond the normal exhaustion time");

            server.game().startGame();
            // Mock players are not on the online list used by the round-reset callback.
            stamina.resetRound(player);
            stamina.tick(player);
            test.assertValueEqual(stamina.stamina(player), guard.max() - guard.drainPerTick(),
                    "Starting begins draining an already active block");
            test.assertTrue(player.isBlocking(), "Starting preserves the active block");

            server.game().stopGame();
            stamina.resetRound(player);
            for (int i = 0; i <= exhaustionTicks; i++) stamina.tick(player);
            test.assertValueEqual(stamina.stamina(player), guard.max(), "Stopping restores and preserves full stamina");
            test.assertTrue(player.isBlocking(), "Stopping preserves the active block");

            server.game().startGame();
            stamina.resetRound(player);
            for (int i = 0; i < exhaustionTicks; i++) stamina.tick(player);
            test.assertValueEqual(stamina.stamina(player), 0, "A new round still exhausts the player");
            test.assertTrue(!player.isUsingItem(), "RUNNING still stops an exhausted block");

            server.game().stopGame();
            // Before resetting this mock, exercise WAITING with both low stamina and a pending release.
            use(player);
            test.assertTrue(player.isBlocking(), "WAITING ignores the stamina threshold and exhaustion release lock");
            stamina.tick(player);
            test.assertTrue(player.isBlocking(), "WAITING never stops a block due to zero stamina");
            player.stopUsingItem();
            server.game().startGame();
            stamina.resetRound(player);
            use(player);
            test.assertTrue(player.isBlocking(), "A new round clears an inactive block's old release lock");
            test.succeed();
        } finally {
            server.game().stopGame();
            server.leave(player);
        }
    }

    @GameTest(maxTicks = 50)
    public void exhaustionRequiresReleaseAndRecovery(GameTestHelper test) {
        var player = fixture(test);
        var other = fixture(test);
        var server = RuneLegendServer.find(test.getLevel().getServer());
        var stamina = server.stamina();
        var guard = stamina.config();
        int exhaustionTicks = Math.ceilDiv(guard.max(), guard.drainPerTick());
        int staminaBeforeExhaustion = guard.max() - (exhaustionTicks - 1) * guard.drainPerTick();
        int recoveryTicks = guard.regenDelayTicks() + Math.ceilDiv(guard.minToStart(), guard.regenPerTick());
        int recoveredStamina = Math.min(guard.max(), guard.regenPerTick() * (recoveryTicks - guard.regenDelayTicks()));
        server.game().startGame();
        start(test, player, SWORD);
        player.setSprinting(true);
        test.assertTrue(!player.isSprinting(), "The server rejects sprint with the configured use effects");
        test.assertTrue(player.cannotAttackWithItem(player.getMainHandItem(), 5), "Vanilla melee packets are denied while using");
        for (int i = 0; i < exhaustionTicks - 1; i++) stamina.tick(player);
        test.assertValueEqual(stamina.stamina(player), staminaBeforeExhaustion,
                (exhaustionTicks - 1) + " ticks leave " + staminaBeforeExhaustion + " stamina");
        test.assertTrue(player.isBlocking(), "Player is still blocking one tick before exhaustion");
        stamina.tick(player);
        test.assertValueEqual(stamina.stamina(player), 0, exhaustionTicks + " ticks exhaust the player");
        test.assertTrue(!player.isUsingItem(), "Exhaustion ends vanilla item use");
        test.assertValueEqual(stamina.stamina(other), guard.max(), "The other player's stamina is untouched");
        release(player);
        use(player);
        test.assertTrue(!player.isUsingItem(), "A fresh press at zero stamina fails");
        // In this fixture the mock player isn't on the online tick list, so tick its stamina once per game tick.
        for (int i = 1; i < recoveryTicks; i++) test.runAfterDelay(i, () -> {
            // Other tests in this batch can change the shared game state.
            server.game().startGame();
            stamina.tick(player);
        });
        test.runAfterDelay(recoveryTicks, () -> {
            try {
                server.game().startGame();
                stamina.tick(player);
                test.assertValueEqual(stamina.stamina(player), recoveredStamina,
                        "Recovery reaches the start threshold after " + recoveryTicks + " ticks");
                use(player);
                test.assertTrue(!player.isUsingItem(), "A repeated request cannot rearm the press");
                release(player);
                use(player);
                test.assertTrue(player.isBlocking(), "A new press at the configured start threshold can block");
                player.getInventory().setItem(1, player.getMainHandItem().copy());
                player.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(1));
                test.assertTrue(!player.isUsingItem(), "Vanilla slot switching drops the block even with identical swords");
                release(player);
                use(player);
                test.assertTrue(player.isBlocking(), "Another real release and press starts the new sword");
                player.getInventory().setItem(1, new ItemStack(Items.DIAMOND_SWORD));
                stamina.tick(player);
                test.assertTrue(!player.isUsingItem(), "Same-slot replacement drops the block");
                stamina.leave(player);
                stamina.join(player);
                test.assertValueEqual(stamina.stamina(player), guard.max(), "A new connection gets full stamina");
                test.succeed();
            } finally {
                server.game().stopGame();
                server.leave(player);
                server.leave(other);
            }
        });
    }

    @GameTest
    public void newObserversReceiveEquipmentBeforeTheActiveUseFlag(GameTestHelper test) {
        var player = fixture(test);
        var viewer = fixture(test);
        var level = test.getLevel();
        var server = RuneLegendServer.find(level.getServer());
        try {
            start(test, player, SWORD);
            var tracker = new ServerEntity(level, player, UpdateInterval.periodic(1), false, new ServerEntity.Synchronizer() {
                @Override public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {}
                @Override public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {}
                @Override public void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet,
                                                                      Predicate<ServerPlayer> recipients) {}
            });
            var pairing = new ArrayList<Packet<ClientGamePacketListener>>();
            tracker.sendPairingData(viewer, pairing::add);
            int equipment = -1, useData = -1;
            for (int i = 0; i < pairing.size(); i++) {
                if (pairing.get(i) instanceof ClientboundSetEquipmentPacket) equipment = i;
                if (pairing.get(i) instanceof ClientboundSetEntityDataPacket) useData = i;
            }
            test.assertTrue(equipment >= 0 && useData > equipment,
                    "The native use flag must arrive after the newly tracking client knows the active hand stack");
            test.succeed();
        } finally {
            server.leave(player);
            server.leave(viewer);
        }
    }

    public static ServerPlayer fixture(GameTestHelper test) {
        var player = (ServerPlayer) test.makeMockServerPlayer(GameType.SURVIVAL);
        var server = test.getLevel().getServer();
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        RuneLegendServer.find(server).stamina().join(player);
        player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        return player;
    }

    private static boolean counterActive(RuneLegendServer server, ServerPlayer player) {
        return server.tokens().views(player).stream().anyMatch(token -> (token.flags() & TokenEngine.ACTIVE) != 0);
    }

    private static void start(GameTestHelper test, ServerPlayer player, String item) {
        try {
            var stack = new ItemParser(player.level().registryAccess()).parse(new StringReader(item)).createItemStack(1);
            player.getInventory().setItem(player.getInventory().getSelectedSlot(), stack);
        } catch (CommandSyntaxException ex) {
            throw new AssertionError(ex);
        }
        use(player);
        test.assertTrue(player.isBlocking(), "The component starts vanilla blocking");
    }

    private static void use(ServerPlayer player) {
        player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
    }

    private static void release(ServerPlayer player) {
        player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
    }

    private static void reset(ServerPlayer player, Vec3 origin, float health) {
        player.setPos(origin);
        player.setYRot(0);
        player.setYHeadRot(0);
        player.setHealth(health);
        player.damageCooldownTime = 0;
    }

    private static void hit(GameTestHelper test, ServerPlayer player, DamageSource source, float remaining, String message) {
        test.assertTrue(player.hurtServer(test.getLevel(), source, 6), "Vanilla should accept the hit");
        test.assertValueEqual(player.getHealth(), remaining, message);
    }
}
