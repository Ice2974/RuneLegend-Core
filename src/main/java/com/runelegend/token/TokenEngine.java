package com.runelegend.token;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendServer;
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
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.ToDoubleFunction;
import java.util.function.Predicate;
import java.util.function.Function;

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
    private final BiConsumer<ServerPlayer, Identifier> flash;
    private final Map<UUID, Map<Identifier, TokenRuntimeState>> states = new HashMap<>();
    private final Map<UUID, Long> health = new HashMap<>();
    private final TokenScheduler scheduler = new TokenScheduler();
    private final OwnedEffects owned = new OwnedEffects();
    private final SubstituteTargets substitutes = new SubstituteTargets();
    private final Predicate<ServerPlayer> eligible;
    private final Function<UUID, ServerPlayer> resolve;
    private final Map<UUID, Object> generations = new HashMap<>();
    private final Set<UUID> clearing = new HashSet<>();
    private Object roundGeneration = new Object();
    private boolean clearingRound;

    public TokenEngine(GameController game, PlayerTokenService players, TokenRegistry registry, LongSupplier clock) {
        this(game, players, registry, clock, player -> player.getRandom().nextDouble());
    }

    public TokenEngine(GameController game, PlayerTokenService players, TokenRegistry registry, LongSupplier clock,
                       BiConsumer<ServerPlayer, Identifier> flash) {
        this(game, players, registry, clock, player -> player.getRandom().nextDouble(), flash);
    }

    TokenEngine(GameController game, PlayerTokenService players, TokenRegistry registry, LongSupplier clock,
                ToDoubleFunction<ServerPlayer> random) {
        this(game, players, registry, clock, random, (player, token) -> {});
    }

    private TokenEngine(GameController game, PlayerTokenService players, TokenRegistry registry, LongSupplier clock,
                        ToDoubleFunction<ServerPlayer> random, BiConsumer<ServerPlayer, Identifier> flash) {
        this(game, players, registry, clock, random, flash, player -> {
            var server = RuneLegendServer.find(player.level().getServer());
            return server != null && server.tokenEligible(player);
        }, null);
    }

    public TokenEngine(GameController game, PlayerTokenService players, TokenRegistry registry, LongSupplier clock,
                       BiConsumer<ServerPlayer, Identifier> flash, Predicate<ServerPlayer> eligible,
                       Function<UUID, ServerPlayer> resolve) {
        this(game, players, registry, clock, player -> player.getRandom().nextDouble(), flash, eligible, resolve);
    }

    TokenEngine(GameController game, PlayerTokenService players, TokenRegistry registry, LongSupplier clock,
                ToDoubleFunction<ServerPlayer> random, BiConsumer<ServerPlayer, Identifier> flash,
                Predicate<ServerPlayer> eligible, Function<UUID, ServerPlayer> resolve) {
        this.game = game;
        this.players = players;
        this.registry = registry;
        this.clock = clock;
        this.random = random;
        this.flash = flash;
        this.eligible = eligible;
        this.resolve = resolve;
    }

    public boolean eligible(ServerPlayer player) {
        return game.getRules().running() && !clearingRound && !clearing.contains(player.getUUID()) && eligible.test(player);
    }

    SubstituteTargets substitutes() { return substitutes; }

    public Boost melee(ServerPlayer player, float amount) {
        return melee(player, null, amount);
    }

    public Boost melee(ServerPlayer player, LivingEntity target, float amount) {
        if (!eligible(player) || amount <= 0 || !player.isAlive() || target instanceof Player
                || target instanceof SubstituteMannequin) return new Boost(0, List.of());
        float bonus = 0;
        var applied = new ArrayList<Identifier>();
        for (var id : players.get(player.getUUID()).equipped()) {
            if (!eligible(player) || !player.isAlive()) break;
            var definition = registry.get(id);
            if (definition == null || !definition.events().contains(TokenEvent.Kind.MELEE)) continue;
            float extra = definition.effect().addMeleeDamage(new Context(player, id, target), state(player, id));
            if (Float.isFinite(extra) && extra > 0 && Float.isFinite(bonus + extra)) {
                bonus += extra;
                applied.add(id);
            }
        }
        return new Boost(bonus, List.copyOf(applied));
    }

    public void confirmBoost(ServerPlayer player, List<Identifier> applied) {
        if (!eligible(player) || !player.isAlive()) return;
        long tick = clock.getAsLong();
        for (var id : applied) {
            var definition = registry.get(id);
            var state = state(player, id);
            if (tick >= state.activeUntil && definition != null && definition.effect().flashOnMeleeBoost()) state.flash(tick);
        }
    }

    public void dispatch(TokenEvent event) {
        if (!eligible(event.player()) || (event.kind() != TokenEvent.Kind.HEALTH && !event.player().isAlive())
                || event.source() != null && event.source().is(TOKEN_DAMAGE)) return;
        if (event.target() instanceof SubstituteMannequin
                || event.target() instanceof Player && event.target() != event.player()) return;
        for (var id : players.get(event.player().getUUID()).equipped()) {
            if (!eligible(event.player())) break;
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
            if (!eligible(player) || !player.isAlive()) return new View(id, 0, 0, 0, 0);
            var state = state(player, id);
            int flags = (definition.effect().ready(player, state, tick) ? READY : 0)
                    | (tick < state.triggeredUntil ? TRIGGERED : 0)
                    | (tick < state.activeUntil ? ACTIVE : 0)
                    | (isCoolingDown(state, tick) ? COOLDOWN : 0)
                    | (definition.effect().preparing(player, state, tick) ? PREPARING : 0);
            return new View(id, flags,
                    (flags & TRIGGERED) != 0 ? state.triggeredUntil : 0,
                    (flags & ACTIVE) != 0 ? state.activeUntil : 0,
                    (flags & COOLDOWN) != 0 ? state.cooldownUntil : 0);
        }).toList();
    }

    /** Reads the cooldown phase for an equipped token without creating runtime state. */
    public int cooldownTicks(ServerPlayer player, Identifier id) {
        if (!eligible(player) || !player.isAlive()) return 0;
        var runtime = states.get(player.getUUID());
        var state = runtime == null ? null : runtime.get(id);
        long tick = clock.getAsLong();
        return state != null && isCoolingDown(state, tick) ? Math.toIntExact(state.cooldownUntil - tick) : 0;
    }

    private static boolean isCoolingDown(TokenRuntimeState state, long tick) {
        return tick < state.cooldownUntil && tick >= state.activeUntil;
    }

    public void tick(Iterable<ServerPlayer> online) {
        var rules = game.getRules();
        if (!rules.running()) return;
        scheduler.tick(clock.getAsLong(), rules.round());
        for (var player : online) {
            if (player.isAlive()) updateHealth(player);
        }
    }

    public void updateHealth(ServerPlayer player) {
        if (!eligible(player) || !subscribesToHealth(player)) return;
        // A Totem can restore health in the same hit. Observe zero for existing progress
        // without creating new runtime state for a player whose death was already handled.
        if (!player.isAlive() && !states.containsKey(player.getUUID())) return;
        long value = ((long) Float.floatToIntBits(player.getHealth()) << 32)
                | Integer.toUnsignedLong(Float.floatToIntBits(player.getMaxHealth()));
        var previous = health.put(player.getUUID(), value);
        if (previous == null || previous != value) {
            dispatch(new TokenEvent(TokenEvent.Kind.HEALTH, player, player, player.level(), null, 0, 0, 0));
        }
    }

    /** Discard all player token runtime on death, disconnect and reconnect. */
    public void clearEffects(UUID uuid) {
        if (!clearing.add(uuid)) return;
        generations.put(uuid, new Object());
        try {
            owned.removePlayer(uuid);
        } finally {
            scheduler.cancelPlayer(uuid);
            health.remove(uuid);
            try { cleanup(states.remove(uuid)); }
            finally { clearing.remove(uuid); }
        }
    }

    public void playerReplaced(ServerPlayer previous, ServerPlayer replacement) {
        var runtime = states.get(previous.getUUID());
        if (runtime == null) return;
        for (var id : List.copyOf(runtime.keySet())) {
            if (!eligible(previous) || states.get(previous.getUUID()) != runtime) break;
            var definition = registry.get(id);
            if (definition != null && definition.effect() instanceof ExtensionTokenEffect effect) {
                effect.playerReplaced(runtime.get(id), previous, replacement);
            }
        }
    }

    public void clearRound() {
        if (clearingRound) return;
        clearingRound = true;
        roundGeneration = new Object();
        try { owned.clear(); }
        finally {
            scheduler.clear();
            try {
                for (var runtime : List.copyOf(states.values())) cleanup(runtime);
            } finally {
                states.clear();
                health.clear();
                generations.clear();
                clearingRound = false;
            }
        }
    }

    private void cleanup(Map<Identifier, TokenRuntimeState> runtime) {
        if (runtime == null) return;
        for (var entry : runtime.entrySet()) {
            var definition = registry.get(entry.getKey());
            if (definition != null && definition.effect() instanceof ExtensionTokenEffect effect) {
                effect.cleanup(entry.getValue());
            }
        }
    }

    private boolean subscribesToHealth(ServerPlayer player) {
        return players.get(player.getUUID()).equipped().stream().map(registry::get)
                .anyMatch(definition -> definition != null && definition.events().contains(TokenEvent.Kind.HEALTH));
    }

    private TokenRuntimeState state(ServerPlayer player, Identifier id) {
        return states.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
                .computeIfAbsent(id, ignored -> new TokenRuntimeState());
    }

    public final class Context {
        private final ServerPlayer player;
        private final Identifier token;
        private final LivingEntity meleeTarget;
        private final Object generation;
        private final Object round;

        private Context(ServerPlayer player, Identifier token) {
            this(player, token, null);
        }

        private Context(ServerPlayer player, Identifier token, LivingEntity meleeTarget) {
            this.player = player;
            this.token = token;
            this.meleeTarget = meleeTarget;
            this.generation = generations.computeIfAbsent(player.getUUID(), _ -> new Object());
            this.round = roundGeneration;
        }

        public ServerPlayer player() {
            var current = resolve == null ? player : resolve.apply(player.getUUID());
            return current == null ? player : current;
        }
        public boolean valid() {
            var current = player();
            return currentGeneration() && current.connection == player.connection && eligible(current) && current.isAlive();
        }
        private boolean currentGeneration() { return round == roundGeneration && generations.get(player.getUUID()) == generation; }
        public boolean canHeal(ServerPlayer target) { return valid() && eligible(target) && target.isAlive() && !target.isSpectator(); }
        public boolean canHarm(LivingEntity target) {
            return valid() && target != null && !(target instanceof Player) && !(target instanceof SubstituteMannequin)
                    && target.isAlive();
        }
        public LivingEntity meleeTarget() { return meleeTarget; }
        public long tick() { return clock.getAsLong(); }
        public void flash() { if (valid()) flash.accept(player(), token); }
        public boolean roll(double chance) { return valid() && TokenEngine.roll(chance, () -> random.applyAsDouble(player())); }

        public void replaceEffect(String name, Runnable remove) {
            if (!valid()) { remove.run(); return; }
            var key = key(name);
            owned.remove(key);
            if (!valid()) { remove.run(); return; }
            owned.track(key, () -> {
                scheduler.cancel(key);
                remove.run();
            });
        }

        public boolean summonSubstitute(String name, long expiresAt, Runnable ended) {
            if (!valid()) return false;
            var owner = player();
            var level = owner.level();
            var model = new SubstituteMannequin(owner);
            if (!level.addFreshEntity(model) || !model.isAlive()) return false;
            substitutes.add(level, model);
            model.onEnd(ended);
            replaceEffect(name, () -> {
                substitutes.remove(level, model);
                model.onEnd(null);
                if (model.isAlive()) model.discard();
            });
            if (model.isRemoved()) return false;
            at(name, expiresAt, model::expire);
            return true;
        }

        public void at(String effect, long tick, Runnable action) {
            if (valid()) scheduler.schedule(tick, game.getRules().round(), key(effect), () -> {
                if (valid()) action.run();
                else finish(effect);
            });
        }

        // Revocation can run after losing ALIVE, but an old generation must never revoke a new effect.
        public void finish(String name) {
            if (!currentGeneration()) return;
            var key = key(name);
            scheduler.cancel(key);
            owned.remove(key);
        }

        public void damage(ServerLevel level, LivingEntity victim, float amount) {
            if (!canHarm(victim) || victim.level() != level) return;
            victim.hurtServer(level, directDamageSource(level, player()), amount);
        }

        boolean normalDamage(LivingEntity victim, float amount) {
            var owner = player();
            var level = owner.level();
            if (!canHarm(victim) || victim.level() != level) return false;
            var source = level.damageSources().playerAttack(owner);
            return TokenEventBridge.directDamage(victim, source, () -> victim.hurtServer(level, source, amount));
        }

        private OwnedEffects.Key key(String name) { return new OwnedEffects.Key(player.getUUID(), token, name); }
    }

    static DamageSource directDamageSource(ServerLevel level, ServerPlayer player) {
        var type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(TOKEN_DAMAGE);
        return new DamageSource(type, player);
    }

    static boolean roll(double chance, DoubleSupplier random) { return random.getAsDouble() < chance; }
}
