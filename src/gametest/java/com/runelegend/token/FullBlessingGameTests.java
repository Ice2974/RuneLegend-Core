package com.runelegend.token;

import com.google.gson.JsonParser;
import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
import com.runelegend.player.PlayerState;
import com.runelegend.player.PlayerTokenService;
import com.runelegend.player.PlayerTokenStore;
import com.runelegend.test.PlayerStateFixtures;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class FullBlessingGameTests {
    private static final Identifier FULL_BLESSING = FullBlessingEffect.ID;

    @GameTest
    public void blessesAllEligiblePlayersInOneTick(GameTestHelper test) {
        try (var fixture = new Fixture(test)) {
            var server = fixture.server;
            var caster = fixture.join();
            var recipient = fixture.join();
            var spectator = fixture.join();
            var downed = fixture.join();
            fixture.equip(caster, FULL_BLESSING);
            var arrogance = RuneLegendMod.id("arrogance");
            fixture.equip(caster, arrogance);
            server.playerStates().selectSpectator(spectator.getUUID(), true);
            caster.setHealth(10);
            test.assertValueEqual(caster.getHealth(), 10F, "WAITING suppresses the token");
            caster.setHealth(20);
            server.game().startGame();

            var nether = test.getLevel().getServer().getLevel(Level.NETHER);
            test.assertTrue(recipient.teleportTo(nether, 0.5, 64, 0.5, Set.of(), 0, 0, false), "Enter the Nether");
            recipient.setHealth(6);
            downed.die(downed.damageSources().genericKill());
            downed.setGameMode(GameType.ADVENTURE);
            downed.setHealth(6);
            spectator.setHealth(6);

            var enemy = test.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
            server.tokens().dispatch(new TokenEvent(TokenEvent.Kind.KILL, caster, enemy, caster.level(), null, 1, 1, 0));
            test.assertTrue((server.tokens().views(caster).get(1).flags() & TokenEngine.ACTIVE) != 0, "Arrogance gained progress");
            caster.setHealth(12.5F);
            test.assertValueEqual(recipient.getHealth(), 6F, "Above the threshold the token is idle");
            caster.setHealth(10);
            test.assertValueEqual(caster.getHealth(), 14F, "The caster receives its own blessing at exactly 50%");
            test.assertValueEqual(recipient.getHealth(), 14F, "Same-tick steps in the Nether go from 6 to 10 to 14 HP");
            test.assertValueEqual(downed.getHealth(), 6F, "A DOWNED player cannot be healed even in Adventure mode");
            test.assertValueEqual(spectator.getHealth(), 6F, "A SPECTATOR cannot be healed");
            test.assertTrue((server.tokens().views(caster).get(1).flags() & TokenEngine.ACTIVE) == 0,
                    "Arrogance observes low health before Full Blessing heals the player");

            var view = server.tokens().views(caster).getFirst();
            test.assertValueEqual(view.flags(), TokenEngine.TRIGGERED | TokenEngine.COOLDOWN, "Trigger flash and cooldown flags");
            test.assertValueEqual(view.cooldownUntil(), server.tick() + 400, "Default cooldown starts on the same tick");
            recipient.setHealth(10);
            test.assertValueEqual(recipient.getHealth(), 14F, "The unequipped recipient kept one blessing from the batch");
            recipient.setHealth(10);
            test.assertValueEqual(recipient.getHealth(), 10F, "The other two blessings were already used");
            test.succeed();
        }
    }

    @GameTest
    public void sharesOneStockLimitAndPrioritizesTheGroupBatch(GameTestHelper test) {
        try (var fixture = new Fixture(test)) {
            var server = fixture.server;
            var recipient = fixture.join();
            var casters = List.of(fixture.join(), fixture.join(), fixture.join(), fixture.join());
            casters.forEach(caster -> fixture.equip(caster, FULL_BLESSING));
            server.game().startGame();

            for (var caster : casters.subList(0, 3)) {
                caster.setHealth(10);
                test.assertValueEqual(caster.getHealth(), 14F, "The caster receives its batch before using any stock");
                test.assertValueEqual(server.tokens().cooldownTicks(caster, FULL_BLESSING), 400,
                        "Each caster triggers independently, including with stock or when a recipient is full");
            }
            casters.get(1).setHealth(2);
            test.assertValueEqual(casters.get(1).getHealth(), 14F, "Even on cooldown an equipped recipient can use saved blessings");

            for (float lowHealth : new float[]{2, 6, 10}) {
                recipient.setHealth(lowHealth);
                test.assertValueEqual(recipient.getHealth(), 14F, "The recipient spends 3, 2, then 1 blessing immediately");
            }
            recipient.setHealth(10);
            test.assertValueEqual(recipient.getHealth(), 10F, "All casters share the recipient's six-slot cap");

            recipient.setHealth(20);
            casters.get(3).setHealth(10);
            recipient.setHealth(10);
            test.assertValueEqual(recipient.getHealth(), 14F, "New stock is observed even at the same health as the last spend");
            test.succeed();
        }
    }

    @GameTest
    public void clearsStockAndCooldownAcrossLifecycleTransitions(GameTestHelper test) {
        try (var fixture = new Fixture(test)) {
            var server = fixture.server;
            var caster = fixture.join();
            var recipient = fixture.join();
            var disconnected = fixture.join();
            fixture.equip(caster, FULL_BLESSING);
            server.game().startGame();
            caster.setHealth(10);

            caster.die(caster.damageSources().genericKill());
            fixture.revive(caster);
            caster.setHealth(20);
            test.assertValueEqual(server.tokens().cooldownTicks(caster, FULL_BLESSING), 0, "Revival has no old cooldown");
            recipient.die(recipient.damageSources().genericKill());
            fixture.revive(recipient);
            recipient.setHealth(2);
            test.assertValueEqual(recipient.getHealth(), 2F, "Revival has no old stock");

            server.leave(disconnected);
            var reconnected = fixture.join(disconnected.getUUID());
            test.assertValueEqual(server.playerStates().state(reconnected.getUUID()), PlayerState.ELIMINATED,
                    "Disconnect eliminates the player");
            reconnected.setGameMode(GameType.ADVENTURE);
            reconnected.setHealth(6);
            recipient.setHealth(20);
            caster.setHealth(10);
            test.assertValueEqual(caster.getHealth(), 14F, "The revived caster can trigger again");
            test.assertValueEqual(reconnected.getHealth(), 6F, "An ELIMINATED player cannot receive a blessing");

            server.game().stopGame();
            server.game().startGame();
            for (var player : List.of(recipient, reconnected)) {
                player.setHealth(6);
                test.assertValueEqual(player.getHealth(), 6F, "A new round begins with empty stock");
            }
            test.assertValueEqual(server.tokens().cooldownTicks(caster, FULL_BLESSING), 0, "A new round begins ready");
            test.succeed();
        }
    }

    @GameTest
    public void customSettingsAndCooldownExpiryUseCurrentHealth(GameTestHelper test) {
        try (var fixture = new Fixture(test)) {
            var server = fixture.server;
            var caster = fixture.join();
            var recipient = fixture.join();
            var settings = JsonParser.parseString("""
                    {"runelegend:full_blessing":{"blessings_per_trigger":2,"health_threshold":0.6,
                    "heal_amount":1.5,"max_stored_blessings":2,"cooldown_seconds":2}}
                    """).getAsJsonObject();
            var registry = TokenRegistry.builtIn(TokenConfig.fromJson(settings));
            var players = new PlayerTokenService(server.game(), PlayerTokenStore.inMemory(), registry);
            var clock = new AtomicLong(100);
            var engine = new TokenEngine(server.game(), players, registry, clock::get, (_, _) -> {},
                    server::tokenEligible, server::currentPlayer);

            players.equip(caster.getUUID(), FULL_BLESSING);
            server.game().startGame();
            caster.setHealth(13);
            engine.updateHealth(caster);
            test.assertValueEqual(engine.cooldownTicks(caster, FULL_BLESSING), 0, "13 HP is above the configured threshold");
            caster.setHealth(6);
            engine.updateHealth(caster);
            test.assertValueEqual(caster.getHealth(), 9F, "The two configured 1.5 HP blessings apply immediately");
            test.assertValueEqual(engine.cooldownTicks(caster, FULL_BLESSING), 40, "The configured cooldown is two seconds");
            recipient.setHealth(8);
            engine.updateHealth(recipient);
            test.assertValueEqual(recipient.getHealth(), 11F, "The two stored blessings apply at the same configured threshold");

            clock.set(139);
            engine.tick(List.of(caster, recipient));
            test.assertValueEqual(caster.getHealth(), 9F, "No second batch just before cooldown expiry");
            clock.set(140);
            engine.tick(List.of(caster, recipient));
            test.assertValueEqual(caster.getHealth(), 12F, "Still-low health retriggers exactly at cooldown expiry");
            test.assertValueEqual(engine.cooldownTicks(caster, FULL_BLESSING), 40, "The expiry batch starts its own cooldown");
            test.succeed();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper test;
        private final RuneLegendServer server;
        private final List<ServerPlayer> players = new ArrayList<>();

        private Fixture(GameTestHelper test) {
            this.test = test;
            server = RuneLegendServer.find(test.getLevel().getServer());
            server.game().stopGame();
        }

        private ServerPlayer join() { return join(UUID.randomUUID()); }

        private ServerPlayer join(UUID uuid) {
            var player = PlayerStateFixtures.join(test.getLevel().getServer(), test.getLevel(), uuid);
            players.add(player);
            return player;
        }

        private void equip(ServerPlayer player, Identifier token) {
            test.assertValueEqual(server.players().equip(player.getUUID(), token), PlayerTokenService.Result.CHANGED, "Equip " + token);
        }

        private void revive(ServerPlayer player) {
            var uuid = player.getUUID();
            test.assertTrue(server.revive(uuid, server.downed().get(uuid).sequence()), "Revive the downed player");
        }

        @Override public void close() {
            server.game().stopGame();
            for (var player : players) {
                server.players().clear(player.getUUID());
                server.leave(player);
                player.discard();
            }
        }
    }
}
