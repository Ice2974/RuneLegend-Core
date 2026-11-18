package com.runelegend.client.hud;

import com.runelegend.RuneLegendMod;
import com.runelegend.client.ClientConfig;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.combat.GuardItems;
import com.runelegend.token.TokenEngine;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.List;
import java.util.stream.IntStream;

public final class RuneLegendHud {
    private static final Identifier UNKNOWN_ICON = RuneLegendMod.id("token/unknown");
    private static final int GLOW_SIZE = 32;
    private static final List<Identifier> GLOW = IntStream.range(0, TokenHudAnimation.FRAME_COUNT)
            .mapToObj(frame -> RuneLegendMod.id("token/glow_0" + frame)).toList();

    private RuneLegendHud() {}

    public static void register(ClientConfig config) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.OVERLAY_MESSAGE, RuneLegendMod.id("hud"),
                (graphics, delta) -> draw(graphics, config));
    }

    static void draw(GuiGraphicsExtractor graphics, ClientConfig config) {
        var client = Minecraft.getInstance();
        var cache = ClientStateCache.INSTANCE;
        var view = cache.view();
        if (view == null || client.player == null || client.gui.hud.isHidden() || client.player.isSpectator()) return;
        boolean showStamina = view.stamina() < view.staminaMax() || GuardItems.using(client.player);
        var rules = cache.rules();
        int slots = rules != null && rules.running() && view.playerState() == com.runelegend.player.PlayerState.ALIVE
                && client.player.isAlive() ? view.tokens().size() : 0;
        if (slots == 0 && !showStamina) return;
        var layout = HudLayout.place(graphics.guiWidth(), graphics.guiHeight(), slots, config.tokenHud());
        var atlas = client.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
        long now = Util.getMillis();
        int[] glowFrames = new int[slots];
        for (int i = 0; i < slots; i++) {
            var token = view.tokens().get(i);
            var display = cache.tokenAnimation().display(token.id(), now);
            glowFrames[i] = display.glowFrame();
            int x = layout.first() + i * layout.stride(), y = layout.top();
            int alpha = display.opacity() == 1F ? 0xFF000000 : 0x80000000;
            int size = HudLayout.SIZE;
            graphics.fill(x, y, x + size, y + size, alpha | 0x231D2D);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon(atlas, token), x, y, size, size, display.opacity());
        }
        int glowOffset = (GLOW_SIZE - HudLayout.SIZE) / 2;
        for (int i = 0; i < slots; i++) {
            if (glowFrames[i] < 0) continue;
            int x = layout.first() + i * layout.stride();
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GLOW.get(glowFrames[i]),
                    x - glowOffset, layout.top() - glowOffset, GLOW_SIZE, GLOW_SIZE);
        }
        for (int i = 0; i < slots; i++) {
            var token = view.tokens().get(i);
            if ((token.flags() & TokenEngine.COOLDOWN) != 0) {
                long seconds = Math.max(0, (token.cooldownUntil() - cache.serverTick() + 19) / 20);
                int x = layout.first() + i * layout.stride();
                graphics.centeredText(client.font, Long.toString(seconds), x + HudLayout.SIZE / 2, layout.top() + 4, 0xFFFFFFFF);
            }
        }
        if (showStamina) {
            int percent = view.stamina() * 100 / view.staminaMax();
            var text = Component.translatable("runelegend.hud.stamina", percent);
            var position = HudLayout.placeStamina(graphics.guiWidth(), graphics.guiHeight(), slots > 0,
                    client.font.width(text), client.font.lineHeight, config.staminaHud());
            graphics.centeredText(client.font, text, position.center(), position.top(), 0xFFFFFFFF);
        }
    }

    static Identifier icon(TextureAtlas atlas, TokenEngine.View token) {
        if ((token.flags() & TokenEngine.UNKNOWN) == 0) {
            var id = token.id().withPrefix("token/");
            if (atlas.getSprite(id) != atlas.missingSprite()) return id;
        }
        return UNKNOWN_ICON;
    }
}
