package com.chat.upgrade.client;

import com.chat.upgrade.client.mixininterface.ImageAttachable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

/** Draws URL preview tiles in the chat HUD using the scoped {@link GuiGraphicsExtractor} from {@link ChatUpgradeRenderScope}. */
public final class UpgradeHudInlinePaint {
    private UpgradeHudInlinePaint() {}

    public static void paintLinePreview(GuiMessage.Line line, int messageY, float opacity) {
        if (!(((Object) line) instanceof ImageAttachable attachable)) return;

        String resourceUrl = attachable.chatupgrade$getImageUrl();
        if (resourceUrl == null) return;

        GuiGraphicsExtractor gfx = ChatUpgradeRenderScope.current();
        if (gfx == null) return;

        if (attachable.chatupgrade$getResourceType() == InlineResourceType.AUDIO) {
            AudioEntry entry = AudioLoader.getIfPresent(resourceUrl);
            if (entry == null) {
                return;
            }
            paintAudio(gfx, entry, resourceUrl, messageY, opacity);
            return;
        }

        ImageEntry imageEntry = ImageLoader.getIfPresent(resourceUrl);
        if (imageEntry == null) {
            return;
        }

        switch (imageEntry.getState()) {
            case FAILED -> {}
            case LOADING -> paintLoadingStrip(gfx, imageEntry, messageY, opacity);
            case LOADED -> paintDecodedBlit(gfx, imageEntry, messageY, opacity);
        }
    }

    private static void paintAudio(GuiGraphicsExtractor gfx, AudioEntry entry, String url, int messageY, float opacity) {
        int h = ImageLoader.PREVIEW_HEIGHT;
        int w = 220;
        int x0 = 0;
        int y0 = messageY;
        int x1 = x0 + w;
        int y1 = y0 + h;
        gfx.fill(x0, y0, x1, y1, argb(opacity * 0.9f, 24, 26, 31));
        int btn = h - 12;
        int bx0 = x0 + 6;
        int by0 = y0 + 6;
        gfx.fill(bx0, by0, bx0 + btn, by0 + btn, argb(opacity, 52, 120, 200));

        Font font = Minecraft.getInstance().font;
        if (entry.getState() == AudioEntry.State.LOADING) {
            String label = entry.getLoadPhase() == AudioEntry.LoadPhase.DECODE ? "音频处理中…" : "音频下载中…";
            gfx.text(font, label, bx0 + btn + 8, y0 + 9, argb(opacity, 210, 210, 215), false);
            return;
        }
        if (entry.getState() == AudioEntry.State.FAILED) {
            gfx.text(font, "音频加载失败", bx0 + btn + 8, y0 + 9, argb(opacity, 255, 120, 120), false);
            return;
        }

        boolean playing = AudioPlayerService.isPlaying(url);
        String icon = playing ? "||" : ">";
        gfx.text(font, icon, bx0 + 7, by0 + 6, argb(opacity, 255, 255, 255), false);

        long pos = AudioPlayerService.positionMs(url);
        long total = AudioPlayerService.durationMs(url);
        if (total <= 0) {
            total = entry.getDurationMs();
        }
        int barX0 = bx0 + btn + 8;
        int barX1 = x1 - 8;
        int barY0 = y0 + h - 14;
        int barY1 = barY0 + 6;
        gfx.fill(barX0, barY0, barX1, barY1, argb(opacity, 68, 72, 82));
        float ratio = total <= 0 ? 0f : Math.clamp((float) pos / total, 0f, 1f);
        int fillX = barX0 + Math.round((barX1 - barX0) * ratio);
        gfx.fill(barX0, barY0, fillX, barY1, argb(opacity, 100, 200, 255));
        String time = formatMs(pos) + " / " + formatMs(total);
        gfx.text(font, time, barX0, y0 + 8, argb(opacity, 215, 220, 230), false);
    }

    private static String formatMs(long ms) {
        long s = Math.max(0L, ms / 1000L);
        long m = s / 60L;
        long r = s % 60L;
        return String.format("%d:%02d", m, r);
    }

    private static void paintDecodedBlit(GuiGraphicsExtractor gfx, ImageEntry entry, int messageY, float opacity) {
        Identifier textureId = entry.isAnimated()
                ? entry.textureIdAtMillis(Util.getMillis())
                : entry.getTextureId();
        if (textureId == null) return;

        int drawW = entry.getWidth();
        int drawH = entry.getHeight();
        int texW = entry.getTextureWidth();
        int texH = entry.getTextureHeight();

        if (drawW <= 0 || drawH <= 0 || texW <= 0 || texH <= 0) return;

        if (drawH > ImageLoader.PREVIEW_HEIGHT) drawH = ImageLoader.PREVIEW_HEIGHT;

        int color = ARGB.white(opacity);
        gfx.blit(
                RenderPipelines.GUI_TEXTURED,
                textureId,
                0, messageY,
                0.0f, 0.0f,
                drawW, drawH,
                texW, texH,
                texW, texH,
                color
        );
    }

    private static int argb(float opacity, int r, int g, int b) {
        int a = Math.clamp(Math.round(opacity * 255.0f), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void paintLoadingStrip(GuiGraphicsExtractor gfx, ImageEntry entry, int messageY, float opacity) {
        int s = ImageLoader.PREVIEW_HEIGHT;
        int x0 = 0;
        int y0 = messageY;
        int x1 = x0 + s;
        int y1 = y0 + s;

        gfx.fill(x0, y0, x1, y1, argb(opacity * 0.85f, 28, 28, 32));

        long t = Util.getMillis();
        int sweepW = Math.min(48, s - 16);
        int travel = Math.max(1, s - 16 - sweepW);
        int sweepX = x0 + 8 + (int) ((t / 35L) % travel);

        gfx.fill(sweepX, y1 - 7, sweepX + sweepW, y1 - 3, argb(opacity, 100, 180, 255));

        Font font = Minecraft.getInstance().font;
        String label = entry.getLoadPhase() == ImageEntry.LoadPhase.DECODE ? "处理中…" : "下载中…";
        int textColor = argb(opacity, 200, 200, 210);
        gfx.centeredText(font, label, x0 + s / 2, y0 + s / 2 - font.lineHeight / 2, textColor);
    }
}
