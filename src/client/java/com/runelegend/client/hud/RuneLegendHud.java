package com.runelegend.client.hud;

import com.runelegend.RuneLegendMod;
import com.runelegend.client.network.ClientStateCache;
import com.runelegend.combat.GuardItems;
import com.runelegend.token.TokenEngine;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public final class RuneLegendHud {
    private static final Set<String> ICONS = Set.of(
            "sharpened_tip", "soul_siphon", "echo", "immediate_counter",
            // Every built-in token currently uses a placeholder sprite copied from unknown.png.
            "venomous_tip", "ghost_bane", "substitute", "poisoned_arrow",
            "fountain", "full_blessing", "retribution",
            "asura_form", "vernal_tide", "execution_momentum", "surging_fury",
            "resonance_healing", "arrogance", "deflective_backlash");
    private static final int VERTICAL_OFFSET = Integer.getInteger("runelegend.hudOffset", 0);
    private static final int GLOW_SIZE = 32;
    private static final List<Identifier> GLOW = IntStream.range(0, TokenHudAnimation.FRAME_COUNT)
            .mapToObj(frame -> RuneLegendMod.id("token/glow_0" + frame)).toList();

    private RuneLegendHud() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.OVERLAY_MESSAGE, RuneLegendMod.id("hud"), RuneLegendHud::draw);
    }

    private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        var client = Minecraft.getInstance();
        var cache = ClientStateCache.INSTANCE;
        var view = cache.view();
        if (view == null || client.player == null || client.gui.hud.isHidden() || client.player.isSpectator()) return;
        boolean showStamina = view.stamina() < view.staminaMax() || GuardItems.using(client.player);
        var rules = cache.rules();
        int slots = rules != null && rules.running() && client.player.isAlive() ? view.tokens().size() : 0;
        if (slots == 0 && !showStamina) return;
        var layout = HudLayout.place(graphics.guiWidth(), graphics.guiHeight(), slots, VERTICAL_OFFSET);
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
            String path = token.id().getPath();
            String icon = token.id().getNamespace().equals(RuneLegendMod.MOD_ID) && ICONS.contains(path) ? path : "unknown";
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, RuneLegendMod.id("token/" + icon), x, y, size, size, display.opacity());
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
            graphics.centeredText(client.font, text, layout.center(), layout.staminaTop(), 0xFFFFFFFF);
        }
    }
}
