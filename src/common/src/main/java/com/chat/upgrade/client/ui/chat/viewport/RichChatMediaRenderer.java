package com.chat.upgrade.client.ui.chat.viewport;

import com.chat.upgrade.client.ChatClientConfigRuntime;
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
import com.chat.upgrade.client.ui.chat.InlineEmojiLayout;
import com.chat.upgrade.client.ui.chat.InlineEmojiSlot;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceRuntime;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.render.UiPrimitives;
import com.chat.upgrade.client.ui.render.UiTextureAtlas;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

public final class RichChatMediaRenderer {

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
        UiPrimitives.withRoundedClip(
                gfx,
                bounds,
                activeAppearance.cornerRadius(),
                () -> paintAttachmentContents(
                        gfx,
                        font,
                        kind,
                        bounds,
                        attachment,
                        opacity,
                        activeAppearance));
    }

    private static void paintAttachmentContents(
            GuiGraphicsExtractor gfx,
            Font font,
            RichChatRenderNodeKind kind,
            RichChatBounds bounds,
            RichAttachment attachment,
            float opacity,
            ChatAppearanceSnapshot appearance) {
        ChatAppearanceSnapshot.Media tokens = appearance.media();
        int cornerRadius = appearance.cornerRadius();
        UiPrimitives.fillRounded(
                gfx,
                bounds,
                cornerRadius,
                UiPrimitives.withOpacity(tokens.cardBackground(), opacity));
        if (attachment == null || kind == RichChatRenderNodeKind.ATTACHMENT_PENDING || !attachment.hasRenderableUrl()) {
            paintPending(gfx, font, bounds, opacity, tokens, cornerRadius);
        } else if (kind == RichChatRenderNodeKind.ATTACHMENT_FAILED) {
            paintFailure(gfx, font, bounds, attachment, opacity, tokens, cornerRadius);
        } else {
            String url = attachment.requireRenderableUrl();
            switch (attachment.type()) {
                case IMAGE -> paintImage(gfx, font, bounds, url, opacity, tokens, cornerRadius);
                case AUDIO -> paintAudio(gfx, font, bounds, attachment.displayName(), url, opacity, tokens, cornerRadius);
                case VIDEO -> paintVideo(gfx, font, bounds, attachment.displayName(), url, opacity, tokens, cornerRadius);
            }
        }
        UiPrimitives.strokeRounded(
                gfx,
                bounds,
                cornerRadius,
                1,
                UiPrimitives.withOpacity(tokens.cardBorder(), opacity));
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
        float opacity = messageOpacity * metrics.textOpacity();
        for (InlineEmojiSlot slot : node.inlineEmojiSlots()) {
            InlineEmojiLayout.Placement placement = InlineEmojiLayout.place(
                    font,
                    node.text(),
                    slot.charIndex(),
                    bounds.left(),
                    textY);
            paintInlineEmoji(
                    gfx,
                    slot.iconUrl(),
                    placement.x(),
                    placement.y(),
                    placement.size(),
                    opacity,
                    tokens);
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
        boolean compact = ChatClientConfigRuntime.uiPreferences().compactMediaCards();
        RichChatMediaLayout.AudioGeometry geometry = RichChatMediaLayout.audio(bounds, compact);
        String name = RichChatMediaLayout.displayName(resourceName, url);
        String visibleName = font.plainSubstrByWidth(name, Math.max(1, geometry.title().width()));

        if (entry.getState() == AudioEntry.State.LOADING) {
            String label = entry.getLoadPhase() == AudioEntry.LoadPhase.DECODE
                    ? I18n.get("chatupgrade.hud.audio.processing")
                    : I18n.get("chatupgrade.hud.audio.downloading");
            gfx.text(
                    font,
                    visibleName,
                    geometry.title().left(),
                    geometry.title().top(),
                    UiPrimitives.withOpacity(tokens.text(), opacity),
                    false);
            gfx.text(
                    font,
                    label,
                    geometry.progress().left(),
                    geometry.progress().top() - 2,
                    UiPrimitives.withOpacity(tokens.muted(), opacity),
                    false);
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
        boolean loop = AudioPlayerService.isLoopEnabled(url);
        long pos = Math.max(0L, AudioPlayerService.positionMs(url));
        long total = AudioPlayerService.durationMs(url);
        if (total <= 0L) {
            total = entry.getDurationMs();
        }
        total = Math.max(0L, total);

        gfx.text(
                font,
                visibleName,
                geometry.title().left(),
                geometry.title().top(),
                UiPrimitives.withOpacity(tokens.text(), opacity),
                false);
        paintButton(gfx, geometry.play(), playing ? UiTextureAtlas.Icon.PAUSE : UiTextureAtlas.Icon.PLAY,
                opacity, playing, tokens, cornerRadius);
        if (compact) {
            UiTextureAtlas.drawIcon(
                    gfx,
                    UiTextureAtlas.Icon.MORE,
                    geometry.popout(),
                    UiPrimitives.withOpacity(tokens.muted(), opacity));
        } else {
            paintButton(gfx, geometry.loop(), UiTextureAtlas.Icon.LOOP, opacity, loop, tokens, cornerRadius);
            paintButton(gfx, geometry.open(), UiTextureAtlas.Icon.OPEN, opacity, false, tokens, cornerRadius);
            paintButton(gfx, geometry.popout(), UiTextureAtlas.Icon.POPOUT, opacity, false, tokens, cornerRadius);
        }

        paintProgress(gfx, geometry.progress(), pos, total, opacity, tokens);
        if (compact) {
            String durationLabel = font.plainSubstrByWidth(
                    ChatUpgradeFormatters.formatMs(total),
                    Math.max(1, geometry.durationTime().width()));
            gfx.text(
                    font,
                    durationLabel,
                    Math.max(
                            geometry.durationTime().left(),
                            geometry.durationTime().right() - font.width(durationLabel)),
                    geometry.durationTime().top(),
                    UiPrimitives.withOpacity(tokens.muted(), opacity),
                    false);
        } else {
            gfx.text(
                    font,
                    ChatUpgradeFormatters.formatMs(pos) + " / " + ChatUpgradeFormatters.formatMs(total),
                    geometry.currentTime().left(),
                    geometry.currentTime().top(),
                    UiPrimitives.withOpacity(tokens.muted(), opacity),
                    false);
        }
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
        boolean compact = ChatClientConfigRuntime.uiPreferences().compactMediaCards();
        boolean hovered = RichChatMediaHoverState.contains(bounds);
        String name = RichChatMediaLayout.displayName(resourceName, url);
        long pos = Math.max(0L, VideoPlayerService.positionMs(url));
        long total = VideoPlayerService.durationMs(url);
        if (total <= 0L) {
            total = entry.getDurationMs();
        }
        total = Math.max(0L, total);
        RichChatMediaLayout.VideoGeometry geometry = RichChatMediaLayout.video(
                bounds,
                font,
                pos,
                total,
                entry.getRawWidth(),
                entry.getRawHeight(),
                compact);

        gfx.fill(
                geometry.preview().left(),
                geometry.preview().top(),
                geometry.preview().right(),
                geometry.preview().bottom(),
                UiPrimitives.withOpacity(tokens.mediaBackground(), opacity));
        if (entry.getState() == VideoEntry.State.LOADING) {
            String label = entry.getLoadPhase() == VideoEntry.LoadPhase.DECODE
                    ? I18n.get("chatupgrade.hud.video.processing")
                    : I18n.get("chatupgrade.hud.video.downloading");
            String visibleName = font.plainSubstrByWidth(name, Math.max(1, geometry.title().width()));
            gfx.text(
                    font,
                    visibleName,
                    geometry.title().left(),
                    geometry.title().top(),
                    UiPrimitives.withOpacity(tokens.text(), opacity),
                    false);
            gfx.centeredText(
                    font,
                    label,
                    geometry.preview().left() + geometry.preview().width() / 2,
                    geometry.preview().top() + geometry.preview().height() / 2 - font.lineHeight / 2,
                    UiPrimitives.withOpacity(tokens.muted(), opacity));
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
        if (textureId != null && geometry.frame().width() > 0 && geometry.frame().height() > 0) {
            gfx.blit(
                    RenderPipelines.GUI_TEXTURED,
                    textureId,
                    geometry.frame().left(), geometry.frame().top(),
                    0.0f, 0.0f,
                    geometry.frame().width(), geometry.frame().height(),
                    entry.getRawWidth() > 0 ? entry.getRawWidth() : geometry.frame().width(),
                    entry.getRawHeight() > 0 ? entry.getRawHeight() : geometry.frame().height(),
                    entry.getRawWidth() > 0 ? entry.getRawWidth() : geometry.frame().width(),
                    entry.getRawHeight() > 0 ? entry.getRawHeight() : geometry.frame().height(),
                    ARGB.white(opacity));
        }

        boolean playing = VideoPlayerService.isPlaying(url);
        boolean showTitle = !compact || !playing || hovered;
        if (showTitle) {
            RichChatBounds titleScrim = new RichChatBounds(
                    bounds.left(),
                    bounds.top(),
                    bounds.right(),
                    Math.min(geometry.preview().bottom(), bounds.top() + 24));
            gfx.fill(
                    titleScrim.left(),
                    titleScrim.top(),
                    titleScrim.right(),
                    titleScrim.bottom(),
                    UiPrimitives.withOpacity(tokens.scrim(), opacity));
            String visibleName = font.plainSubstrByWidth(name, Math.max(1, geometry.title().width()));
            gfx.text(
                    font,
                    visibleName,
                    geometry.title().left(),
                    geometry.title().top(),
                    UiPrimitives.withOpacity(tokens.text(), opacity),
                    false);
        }
        if (!compact || hovered) {
            paintButton(gfx, geometry.open(), UiTextureAtlas.Icon.OPEN, opacity, false, tokens, cornerRadius);
        }

        if (!compact || !playing || hovered) {
            paintButton(gfx, geometry.play(), playing ? UiTextureAtlas.Icon.PAUSE : UiTextureAtlas.Icon.PLAY,
                    opacity, playing, tokens, cornerRadius);
        }
        if (compact && !hovered) {
            return;
        }
        if (compact) {
            RichChatBounds controlsScrim = new RichChatBounds(
                    bounds.left(),
                    Math.max(bounds.top(), bounds.bottom() - 18),
                    bounds.right(),
                    bounds.bottom());
            gfx.fill(
                    controlsScrim.left(),
                    controlsScrim.top(),
                    controlsScrim.right(),
                    controlsScrim.bottom(),
                    UiPrimitives.withOpacity(tokens.scrim(), opacity));
        }
        int timeColor = compact ? tokens.text() : tokens.muted();
        gfx.text(
                font,
                ChatUpgradeFormatters.formatMs(pos),
                geometry.leftTime().left(),
                geometry.leftTime().top(),
                UiPrimitives.withOpacity(timeColor, opacity),
                false);
        gfx.text(
                font,
                ChatUpgradeFormatters.formatMs(total),
                geometry.rightTime().left(),
                geometry.rightTime().top(),
                UiPrimitives.withOpacity(timeColor, opacity),
                false);
        paintProgress(gfx, geometry.progress(), pos, total, opacity, tokens);
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
            RichChatBounds bounds,
            UiTextureAtlas.Icon icon,
            float opacity,
            boolean active,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        int background = active ? tokens.controlActiveBackground() : tokens.controlBackground();
        UiPrimitives.fillRounded(
                gfx,
                bounds,
                Math.min(Math.max(2, cornerRadius), Math.min(bounds.width(), bounds.height()) / 2),
                UiPrimitives.withOpacity(background, opacity));
        int size = Math.max(1, Math.min(12, Math.min(bounds.width(), bounds.height()) - 4));
        UiTextureAtlas.drawIcon(
                gfx,
                icon,
                RichChatBounds.ofSize(
                        bounds.left() + Math.max(0, (bounds.width() - size) / 2),
                        bounds.top() + Math.max(0, (bounds.height() - size) / 2),
                        size,
                        size),
                UiPrimitives.withOpacity(tokens.text(), opacity));
    }

    private static void paintProgress(
            GuiGraphicsExtractor gfx,
            RichChatBounds bounds,
            long positionMs,
            long durationMs,
            float opacity,
            ChatAppearanceSnapshot.Media tokens) {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        UiPrimitives.fillRounded(
                gfx,
                bounds,
                Math.max(1, bounds.height() / 2),
                UiPrimitives.withOpacity(tokens.progressTrack(), opacity));
        float ratio = durationMs <= 0L
                ? 0.0F
                : Math.clamp((float) positionMs / durationMs, 0.0F, 1.0F);
        int fillX = bounds.left() + Math.round(bounds.width() * ratio);
        if (fillX > bounds.left()) {
            UiPrimitives.fillRounded(
                    gfx,
                    new RichChatBounds(bounds.left(), bounds.top(), fillX, bounds.bottom()),
                    Math.max(1, bounds.height() / 2),
                    UiPrimitives.withOpacity(tokens.progressFill(), opacity));
        }
    }

    private static com.chat.upgrade.client.media.model.InlineResourceType attachmentTypeAudio() {
        return com.chat.upgrade.client.media.model.InlineResourceType.AUDIO;
    }

    private static com.chat.upgrade.client.media.model.InlineResourceType attachmentTypeVideo() {
        return com.chat.upgrade.client.media.model.InlineResourceType.VIDEO;
    }
}