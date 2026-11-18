package com.runelegend.client.hud;

import com.runelegend.client.ClientConfig;

record HudLayout(int first, int top, int stride) {
    static final int SIZE = 16;

    static HudLayout place(int width, int height, int slots, ClientConfig.TokenHud config) {
        int gaps = Math.max(0, slots - 1);
        int iconWidth = slots * SIZE;
        int spacing = gaps == 0 ? 0 : Math.min(config.spacing(), Math.max(0, width - iconWidth) / gaps);
        int rowWidth = iconWidth + gaps * spacing;
        int first = clamp((long) (width / 2) - rowWidth / 2 + config.offsetX(), width, rowWidth);
        int top = clamp((long) defaultTokenTop(height) - config.offsetY(), height, SIZE);
        return new HudLayout(first, top, SIZE + spacing);
    }

    static StaminaPosition placeStamina(int width, int height, boolean hasTokens, int textWidth, int textHeight,
                                        ClientConfig.StaminaHud config) {
        int halfText = textWidth / 2;
        long left = (long) (width / 2) - halfText + config.offsetX();
        int center = textWidth > width ? width / 2 : clamp(left, width, textWidth) + halfText;
        int defaultTop = defaultTokenTop(height) + (hasTokens ? -12 : 3);
        int top = clamp((long) defaultTop - config.offsetY(), height, textHeight);
        return new StaminaPosition(center, top);
    }

    private static int defaultTokenTop(int height) {
        // 26.3 anchors the vanilla overlay at height - 68; its text starts four pixels above that.
        return Math.max(14, height - 95);
    }

    private static int clamp(long start, int available, int size) {
        return Math.clamp(start, 0, Math.max(0, available - size));
    }

    record StaminaPosition(int center, int top) {}
}
