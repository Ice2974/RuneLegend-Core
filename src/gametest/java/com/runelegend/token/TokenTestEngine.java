package com.runelegend.token;

import com.runelegend.game.GameController;
import com.runelegend.player.PlayerTokenService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/** Explicit participants for isolated effect tests; production never grants unknown UUIDs eligibility. */
final class TokenTestEngine {
    private final Map<UUID, ServerPlayer> participants;
    private TokenTestEngine(ServerPlayer[] players) {
        participants = Arrays.stream(players).collect(Collectors.toMap(ServerPlayer::getUUID, player -> player));
    }
    static TokenTestEngine forPlayers(ServerPlayer... players) { return new TokenTestEngine(players); }
    TokenEngine create(GameController game, PlayerTokenService players, TokenRegistry registry, LongSupplier clock) {
        return create(game, players, registry, clock, player -> player.getRandom().nextDouble(), (_, _) -> {});
    }
    TokenEngine create(GameController game, PlayerTokenService players, TokenRegistry registry, LongSupplier clock,
                       ToDoubleFunction<ServerPlayer> random) {
        return create(game, players, registry, clock, random, (_, _) -> {});
    }
    TokenEngine create(GameController game, PlayerTokenService players, TokenRegistry registry, LongSupplier clock,
                       BiConsumer<ServerPlayer, Identifier> flash) {
        return create(game, players, registry, clock, player -> player.getRandom().nextDouble(), flash);
    }
    private TokenEngine create(GameController game, PlayerTokenService players, TokenRegistry registry, LongSupplier clock,
                               ToDoubleFunction<ServerPlayer> random, BiConsumer<ServerPlayer, Identifier> flash) {
        return new TokenEngine(game, players, registry, clock, random, flash,
                player -> participants.get(player.getUUID()) == player && !player.isSpectator(), participants::get);
    }
}
