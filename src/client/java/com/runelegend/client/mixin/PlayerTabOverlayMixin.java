package com.runelegend.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.runelegend.client.render.TabStatusLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.List;
import java.util.UUID;

@Mixin(PlayerTabOverlay.class)
abstract class PlayerTabOverlayMixin {
    @Shadow @Final private Minecraft minecraft;

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getPlayerInfos()Ljava/util/List;"), require = 1, allow = 1)
    private List<PlayerInfo> runelegend$measure(PlayerTabOverlay overlay, Operation<List<PlayerInfo>> original,
                                               @Share("runelegend$layout") LocalRef<TabStatusLayout> frame) {
        var players = original.call(overlay);
        frame.set(TabStatusLayout.collect(players, minecraft.font));
        return players;
    }

    // Vanilla applies the total width cap before dividing into columns.
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Ljava/lang/Math;min(II)I"), require = 1, allow = 1)
    private int runelegend$reserveWidth(int desired, int limit, Operation<Integer> original,
                                       @Share("runelegend$layout") LocalRef<TabStatusLayout> frame,
                                       @Local(index = 12) int columns, @Local(index = 14) int scoreWidth) {
        var layout = frame.get();
        if (layout == null) return original.call(desired, limit);
        int total = original.call(desired + layout.extraWidthPerColumn() * columns, limit);
        layout.columns(total / columns, minecraft.getConnection().onlineMode(), scoreWidth);
        return total;
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"),
            require = 1, allow = 1)
    private void runelegend$clipName(GuiGraphicsExtractor graphics, Font font, Component name, int x, int y, int color,
                                    Operation<Void> original, @Share("runelegend$layout") LocalRef<TabStatusLayout> frame) {
        var layout = frame.get();
        if (layout == null || x + font.width(name) <= layout.nameRight(x)) {
            original.call(graphics, font, name, x, y, color);
            return;
        }
        int right = layout.nameRight(x);
        if (right <= x) return;
        graphics.enableScissor(x, y, right, y + 9);
        try { original.call(graphics, font, name, x, y, color); }
        finally { graphics.disableScissor(); }
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;extractTablistScore(Lnet/minecraft/world/scores/Objective;ILnet/minecraft/client/gui/components/PlayerTabOverlay$ScoreDisplayEntry;IILjava/util/UUID;Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"),
            require = 1, allow = 1)
    private void runelegend$fitScore(PlayerTabOverlay overlay, Objective objective, int y, @Coerce Object entry, int left, int right,
                                     UUID player, GuiGraphicsExtractor graphics, Operation<Void> original,
                                     @Share("runelegend$layout") LocalRef<TabStatusLayout> frame, @Local(index = 25) int nameX) {
        var layout = frame.get();
        if (layout == null) {
            original.call(overlay, objective, y, entry, left, right, player, graphics);
            return;
        }
        int fittedLeft = layout.scoreLeft(nameX), fittedRight = layout.scoreRight(nameX);
        if (fittedRight - fittedLeft <= 5) return;
        if (fittedRight - fittedLeft == right - left) {
            original.call(overlay, objective, y, entry, fittedLeft, fittedRight, player, graphics);
            return;
        }
        graphics.enableScissor(fittedLeft, y, fittedRight, y + 9);
        try { original.call(overlay, objective, y, entry, fittedLeft, fittedRight, player, graphics); }
        finally { graphics.disableScissor(); }
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;extractPingIcon(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V"),
            require = 1, allow = 1)
    private void runelegend$drawStatus(PlayerTabOverlay overlay, GuiGraphicsExtractor graphics, int columnWidth, int x, int y,
                                       PlayerInfo player, Operation<Void> original, @Share("runelegend$layout") LocalRef<TabStatusLayout> frame) {
        if (frame.get() != null) frame.get().draw(graphics, minecraft.font, x, y, player);
        original.call(overlay, graphics, columnWidth, x, y, player);
    }
}
