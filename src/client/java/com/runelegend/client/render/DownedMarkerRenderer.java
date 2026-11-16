package com.runelegend.client.render;

import com.runelegend.client.network.ClientStateCache;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Immutable extraction data only; no player state decisions occur in the renderer. */
public final class DownedMarkerRenderer {
    private record Label(Vec3 position, FormattedCharSequence hint, int hintWidth, FormattedCharSequence countdown, int countdownWidth) {}
    private static final RenderStateDataKey<List<Label>> LABELS = RenderStateDataKey.create(() -> "runelegend:downed_markers");
    private DownedMarkerRenderer() {}

    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            var client = Minecraft.getInstance();
            var cache = ClientStateCache.INSTANCE;
            var view = cache.view();
            var labels = new ArrayList<Label>();
            if (view != null && cache.rules() != null && cache.rules().running() && !client.gui.hud.isHidden()) {
                var camera = context.levelState().cameraRenderState.pos;
                for (var marker : view.downed()) {
                    var position = new Vec3(marker.x(), marker.y() + 2, marker.z());
                    if (!marker.dimension().equals(context.level().dimension().identifier())
                            || marker.round() != cache.rules().round() || position.distanceToSqr(camera) > 64 * 64) continue;
                    var hint = Component.translatable("runelegend.player.revive_hint").getVisualOrderText();
                    var countdown = Component.translatable("runelegend.player.countdown", marker.secondsRemaining()).getVisualOrderText();
                    labels.add(new Label(position, hint, client.font.width(hint), countdown, client.font.width(countdown)));
                }
            }
            context.levelState().setData(LABELS, List.copyOf(labels));
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            var camera = context.levelState().cameraRenderState;
            var poses = context.poseStack();
            for (var label : context.levelState().getDataOrDefault(LABELS, List.of())) {
                poses.pushPose();
                poses.translate(label.position.x - camera.pos.x, label.position.y - camera.pos.y, label.position.z - camera.pos.z);
                poses.rotate(camera.orientation);
                poses.scale(0.025F, -0.025F, 0.025F);
                var collector = context.submitNodeCollector();
                collector.submitText(poses, -label.hintWidth / 2F, -10, label.hint, false, Font.DisplayMode.NORMAL,
                        LightCoordsUtil.FULL_BRIGHT, 0xFFFFFFFF, 0, 0);
                collector.submitText(poses, -label.countdownWidth / 2F, 0, label.countdown, false, Font.DisplayMode.NORMAL,
                        LightCoordsUtil.FULL_BRIGHT, 0xFFFFFFFF, 0, 0);
                poses.popPose();
            }
        });
    }
}
