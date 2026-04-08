package com.chat.upgrade.client.ui.layout;

import com.chat.upgrade.client.ui.chat.UpgradeHudInlinePaint;

public final class AudioUiLayout {
    private static final int BUTTON_TOP_OFFSET = 11;
    private static final int BUTTON_HEIGHT = 8;
    private static final int GAP = 4;
    private static final int PLAY_W = 14;
    private static final int LOOP_W = 14;
    private static final int OPEN_W = 14;
    private static final int POP_W = 14;

    private AudioUiLayout() {}

    public static ButtonRects buttonRects(int x0, int y0) {
        int left = x0 + UpgradeHudInlinePaint.AUDIO_PAD_X;
        int top = y0 + BUTTON_TOP_OFFSET;
        int playLeft = left;
        int playRight = playLeft + PLAY_W;
        int loopLeft = playRight + GAP;
        int loopRight = loopLeft + LOOP_W;
        int openLeft = loopRight + GAP;
        int openRight = openLeft + OPEN_W;
        int popLeft = openRight + GAP;
        int popRight = popLeft + POP_W;
        return new ButtonRects(playLeft, playRight, loopLeft, loopRight, openLeft, openRight, popLeft, popRight, top, top + BUTTON_HEIGHT);
    }

    public static String shortName(String resourceName, String url) {
        if (resourceName != null) {
            String name = resourceName.trim();
            if (!name.isEmpty()) {
                return name;
            }
        }
        int slash = Math.max(url.lastIndexOf('/'), url.lastIndexOf('\\'));
        String base = slash >= 0 && slash + 1 < url.length() ? url.substring(slash + 1) : url;
        if (base.length() > 24) {
            return base.substring(0, 21) + "...";
        }
        return base;
    }

    public record ButtonRects(
            int playLeft,
            int playRight,
            int loopLeft,
            int loopRight,
            int openLeft,
            int openRight,
            int popLeft,
            int popRight,
            int top,
            int bottom
    ) {}
}
