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
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceRuntime;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.layout.AudioUiLayout;
import com.chat.upgrade.client.ui.layout.VideoUiLayout;
import com.chat.upgrade.client.ui.render.UiPrimitives;
import com.chat.upgrade.client.ui.render.UiTextureAtlas;

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
            float messageOpacity,
            ChatAppearanceSnapshot appearance) {
        if (gfx == null || font == null || metrics == null || node == null || messageOpacity <= 1.0e-5f) {
            return;
        }
        ChatAppearanceSnapshot activeAppearance = appearance == null
                ? ChatAppearanceRuntime.current()
                : appearance;
        RichChatBounds localBounds = node.bounds().translateY(contentToLocalY);
        switch (node.kind()) {
            case DELETED, REPLY, TEXT, SYSTEM -> paintText(
                    gfx,
                    font,
                    metrics,
                    node,
                    localBounds,
                    messageOpacity,
                    activeAppearance.message(),
                    activeAppearance.media());
            case IMAGE, AUDIO, VIDEO, ATTACHMENT_PENDING, ATTACHMENT_FAILED -> paintAttachment(
                    gfx,
                    font,
                    node.kind(),
                    localBounds,
                    node.attachment(),
                    messageOpacity,
                    activeAppearance);
        }
    }

    public static void paintNode(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatViewportMetrics metrics,
            RichChatRenderNode node,
            int contentToLocalY,
            float messageOpacity) {
        paintNode(gfx, font, metrics, node, contentToLocalY, messageOpacity, ChatAppearanceRuntime.current());
    }

    public static void paintAttachment(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatRenderNodeKind kind,
            RichChatBounds bounds,
            RichAttachment attachment,
            float opacity,
            ChatAppearanceSnapshot appearance) {
        if (gfx == null || font == null || bounds == null || opacity <= 1.0e-5f) {
            return;
        }
        ChatAppearanceSnapshot activeAppearance = appearance == null
                ? ChatAppearanceRuntime.current()
                : appearance;
        ChatAppearanceSnapshot.Media tokens = activeAppearance.media();
        int cornerRadius = activeAppearance.cornerRadius();
        if (attachment == null || kind == RichChatRenderNodeKind.ATTACHMENT_PENDING || !attachment.hasRenderableUrl()) {
            paintPending(gfx, font, bounds, opacity, tokens, cornerRadius);
            return;
        }
        if (kind == RichChatRenderNodeKind.ATTACHMENT_FAILED) {
            paintFailure(gfx, font, bounds, attachment, opacity, tokens, cornerRadius);
            return;
        }
        String url = attachment.requireRenderableUrl();
        switch (attachment.type()) {
            case IMAGE -> paintImage(gfx, font, bounds, url, opacity, tokens, cornerRadius);
            case AUDIO -> paintAudio(gfx, font, bounds, attachment.displayName(), url, opacity, tokens, cornerRadius);
            case VIDEO -> paintVideo(gfx, font, bounds, attachment.displayName(), url, opacity, tokens, cornerRadius);
        }
    }

    public static void paintAttachment(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatRenderNodeKind kind,
            RichChatBounds bounds,
            RichAttachment attachment,
            float opacity) {
        paintAttachment(gfx, font, kind, bounds, attachment, opacity, ChatAppearanceRuntime.current());
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
            float messageOpacity,
            ChatAppearanceSnapshot.Message tokens,
            ChatAppearanceSnapshot.Media mediaTokens) {
        if (node.text() == null) {
            return;
        }
        int textY = bounds.bottom() - metrics.entryBottomToMessageY();
        int textColor = switch (node.kind()) {
            case DELETED -> tokens.deletedText();
            case REPLY -> tokens.replyText();
            case SYSTEM -> tokens.systemText();
            default -> tokens.text();
        };
        gfx.text(
                font,
                node.text(),
                bounds.left(),
                textY,
                UiPrimitives.withOpacity(textColor, messageOpacity * metrics.textOpacity()),
                false);
        paintInlineEmojis(gfx, font, metrics, node, bounds, textY, messageOpacity, mediaTokens);
    }

    private static void paintInlineEmojis(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatViewportMetrics metrics,
            RichChatRenderNode node,
            RichChatBounds bounds,
            int textY,
            float messageOpacity,
            ChatAppearanceSnapshot.Media tokens) {
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
            paintInlineEmoji(gfx, slot.iconUrl(), x, y, size, opacity, tokens);
        }
    }

    private static void paintInlineEmoji(
            GuiGraphicsExtractor gfx,
            String url,
            int x,
            int y,
            int size,
            float opacity,
            ChatAppearanceSnapshot.Media tokens) {
        ImageEntry entry = ImageLoader.getOrLoad(url);
        switch (entry.getState()) {
            case FAILED -> {
                return;
            }
            case LOADING -> gfx.fill(
                    x,
                    y,
                    x + size,
                    y + size,
                    UiPrimitives.withOpacity(tokens.emojiLoadingBackground(), opacity));
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

    private static void paintImage(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatBounds bounds,
            String url,
            float opacity,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        ImageEntry entry = ImageLoader.getOrLoad(url);
        switch (entry.getState()) {
            case FAILED -> paintImageFailure(gfx, font, bounds, opacity, tokens, cornerRadius);
            case LOADING -> paintImageLoading(gfx, font, bounds, entry, opacity, tokens, cornerRadius);
            case LOADED -> paintDecodedImage(gfx, bounds, entry, opacity);
        }
    }

    private static void paintAudio(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatBounds bounds,
            String resourceName,
            String url,
            float opacity,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        AudioEntry entry = AudioLoader.getOrLoad(url);
        int x0 = bounds.left();
        int y0 = bounds.top();
        int x1 = bounds.right();
        String name = AudioUiLayout.shortName(resourceName, url);
        UiPrimitives.fillRounded(
                gfx,
                bounds,
                cornerRadius,
                UiPrimitives.withOpacity(tokens.loadingBackground(), opacity));
        if (entry.getState() == AudioEntry.State.LOADING) {
            String label = entry.getLoadPhase() == AudioEntry.LoadPhase.DECODE
                    ? I18n.get("chatupgrade.hud.audio.processing")
                    : I18n.get("chatupgrade.hud.audio.downloading");
            gfx.text(font, name + "  " + ChatUpgradeFormatters.formatMs(0) + " / " + ChatUpgradeFormatters.formatMs(0),
                    x0 + UpgradeHudInlinePaint.AUDIO_PAD_X, y0 + UpgradeHudInlinePaint.AUDIO_LINE1_Y,
                    UiPrimitives.withOpacity(tokens.muted(), opacity), false);
            gfx.text(font, label, x0 + UpgradeHudInlinePaint.AUDIO_PAD_X,
                    y0 + UpgradeHudInlinePaint.AUDIO_LINE2_Y,
                    UiPrimitives.withOpacity(tokens.muted(), opacity), false);
            return;
        }
        if (entry.getState() == AudioEntry.State.FAILED) {
            paintFailure(
                    gfx,
                    font,
                    bounds,
                    RichAttachment.structured(attachmentTypeAudio(), name, url, null, null),
                    opacity,
                    tokens,
                    cornerRadius);
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
                UiPrimitives.withOpacity(tokens.text(), opacity),
                false);

        boolean loop = AudioPlayerService.isLoopEnabled(url);
        AudioUiLayout.ButtonRects rects = AudioUiLayout.buttonRects(x0, y0);
        paintButton(
                gfx,
                rects.playLeft(),
                rects.top(),
                rects.playRight(),
                rects.bottom(),
                playing ? UiTextureAtlas.Icon.PAUSE : UiTextureAtlas.Icon.PLAY,
                opacity,
                true,
                tokens);
        paintButton(
                gfx,
                rects.loopLeft(),
                rects.top(),
                rects.loopRight(),
                rects.bottom(),
                UiTextureAtlas.Icon.LOOP,
                opacity,
                loop,
                tokens);
        paintButton(
                gfx,
                rects.openLeft(),
                rects.top(),
                rects.openRight(),
                rects.bottom(),
                UiTextureAtlas.Icon.OPEN,
                opacity,
                false,
                tokens);
        paintButton(
                gfx,
                rects.popLeft(),
                rects.top(),
                rects.popRight(),
                rects.bottom(),
                UiTextureAtlas.Icon.POPOUT,
                opacity,
                false,
                tokens);

        int barX0 = x0 + UpgradeHudInlinePaint.AUDIO_PAD_X;
        int barX1 = x1 - UpgradeHudInlinePaint.AUDIO_PAD_X;
        int barY0 = y0 + UpgradeHudInlinePaint.AUDIO_PROGRESS_Y;
        int barY1 = barY0 + UpgradeHudInlinePaint.AUDIO_PROGRESS_H;
        gfx.fill(
                barX0,
                barY0,
                barX1,
                barY1,
                UiPrimitives.withOpacity(tokens.progressTrack(), opacity));
        float ratio = total <= 0 ? 0f : Math.clamp((float) pos / total, 0f, 1f);
        int fillX = barX0 + Math.round((barX1 - barX0) * ratio);
        gfx.fill(
                barX0,
                barY0,
                fillX,
                barY1,
                UiPrimitives.withOpacity(tokens.progressFill(), opacity));
    }

    private static void paintVideo(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatBounds bounds,
            String resourceName,
            String url,
            float opacity,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        VideoEntry entry = VideoLoader.getOrLoad(url);
        int x0 = bounds.left();
        int y0 = bounds.top();
        int drawW = Math.max(1, bounds.width());
        int x1 = bounds.right();
        String name = AudioUiLayout.shortName(resourceName, url);
        UiPrimitives.fillRounded(
                gfx,
                bounds,
                cornerRadius,
                UiPrimitives.withOpacity(tokens.loadingBackground(), opacity));
        if (entry.getState() == VideoEntry.State.LOADING) {
            String label = entry.getLoadPhase() == VideoEntry.LoadPhase.DECODE
                    ? I18n.get("chatupgrade.hud.video.processing")
                    : I18n.get("chatupgrade.hud.video.downloading");
            gfx.text(
                    font,
                    name,
                    x0 + VideoUiLayout.PAD_X,
                    y0 + 2,
                    UiPrimitives.withOpacity(tokens.text(), opacity),
                    false);
            gfx.text(
                    font,
                    label,
                    x0 + VideoUiLayout.PAD_X,
                    y0 + 13,
                    UiPrimitives.withOpacity(tokens.muted(), opacity),
                    false);
            return;
        }
        if (entry.getState() == VideoEntry.State.FAILED) {
            paintFailure(
                    gfx,
                    font,
                    bounds,
                    RichAttachment.structured(attachmentTypeVideo(), name, url, null, null),
                    opacity,
                    tokens,
                    cornerRadius);
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
        gfx.fill(
                btnX0,
                controlY,
                btnX1,
                controlY + VideoUiLayout.BTN_H,
                UiPrimitives.withOpacity(tokens.controlBackground(), opacity));
        UiTextureAtlas.drawIcon(
                gfx,
                playing ? UiTextureAtlas.Icon.PAUSE : UiTextureAtlas.Icon.PLAY,
                RichChatBounds.ofSize(
                        btnX0 + Math.max(1, (VideoUiLayout.BTN_W - 12) / 2),
                        controlY + Math.max(1, (VideoUiLayout.BTN_H - 12) / 2),
                        12,
                        12),
                UiPrimitives.withOpacity(tokens.text(), opacity));

        String left = ChatUpgradeFormatters.formatMs(pos);
        String right = ChatUpgradeFormatters.formatMs(total);
        int leftX = btnX1 + 4;
        int rightX = x1 - VideoUiLayout.PAD_X - font.width(right);
        gfx.text(font, left, leftX, controlY, UiPrimitives.withOpacity(tokens.text(), opacity), false);
        gfx.text(font, right, rightX, controlY, UiPrimitives.withOpacity(tokens.text(), opacity), false);

        int barX0 = leftX + font.width(left) + 4;
        int barX1 = rightX - 4;
        int barY0 = y0 + VideoUiLayout.PROGRESS_TOP;
        int barY1 = barY0 + VideoUiLayout.PROGRESS_H;
        if (barX1 > barX0) {
            gfx.fill(
                    barX0,
                    barY0,
                    barX1,
                    barY1,
                    UiPrimitives.withOpacity(tokens.progressTrack(), opacity));
            float ratio = total <= 0 ? 0f : Math.clamp((float) pos / total, 0f, 1f);
            int fillX = barX0 + Math.round((barX1 - barX0) * ratio);
            gfx.fill(
                    barX0,
                    barY0,
                    fillX,
                    barY1,
                    UiPrimitives.withOpacity(tokens.progressFill(), opacity));
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
            float opacity,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        int x0 = bounds.left();
        int y0 = bounds.top();
        int y1 = bounds.bottom();
        UiPrimitives.fillRounded(
                gfx,
                bounds,
                cornerRadius,
                UiPrimitives.withOpacity(tokens.loadingBackground(), opacity));

        long t = Util.getMillis();
        int width = Math.max(1, bounds.width());
        int sweepW = Math.min(48, Math.max(1, width - 16));
        int travel = Math.max(1, width - 16 - sweepW);
        int sweepX = x0 + 8 + (int) ((t / 35L) % travel);
        gfx.fill(
                sweepX,
                y1 - 7,
                sweepX + sweepW,
                y1 - 3,
                UiPrimitives.withOpacity(tokens.progressFill(), opacity));

        String label = entry.getLoadPhase() == ImageEntry.LoadPhase.DECODE
                ? I18n.get("chatupgrade.hud.image.processing")
                : I18n.get("chatupgrade.hud.image.downloading");
        gfx.centeredText(
                font,
                label,
                x0 + width / 2,
                y0 + bounds.height() / 2 - font.lineHeight / 2,
                UiPrimitives.withOpacity(tokens.muted(), opacity));
    }

    private static void paintPending(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatBounds bounds,
            float opacity,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        UiPrimitives.fillRounded(
                gfx,
                bounds,
                cornerRadius,
                UiPrimitives.withOpacity(tokens.pendingBackground(), opacity));
        gfx.text(
                font,
                I18n.get("chatupgrade.hud.image.downloading"),
                bounds.left() + 6,
                bounds.top() + 1,
                UiPrimitives.withOpacity(tokens.muted(), opacity),
                false);
    }

    private static void paintImageFailure(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatBounds bounds,
            float opacity,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        UiPrimitives.fillRounded(
                gfx,
                bounds,
                cornerRadius,
                UiPrimitives.withOpacity(tokens.failureBackground(), opacity));
        gfx.text(
                font,
                I18n.get("chatupgrade.inline.state.image_failed"),
                bounds.left() + 6,
                bounds.top() + 1,
                UiPrimitives.withOpacity(tokens.failureText(), opacity),
                false);
    }

    private static void paintFailure(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatBounds bounds,
            RichAttachment attachment,
            float opacity,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        UiPrimitives.fillRounded(
                gfx,
                bounds,
                cornerRadius,
                UiPrimitives.withOpacity(tokens.failureBackground(), opacity));
        String label = switch (attachment.type()) {
            case IMAGE -> I18n.get("chatupgrade.inline.state.image_failed");
            case AUDIO -> I18n.get("chatupgrade.hud.audio.failed");
            case VIDEO -> I18n.get("chatupgrade.hud.video.failed");
        };
        gfx.text(
                font,
                label,
                bounds.left() + 6,
                bounds.top() + 1,
                UiPrimitives.withOpacity(tokens.failureText(), opacity),
                false);
    }

    private static void paintButton(
            GuiGraphicsExtractor gfx,
            int x0,
            int y0,
            int x1,
            int y1,
            UiTextureAtlas.Icon icon,
            float opacity,
            boolean active,
            ChatAppearanceSnapshot.Media tokens) {
        int background = active ? tokens.controlActiveBackground() : tokens.controlBackground();
        gfx.fill(x0, y0, x1, y1, UiPrimitives.withOpacity(background, opacity));
        int size = Math.max(1, Math.min(12, Math.min(x1 - x0, y1 - y0) - 2));
        UiTextureAtlas.drawIcon(
                gfx,
                icon,
                RichChatBounds.ofSize(
                        x0 + Math.max(0, (x1 - x0 - size) / 2),
                        y0 + Math.max(0, (y1 - y0 - size) / 2),
                        size,
                        size),
                UiPrimitives.withOpacity(tokens.text(), opacity));
    }

    private static com.chat.upgrade.client.media.model.InlineResourceType attachmentTypeAudio() {
        return com.chat.upgrade.client.media.model.InlineResourceType.AUDIO;
    }

    private static com.chat.upgrade.client.media.model.InlineResourceType attachmentTypeVideo() {
        return com.chat.upgrade.client.media.model.InlineResourceType.VIDEO;
    }
}