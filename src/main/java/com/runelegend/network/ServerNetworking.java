package com.runelegend.network;

import com.runelegend.RuneLegendServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import static com.runelegend.network.RuneLegendPayloads.*;

public final class ServerNetworking {
    private ServerNetworking() {}

    public static void register() {
        ProtocolHandshake.register();
        ServerPlayConnectionEvents.JOIN.register((listener, sender, minecraft) -> {
            var player = listener.player;
            if (!ServerPlayNetworking.canSend(player, PlayerSnapshot.TYPE) || !ServerPlayNetworking.canSend(player, PlayerDelta.TYPE)
                    || !ServerPlayNetworking.canSend(player, Rules.TYPE) || !ServerPlayNetworking.canSend(player, TokenFlash.TYPE)) {
                listener.disconnect(Component.translatableWithFallback("runelegend.network.required", "This server requires the RuneLegend mod."));
                return;
            }
            var server = RuneLegendServer.find(minecraft);
            if (server != null) server.join(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((listener, minecraft) -> {
            // Fabric can deliver channel-inactive notifications from the Netty thread.
            // Resolve the current save and connection again on the authoritative thread.
            minecraft.execute(() -> {
                var server = RuneLegendServer.find(minecraft);
                if (server != null) server.leave(listener.player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(StateResync.TYPE, (request, context) -> {
            var server = RuneLegendServer.find(context.server());
            if (server != null && server.isCurrent(context.player())) server.sync().resync(context.player());
        });
    }
}
