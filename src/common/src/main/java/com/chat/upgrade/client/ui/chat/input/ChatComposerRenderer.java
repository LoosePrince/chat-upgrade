package com.chat.upgrade.client.ui.chat.input;

import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.ui.chat.state.ChatReplySummary;
import com.chat.upgrade.client.ui.chat.surface.ChatTheme;
import com.chat.upgrade.client.ui.chat.surface.ChatThemeTokens;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ChatComposerRenderer {
    private ChatComposerRenderer() {
    }

    public static void paintReplyPreview(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatTheme theme,
            RichChatBounds composerBounds,
            ChatReplySummary target) {
        if (graphics == null || font == null || theme == null || composerBounds == null || target == null) {
            return;
        }
        RichChatBounds preview = replyPreviewBounds(composerBounds);
        ChatThemeTokens.Message tokens = theme.tokens().message();
        graphics.fill(preview.left(), preview.top(), preview.right(), preview.bottom(), tokens.replyBackground());
        graphics.outline(preview.left(), preview.top(), preview.width(), preview.height(), tokens.replyBorder());
        String label = Component.translatable(
                "chatupgrade.reply.composer_preview",
                target.author().visibleName(),
                target.excerpt()).getString();
        int closeWidth = font.width("×") + 8;
        String visibleLabel = font.plainSubstrByWidth(
                label,
                Math.max(1, preview.width() - closeWidth - 8));
        graphics.text(font, visibleLabel, preview.left() + 4, preview.top() + 4, tokens.replyText(), false);
        graphics.text(font, "×", preview.right() - closeWidth + 3, preview.top() + 4, tokens.replyText(), false);
    }

    public static boolean isReplyCancelClick(
            Font font,
            RichChatBounds composerBounds,
            double mouseX,
            double mouseY) {
        if (font == null || composerBounds == null) {
            return false;
        }
        RichChatBounds preview = replyPreviewBounds(composerBounds);
        int closeWidth = font.width("×") + 8;
        RichChatBounds close = RichChatBounds.ofSize(
                preview.right() - closeWidth,
                preview.top(),
                closeWidth,
                preview.height());
        return close.contains((int) Math.round(mouseX), (int) Math.round(mouseY));
    }

    public static void paintAttachmentChip(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatTheme theme,
            AttachmentDraft draft,
            int left,
            int right,
            int top) {
        if (graphics == null || font == null || theme == null || draft == null || right <= left + 12) {
            return;
        }
        ChatThemeTokens tokens = theme.tokens();
        int background = draft.status() == AttachmentDraft.Status.FAILED
                ? tokens.message().errorBackground()
                : tokens.media().pendingBackground();
        int outline = switch (draft.status()) {
            case READY -> tokens.message().playerBorder();
            case UPLOADING -> tokens.message().announcementBorder();
            case UPLOADED -> tokens.message().replyBorder();
            case FAILED -> tokens.message().errorBorder();
        };
        graphics.fill(left, top, right, top + 16, background);
        graphics.outline(left, top, right - left, 16, outline);
        String label = chipLabel(draft);
        int maxTextWidth = Math.max(12, right - left - 8);
        if (font.width(label) > maxTextWidth) {
            label = font.plainSubstrByWidth(label, maxTextWidth - font.width("…")) + "…";
        }
        int textColor = draft.status() == AttachmentDraft.Status.FAILED
                ? tokens.media().failureText()
                : tokens.media().text();
        graphics.text(font, label, left + 4, top + 4, textColor, false);
    }

    public static Component typeName(InlineResourceType type) {
        return switch (type) {
            case IMAGE -> Component.translatable("chatupgrade.type.image");
            case AUDIO -> Component.translatable("chatupgrade.type.audio");
            case VIDEO -> Component.translatable("chatupgrade.type.video");
        };
    }

    private static RichChatBounds replyPreviewBounds(RichChatBounds composerBounds) {
        return RichChatBounds.ofSize(
                composerBounds.left() + 6,
                composerBounds.top() + 3,
                Math.max(1, composerBounds.width() - 12),
                16);
    }

    private static String chipLabel(AttachmentDraft draft) {
        String status = switch (draft.status()) {
            case READY -> Component.translatable("chatupgrade.input.status.ready").getString();
            case UPLOADING -> Component.translatable("chatupgrade.input.status.uploading").getString();
            case UPLOADED -> Component.translatable("chatupgrade.input.status.uploaded").getString();
            case FAILED -> draft.failureMessage()
                    .orElseGet(() -> Component.translatable("chatupgrade.input.status.failed").getString());
        };
        return Component.translatable(
                "chatupgrade.input.chip",
                typeName(draft.type()),
                draft.displayName(),
                status).getString();
    }
}