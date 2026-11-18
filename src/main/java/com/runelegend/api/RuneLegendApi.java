package com.runelegend.api;

import com.runelegend.RuneLegendServer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.UUID;

/** Map integration. Call on the logical server thread after server startup. */
public interface RuneLegendApi {
    static RuneLegendApi get(MinecraftServer server) {
        return requireServer(server).game();
    }

    /** Returns the read-only game and player queries for this server session. */
    static RuneLegendQueries queries(MinecraftServer server) {
        return new RuneLegendQueries(requireServer(server));
    }

    /** Starts with the listed online candidates, honoring their next-round spectator choices. */
    static boolean startGame(MinecraftServer server, Collection<UUID> participants) {
        return requireServer(server).game().startGame(participants);
    }

    /** Ends the active round with a map-defined outcome. Returns false in WAITING or during a notification. */
    static boolean finishGame(MinecraftServer server, Identifier outcomeId) {
        return requireServer(server).game().finishGame(outcomeId);
    }

    /** Completes a map rescue if the credential still identifies a revivable downing. */
    static boolean tryRevive(MinecraftServer server, DownedHandle handle) {
        return requireServer(server).tryRevive(handle);
    }

    /** Eliminates a connected ALIVE or DOWNED player in the specified round with a map-defined reason ID. */
    static boolean tryEliminate(MinecraftServer server, UUID playerId, long round, Identifier reasonId) {
        return requireServer(server).tryEliminate(playerId, round, reasonId);
    }

    private static RuneLegendServer requireServer(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("RuneLegend requires the server thread");
        var instance = RuneLegendServer.find(server);
        if (instance == null) throw new IllegalStateException("RuneLegend is not active on this server");
        return instance;
    }

    RuleSnapshot getRules();
    boolean startGame();
    boolean stopGame();
}
