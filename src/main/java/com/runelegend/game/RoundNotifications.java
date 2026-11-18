package com.runelegend.game;

import com.runelegend.RuneLegendMod;
import com.runelegend.api.PlayerSnapshot;
import com.runelegend.api.RoundEvents;
import com.runelegend.api.RoundSummary;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RoundNotifications {
    private static final Identifier STARTED = RuneLegendMod.id("round_started");
    private static final Identifier ENDED = RuneLegendMod.id("round_ended");

    private RoundNotifications() {}

    public static void started(MinecraftServer server, long round, Map<UUID, PlayerSnapshot.Identity> identities) {
        FunctionNotifications.publish(server, STARTED, () -> RoundEvents.STARTED.invoker().onRoundStarted(server, round, identities),
                () -> arguments(round, identities));
    }

    public static void ended(MinecraftServer server, RoundSummary summary) {
        FunctionNotifications.publish(server, ENDED, () -> RoundEvents.ENDED.invoker().onRoundEnded(server, summary), () -> {
            var args = arguments(summary.round(), summary.identities());
            args.putString("reason", summary.reason().name().toLowerCase(Locale.ROOT));
            args.putString("outcome_id", summary.outcomeId().map(Identifier::toString).orElse(""));
            return args;
        });
    }

    private static CompoundTag arguments(long round, Map<UUID, PlayerSnapshot.Identity> identities) {
        var args = new CompoundTag();
        args.putLong("round", round);
        var players = new CompoundTag();
        identities.forEach((uuid, identity) -> players.putString(uuid.toString(), identity.name()));
        args.put("identities", players);
        return args;
    }
}
