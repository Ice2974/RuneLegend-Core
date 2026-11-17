package com.runelegend.client.render;

import com.runelegend.client.network.ClientStateCache;
import com.runelegend.network.TabStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per extraction, laid out in the same coordinates as vanilla's player rows. */
public final class TabStatusLayout {
    private static final int PING_INSET = 11;
    private static final int GAP = 2;
    private static final int VANILLA_SCORE_GAP = 1;
    private static final int FACE_WIDTH = 9;
    private record Label(Component text, int width) {}

    private final Map<UUID, Label> labels;
    private final int widest;
    private int headWidth;
    private int columnWidth;
    private int scoreWidth;

    private TabStatusLayout(Map<UUID, Label> labels, int widest) {
        this.labels = labels;
        this.widest = widest;
    }

    public static TabStatusLayout collect(List<PlayerInfo> players, Font font) {
        var cache = ClientStateCache.INSTANCE;
        var view = cache.view();
        if (cache.rules() == null || !cache.rules().running() || view == null || view.tab().isEmpty()) return null;
        var labels = new HashMap<UUID, Label>();
        int widest = 0;
        for (var player : players) {
            var uuid = player.getProfile().id();
            var status = view.tab().get(uuid);
            if (status == null) continue;
            var text = text(status);
            int width = font.width(text);
            labels.put(uuid, new Label(text, width));
            widest = Math.max(widest, width);
        }
        return labels.isEmpty() ? null : new TabStatusLayout(labels, widest);
    }

    private static Component text(TabStatus status) {
        return switch (status.kind()) {
            case HIGH_HEALTH -> healthText(status.value(), ChatFormatting.GREEN);
            case MEDIUM_HEALTH -> healthText(status.value(), ChatFormatting.YELLOW);
            case LOW_HEALTH -> healthText(status.value(), ChatFormatting.RED);
            case DOWNED -> Component.translatable("runelegend.player.tab.countdown", status.value()).withStyle(ChatFormatting.DARK_RED);
            case ELIMINATED -> Component.literal("☠").withStyle(ChatFormatting.GRAY);
        };
    }

    private static Component healthText(int value, ChatFormatting numberColor) {
        return Component.empty().append(Component.literal("❤").withStyle(ChatFormatting.RED))
                .append(Component.literal(Integer.toString(value)).withStyle(numberColor));
    }

    public int extraWidthPerColumn() { return widest + GAP + VANILLA_SCORE_GAP; }

    public void columns(int width, boolean faces, int vanillaScoreWidth) {
        columnWidth = width;
        headWidth = faces ? FACE_WIDTH : 0;
        scoreWidth = vanillaScoreWidth;
    }

    private int statusRight(int rowLeft) { return rowLeft + columnWidth - PING_INSET - GAP; }
    private int statusWidth() { return Math.min(widest, Math.max(0, statusRight(0) - headWidth)); }

    public int scoreRight(int nameX) {
        return Math.max(nameX, statusRight(nameX - headWidth) - statusWidth() - GAP);
    }

    public int scoreLeft(int nameX) { return Math.max(nameX, scoreRight(nameX) - scoreWidth); }
    public int nameRight(int nameX) { return Math.max(nameX, scoreLeft(nameX) - VANILLA_SCORE_GAP); }

    public void draw(GuiGraphicsExtractor graphics, Font font, int rowLeft, int y, PlayerInfo player) {
        var label = labels.get(player.getProfile().id());
        if (label != null && label.width() <= statusWidth()) {
            graphics.text(font, label.text(), statusRight(rowLeft) - label.width(), y, -1);
        }
    }
}
