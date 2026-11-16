package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.player.PlayerState;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.player.PlayerTokenStore;
import com.runelegend.test.PlayerStateFixtures;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import java.util.*;
import java.util.concurrent.atomic.*;

public final class PlayerStateTokenGameTests {
    @GameTest
    public void livingEntityReplacementKeepsFormDeadlineAndBindsCleanupToNewBody(GameTestHelper test) {
        var minecraft = test.getLevel().getServer();
        var server = RuneLegendServer.find(minecraft);
        server.game().stopGame();
        var original = PlayerStateFixtures.join(minecraft, test.getLevel(), UUID.randomUUID());
        var registry = TokenRegistry.builtIn(TokenConfig.DEFAULT);
        var players = new PlayerTokenService(server.game(), PlayerTokenStore.inMemory(), registry);
        var clock = new AtomicLong(100);
        var tokens = new TokenEngine(server.game(), players, registry, clock::get, (p, token) -> {},
                server::tokenEligible, server::currentPlayer);
        var replacement = new net.minecraft.server.level.ServerPlayer(minecraft, test.getLevel(), original.getGameProfile(),
                net.minecraft.server.level.ClientInformation.createDefault());
        try {
            players.equip(original.getUUID(), RuneLegendMod.id("asura_form"));
            server.game().startGame();
            original.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(24);
            original.setHealth(18);
            var enemy = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
            for (int i = 0; i < 3; i++) tokens.dispatch(new TokenEvent(TokenEvent.Kind.KILL, original, enemy, test.getLevel(), null, 0, 0, 0));
            long deadline = tokens.views(original).getFirst().activeUntil();
            replacement.connection = original.connection;
            replacement.restoreFrom(original, true);
            original.connection.player = replacement;
            server.respawned(original, replacement, true);
            test.assertValueEqual(replacement.getMaxHealth(), 16F, "Temporary bar follows the replacement body");
            test.assertValueEqual(original.getMaxHealth(), 24F, "Old body releases the temporary attribute");
            test.assertValueEqual(tokens.views(replacement).getFirst().activeUntil(), deadline, "Entity replacement does not extend duration");
            clock.set(deadline);
            tokens.tick(List.of(replacement));
            test.assertValueEqual(replacement.getMaxHealth(), 24F, "Scheduled cleanup targets the current body");
            test.assertValueEqual(replacement.getHealth(), 18F, "The saved normal health survives living replacement");
            test.succeed();
        } finally {
            tokens.clearRound(); server.game().stopGame();
            server.leave(server.currentPlayer(original.getUUID()));
        }
    }

    @GameTest
    public void inactivePlayersAndStaleContextsCannotApplyOrRecreateEffects(GameTestHelper test) {
        var server = RuneLegendServer.find(test.getLevel().getServer());
        server.game().stopGame();
        var caster = PlayerStateFixtures.join(test.getLevel().getServer(), test.getLevel(), UUID.randomUUID());
        var recipient = PlayerStateFixtures.join(test.getLevel().getServer(), test.getLevel(), UUID.randomUUID());
        var target = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
        var clock = new AtomicLong(100);
        var contexts = new ArrayList<TokenEngine.Context>();
        var pulses = new AtomicInteger();
        var removed = new AtomicInteger();
        var healthEvents = new AtomicInteger();
        var reference = new AtomicReference<TokenEngine>();
        var id = RuneLegendMod.id("state_gate_fixture");
        var registry = new TokenRegistry.Builder().register(new TokenDefinition(id, Set.of(TokenEvent.Kind.KILL, TokenEvent.Kind.HEALTH),
                new TokenEffectHandler() {
                    @Override public void onEvent(TokenEngine.Context context, TokenRuntimeState state, TokenEvent event) {
                        if (event.kind() == TokenEvent.Kind.HEALTH) { healthEvents.incrementAndGet(); return; }
                        contexts.add(context);
                        context.replaceEffect("active", () -> {
                            removed.incrementAndGet();
                            // Attribute cleanup can synchronously produce a health event.
                            caster.setHealth(2);
                            reference.get().updateHealth(caster);
                        });
                        context.at("active", context.tick() + 5, pulses::incrementAndGet);
                        context.at("unowned", context.tick() + 6, pulses::incrementAndGet);
                    }
                })).build();
        var players = new PlayerTokenService(server.game(), PlayerTokenStore.inMemory(), registry);
        var tokens = new TokenEngine(server.game(), players, registry, clock::get, (p, token) -> {},
                server::tokenEligible, server::currentPlayer);
        reference.set(tokens);
        try {
            players.equip(caster.getUUID(), id);
            server.game().startGame();
            var event = new TokenEvent(TokenEvent.Kind.KILL, caster, target, test.getLevel(), null, 0, 0, 0);
            tokens.dispatch(event);
            var old = contexts.getFirst();
            test.assertTrue(old.canHeal(recipient), "ALIVE players may receive ordinary healing");
            float health = recipient.getHealth();
            old.damage(test.getLevel(), recipient, 100);
            test.assertValueEqual(recipient.getHealth(), health, "Central damage sink excludes every player");
            test.assertTrue(!TokenTargeting.enemy(recipient), "Player types are excluded before tag classification");
            recipient.die(recipient.damageSources().genericKill());
            recipient.setGameMode(GameType.ADVENTURE);
            test.assertTrue(!old.canHeal(recipient), "Changing game mode cannot make DOWNED a healing target");
            caster.die(caster.damageSources().genericKill());
            var record = server.downed().get(caster.getUUID());
            tokens.clearEffects(caster.getUUID()); tokens.clearEffects(caster.getUUID());
            test.assertValueEqual(removed.get(), 1, "Cleanup is idempotent");
            test.assertValueEqual(healthEvents.get(), 0, "Cleanup health callbacks cannot recreate runtime");
            tokens.dispatch(event);
            test.assertValueEqual(contexts.size(), 1, "DOWNED cannot dispatch a token event");
            test.assertTrue(server.revive(caster.getUUID(), record.sequence()), "Caster can be revived");
            test.assertTrue(!old.valid(), "Revival does not reactivate an old context");
            old.at("stale", 101, pulses::incrementAndGet);
            old.damage(test.getLevel(), target, 100);
            test.assertTrue(target.isAlive(), "Stale contexts cannot damage even after revival");
            clock.set(110); tokens.tick(List.of(caster, recipient));
            test.assertValueEqual(pulses.get(), 0, "Owned and unowned old tasks are both cancelled");
            tokens.dispatch(event);
            test.assertTrue(contexts.getLast().valid(), "Fresh state has a new valid generation");
            old.finish("active");
            test.assertValueEqual(removed.get(), 1, "Old contexts cannot revoke a newly activated effect");
            // Execution-time gating also protects tasks when eligibility changes before explicit cleanup.
            server.playerStates().eliminate(caster.getUUID());
            clock.set(120); tokens.tick(List.of(caster));
            test.assertValueEqual(pulses.get(), 0, "Delayed callbacks recheck current eligibility");
            test.assertValueEqual(removed.get(), 2, "Ineligible execution revokes owned resources");
            test.assertValueEqual(server.playerStates().state(caster.getUUID()), PlayerState.ELIMINATED, "Cleanup cannot restore ALIVE");
            tokens.clearRound(); tokens.clearRound();
            test.succeed();
        } finally {
            tokens.clearRound(); server.game().stopGame(); server.leave(caster); server.leave(recipient);
        }
    }
}
