package com.chat.upgrade.client.ui.chat.viewport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.ui.chat.InlineEmojiCoordinator;
import com.chat.upgrade.client.ui.chat.InlineEmojiSlot;
import com.chat.upgrade.client.ui.chat.state.ChatTimelineProjection;
import com.chat.upgrade.client.ui.chat.state.ChatTimelineProjector;
import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageStatus;
import com.chat.upgrade.client.ui.chat.state.RichChatStateStore;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public final class RichChatLayoutEngine {
    private static final int NODE_GAP = 2;
    private static final int GROUP_GAP = 5;
    private static final int MESSAGE_GAP = 1;
    private static final int IDENTITY_GUTTER = 24;
    private static final int AVATAR_SIZE = 18;
    private static final Component DELETED_MARKER = Component.translatable("chatupgrade.message.deleted")
            .withStyle(new ChatFormatting[] { ChatFormatting.GRAY, ChatFormatting.ITALIC });

    public RichChatLayout layoutFromStore(Font font, RichChatViewportMetrics metrics) {
        return layout(font, metrics, RichChatStateStore.snapshotNewestFirst(), RichChatStateStore.version());
    }

    public RichChatLayout layout(
            Font font,
            RichChatViewportMetrics metrics,
            List<RichChatMessage> newestFirst,
            long storeVersion) {
        if (font == null) {
            throw new IllegalArgumentException("font must not be null");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("metrics must not be null");
        }
        if (newestFirst == null || newestFirst.isEmpty()) {
            return RichChatLayout.empty(storeVersion, metrics.textWidth());
        }

        List<RichChatMessage> oldestFirst = new ArrayList<>(newestFirst);
        Collections.reverse(oldestFirst);

        List<ChatTimelineProjection> timeline = ChatTimelineProjector.projectOldestFirst(oldestFirst);
        List<RichChatMessageLayout> messageLayouts = new ArrayList<>();
        List<RichChatRenderNode> allNodes = new ArrayList<>();
        List<RichChatHitBox> allHitBoxes = new ArrayList<>();
        int cursorY = 0;
        for (ChatTimelineProjection projection : timeline) {
            if (cursorY > 0) {
                cursorY += projection.groupPosition().startsGroup() ? GROUP_GAP : MESSAGE_GAP;
            }
            RichChatMessageLayout layout = layoutMessage(font, metrics, projection, cursorY);
            cursorY = layout.bounds().bottom();
            if (!layout.nodes().isEmpty()) {
                messageLayouts.add(layout);
                allNodes.addAll(layout.nodes());
                allHitBoxes.addAll(layout.hitBoxes());
            }
        }

        return new RichChatLayout(
                messageLayouts,
                allNodes,
                allHitBoxes,
                cursorY,
                storeVersion,
                metrics.textWidth());
    }

    private RichChatMessageLayout layoutMessage(
            Font font,
            RichChatViewportMetrics metrics,
            ChatTimelineProjection timeline,
            int top) {
        RichChatMessage message = timeline.message();
        List<RichChatRenderNode> nodes = new ArrayList<>();
        List<RichChatHitBox> hitBoxes = new ArrayList<>();
        Component textComponent = textComponent(message);
        boolean hasText = !textComponent.getString().isBlank() || message.attachments().isEmpty();
        boolean playerMessage = timeline.kind().playerAuthored();
        int contentLeft = metrics.textLeft() + (playerMessage ? IDENTITY_GUTTER : 0);
        int contentWidth = Math.max(1, metrics.textWidth() - (playerMessage ? IDENTITY_GUTTER : 0));
        RichChatBounds identityBounds = timeline.showIdentity()
                ? RichChatBounds.ofSize(metrics.textLeft(), top, AVATAR_SIZE, AVATAR_SIZE)
                : null;
        int cursorY = top;
        if (timeline.showIdentity()) {
            cursorY += metrics.entryHeight();
        }
        int order = 0;
        ArrayDeque<InlineEmojiSlot> emojiQueue = new ArrayDeque<>(message.inlineEmojiSlots());

        if (hasText) {
            List<FormattedCharSequence> lines = font.split(textComponent, contentWidth);
            if (lines.isEmpty()) {
                lines = List.of(FormattedCharSequence.EMPTY);
            }
            for (FormattedCharSequence line : lines) {
                RichChatBounds lineBounds = RichChatBounds.ofSize(
                        contentLeft,
                        cursorY,
                        contentWidth,
                        metrics.entryHeight());
                List<InlineEmojiSlot> lineEmojiSlots = InlineEmojiCoordinator.consumeForLine(line, emojiQueue);
                RichChatRenderNode node = timeline.kind().systemLike()
                        ? RichChatRenderNode.system(message.messageId(), lineBounds, order, line, textComponent, lineEmojiSlots)
                        : RichChatRenderNode.text(message.messageId(), lineBounds, order, line, textComponent, lineEmojiSlots);
                nodes.add(node);
                hitBoxes.addAll(styledTextHitBoxes(font, message.messageId(), lineBounds, line));
                hitBoxes.addAll(emojiHitBoxes(font, metrics, message.messageId(), lineBounds, line, lineEmojiSlots));
                cursorY += metrics.entryHeight();
                order++;
            }
        }

        for (RichAttachment attachment : message.attachments()) {
            if (!nodes.isEmpty()) {
                cursorY += NODE_GAP;
            }
            RichChatMediaBox attachmentLayout = RichChatMediaSizing.measure(
                    contentWidth,
                    metrics.entryHeight(),
                    attachment);
            RichChatBounds attachmentBounds = RichChatBounds.ofSize(
                    contentLeft,
                    cursorY,
                    attachmentLayout.width(),
                    attachmentLayout.height());
            nodes.add(RichChatRenderNode.attachment(
                    attachmentLayout.kind(),
                    message.messageId(),
                    attachmentBounds,
                    order,
                    attachment));
            hitBoxes.add(new RichChatHitBox(
                    hitBoxKind(attachmentLayout.kind()),
                    message.messageId(),
                    attachmentBounds,
                    attachment,
                    null,
                    attachmentActionKey(attachment)));
            cursorY += attachmentLayout.height();
            order++;
        }

        RichChatBounds messageBounds = RichChatBounds.ofSize(
                metrics.backgroundLeft(),
                top,
                metrics.backgroundRight() - metrics.backgroundLeft(),
                Math.max(0, cursorY - top));
        return new RichChatMessageLayout(message, timeline, messageBounds, identityBounds, nodes, hitBoxes);
    }

    private static List<RichChatHitBox> styledTextHitBoxes(
            Font font,
            String messageId,
            RichChatBounds lineBounds,
            FormattedCharSequence line) {
        List<StyledTextRun> runs = styledTextRuns(line);
        if (runs.isEmpty()) {
            return List.of();
        }
        List<RichChatHitBox> hitBoxes = new ArrayList<>();
        int cursorX = lineBounds.left();
        int runIndex = 0;
        for (StyledTextRun run : runs) {
            int width = font.width(run.text());
            if (width > 0 && hasInteractiveStyle(run.style())) {
                hitBoxes.add(new RichChatHitBox(
                        RichChatHitBoxKind.TEXT,
                        messageId,
                        RichChatBounds.ofSize(cursorX, lineBounds.top(), width, lineBounds.height()),
                        null,
                        run.style(),
                        "text:" + messageId + ":" + runIndex));
            }
            cursorX += width;
            runIndex++;
        }
        return hitBoxes;
    }

    private static List<StyledTextRun> styledTextRuns(FormattedCharSequence line) {
        if (line == null) {
            return List.of();
        }
        List<StyledTextRun> runs = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        Style[] currentStyle = new Style[1];
        line.accept((index, style, codePoint) -> {
            Style safeStyle = style == null ? Style.EMPTY : style;
            if (currentStyle[0] != null && !currentStyle[0].equals(safeStyle)) {
                runs.add(new StyledTextRun(currentStyle[0], currentText.toString()));
                currentText.setLength(0);
            }
            currentStyle[0] = safeStyle;
            currentText.appendCodePoint(codePoint);
            return true;
        });
        if (currentStyle[0] != null && !currentText.isEmpty()) {
            runs.add(new StyledTextRun(currentStyle[0], currentText.toString()));
        }
        return runs;
    }

    private static boolean hasInteractiveStyle(Style style) {
        return style != null && (style.getClickEvent() != null || style.getHoverEvent() != null);
    }

    private record StyledTextRun(Style style, String text) {
    }

    private static List<RichChatHitBox> emojiHitBoxes(
            Font font,
            RichChatViewportMetrics metrics,
            String messageId,
            RichChatBounds lineBounds,
            FormattedCharSequence line,
            List<InlineEmojiSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        String plain = extractPlain(line);
        int textY = lineBounds.bottom() - metrics.entryBottomToMessageY();
        int size = Math.max(1, metrics.entryHeight() - 2);
        List<RichChatHitBox> hitBoxes = new ArrayList<>();
        for (InlineEmojiSlot slot : slots) {
            int charIndex = Math.clamp(slot.charIndex(), 0, plain.length());
            int x = lineBounds.left() + font.width(plain.substring(0, charIndex)) + 1;
            int y = textY + 1;
            RichAttachment attachment = RichAttachment.structured(
                    InlineResourceType.IMAGE,
                    slot.token(),
                    slot.iconUrl(),
                    null,
                    null);
            hitBoxes.add(new RichChatHitBox(
                    RichChatHitBoxKind.IMAGE,
                    messageId,
                    RichChatBounds.ofSize(x, y, size, size),
                    attachment,
                    null,
                    "emoji:" + slot.token() + ":" + slot.iconUrl()));
        }
        return hitBoxes;
    }

    private static String extractPlain(FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    private static Component textComponent(RichChatMessage message) {
        if (message.status() == RichChatMessageStatus.DELETED) {
            return DELETED_MARKER;
        }
        return message.component();
    }

    private static RichChatHitBoxKind hitBoxKind(RichChatRenderNodeKind kind) {
        return switch (kind) {
            case IMAGE -> RichChatHitBoxKind.IMAGE;
            case AUDIO -> RichChatHitBoxKind.AUDIO;
            case VIDEO -> RichChatHitBoxKind.VIDEO;
            case ATTACHMENT_FAILED -> RichChatHitBoxKind.RETRY;
            case ATTACHMENT_PENDING, TEXT, SYSTEM -> RichChatHitBoxKind.ATTACHMENT;
        };
    }

    private static String attachmentActionKey(RichAttachment attachment) {
        if (!attachment.hasRenderableUrl()) {
            return "pending";
        }
        return attachment.type().toWire() + ":" + attachment.requireRenderableUrl();
    }
}