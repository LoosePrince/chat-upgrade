package com.chat.upgrade.client.ui.chat;

import java.net.URI;

import com.chat.upgrade.client.ChatUpgradeFormatters;
import com.chat.upgrade.client.media.audio.AudioEntry;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.audio.AudioPlayerService;
import com.chat.upgrade.client.ui.chat.interaction.ChatGestureArena;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceRuntime;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.chat.viewport.RichChatMediaLayout;
import com.chat.upgrade.client.ui.render.UiPrimitives;
import com.chat.upgrade.client.ui.render.UiTextureAtlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Util;

public final class AudioFloatingWindow {
    private static final int WIDTH = 168;
    private static final int HEIGHT = 48;
    private static final int PAD = 7;
    private static final int CONTROL_SIZE = 16;
    private static final int CONTROL_GAP = 3;
    private static final int DRAG_H = 18;

    private static boolean visible;
    private static String url;
    private static String displayName;
    private static int x;
    private static int y;
    private static boolean dragging;
    private static int dragOffsetX;
    private static int dragOffsetY;

    private AudioFloatingWindow() {
    }

    public static void toggleFor(String targetUrl, String targetName) {
        if (targetUrl == null || targetUrl.isBlank()) {
            return;
        }
        ChatGestureArena.cancel(ChatGestureArena.Owner.FLOATING_AUDIO);
        if (visible && targetUrl.equals(url)) {
            visible = false;
            return;
        }
        url = targetUrl;
        displayName = targetName == null ? "" : targetName;
        visible = true;
        dragging = false;
        AudioLoader.getOrLoad(targetUrl);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && x <= 0 && y <= 0) {
            int screenWidth = minecraft.getWindow().getGuiScaledWidth();
            x = Math.max(8, screenWidth - WIDTH - 8);
            y = 8;
        }
    }

    public static boolean isVisible() {
        return visible && url != null && !url.isBlank();
    }

    public static boolean isVisibleFor(String targetUrl) {
        return isVisible() && targetUrl != null && targetUrl.equals(url);
    }

    public static boolean contains(double pointerX, double pointerY, int screenWidth, int screenHeight) {
        if (!isVisible()) {
            return false;
        }
        clampToScreen(screenWidth, screenHeight);
        return inside(pointerX, pointerY, windowBounds());
    }

    public static void clear() {
        ChatGestureArena.cancel(ChatGestureArena.Owner.FLOATING_AUDIO);
        visible = false;
        url = null;
        displayName = null;
    }

    public static void render(GuiGraphicsExtractor gfx, Font font, int screenWidth, int screenHeight) {
        if (!isVisible()) {
            return;
        }
        clampToScreen(screenWidth, screenHeight);
        AudioEntry entry = AudioLoader.getOrLoad(url);
        ChatAppearanceSnapshot appearance = ChatAppearanceRuntime.current();
        ChatAppearanceSnapshot.Media tokens = appearance.media();
        int cornerRadius = Math.max(3, appearance.cornerRadius());
        RichChatBounds window = windowBounds();

        UiPrimitives.paintBox(
                gfx,
                window,
                cornerRadius,
                1,
                tokens.cardBackground(),
                tokens.cardBorder());

        Controls controls = controls(window);
        String name = RichChatMediaLayout.displayName(displayName, url);
        String visibleName = font.plainSubstrByWidth(name, Math.max(1, controls.title().width()));
        gfx.text(font, visibleName, controls.title().left(), controls.title().top(), tokens.text(), false);

        paintControl(gfx, controls.close(), UiTextureAtlas.Icon.CLOSE, false, tokens, cornerRadius);

        long total = AudioPlayerService.durationMs(url);
        if (total <= 0L) {
            total = entry.getDurationMs();
        }
        total = Math.max(0L, total);
        long position = Math.max(0L, AudioPlayerService.positionMs(url));

        if (entry.getState() == AudioEntry.State.LOADING) {
            String label = I18n.get("chatupgrade.floating.audio.loading");
            gfx.text(font, label, controls.play().left(), controls.play().top() + 3, tokens.muted(), false);
        } else if (entry.getState() == AudioEntry.State.FAILED) {
            gfx.text(
                    font,
                    I18n.get("chatupgrade.floating.audio.failed"),
                    controls.play().left(),
                    controls.play().top() + 3,
                    tokens.failureText(),
                    false);
        } else {
            boolean playing = AudioPlayerService.isPlaying(url);
            boolean loop = AudioPlayerService.isLoopEnabled(url);
            paintControl(
                    gfx,
                    controls.play(),
                    playing ? UiTextureAtlas.Icon.PAUSE : UiTextureAtlas.Icon.PLAY,
                    playing,
                    tokens,
                    cornerRadius);
            paintControl(gfx, controls.loop(), UiTextureAtlas.Icon.LOOP, loop, tokens, cornerRadius);
            paintControl(gfx, controls.open(), UiTextureAtlas.Icon.OPEN, false, tokens, cornerRadius);
        }

        paintProgress(gfx, controls.progress(), position, total, tokens, cornerRadius);
        String time = ChatUpgradeFormatters.formatMs(position) + " / " + ChatUpgradeFormatters.formatMs(total);
        gfx.text(font, time, controls.time().left(), controls.time().top(), tokens.muted(), false);
    }

    public static boolean mouseClicked(MouseButtonEvent event, int screenWidth, int screenHeight) {
        if (!isVisible() || event.button() != 0) {
            return false;
        }
        clampToScreen(screenWidth, screenHeight);
        RichChatBounds window = windowBounds();
        if (!inside(event.x(), event.y(), window)) {
            return false;
        }
        Controls controls = controls(window);
        if (inside(event.x(), event.y(), controls.close())) {
            visible = false;
            dragging = false;
            ChatGestureArena.release(ChatGestureArena.Owner.FLOATING_AUDIO);
            return true;
        }
        if (inside(event.x(), event.y(), controls.play())) {
            AudioPlayerService.toggle(url);
            return true;
        }
        if (inside(event.x(), event.y(), controls.loop())) {
            AudioPlayerService.toggleLoop(url);
            return true;
        }
        if (inside(event.x(), event.y(), controls.open())) {
            openUrl(url);
            return true;
        }
        if (inside(event.x(), event.y(), expandVertical(controls.progress(), 4))) {
            double ratio = Math.clamp(
                    (event.x() - controls.progress().left()) / Math.max(1.0, controls.progress().width()),
                    0.0,
                    1.0);
            AudioPlayerService.seek(url, ratio);
            return true;
        }
        RichChatBounds dragArea = new RichChatBounds(
                window.left(),
                window.top(),
                controls.close().left() - 2,
                window.top() + DRAG_H);
        if (inside(event.x(), event.y(), dragArea)) {
            if (!ChatGestureArena.tryCapture(
                    ChatGestureArena.Owner.FLOATING_AUDIO,
                    AudioFloatingWindow::cancelDrag)) {
                return false;
            }
            dragging = true;
            dragOffsetX = (int) event.x() - x;
            dragOffsetY = (int) event.y() - y;
        }
        return true;
    }

    public static boolean mouseDragged(MouseButtonEvent event, double dx, double dy, int screenWidth, int screenHeight) {
        if (!isVisible()
                || !dragging
                || !ChatGestureArena.isCapturedBy(ChatGestureArena.Owner.FLOATING_AUDIO)
                || event.button() != 0) {
            return false;
        }
        x = (int) event.x() - dragOffsetX;
        y = (int) event.y() - dragOffsetY;
        clampToScreen(screenWidth, screenHeight);
        return true;
    }

    public static void cancelDrag() {
        dragging = false;
    }

    public static boolean mouseReleased(MouseButtonEvent event) {
        if (!isVisible()) {
            return false;
        }
        if (event.button() == 0
                && dragging
                && ChatGestureArena.isCapturedBy(ChatGestureArena.Owner.FLOATING_AUDIO)) {
            dragging = false;
            ChatGestureArena.release(ChatGestureArena.Owner.FLOATING_AUDIO);
            return true;
        }
        return false;
    }

    private static Controls controls(RichChatBounds window) {
        RichChatBounds close = RichChatBounds.ofSize(
                window.right() - PAD - CONTROL_SIZE,
                window.top() + 4,
                CONTROL_SIZE,
                CONTROL_SIZE);
        RichChatBounds title = new RichChatBounds(
                window.left() + PAD,
                window.top() + 7,
                close.left() - 5,
                window.top() + 17);
        int controlsTop = window.top() + 22;
        RichChatBounds play = RichChatBounds.ofSize(window.left() + PAD, controlsTop, CONTROL_SIZE, CONTROL_SIZE);
        RichChatBounds loop = RichChatBounds.ofSize(
                play.right() + CONTROL_GAP,
                controlsTop,
                CONTROL_SIZE,
                CONTROL_SIZE);
        RichChatBounds open = RichChatBounds.ofSize(
                loop.right() + CONTROL_GAP,
                controlsTop,
                CONTROL_SIZE,
                CONTROL_SIZE);
        RichChatBounds time = RichChatBounds.ofSize(window.right() - PAD - 52, controlsTop + 4, 52, 10);
        RichChatBounds progress = new RichChatBounds(
                open.right() + 6,
                controlsTop + 6,
                Math.max(open.right() + 7, time.left() - 5),
                controlsTop + 10);
        return new Controls(title, close, play, loop, open, progress, time);
    }

    private static void paintControl(
            GuiGraphicsExtractor gfx,
            RichChatBounds bounds,
            UiTextureAtlas.Icon icon,
            boolean active,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        UiPrimitives.fillRounded(
                gfx,
                bounds,
                Math.min(cornerRadius, bounds.height() / 2),
                active ? tokens.controlActiveBackground() : tokens.controlBackground());
        UiTextureAtlas.drawIcon(gfx, icon, inset(bounds, 3), tokens.text());
    }

    private static void paintProgress(
            GuiGraphicsExtractor gfx,
            RichChatBounds bounds,
            long positionMs,
            long durationMs,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        UiPrimitives.fillRounded(gfx, bounds, Math.min(cornerRadius, bounds.height() / 2), tokens.progressTrack());
        float ratio = durationMs <= 0L
                ? 0.0F
                : Math.clamp((float) positionMs / durationMs, 0.0F, 1.0F);
        int fillRight = bounds.left() + Math.round(bounds.width() * ratio);
        if (fillRight > bounds.left()) {
            UiPrimitives.fillRounded(
                    gfx,
                    new RichChatBounds(bounds.left(), bounds.top(), fillRight, bounds.bottom()),
                    Math.min(cornerRadius, bounds.height() / 2),
                    tokens.progressFill());
        }
    }

    private static void openUrl(String value) {
        try {
            Util.getPlatform().openUri(URI.create(value));
        } catch (Exception ignored) {
        }
    }

    private static void clampToScreen(int screenWidth, int screenHeight) {
        int maxX = Math.max(2, screenWidth - WIDTH - 2);
        int maxY = Math.max(2, screenHeight - HEIGHT - 2);
        x = Math.clamp(x, 2, maxX);
        y = Math.clamp(y, 2, maxY);
    }

    private static RichChatBounds windowBounds() {
        return RichChatBounds.ofSize(x, y, WIDTH, HEIGHT);
    }

    private static RichChatBounds inset(RichChatBounds bounds, int amount) {
        return new RichChatBounds(
                bounds.left() + amount,
                bounds.top() + amount,
                bounds.right() - amount,
                bounds.bottom() - amount);
    }

    private static RichChatBounds expandVertical(RichChatBounds bounds, int amount) {
        return new RichChatBounds(
                bounds.left(),
                bounds.top() - amount,
                bounds.right(),
                bounds.bottom() + amount);
    }

    private static boolean inside(double pointerX, double pointerY, RichChatBounds bounds) {
        return pointerX >= bounds.left()
                && pointerX < bounds.right()
                && pointerY >= bounds.top()
                && pointerY < bounds.bottom();
    }

    private record Controls(
            RichChatBounds title,
            RichChatBounds close,
            RichChatBounds play,
            RichChatBounds loop,
            RichChatBounds open,
            RichChatBounds progress,
            RichChatBounds time) {
    }
}
