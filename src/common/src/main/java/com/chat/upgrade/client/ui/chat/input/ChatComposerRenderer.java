package com.chat.upgrade.client.ui.chat.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.ui.chat.state.ChatReplySummary;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.render.UiPrimitives;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ChatComposerRenderer {
    private static final int CHIP_HEIGHT = 16;
    private static final int CHIP_GAP = 3;
    private static final int CHIP_MIN_WIDTH = 42;
    private static final int CHIP_MAX_WIDTH = 112;
    private static final int CHIP_REMOVE_WIDTH = 14;

    public record AttachmentChip(AttachmentDraft draft, RichChatBounds bounds, RichChatBounds removeBounds) {
    }

    private ChatComposerRenderer() {
    }

    public static void paintReplyPreview(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatAppearanceSnapshot appearance,
            RichChatBounds composerBounds,
            ChatReplySummary target) {
        if (graphics == null || font == null || appearance == null || composerBounds == null || target == null) {
            return;
        }
        RichChatBounds preview = replyPreviewBounds(composerBounds);
        ChatAppearanceSnapshot.Message tokens = appearance.message();
        UiPrimitives.paintBox(
                graphics,
                preview,
                appearance.cornerRadius(),
                1,
                tokens.replyBackground(),
                tokens.replyBorder());
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

    public static void paintAttachmentChips(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatAppearanceSnapshot appearance,
            List<AttachmentDraft> drafts,
            int left,
            int right,
            int top) {
        if (graphics == null || font == null || appearance == null || drafts == null || drafts.isEmpty()) {
            return;
        }
        for (AttachmentChip chip : attachmentChips(font, drafts, left, right, top)) {
            paintAttachmentChip(graphics, font, appearance, chip);
        }
    }

    public static Optional<AttachmentDraft> attachmentAt(
            Font font,
            List<AttachmentDraft> drafts,
            int left,
            int right,
            int top,
            double mouseX,
            double mouseY) {
        return attachmentAt(font, drafts, left, right, top, mouseX, mouseY, true);
    }

    public static Optional<AttachmentDraft> attachmentChipAt(
            Font font,
            List<AttachmentDraft> drafts,
            int left,
            int right,
            int top,
            double mouseX,
            double mouseY) {
        return attachmentAt(font, drafts, left, right, top, mouseX, mouseY, false);
    }

    private static Optional<AttachmentDraft> attachmentAt(
            Font font,
            List<AttachmentDraft> drafts,
            int left,
            int right,
            int top,
            double mouseX,
            double mouseY,
            boolean removeOnly) {
        if (font == null || drafts == null || drafts.isEmpty()) {
            return Optional.empty();
        }
        int x = (int) Math.round(mouseX);
        int y = (int) Math.round(mouseY);
        return attachmentChips(font, drafts, left, right, top).stream()
                .filter(chip -> (removeOnly ? chip.removeBounds() : chip.bounds()).contains(x, y))
                .map(AttachmentChip::draft)
                .findFirst();
    }

    public static List<AttachmentChip> attachmentChips(
            Font font,
            List<AttachmentDraft> drafts,
            int left,
            int right,
            int top) {
        if (font == null || drafts == null || right <= left) {
            return List.of();
        }
        List<AttachmentChip> chips = new ArrayList<>();
        int cursor = left;
        int gapWidth = CHIP_GAP * Math.max(0, drafts.size() - 1);
        int slotWidth = Math.max(1, (right - left - gapWidth) / Math.max(1, drafts.size()));
        for (AttachmentDraft draft : drafts) {
            String label = chipLabel(draft);
            int desiredWidth = Math.clamp(
                    font.width(label) + CHIP_REMOVE_WIDTH + 9,
                    CHIP_MIN_WIDTH,
                    CHIP_MAX_WIDTH);
            int width = Math.max(1, Math.min(desiredWidth, slotWidth));
            RichChatBounds bounds = RichChatBounds.ofSize(cursor, top, width, CHIP_HEIGHT);
            RichChatBounds remove = RichChatBounds.ofSize(
                    Math.max(bounds.left(), bounds.right() - CHIP_REMOVE_WIDTH),
                    bounds.top(),
                    Math.min(CHIP_REMOVE_WIDTH, bounds.width()),
                    bounds.height());
            chips.add(new AttachmentChip(draft, bounds, remove));
            cursor = bounds.right() + CHIP_GAP;
        }
        return List.copyOf(chips);
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

    private static void paintAttachmentChip(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatAppearanceSnapshot appearance,
            AttachmentChip chip) {
        AttachmentDraft draft = chip.draft();
        RichChatBounds bounds = chip.bounds();
        ChatAppearanceSnapshot tokens = appearance;
        int background = draft.status() == AttachmentDraft.Status.FAILED
                ? tokens.message().errorBackground()
                : tokens.media().pendingBackground();
        int outline = switch (draft.status()) {
            case READY -> tokens.message().playerBorder();
            case UPLOADING -> tokens.message().announcementBorder();
            case UPLOADED -> tokens.message().replyBorder();
            case FAILED -> tokens.message().errorBorder();
        };
        UiPrimitives.paintBox(
                graphics,
                bounds,
                Math.min(appearance.cornerRadius(), 6),
                1,
                background,
                outline);
        int textWidth = Math.max(1, chip.removeBounds().left() - bounds.left() - 6);
        String label = font.plainSubstrByWidth(chipLabel(draft), textWidth);
        graphics.text(font, label, bounds.left() + 4, bounds.top() + 4, chipTextColor(tokens, draft), false);
        if (draft.status() != AttachmentDraft.Status.UPLOADING) {
            graphics.text(font, "×", chip.removeBounds().left() + 3, bounds.top() + 4, chipTextColor(tokens, draft), false);
        }
    }

    private static int chipTextColor(ChatAppearanceSnapshot tokens, AttachmentDraft draft) {
        return draft.status() == AttachmentDraft.Status.FAILED
                ? tokens.media().failureText()
                : tokens.media().text();
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
