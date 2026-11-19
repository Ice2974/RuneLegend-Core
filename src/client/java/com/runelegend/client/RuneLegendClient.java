package com.runelegend.client;

import com.runelegend.client.hud.RuneLegendHud;
import com.runelegend.client.input.GuardInputController;
import com.runelegend.client.network.ClientNetworking;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.client.render.EchoPulseParticle;
import com.runelegend.client.render.FountainBubbleParticle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class RuneLegendClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EchoPulseParticle.register();
        FountainBubbleParticle.register();
        ClientNetworking.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientNetworking.tick();
            if (client.player != null && !client.player.isAlive()) ClientStateCache.INSTANCE.tokenAnimation().clear();
            GuardInputController.INSTANCE.tick(client);
        });
        RuneLegendHud.register(ClientConfig.load());
        com.runelegend.client.render.DownedMarkerRenderer.register();
    }
}
