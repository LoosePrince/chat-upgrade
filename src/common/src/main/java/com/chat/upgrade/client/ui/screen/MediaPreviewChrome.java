package com.chat.upgrade.client.ui.screen;

import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.render.UiPrimitives;
import com.chat.upgrade.client.ui.render.UiTextureAtlas;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared preview-screen paint primitives backed by the chat media appearance tokens. */
final class MediaPreviewChrome {
    private MediaPreviewChrome() {
    }

    static void paintFrame(
            GuiGraphicsExtractor graphics,
            Font font,
            MediaPreviewLayout.Frame frame,
            String title,
            String metadata,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        UiPrimitives.fillRounded(graphics, frame.panel(), cornerRadius, tokens.cardBackground());
        UiPrimitives.strokeRounded(graphics, frame.panel(), cornerRadius, 1, tokens.cardBorder());
        UiPrimitives.fillRounded(graphics, frame.media(), cornerRadius, tokens.mediaBackground());
        graphics.fill(
                frame.header().left() + 8,
                frame.header().bottom() - 1,
                frame.header().right() - 8,
                frame.header().bottom(),
                tokens.cardBorder());
        graphics.fill(
                frame.footer().left() + 8,
                frame.footer().top(),
                frame.footer().right() - 8,
                frame.footer().top() + 1,
                tokens.cardBorder());

        String visibleTitle = font.plainSubstrByWidth(title, Math.max(1, frame.title().width()));
        String visibleMetadata = font.plainSubstrByWidth(metadata, Math.max(1, frame.metadata().width()));
        graphics.text(font, visibleTitle, frame.title().left(), frame.title().top(), tokens.text(), false);
        graphics.text(font, visibleMetadata, frame.metadata().left(), frame.metadata().top(), tokens.muted(), false);
        paintIconButton(graphics, frame.close(), UiTextureAtlas.Icon.CLOSE, tokens, cornerRadius);
    }

    static void paintCenteredState(
            GuiGraphicsExtractor graphics,
            Font font,
            RichChatBounds bounds,
            String label,
            int color) {
        graphics.centeredText(
                font,
                label,
                bounds.left() + bounds.width() / 2,
                bounds.top() + bounds.height() / 2 - font.lineHeight / 2,
                color);
    }

    static void paintActionButton(
            GuiGraphicsExtractor graphics,
            Font font,
            RichChatBounds bounds,
            String text,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        UiPrimitives.fillRounded(
                graphics,
                bounds,
                Math.min(cornerRadius, bounds.height() / 2),
                tokens.controlBackground());
        graphics.centeredText(
                font,
                text,
                bounds.left() + bounds.width() / 2,
                bounds.top() + 8,
                tokens.text());
    }

    static void paintIconButton(
            GuiGraphicsExtractor graphics,
            RichChatBounds bounds,
            UiTextureAtlas.Icon icon,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        UiPrimitives.fillRounded(
                graphics,
                bounds,
                Math.min(cornerRadius, bounds.height() / 2),
                tokens.controlBackground());
        int size = Math.max(1, Math.min(12, Math.min(bounds.width(), bounds.height()) - 6));
        UiTextureAtlas.drawIcon(
                graphics,
                icon,
                RichChatBounds.ofSize(
                        bounds.left() + (bounds.width() - size) / 2,
                        bounds.top() + (bounds.height() - size) / 2,
                        size,
                        size),
                tokens.text());
    }

    static void paintProgress(
            GuiGraphicsExtractor graphics,
            RichChatBounds bounds,
            long positionMs,
            long durationMs,
            ChatAppearanceSnapshot.Media tokens) {
        UiPrimitives.fillRounded(graphics, bounds, Math.max(1, bounds.height() / 2), tokens.progressTrack());
        float ratio = durationMs <= 0L
                ? 0.0F
                : Math.clamp((float) positionMs / durationMs, 0.0F, 1.0F);
        int right = bounds.left() + Math.round(bounds.width() * ratio);
        if (right > bounds.left()) {
            UiPrimitives.fillRounded(
                    graphics,
                    new RichChatBounds(bounds.left(), bounds.top(), right, bounds.bottom()),
                    Math.max(1, bounds.height() / 2),
                    tokens.progressFill());
        }
    }
}