package com.chat.upgrade.client.ui.screen;
import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ChatUpgradeFormatters;
import com.chat.upgrade.client.media.video.VideoEntry;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.media.video.VideoPlayerService;
import com.chat.upgrade.client.ui.layout.AudioUiLayout;
import com.chat.upgrade.client.ui.layout.VideoUiLayout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

public final class VideoPreviewScreen extends Screen {
    private static final int GAP = 8;
    private static final int TOP_H = 24;
    private static final int CONTROL_H = 18;
    private static final int PLAY_W = 52;
    private static final int PROGRESS_H = 4;

    private final String url;
    private final @Nullable String nameHint;

    public VideoPreviewScreen(String url, @Nullable String nameHint) {
        super(Component.translatable("chatupgrade.screen.video_preview.title"));
        this.url = url;
        this.nameHint = nameHint;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, 0xE0101010);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        VideoEntry entry = VideoLoader.getOrLoad(url);
        String name = AudioUiLayout.shortName(nameHint, url);
        String size = ChatUpgradeFormatters.formatBytes(entry.getFetchedByteLength());
        String link = url.length() > 56 ? url.substring(0, 53) + "..." : url;

        int panelLeft = GAP;
        int panelRight = width - GAP;
        String header = I18n.get("chatupgrade.screen.preview.header", name, size, link);
        guiGraphics.text(font, header, panelLeft, GAP + 6, 0xFFE7ECF4, false);
        guiGraphics.fill(panelLeft, GAP + TOP_H - 1, panelRight, GAP + TOP_H, 0xFF3A4456);

        int videoTop = GAP + TOP_H;
        int controlTop = height - GAP - CONTROL_H;
        int videoBottom = Math.max(videoTop + 16, controlTop - GAP);
        int boxW = panelRight - panelLeft;
        int boxH = videoBottom - videoTop;

        guiGraphics.fill(panelLeft, videoTop, panelRight, videoBottom, 0xFF141A22);
        guiGraphics.outline(panelLeft, videoTop, boxW, boxH, 0xFF344055);

        if (entry.getState() == VideoEntry.State.LOADING) {
            guiGraphics.centeredText(font, I18n.get("chatupgrade.screen.video_preview.loading"), width / 2, videoTop + boxH / 2 - 4, 0xFFE6E6E6);
            return;
        }
        if (entry.getState() == VideoEntry.State.FAILED) {
            guiGraphics.centeredText(font, I18n.get("chatupgrade.screen.video_preview.failed"), width / 2, videoTop + boxH / 2 - 4, 0xFFFF8080);
            return;
        }

        long now = Util.getMillis();
        @Nullable
        Identifier textureId = VideoPlayerService.textureIdAtMillis(url, now);
        if (textureId != null) {
            VideoUiLayout.Rect rect = fitRect(panelLeft, videoTop, boxW, boxH, entry.getRawWidth(), entry.getRawHeight());
            guiGraphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    textureId,
                    rect.left(),
                    rect.top(),
                    0.0f,
                    0.0f,
                    rect.right() - rect.left(),
                    rect.bottom() - rect.top(),
                    entry.getRawWidth() > 0 ? entry.getRawWidth() : (rect.right() - rect.left()),
                    entry.getRawHeight() > 0 ? entry.getRawHeight() : (rect.bottom() - rect.top()),
                    entry.getRawWidth() > 0 ? entry.getRawWidth() : (rect.right() - rect.left()),
                    entry.getRawHeight() > 0 ? entry.getRawHeight() : (rect.bottom() - rect.top()),
                    ARGB.white(1.0f));
        } else {
            guiGraphics.centeredText(font, I18n.get("chatupgrade.screen.video_preview.no_frame"), width / 2, videoTop + boxH / 2 - 4, 0xFFFFB0B0);
        }

        renderControls(guiGraphics, panelLeft, panelRight, controlTop);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        int panelLeft = GAP;
        int panelRight = width - GAP;
        int controlTop = height - GAP - CONTROL_H;

        int btnX0 = panelLeft;
        int btnX1 = btnX0 + PLAY_W;
        int btnY0 = controlTop;
        int btnY1 = btnY0 + CONTROL_H;
        if (inside(event.x(), event.y(), btnX0, btnY0, btnX1, btnY1)) {
            VideoPlayerService.toggle(url);
            return true;
        }

        long total = Math.max(0L, VideoPlayerService.durationMs(url));
        long pos = Math.max(0L, VideoPlayerService.positionMs(url));
        int rightTextW = font.width(ChatUpgradeFormatters.formatMs(total));
        int leftTextW = font.width(ChatUpgradeFormatters.formatMs(pos));
        int leftX = btnX1 + 8;
        int barX0 = leftX + leftTextW + 6;
        int barX1 = panelRight - rightTextW - 6;
        int barY0 = controlTop + (CONTROL_H - PROGRESS_H) / 2;
        int barY1 = barY0 + PROGRESS_H;
        if (barX1 > barX0 && inside(event.x(), event.y(), barX0, barY0 - 4, barX1, barY1 + 4)) {
            double ratio = Math.clamp((event.x() - barX0) / Math.max(1.0, barX1 - barX0), 0.0, 1.0);
            VideoPlayerService.seek(url, ratio);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void open(String url, @Nullable String nameHint) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        VideoLoader.getOrLoad(url);
        mc.setScreen(new VideoPreviewScreen(url, nameHint));
    }

    private void renderControls(GuiGraphicsExtractor guiGraphics, int panelLeft, int panelRight, int controlTop) {
        long total = Math.max(0L, VideoPlayerService.durationMs(url));
        long pos = Math.max(0L, VideoPlayerService.positionMs(url));
        boolean playing = VideoPlayerService.isPlaying(url);

        guiGraphics.fill(panelLeft, controlTop, panelRight, controlTop + CONTROL_H, 0xFF1A212C);
        guiGraphics.outline(panelLeft, controlTop, panelRight - panelLeft, CONTROL_H, 0xFF3A4456);

        int btnX0 = panelLeft;
        int btnX1 = btnX0 + PLAY_W;
        guiGraphics.fill(btnX0, controlTop, btnX1, controlTop + CONTROL_H, 0xFF2B3646);
        guiGraphics.centeredText(font, I18n.get(playing
                ? "chatupgrade.screen.video_preview.button.pause"
                : "chatupgrade.screen.video_preview.button.play"), (btnX0 + btnX1) / 2, controlTop + 5, 0xFFE7ECF4);

        String left = ChatUpgradeFormatters.formatMs(pos);
        String right = ChatUpgradeFormatters.formatMs(total);
        int leftX = btnX1 + 8;
        int rightX = panelRight - font.width(right) - 6;
        guiGraphics.text(font, left, leftX, controlTop + 5, 0xFFCAD2DD, false);
        guiGraphics.text(font, right, rightX, controlTop + 5, 0xFFCAD2DD, false);

        int barX0 = leftX + font.width(left) + 6;
        int barX1 = rightX - 6;
        int barY0 = controlTop + (CONTROL_H - PROGRESS_H) / 2;
        int barY1 = barY0 + PROGRESS_H;
        if (barX1 > barX0) {
            guiGraphics.fill(barX0, barY0, barX1, barY1, 0xFF4A5568);
            float ratio = total <= 0L ? 0.0f : Math.clamp((float) pos / total, 0.0f, 1.0f);
            int fillX = barX0 + Math.round((barX1 - barX0) * ratio);
            guiGraphics.fill(barX0, barY0, fillX, barY1, 0xFF64C8FF);
        }
    }

    private static VideoUiLayout.Rect fitRect(int x0, int y0, int drawW, int drawH, int rawW, int rawH) {
        int boxW = Math.max(1, drawW);
        int boxH = Math.max(1, drawH);
        if (rawW <= 0 || rawH <= 0) {
            return new VideoUiLayout.Rect(x0, y0, x0 + boxW, y0 + boxH);
        }
        double sx = (double) boxW / rawW;
        double sy = (double) boxH / rawH;
        double scale = Math.min(sx, sy);
        int w = Math.max(1, (int) Math.round(rawW * scale));
        int h = Math.max(1, (int) Math.round(rawH * scale));
        int left = x0 + (boxW - w) / 2;
        int top = y0 + (boxH - h) / 2;
        return new VideoUiLayout.Rect(left, top, left + w, top + h);
    }

    private static boolean inside(double x, double y, int x0, int y0, int x1, int y1) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }
}
