package com.runelegend.api;

import com.runelegend.RuneLegendMod;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/** Server-thread notifications of committed player transitions. Payloads can be retained by listeners. */
public final class PlayerStateEvents {
    public static final Event<DownedListener> DOWNED = EventFactory.createArrayBacked(DownedListener.class, listeners ->
            (server, event) -> {
                for (var listener : listeners) {
                    try {
                        listener.onDowned(server, event);
                    } catch (RuntimeException error) {
                        RuneLegendMod.LOGGER.error("Downed listener failed for {}", event.playerId(), error);
                    }
                }
            });

    public static final Event<RevivedListener> REVIVED = EventFactory.createArrayBacked(RevivedListener.class, listeners ->
            (server, event) -> {
                for (var listener : listeners) {
                    try {
                        listener.onRevived(server, event);
                    } catch (RuntimeException error) {
                        RuneLegendMod.LOGGER.error("Revived listener failed for {}", event.playerId(), error);
                    }
                }
            });

    public static final Event<EliminatedListener> ELIMINATED = EventFactory.createArrayBacked(EliminatedListener.class, listeners ->
            (server, event) -> {
                for (var listener : listeners) {
                    try {
                        listener.onEliminated(server, event);
                    } catch (RuntimeException error) {
                        RuneLegendMod.LOGGER.error("Eliminated listener failed for {}", event.playerId(), error);
                    }
                }
            });

    @FunctionalInterface
    public interface DownedListener {
        void onDowned(MinecraftServer server, Downed event);
    }

    @FunctionalInterface
    public interface RevivedListener {
        void onRevived(MinecraftServer server, Revived event);
    }

    @FunctionalInterface
    public interface EliminatedListener {
        void onEliminated(MinecraftServer server, Eliminated event);
    }

    /** Original death location and orientation for this downing. */
    public record DownedAnchor(long sequence, ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {}

    public record Downed(long round, UUID playerId, DownedAnchor anchor) {}

    /** The player rescue path supplies the matched rescuer. Unattributed completions have an empty rescuerId. */
    public record Revived(long round, UUID playerId, DownedAnchor anchor, Optional<UUID> rescuerId) {}

    public record Eliminated(long round, UUID playerId, PlayerSnapshot.Identity previousIdentity,
                             EliminationReason reason, Optional<DownedAnchor> anchor) {}

    public enum EliminationReason { TIMEOUT, DISCONNECT, DEATH }

    private PlayerStateEvents() {}
}
