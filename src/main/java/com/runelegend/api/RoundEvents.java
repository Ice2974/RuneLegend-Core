package com.runelegend.api;

import com.runelegend.RuneLegendMod;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.UUID;

/** Server-thread round notifications. Listeners may query state. Round transitions during notification return false. */
public final class RoundEvents {
    /** Fires after round identities and player modes are initialized. The identity map is immutable. */
    public static final Event<Started> STARTED = EventFactory.createArrayBacked(Started.class, listeners ->
            (server, round, identities) -> {
                for (var listener : listeners) {
                    try {
                        listener.onRoundStarted(server, round, identities);
                    } catch (RuntimeException error) {
                        RuneLegendMod.LOGGER.error("Round {} start listener failed", round, error);
                    }
                }
            });

    /** Fires after round cleanup and the return-to-spawn flow. */
    public static final Event<Ended> ENDED = EventFactory.createArrayBacked(Ended.class, listeners ->
            (server, summary) -> {
                for (var listener : listeners) {
                    try {
                        listener.onRoundEnded(server, summary);
                    } catch (RuntimeException error) {
                        RuneLegendMod.LOGGER.error("Round {} end listener failed", summary.round(), error);
                    }
                }
            });

    @FunctionalInterface
    public interface Started {
        void onRoundStarted(MinecraftServer server, long round, Map<UUID, PlayerSnapshot.Identity> identities);
    }

    @FunctionalInterface
    public interface Ended {
        void onRoundEnded(MinecraftServer server, RoundSummary summary);
    }

    private RoundEvents() {}
}
