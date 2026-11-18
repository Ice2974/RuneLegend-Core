package com.runelegend.api;

import com.runelegend.RuneLegendServer;
import net.minecraft.server.MinecraftServer;

/** Map integration. Call on the logical server thread after server startup. */
public interface RuneLegendApi {
    static RuneLegendApi get(MinecraftServer server) {
        return requireServer(server).game();
    }

    /** Returns the read-only game and player queries for this server session. */
    static RuneLegendQueries queries(MinecraftServer server) {
        return new RuneLegendQueries(requireServer(server));
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
