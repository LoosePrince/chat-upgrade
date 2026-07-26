package com.chat.upgrade.client.ui.animation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceRuntime;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/** Shared, render-only entrance motion for Chat Upgrade UI surfaces. */
public final class UiMotion {
    public static final String CHAT_PANEL = "chat-panel";
    public static final String SETTINGS = "settings";
    public static final String EMOJI_PICKER = "emoji-picker";
    public static final String MENTION_COMPLETION = "mention-completion";
    public static final String CONTEXT_MENU = "context-menu";
    public static final String AUDIO_OPTIONS = "audio-options";
    public static final String FLOATING_AUDIO = "floating-audio";
    public static final String IMAGE_PREVIEW = "image-preview";
    public static final String VIDEO_PREVIEW = "video-preview";
    public static final String CHAT_DETAILS = "chat-details";

    private static final long ENTER_DURATION_MS = 180L;
    private static final int MESSAGE_ENTER_TICKS = 9;
    private static final Map<String, Long> ENTERED_AT_MS = new ConcurrentHashMap<>();

    private UiMotion() {
    }

    public static void begin(String key) {
        if (key != null && !key.isBlank()) {
            ENTERED_AT_MS.put(key, Util.getMillis());
        }
    }

    public static void end(String key) {
        if (key != null) {
            ENTERED_AT_MS.remove(key);
        }
    }

    public static void clear() {
        ENTERED_AT_MS.clear();
    }

    public static float progress(String key) {
        if (!enabled() || key == null || key.isBlank()) {
            return 1.0F;
        }
        long startedAt = ENTERED_AT_MS.computeIfAbsent(key, ignored -> Util.getMillis());
        return easeOutCubic((Util.getMillis() - startedAt) / (float) ENTER_DURATION_MS);
    }

    public static int enterFromLeft(String key, int distance) {
        return Math.round(-Math.max(0, distance) * (1.0F - progress(key)));
    }

    public static int enterFromBottom(String key, int distance) {
        return Math.round(Math.max(0, distance) * (1.0F - progress(key)));
    }

    public static boolean isEntering(String key) {
        return progress(key) < 0.999F;
    }

    public static float messageOpacity(RichChatMessage message, int ticks) {
        return messageProgress(message, ticks);
    }

    public static int messageEnterOffsetY(RichChatMessage message, int ticks) {
        return Math.round(10.0F * (1.0F - messageProgress(message, ticks)));
    }

    public static void withTranslation(GuiGraphicsExtractor graphics, int x, int y, Runnable draw) {
        if (graphics == null || draw == null) {
            return;
        }
        if (x == 0 && y == 0) {
            draw.run();
            return;
        }
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        try {
            draw.run();
        } finally {
            pose.popMatrix();
        }
    }

    private static boolean enabled() {
        return ChatAppearanceRuntime.current().animationsEnabled();
    }

    private static float messageProgress(RichChatMessage message, int ticks) {
        if (!enabled() || message == null) {
            return 1.0F;
        }
        int age = Math.max(0, ticks - message.addedTime());
        return easeOutCubic(Math.min(1.0F, age / (float) MESSAGE_ENTER_TICKS));
    }

    private static float easeOutCubic(float value) {
        float clamped = Math.clamp(value, 0.0F, 1.0F);
        float inverse = 1.0F - clamped;
        return 1.0F - inverse * inverse * inverse;
    }
}