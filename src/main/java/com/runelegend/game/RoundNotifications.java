package com.runelegend.game;

import com.runelegend.RuneLegendMod;
import com.runelegend.api.PlayerSnapshot;
import com.runelegend.api.RoundEvents;
import com.runelegend.api.RoundSummary;
import com.runelegend.mixin.CommandExecutionContextAccessor;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class RoundNotifications {
    private static final Identifier STARTED = RuneLegendMod.id("round_started");
    private static final Identifier ENDED = RuneLegendMod.id("round_ended");

    private RoundNotifications() {}

    public static void started(MinecraftServer server, long round, Map<UUID, PlayerSnapshot.Identity> identities) {
        publish(server, STARTED, () -> RoundEvents.STARTED.invoker().onRoundStarted(server, round, identities),
                () -> arguments(round, identities));
    }

    public static void ended(MinecraftServer server, RoundSummary summary) {
        publish(server, ENDED, () -> RoundEvents.ENDED.invoker().onRoundEnded(server, summary), () -> {
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

    private static void publish(MinecraftServer server, Identifier tag, Runnable notifyJava, Supplier<CompoundTag> arguments) {
        // Vanilla otherwise queues nested commands in the caller's context. Run callbacks to completion
        // while the transition is guarded, even when it was initiated by a command or a function.
        var context = CommandExecutionContextAccessor.runelegend$executionContext();
        var caller = context.get();
        context.remove();
        try {
            notifyJava.run();
            var functions = server.getFunctions();
            var members = functions.getTag(tag);
            if (members.isEmpty()) return;
            var args = arguments.get();
            var source = functions.getGameLoopSender();
            for (var member : members) {
                try {
                    var function = member.instantiate(args, functions.getDispatcher());
                    Commands.executeCommandInContext(source, execution ->
                            ExecutionContext.queueInitialFunctionCall(execution, function, source, CommandResultCallback.EMPTY));
                } catch (FunctionInstantiationException | RuntimeException error) {
                    RuneLegendMod.LOGGER.error("Function {} failed in #{}", member.id(), tag, error);
                }
            }
        } finally {
            if (caller != null) context.set(caller);
        }
    }
}
