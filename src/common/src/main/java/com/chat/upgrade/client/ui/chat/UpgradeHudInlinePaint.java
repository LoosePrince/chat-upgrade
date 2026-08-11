package com.chat.upgrade.client.ui.chat;

import com.chat.upgrade.client.mixininterface.ImageAttachable;
import com.chat.upgrade.client.ChatUpgradeFormatters;
import com.chat.upgrade.client.media.audio.AudioEntry;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.audio.AudioPlayerService;
import com.chat.upgrade.client.media.image.ImageEntry;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.model.MediaFailureKind;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.media.video.VideoEntry;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.media.video.VideoPlayerService;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.chat.viewport.RichChatMediaBox;
import com.chat.upgrade.client.ui.chat.viewport.RichChatMediaRenderer;
import com.chat.upgrade.client.ui.chat.viewport.RichChatMediaSizing;
import com.chat.upgrade.client.ui.layout.AudioUiLayout;
import com.chat.upgrade.client.ui.layout.VideoUiLayout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

/**
 * Draws URL preview tiles in the chat HUD using the scoped
 * {@link GuiGraphicsExtractor} from {@link ChatUpgradeRenderScope}.
 */
public final class UpgradeHudInlinePaint {
    private UpgradeHudInlinePaint() {
    }

    public static final int AUDIO_HEIGHT = 27;
    public static final int AUDIO_WIDTH = 220;
    public static final int AUDIO_PAD_X = 6;
    public static final int AUDIO_LINE1_Y = 2;
    public static final int AUDIO_LINE2_Y = 12;
    public static final int AUDIO_PROGRESS_Y = 21;
    public static final int AUDIO_PROGRESS_H = 4;

    public static void paintLinePreview(GuiMessage.Line line, int messageY, float opacity) {
        if (!(((Object) line) instanceof ImageAttachable attachable))
            return;

        String resourceUrl = attachable.chatupgrade$getImageUrl();
        if (resourceUrl == null)
            return;

        GuiGraphicsExtractor gfx = ChatUpgradeRenderScope.current();
        if (gfx == null)
            return;

        RichAttachment attachment = attachable.chatupgrade$getAttachment();
        if (attachment == null) {
            attachment = RichAttachment.bracketProtocol(
                    resourceUrl,
                    attachable.chatupgrade$getResourceName(),
                    attachable.chatupgrade$getResourceType());
        }
        RichChatMediaBox box = RichChatMediaSizing.measure(
                Math.max(ImageLoader.MAX_PREVIEW_WIDTH, Math.max(AUDIO_WIDTH, VideoUiLayout.WIDTH)),
                9,
                attachment);
        RichChatBounds bounds = box.at(0, messageY);
        RichChatMediaRenderer.paintAttachment(
                gfx,
                Minecraft.getInstance().font,
                box.kind(),
                bounds,
                attachment,
                opacity);
    }

    private static void paintAudio(
            GuiGraphicsExtractor gfx,
            AudioEntry entry,
            String resourceName,
            String url,
            int messageY,
            float opacity) {
        int h = AUDIO_HEIGHT;
        int w = AUDIO_WIDTH;
        int x0 = 0;
        int y0 = messageY;
        int x1 = x0 + w;

        Font font = Minecraft.getInstance().font;
        String name = AudioUiLayout.shortName(resourceName, url);
        if (entry.getState() == AudioEntry.State.LOADING) {
            gfx.fill(x0, y0, x1, y0 + h, argb(opacity * 0.5f, 24, 26, 31));
            String label = entry.getLoadPhase() == AudioEntry.LoadPhase.DECODE
                    ? I18n.get("chatupgrade.hud.audio.processing")
                    : I18n.get("chatupgrade.hud.audio.downloading");
            gfx.text(font, name + "  " + ChatUpgradeFormatters.formatMs(0) + " / " + ChatUpgradeFormatters.formatMs(0),
                    x0 + AUDIO_PAD_X, y0 + AUDIO_LINE1_Y, argb(opacity, 210, 210, 215), false);
            gfx.text(font, label, x0 + AUDIO_PAD_X, y0 + AUDIO_LINE2_Y, argb(opacity, 210, 210, 215), false);
            return;
        }
        if (entry.getState() == AudioEntry.State.FAILED) {
            gfx.fill(x0, y0, x1, y0 + h, argb(opacity * 0.5f, 24, 26, 31));
            gfx.text(font, name + "  " + ChatUpgradeFormatters.formatMs(0) + " / " + ChatUpgradeFormatters.formatMs(0),
                    x0 + AUDIO_PAD_X, y0 + AUDIO_LINE1_Y, argb(opacity, 255, 120, 120), false);
            gfx.text(font, I18n.get("chatupgrade.hud.audio.failed"), x0 + AUDIO_PAD_X, y0 + AUDIO_LINE2_Y, argb(opacity, 255, 120, 120), false);
            return;
        }

        boolean playing = AudioPlayerService.isPlaying(url);
        long pos = AudioPlayerService.positionMs(url);
        long total = AudioPlayerService.durationMs(url);
        if (total <= 0) {
            total = entry.getDurationMs();
        }
        gfx.text(font,
                name + "  " + ChatUpgradeFormatters.formatMs(pos) + " / " + ChatUpgradeFormatters.formatMs(total),
                x0 + AUDIO_PAD_X, y0 + AUDIO_LINE1_Y, argb(opacity, 215, 220, 230), false);

        boolean loop = AudioPlayerService.isLoopEnabled(url);
        AudioUiLayout.ButtonRects rects = AudioUiLayout.buttonRects(x0, y0);
        String playIcon = playing ? "⏸" : "▶";
        String loopIcon = loop ? "🔁" : "1×";
        String openIcon = "⧉";
        String popIcon = "🗖";
        paintButton(gfx, font, rects.playLeft(), rects.top(), rects.playRight(), rects.bottom(), playIcon,
                opacity, true);
        paintButton(gfx, font, rects.loopLeft(), rects.top(), rects.loopRight(), rects.bottom(), loopIcon,
                opacity, loop);
        paintButton(gfx, font, rects.openLeft(), rects.top(), rects.openRight(), rects.bottom(), openIcon, opacity, false);
        paintButton(gfx, font, rects.popLeft(), rects.top(), rects.popRight(), rects.bottom(), popIcon, opacity, false);

        int barX0 = x0 + AUDIO_PAD_X;
        int barX1 = x1 - AUDIO_PAD_X;
        int barY0 = y0 + AUDIO_PROGRESS_Y;
        int barY1 = barY0 + AUDIO_PROGRESS_H;
        gfx.fill(barX0, barY0, barX1, barY1, argb(opacity, 68, 72, 82));
        float ratio = total <= 0 ? 0f : Math.clamp((float) pos / total, 0f, 1f);
        int fillX = barX0 + Math.round((barX1 - barX0) * ratio);
        gfx.fill(barX0, barY0, fillX, barY1, argb(opacity, 100, 200, 255));
    }

    private static void paintButton(
            GuiGraphicsExtractor gfx,
            Font font,
            int x0, int y0, int x1, int y1,
            String label,
            float opacity,
            boolean active) {
        int bg = active ? argb(opacity, 76, 98, 132) : argb(opacity, 58, 62, 72);
        gfx.fill(x0, y0, x1, y1, bg);
        int tx = x0 + Math.max(1, (x1 - x0 - font.width(label)) / 2);
        gfx.text(font, label, tx, y0, argb(opacity, 230, 234, 240), false);
    }

    private static void paintVideo(
            GuiGraphicsExtractor gfx,
            VideoEntry entry,
            String resourceName,
            String url,
            int messageY,
            float opacity) {
        int x0 = 0;
        int y0 = messageY;
        int drawW = VideoUiLayout.WIDTH;
        int drawH = VideoUiLayout.HEIGHT;
        int x1 = x0 + drawW;

        Font font = Minecraft.getInstance().font;
        String name = AudioUiLayout.shortName(resourceName, url);
        if (entry.getState() == VideoEntry.State.LOADING) {
            gfx.fill(x0, y0, x1, y0 + drawH, argb(opacity * 0.55f, 20, 22, 26));
            String label = entry.getLoadPhase() == VideoEntry.LoadPhase.DECODE
                    ? I18n.get("chatupgrade.hud.video.processing")
                    : I18n.get("chatupgrade.hud.video.downloading");
            gfx.text(font, name, x0 + VideoUiLayout.PAD_X, y0 + 2, argb(opacity, 220, 220, 225), false);
            gfx.text(font, label, x0 + VideoUiLayout.PAD_X, y0 + 13, argb(opacity, 210, 210, 215), false);
            return;
        }
        if (entry.getState() == VideoEntry.State.FAILED) {
            gfx.fill(x0, y0, x1, y0 + drawH, argb(opacity * 0.55f, 20, 22, 26));
            gfx.text(font, name, x0 + VideoUiLayout.PAD_X, y0 + 2, argb(opacity, 255, 120, 120), false);
            gfx.text(font, videoFailureLabel(entry.getFailureKind()), x0 + VideoUiLayout.PAD_X, y0 + 13,
                    argb(opacity, 255, 120, 120), false);
            return;
        }

        Identifier textureId = VideoPlayerService.textureIdAtMillis(url, Util.getMillis());
        VideoUiLayout.Rect videoRect = VideoUiLayout.fitVideoRect(
                x0,
                y0,
                drawW,
                entry.getRawWidth(),
                entry.getRawHeight());
        if (textureId != null && videoRect.right() > videoRect.left() && videoRect.bottom() > videoRect.top()) {
            gfx.blit(
                    RenderPipelines.GUI_TEXTURED,
                    textureId,
                    videoRect.left(), videoRect.top(),
                    0.0f, 0.0f,
                    videoRect.right() - videoRect.left(), videoRect.bottom() - videoRect.top(),
                    entry.getRawWidth() > 0 ? entry.getRawWidth() : drawW,
                    entry.getRawHeight() > 0 ? entry.getRawHeight() : VideoUiLayout.VIDEO_BOTTOM,
                    entry.getRawWidth() > 0 ? entry.getRawWidth() : drawW,
                    entry.getRawHeight() > 0 ? entry.getRawHeight() : VideoUiLayout.VIDEO_BOTTOM,
                    ARGB.white(opacity));
        }

        long pos = VideoPlayerService.positionMs(url);
        long total = VideoPlayerService.durationMs(url);
        if (total <= 0L) {
            total = entry.getDurationMs();
        }
        boolean playing = VideoPlayerService.isPlaying(url);
        int controlY = y0 + VideoUiLayout.CONTROL_TOP;
        int btnX0 = x0 + VideoUiLayout.PAD_X;
        int btnX1 = btnX0 + VideoUiLayout.BTN_W;
        gfx.fill(btnX0, controlY, btnX1, controlY + VideoUiLayout.BTN_H, argb(opacity, 58, 62, 72));
        String icon = playing ? "⏸" : "▶";
        int iconX = btnX0 + Math.max(1, (VideoUiLayout.BTN_W - font.width(icon)) / 2);
        gfx.text(font, icon, iconX, controlY, argb(opacity, 235, 236, 242), false);

        String left = ChatUpgradeFormatters.formatMs(pos);
        String right = ChatUpgradeFormatters.formatMs(total);
        int leftX = btnX1 + 4;
        int rightX = x1 - VideoUiLayout.PAD_X - font.width(right);
        gfx.text(font, left, leftX, controlY, argb(opacity, 222, 224, 230), false);
        gfx.text(font, right, rightX, controlY, argb(opacity, 222, 224, 230), false);

        int barX0 = leftX + font.width(left) + 4;
        int barX1 = rightX - 4;
        int barY0 = y0 + VideoUiLayout.PROGRESS_TOP;
        int barY1 = barY0 + VideoUiLayout.PROGRESS_H;
        if (barX1 > barX0) {
            gfx.fill(barX0, barY0, barX1, barY1, argb(opacity, 68, 72, 82));
            float ratio = total <= 0 ? 0f : Math.clamp((float) pos / total, 0f, 1f);
            int fillX = barX0 + Math.round((barX1 - barX0) * ratio);
            gfx.fill(barX0, barY0, fillX, barY1, argb(opacity, 100, 200, 255));
        }
    }

    private static String videoFailureLabel(MediaFailureKind failureKind) {
        return I18n.get(switch (failureKind) {
            case RESPONSE_BODY_TOO_LARGE -> "chatupgrade.hud.video.failed.too_large";
            case INVALID_FILE -> "chatupgrade.hud.video.failed.invalid";
            case EXPIRED_FILE -> "chatupgrade.hud.video.failed.expired";
            case MISSING_FILE -> "chatupgrade.hud.video.failed.missing";
            case UNAVAILABLE_FILE -> "chatupgrade.hud.video.failed.unavailable";
            case NETWORK_ERROR -> "chatupgrade.hud.video.failed.network_error";
            case DECODER_UNAVAILABLE -> "chatupgrade.hud.video.failed.decoder_unavailable";
            case UNSUPPORTED_FORMAT -> "chatupgrade.hud.video.failed.unsupported";
            case UNKNOWN -> "chatupgrade.hud.video.failed";
        });
    }

    private static void paintDecodedBlit(GuiGraphicsExtractor gfx, ImageEntry entry, int messageY, float opacity) {
        Identifier textureId = entry.isAnimated()
                ? entry.textureIdAtMillis(Util.getMillis())
                : entry.getTextureId();
        if (textureId == null)
            return;

        int drawW = entry.getWidth();
        int drawH = entry.getHeight();
        int texW = entry.getTextureWidth();
        int texH = entry.getTextureHeight();

        if (drawW <= 0 || drawH <= 0 || texW <= 0 || texH <= 0)
            return;

        if (drawH > ImageLoader.PREVIEW_HEIGHT)
            drawH = ImageLoader.PREVIEW_HEIGHT;

        int color = ARGB.white(opacity);
        gfx.blit(
                RenderPipelines.GUI_TEXTURED,
                textureId,
                0, messageY,
                0.0f, 0.0f,
                drawW, drawH,
                texW, texH,
                texW, texH,
                color);
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
        String label = entry.getLoadPhase() == ImageEntry.LoadPhase.DECODE
                ? I18n.get("chatupgrade.hud.image.processing")
                : I18n.get("chatupgrade.hud.image.downloading");
        int textColor = argb(opacity, 200, 200, 210);
        gfx.centeredText(font, label, x0 + s / 2, y0 + s / 2 - font.lineHeight / 2, textColor);
    }
}
