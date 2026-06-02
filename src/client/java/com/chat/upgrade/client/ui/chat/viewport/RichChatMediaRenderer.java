package com.chat.upgrade.client.ui.chat.viewport;

import com.chat.upgrade.client.ChatUpgradeFormatters;
import com.chat.upgrade.client.media.audio.AudioEntry;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.audio.AudioPlayerService;
import com.chat.upgrade.client.media.image.ImageEntry;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.media.video.VideoEntry;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.media.video.VideoPlayerService;
import com.chat.upgrade.client.ui.chat.InlineEmojiSlot;
import com.chat.upgrade.client.ui.chat.UpgradeHudInlinePaint;
import com.chat.upgrade.client.ui.layout.AudioUiLayout;
import com.chat.upgrade.client.ui.layout.VideoUiLayout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

public final class RichChatMediaRenderer {
    private static final int EMOJI_SIDE_GAP_PX = 1;

    private RichChatMediaRenderer() {
    }

    public static void paintNode(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatViewportMetrics metrics,
            RichChatRenderNode node,
            int contentToLocalY,
            float messageOpacity) {
        if (gfx == null || font == null || metrics == null || node == null || messageOpacity <= 1.0e-5f) {
            return;
        }
        RichChatBounds localBounds = node.bounds().translateY(contentToLocalY);
        switch (node.kind()) {
            case TEXT, SYSTEM -> paintText(gfx, font, metrics, node, localBounds, messageOpacity);
            case IMAGE, AUDIO, VIDEO, ATTACHMENT_PENDING, ATTACHMENT_FAILED -> paintAttachment(
                    gfx,
                    font,
                    node.kind(),
                    localBounds,
                    node.attachment(),
                    messageOpacity);
        }
    }

    public static void paintAttachment(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatRenderNodeKind kind,
            RichChatBounds bounds,
            RichAttachment attachment,
            float opacity) {
        if (gfx == null || font == null || bounds == null || opacity <= 1.0e-5f) {
            return;
        }
        if (attachment == null || kind == RichChatRenderNodeKind.ATTACHMENT_PENDING || !attachment.hasRenderableUrl()) {
            paintPending(gfx, font, bounds, opacity);
            return;
        }
        if (kind == RichChatRenderNodeKind.ATTACHMENT_FAILED) {
            paintFailure(gfx, font, bounds, attachment, opacity);
            return;
        }
        String url = attachment.requireRenderableUrl();
        switch (attachment.type()) {
            case IMAGE -> paintImage(gfx, font, bounds, url, opacity);
            case AUDIO -> paintAudio(gfx, font, bounds, attachment.displayName(), url, opacity);
            case VIDEO -> paintVideo(gfx, font, bounds, attachment.displayName(), url, opacity);
        }
    }

    public static RichChatBounds defaultAttachmentBounds(int left, int top, int maxWidth, int fallbackLineHeight,
            RichAttachment attachment) {
        RichChatMediaBox box = RichChatMediaSizing.measure(maxWidth, fallbackLineHeight, attachment);
        return box.at(left, top);
    }

    private static void paintText(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatViewportMetrics metrics,
            RichChatRenderNode node,
            RichChatBounds bounds,
            float messageOpacity) {
        if (node.text() == null) {
            return;
        }
        int textY = bounds.bottom() - metrics.entryBottomToMessageY();
        gfx.text(font, node.text(), bounds.left(), textY, ARGB.white(messageOpacity * metrics.textOpacity()), false);
        paintInlineEmojis(gfx, font, metrics, node, bounds, textY, messageOpacity);
    }

    private static void paintInlineEmojis(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatViewportMetrics metrics,
            RichChatRenderNode node,
            RichChatBounds bounds,
            int textY,
            float messageOpacity) {
        if (node.inlineEmojiSlots().isEmpty() || node.text() == null) {
            return;
        }
        String plain = extractPlain(node.text());
        int size = Math.max(1, metrics.entryHeight() - EMOJI_SIDE_GAP_PX * 2);
        float opacity = messageOpacity * metrics.textOpacity();
        for (InlineEmojiSlot slot : node.inlineEmojiSlots()) {
            int charIndex = Math.clamp(slot.charIndex(), 0, plain.length());
            int x = bounds.left() + font.width(plain.substring(0, charIndex)) + EMOJI_SIDE_GAP_PX;
            int y = textY + EMOJI_SIDE_GAP_PX;
            paintInlineEmoji(gfx, slot.iconUrl(), x, y, size, opacity);
        }
    }

    private static void paintInlineEmoji(GuiGraphicsExtractor gfx, String url, int x, int y, int size, float opacity) {
        ImageEntry entry = ImageLoader.getOrLoad(url);
        switch (entry.getState()) {
            case FAILED -> {
                return;
            }
            case LOADING -> gfx.fill(x, y, x + size, y + size, argb(opacity * 0.85f, 28, 28, 32));
            case LOADED -> {
                Identifier textureId = entry.isAnimated() ? entry.textureIdAtMillis(Util.getMillis())
                        : entry.getTextureId();
                if (textureId == null) {
                    return;
                }
                gfx.blit(
                        RenderPipelines.GUI_TEXTURED,
                        textureId,
                        x, y,
                        0.0f, 0.0f,
                        size, size,
                        entry.getTextureWidth(), entry.getTextureHeight(),
                        entry.getTextureWidth(), entry.getTextureHeight(),
                        ARGB.white(opacity));
            }
        }
    }

    private static String extractPlain(net.minecraft.util.FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    private static void paintImage(GuiGraphicsExtractor gfx, Font font, RichChatBounds bounds, String url, float opacity) {
        ImageEntry entry = ImageLoader.getOrLoad(url);
        switch (entry.getState()) {
            case FAILED -> paintImageFailure(gfx, font, bounds, opacity);
            case LOADING -> paintImageLoading(gfx, font, bounds, entry, opacity);
            case LOADED -> paintDecodedImage(gfx, bounds, entry, opacity);
        }
    }

    private static void paintAudio(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatBounds bounds,
            String resourceName,
            String url,
            float opacity) {
        AudioEntry entry = AudioLoader.getOrLoad(url);
        int x0 = bounds.left();
        int y0 = bounds.top();
        int x1 = bounds.right();
        String name = AudioUiLayout.shortName(resourceName, url);
        if (entry.getState() == AudioEntry.State.LOADING) {
            gfx.fill(x0, y0, x1, bounds.bottom(), argb(opacity * 0.5f, 24, 26, 31));
            String label = entry.getLoadPhase() == AudioEntry.LoadPhase.DECODE
                    ? I18n.get("chatupgrade.hud.audio.processing")
                    : I18n.get("chatupgrade.hud.audio.downloading");
            gfx.text(font, name + "  " + ChatUpgradeFormatters.formatMs(0) + " / " + ChatUpgradeFormatters.formatMs(0),
                    x0 + UpgradeHudInlinePaint.AUDIO_PAD_X, y0 + UpgradeHudInlinePaint.AUDIO_LINE1_Y,
                    argb(opacity, 210, 210, 215), false);
            gfx.text(font, label, x0 + UpgradeHudInlinePaint.AUDIO_PAD_X,
                    y0 + UpgradeHudInlinePaint.AUDIO_LINE2_Y, argb(opacity, 210, 210, 215), false);
            return;
        }
        if (entry.getState() == AudioEntry.State.FAILED) {
            paintFailure(gfx, font, bounds, RichAttachment.structured(attachmentTypeAudio(), name, url, null, null), opacity);
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
                x0 + UpgradeHudInlinePaint.AUDIO_PAD_X,
                y0 + UpgradeHudInlinePaint.AUDIO_LINE1_Y,
                argb(opacity, 215, 220, 230),
                false);

        boolean loop = AudioPlayerService.isLoopEnabled(url);
        AudioUiLayout.ButtonRects rects = AudioUiLayout.buttonRects(x0, y0);
        paintButton(gfx, font, rects.playLeft(), rects.top(), rects.playRight(), rects.bottom(), playing ? "⏸" : "▶",
                opacity, true);
        paintButton(gfx, font, rects.loopLeft(), rects.top(), rects.loopRight(), rects.bottom(), loop ? "🔁" : "1×",
                opacity, loop);
        paintButton(gfx, font, rects.openLeft(), rects.top(), rects.openRight(), rects.bottom(), "⧉", opacity, false);
        paintButton(gfx, font, rects.popLeft(), rects.top(), rects.popRight(), rects.bottom(), "🗖", opacity, false);

        int barX0 = x0 + UpgradeHudInlinePaint.AUDIO_PAD_X;
        int barX1 = x1 - UpgradeHudInlinePaint.AUDIO_PAD_X;
        int barY0 = y0 + UpgradeHudInlinePaint.AUDIO_PROGRESS_Y;
        int barY1 = barY0 + UpgradeHudInlinePaint.AUDIO_PROGRESS_H;
        gfx.fill(barX0, barY0, barX1, barY1, argb(opacity, 68, 72, 82));
        float ratio = total <= 0 ? 0f : Math.clamp((float) pos / total, 0f, 1f);
        int fillX = barX0 + Math.round((barX1 - barX0) * ratio);
        gfx.fill(barX0, barY0, fillX, barY1, argb(opacity, 100, 200, 255));
    }

    private static void paintVideo(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatBounds bounds,
            String resourceName,
            String url,
            float opacity) {
        VideoEntry entry = VideoLoader.getOrLoad(url);
        int x0 = bounds.left();
        int y0 = bounds.top();
        int drawW = Math.max(1, bounds.width());
        int x1 = bounds.right();
        String name = AudioUiLayout.shortName(resourceName, url);
        if (entry.getState() == VideoEntry.State.LOADING) {
            gfx.fill(x0, y0, x1, bounds.bottom(), argb(opacity * 0.55f, 20, 22, 26));
            String label = entry.getLoadPhase() == VideoEntry.LoadPhase.DECODE
                    ? I18n.get("chatupgrade.hud.video.processing")
                    : I18n.get("chatupgrade.hud.video.downloading");
            gfx.text(font, name, x0 + VideoUiLayout.PAD_X, y0 + 2, argb(opacity, 220, 220, 225), false);
            gfx.text(font, label, x0 + VideoUiLayout.PAD_X, y0 + 13, argb(opacity, 210, 210, 215), false);
            return;
        }
        if (entry.getState() == VideoEntry.State.FAILED) {
            paintFailure(gfx, font, bounds, RichAttachment.structured(attachmentTypeVideo(), name, url, null, null), opacity);
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

    private static void paintDecodedImage(GuiGraphicsExtractor gfx, RichChatBounds bounds, ImageEntry entry, float opacity) {
        Identifier textureId = entry.isAnimated() ? entry.textureIdAtMillis(Util.getMillis()) : entry.getTextureId();
        if (textureId == null) {
            return;
        }
        int drawW = Math.min(Math.max(1, bounds.width()), entry.getWidth());
        int drawH = Math.min(Math.max(1, bounds.height()), entry.getHeight());
        int texW = entry.getTextureWidth();
        int texH = entry.getTextureHeight();
        if (drawW <= 0 || drawH <= 0 || texW <= 0 || texH <= 0) {
            return;
        }
        gfx.blit(
                RenderPipelines.GUI_TEXTURED,
                textureId,
                bounds.left(), bounds.top(),
                0.0f, 0.0f,
                drawW, drawH,
                texW, texH,
                texW, texH,
                ARGB.white(opacity));
    }

    private static void paintImageLoading(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatBounds bounds,
            ImageEntry entry,
            float opacity) {
        int x0 = bounds.left();
        int y0 = bounds.top();
        int x1 = bounds.right();
        int y1 = bounds.bottom();
        gfx.fill(x0, y0, x1, y1, argb(opacity * 0.85f, 28, 28, 32));

        long t = Util.getMillis();
        int width = Math.max(1, bounds.width());
        int sweepW = Math.min(48, Math.max(1, width - 16));
        int travel = Math.max(1, width - 16 - sweepW);
        int sweepX = x0 + 8 + (int) ((t / 35L) % travel);
        gfx.fill(sweepX, y1 - 7, sweepX + sweepW, y1 - 3, argb(opacity, 100, 180, 255));

        String label = entry.getLoadPhase() == ImageEntry.LoadPhase.DECODE
                ? I18n.get("chatupgrade.hud.image.processing")
                : I18n.get("chatupgrade.hud.image.downloading");
        gfx.centeredText(font, label, x0 + width / 2, y0 + bounds.height() / 2 - font.lineHeight / 2,
                argb(opacity, 200, 200, 210));
    }

    private static void paintPending(GuiGraphicsExtractor gfx, Font font, RichChatBounds bounds, float opacity) {
        gfx.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), argb(opacity * 0.5f, 24, 26, 31));
        gfx.text(font, I18n.get("chatupgrade.hud.image.downloading"), bounds.left() + 6, bounds.top() + 1,
                argb(opacity, 210, 210, 215), false);
    }

    private static void paintImageFailure(GuiGraphicsExtractor gfx, Font font, RichChatBounds bounds, float opacity) {
        gfx.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), argb(opacity * 0.5f, 40, 18, 18));
        gfx.text(font, I18n.get("chatupgrade.inline.state.image_failed"), bounds.left() + 6, bounds.top() + 1,
                argb(opacity, 255, 120, 120), false);
    }

    private static void paintFailure(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatBounds bounds,
            RichAttachment attachment,
            float opacity) {
        gfx.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), argb(opacity * 0.5f, 40, 18, 18));
        String label = switch (attachment.type()) {
            case IMAGE -> I18n.get("chatupgrade.inline.state.image_failed");
            case AUDIO -> I18n.get("chatupgrade.hud.audio.failed");
            case VIDEO -> I18n.get("chatupgrade.hud.video.failed");
        };
        gfx.text(font, label, bounds.left() + 6, bounds.top() + 1, argb(opacity, 255, 120, 120), false);
    }

    private static void paintButton(
            GuiGraphicsExtractor gfx,
            Font font,
            int x0,
            int y0,
            int x1,
            int y1,
            String label,
            float opacity,
            boolean active) {
        int bg = active ? argb(opacity, 76, 98, 132) : argb(opacity, 58, 62, 72);
        gfx.fill(x0, y0, x1, y1, bg);
        int tx = x0 + Math.max(1, (x1 - x0 - font.width(label)) / 2);
        gfx.text(font, label, tx, y0, argb(opacity, 230, 234, 240), false);
    }

    private static com.chat.upgrade.client.media.model.InlineResourceType attachmentTypeAudio() {
        return com.chat.upgrade.client.media.model.InlineResourceType.AUDIO;
    }

    private static com.chat.upgrade.client.media.model.InlineResourceType attachmentTypeVideo() {
        return com.chat.upgrade.client.media.model.InlineResourceType.VIDEO;
    }

    private static int argb(float opacity, int r, int g, int b) {
        int a = Math.clamp(Math.round(opacity * 255.0f), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}