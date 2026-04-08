package com.chat.upgrade.client.ui.chat;

import java.net.URI;

import com.chat.upgrade.client.ChatUpgradeFormatters;
import com.chat.upgrade.client.media.audio.AudioEntry;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.audio.AudioPlayerService;
import com.chat.upgrade.client.ui.layout.AudioUiLayout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Util;

public final class AudioFloatingWindow {
    private static final int WIDTH = Math.max(96, UpgradeHudInlinePaint.AUDIO_WIDTH / 2);
    private static final int HEIGHT = UpgradeHudInlinePaint.AUDIO_HEIGHT;
    private static final int PAD = UpgradeHudInlinePaint.AUDIO_PAD_X;
    private static final int BTN_W = 14;
    private static final int BTN_H = 8;
    private static final int BTN_GAP = 4;
    private static final int DRAG_H = 10;

    private static boolean visible = false;
    private static String url;
    private static String displayName;
    private static int x = 0;
    private static int y = 0;
    private static boolean dragging = false;
    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;

    private AudioFloatingWindow() {
    }

    public static void toggleFor(String targetUrl, String targetName) {
        if (targetUrl == null || targetUrl.isBlank()) {
            return;
        }
        if (visible && targetUrl.equals(url)) {
            visible = false;
            dragging = false;
            return;
        }
        url = targetUrl;
        displayName = targetName == null ? "" : targetName;
        visible = true;
        dragging = false;
        AudioLoader.getOrLoad(targetUrl);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            int w = mc.getWindow().getGuiScaledWidth();
            int defaultX = Math.max(8, w - WIDTH - 8);
            if (x <= 0 && y <= 0) {
                x = defaultX;
                y = 8;
            }
        }
    }

    public static boolean isVisible() {
        return visible && url != null && !url.isBlank();
    }

    public static void clear() {
        visible = false;
        dragging = false;
        url = null;
        displayName = null;
    }

    public static void render(GuiGraphicsExtractor gfx, Font font, int screenWidth, int screenHeight) {
        if (!isVisible()) {
            return;
        }
        clampToScreen(screenWidth, screenHeight);
        AudioEntry entry = AudioLoader.getOrLoad(url);

        int x0 = x;
        int y0 = y;
        int x1 = x0 + WIDTH;
        int y1 = y0 + HEIGHT;

        gfx.fill(x0, y0, x1, y1, 0xF01A212C);
        gfx.outline(x0, y0, WIDTH, HEIGHT, 0xFF3A4456);

        String name = AudioUiLayout.shortName(displayName, url);
        long total = AudioPlayerService.durationMs(url);
        if (total <= 0L) {
            total = entry.getDurationMs();
        }
        long pos = AudioPlayerService.positionMs(url);
        String header = name + "  " + ChatUpgradeFormatters.formatMs(pos) + " / " + ChatUpgradeFormatters.formatMs(total);
        gfx.text(font, header, x0 + PAD, y0 + UpgradeHudInlinePaint.AUDIO_LINE1_Y, 0xFFE7ECF4, false);

        int rowTop = y0 + 11;
        Rects rects = buttonRects(x0 + PAD, rowTop);
        boolean playing = AudioPlayerService.isPlaying(url);
        boolean loop = AudioPlayerService.isLoopEnabled(url);
        paintButton(gfx, font, rects.playL, rects.top, rects.playR, rects.bottom, playing ? "Pause" : "Play", true);
        paintButton(gfx, font, rects.loopL, rects.top, rects.loopR, rects.bottom, loop ? "Loop" : "Once", loop);
        paintButton(gfx, font, rects.openL, rects.top, rects.openR, rects.bottom, "Open", false);
        paintButton(gfx, font, rects.removeL, rects.top, rects.removeR, rects.bottom, "X", false);

        int barX0 = x0 + PAD;
        int barX1 = x1 - PAD;
        int barY0 = y0 + UpgradeHudInlinePaint.AUDIO_PROGRESS_Y;
        int barY1 = barY0 + UpgradeHudInlinePaint.AUDIO_PROGRESS_H;
        gfx.fill(barX0, barY0, barX1, barY1, 0xFF4A5568);
        float ratio = total <= 0L ? 0.0f : Math.clamp((float) pos / total, 0.0f, 1.0f);
        int fillX = barX0 + Math.round((barX1 - barX0) * ratio);
        gfx.fill(barX0, barY0, fillX, barY1, 0xFF64C8FF);

        if (entry.getState() == AudioEntry.State.LOADING) {
            gfx.text(font, I18n.get("chatupgrade.floating.audio.loading"), x0 + PAD, y0 + UpgradeHudInlinePaint.AUDIO_LINE2_Y, 0xFFCAD2DD, false);
        } else if (entry.getState() == AudioEntry.State.FAILED) {
            gfx.text(font, I18n.get("chatupgrade.floating.audio.failed"), x0 + PAD, y0 + UpgradeHudInlinePaint.AUDIO_LINE2_Y, 0xFFFF9090, false);
        }
    }

    public static boolean mouseClicked(MouseButtonEvent event, int screenWidth, int screenHeight) {
        if (!isVisible() || event.button() != 0) {
            return false;
        }
        clampToScreen(screenWidth, screenHeight);
        if (!inside(event.x(), event.y(), x, y, x + WIDTH, y + HEIGHT)) {
            return false;
        }
        int rowTop = y + 11;
        Rects rects = buttonRects(x + PAD, rowTop);
        if (inside(event.x(), event.y(), rects.playL, rects.top, rects.playR, rects.bottom)) {
            AudioPlayerService.toggle(url);
            return true;
        }
        if (inside(event.x(), event.y(), rects.loopL, rects.top, rects.loopR, rects.bottom)) {
            AudioPlayerService.toggleLoop(url);
            return true;
        }
        if (inside(event.x(), event.y(), rects.openL, rects.top, rects.openR, rects.bottom)) {
            openUrl(url);
            return true;
        }
        if (inside(event.x(), event.y(), rects.removeL, rects.top, rects.removeR, rects.bottom)) {
            visible = false;
            dragging = false;
            return true;
        }
        int barX0 = x + PAD;
        int barX1 = x + WIDTH - PAD;
        int barY0 = y + UpgradeHudInlinePaint.AUDIO_PROGRESS_Y;
        int barY1 = barY0 + UpgradeHudInlinePaint.AUDIO_PROGRESS_H;
        if (inside(event.x(), event.y(), barX0, barY0 - 4, barX1, barY1 + 4)) {
            double ratio = Math.clamp((event.x() - barX0) / Math.max(1.0, barX1 - barX0), 0.0, 1.0);
            AudioPlayerService.seek(url, ratio);
            return true;
        }
        if (inside(event.x(), event.y(), x, y, x + WIDTH, y + DRAG_H)) {
            dragging = true;
            dragOffsetX = (int) event.x() - x;
            dragOffsetY = (int) event.y() - y;
            return true;
        }
        return true;
    }

    public static boolean mouseDragged(MouseButtonEvent event, double dx, double dy, int screenWidth, int screenHeight) {
        if (!isVisible() || !dragging || event.button() != 0) {
            return false;
        }
        x = (int) event.x() - dragOffsetX;
        y = (int) event.y() - dragOffsetY;
        clampToScreen(screenWidth, screenHeight);
        return true;
    }

    public static boolean mouseReleased(MouseButtonEvent event) {
        if (!isVisible()) {
            return false;
        }
        if (event.button() == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    private static void openUrl(String value) {
        try {
            Util.getPlatform().openUri(URI.create(value));
        } catch (Exception ignored) {
        }
    }

    private static void clampToScreen(int w, int h) {
        int maxX = Math.max(0, w - WIDTH - 2);
        int maxY = Math.max(0, h - HEIGHT - 2);
        x = Math.clamp(x, 2, maxX);
        y = Math.clamp(y, 2, maxY);
    }

    private static void paintButton(
            GuiGraphicsExtractor gfx,
            Font font,
            int x0, int y0, int x1, int y1,
            String label,
            boolean active) {
        int bg = active ? 0xFF4C6284 : 0xFF3A4456;
        gfx.fill(x0, y0, x1, y1, bg);
        int tx = x0 + Math.max(1, (x1 - x0 - font.width(label)) / 2);
        gfx.text(font, label, tx, y0, 0xFFE7ECF4, false);
    }

    private static boolean inside(double px, double py, int x0, int y0, int x1, int y1) {
        return px >= x0 && px < x1 && py >= y0 && py < y1;
    }

    private static Rects buttonRects(int left, int top) {
        int playL = left;
        int playR = playL + BTN_W;
        int loopL = playR + BTN_GAP;
        int loopR = loopL + BTN_W;
        int openL = loopR + BTN_GAP;
        int openR = openL + BTN_W;
        int removeL = openR + BTN_GAP;
        int removeR = removeL + BTN_W;
        return new Rects(playL, playR, loopL, loopR, openL, openR, removeL, removeR, top, top + BTN_H);
    }

    private record Rects(
            int playL, int playR,
            int loopL, int loopR,
            int openL, int openR,
            int removeL, int removeR,
            int top, int bottom) {
    }
}
