package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.game.GameController;
import com.runelegend.player.PlayerTokenService;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.ToDoubleFunction;

public final class TokenEngine {
    public static final ResourceKey<DamageType> TOKEN_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, RuneLegendMod.id("token_damage"));
    public static final int READY = 1, TRIGGERED = 2, ACTIVE = 4, COOLDOWN = 8, UNKNOWN = 16, PREPARING = 32;
    public record View(Identifier id, int flags, long triggeredUntil, long activeUntil, long cooldownUntil) {}
    public record Boost(float bonusDamage, List<Identifier> applied) {}

    private final GameController game;
    private final PlayerTokenService players;
    private final TokenRegistry registry;
    private final LongSupplier clock;
    private final ToDoubleFunction<ServerPlayer> random;
    private final Map<UUID, Map<Identifier, TokenRuntimeState>> states = new HashMap<>();
    private final Map<UUID, Map<Identifier, Long>> retainedCooldowns = new HashMap<>();
    private final Map<UUID, Long> health = new HashMap<>();
    private final TokenScheduler scheduler = new TokenScheduler();
    private final OwnedEffects owned = new OwnedEffects();

    public TokenEngine(GameController game, PlayerTokenService players, TokenRegistry registry, LongSupplier clock) {
        this(game, players, registry, clock, player -> player.getRandom().nextDouble());
    }

    TokenEngine(GameController game, PlayerTokenService players, TokenRegistry registry, LongSupplier clock,
                ToDoubleFunction<ServerPlayer> random) {
        this.game = game;
        this.players = players;
        this.registry = registry;
        this.clock = clock;
        this.random = random;
    }

    public Boost melee(ServerPlayer player, float amount) {
        if (!game.getRules().running() || amount <= 0 || !player.isAlive()) return new Boost(0, List.of());
        float bonus = 0;
        var applied = new ArrayList<Identifier>();
        for (var id : players.get(player.getUUID()).equipped()) {
            var definition = registry.get(id);
            if (definition == null || !definition.events().contains(TokenEvent.Kind.MELEE)) continue;
            float extra = definition.effect().addMeleeDamage(new Context(player, id), state(player, id));
            if (extra > 0) {
                bonus += extra;
                applied.add(id);
            }
        }
        return new Boost(bonus, List.copyOf(applied));
    }

    public void confirmBoost(ServerPlayer player, List<Identifier> applied) {
        if (!game.getRules().running() || !player.isAlive()) return;
        long tick = clock.getAsLong();
        for (var id : applied) {
            var state = state(player, id);
            if (tick >= state.activeUntil) state.flash(tick);
        }
    }

    public void dispatch(TokenEvent event) {
        if (!game.getRules().running() || !event.player().isAlive()
                || event.source() != null && event.source().is(TOKEN_DAMAGE)) return;
        for (var id : players.get(event.player().getUUID()).equipped()) {
            var definition = registry.get(id);
            if (definition != null && definition.events().contains(event.kind())) {
                definition.effect().onEvent(new Context(event.player(), id), state(event.player(), id), event);
            }
        }
    }

    public List<View> views(ServerPlayer player) {
        long tick = clock.getAsLong();
        return players.get(player.getUUID()).equipped().stream().map(id -> {
            var definition = registry.get(id);
            if (definition == null) return new View(id, UNKNOWN, 0, 0, 0);
            if (!game.getRules().running() || !player.isAlive()) return new View(id, 0, 0, 0, 0);
            var state = state(player, id);
            int flags = (definition.effect().ready(player, state, tick) ? READY : 0)
                    | (tick < state.triggeredUntil ? TRIGGERED : 0)
                    | (tick < state.activeUntil ? ACTIVE : 0)
                    | (tick < state.cooldownUntil && tick >= state.activeUntil ? COOLDOWN : 0)
                    | (definition.effect().preparing(player, state, tick) ? PREPARING : 0);
            return new View(id, flags,
                    (flags & TRIGGERED) != 0 ? state.triggeredUntil : 0,
                    (flags & ACTIVE) != 0 ? state.activeUntil : 0,
                    (flags & COOLDOWN) != 0 ? state.cooldownUntil : 0);
        }).toList();
    }

    public void tick(Iterable<ServerPlayer> online) {
        long now = clock.getAsLong();
        retainedCooldowns.values().removeIf(cooldowns -> {
            cooldowns.values().removeIf(until -> until <= now);
            return cooldowns.isEmpty();
        });
        var rules = game.getRules();
        if (!rules.running()) return;
        scheduler.tick(clock.getAsLong(), rules.round());
        for (var player : online) {
            if (!player.isAlive() || !subscribesToHealth(player)) continue;
            long value = ((long) Float.floatToIntBits(player.getHealth()) << 32)
                    | Integer.toUnsignedLong(Float.floatToIntBits(player.getMaxHealth()));
            var previous = health.put(player.getUUID(), value);
            if (previous == null || previous != value) {
                dispatch(new TokenEvent(TokenEvent.Kind.HEALTH, player, player, player.level(), null, 0, 0, 0));
            }
        }
    }

    /** Death, disconnect and reconnect discard effects and charges, retaining only unexpired cooldowns. */
    public void clearEffects(UUID uuid) {
        owned.removePlayer(uuid);
        scheduler.cancelPlayer(uuid);
        health.remove(uuid);
        long now = clock.getAsLong();
        var perPlayer = states.remove(uuid);
        if (perPlayer != null) {
            perPlayer.forEach((id, state) -> {
                state.clearEffects();
                if (state.cooldownUntil > now) {
                    retainedCooldowns.computeIfAbsent(uuid, ignored -> new HashMap<>())
                            .merge(id, state.cooldownUntil, Math::max);
                }
            });
        }
        var cooldowns = retainedCooldowns.get(uuid);
        if (cooldowns != null) {
            cooldowns.values().removeIf(until -> until <= now);
            if (cooldowns.isEmpty()) retainedCooldowns.remove(uuid);
        }
    }

    public void clearRound() {
        owned.clear();
        scheduler.clear();
        states.clear();
        retainedCooldowns.clear();
        health.clear();
    }

    private boolean subscribesToHealth(ServerPlayer player) {
        return players.get(player.getUUID()).equipped().stream().map(registry::get)
                .anyMatch(definition -> definition != null && definition.events().contains(TokenEvent.Kind.HEALTH));
    }

    private TokenRuntimeState state(ServerPlayer player, Identifier id) {
        var uuid = player.getUUID();
        return states.computeIfAbsent(uuid, ignored -> new HashMap<>()).computeIfAbsent(id, ignored -> {
            var state = new TokenRuntimeState();
            var cooldowns = retainedCooldowns.get(uuid);
            if (cooldowns != null) {
                var until = cooldowns.remove(id);
                if (until != null && until > clock.getAsLong()) state.cooldownUntil = until;
                if (cooldowns.isEmpty()) retainedCooldowns.remove(uuid);
            }
            return state;
        });
    }

    public final class Context {
        private final ServerPlayer player;
        private final Identifier token;

        private Context(ServerPlayer player, Identifier token) {
            this.player = player;
            this.token = token;
        }

        public ServerPlayer player() { return player; }
        public long tick() { return clock.getAsLong(); }
        public boolean roll(double chance) { return TokenEngine.roll(chance, () -> random.applyAsDouble(player)); }

        public void replaceEffect(String name, Runnable remove) {
            var key = key(name);
            owned.remove(key);
            owned.track(key, () -> {
                scheduler.cancel(key);
                remove.run();
            });
        }

        public void at(String effect, long tick, Runnable action) {
            scheduler.schedule(tick, game.getRules().round(), key(effect), action);
        }

        public void finish(String name) { owned.remove(key(name)); }

        public void damage(ServerLevel level, LivingEntity victim, float amount) {
            if (!game.getRules().running() || !victim.isAlive() || victim.level() != level) return;
            victim.hurtServer(level, directDamageSource(level, player), amount);
        }

        private OwnedEffects.Key key(String name) { return new OwnedEffects.Key(player.getUUID(), token, name); }
    }

    static DamageSource directDamageSource(ServerLevel level, ServerPlayer player) {
        var type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(TOKEN_DAMAGE);
        return new DamageSource(type, player);
    }

    static boolean roll(double chance, DoubleSupplier random) { return random.getAsDouble() < chance; }
}
