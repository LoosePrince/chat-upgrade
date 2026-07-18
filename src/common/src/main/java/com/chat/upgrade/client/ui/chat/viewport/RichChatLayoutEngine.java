package com.chat.upgrade.client.ui.chat.viewport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.ui.chat.InlineEmojiCoordinator;
import com.chat.upgrade.client.ui.chat.InlineEmojiSlot;
import com.chat.upgrade.client.ui.chat.state.ChatReplySummary;
import com.chat.upgrade.client.ui.chat.state.ChatTimelineProjection;
import com.chat.upgrade.client.ui.chat.state.ChatTimelineProjector;
import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageStatus;
import com.chat.upgrade.client.ui.chat.state.RichChatStateStore;
import com.chat.upgrade.client.ui.chat.surface.ChatLayoutPolicy;
import com.chat.upgrade.client.ui.chat.surface.ChatTheme;
import com.chat.upgrade.client.ui.chat.surface.ChatThemes;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public final class RichChatLayoutEngine {
    private static final Component DELETED_MARKER = Component.translatable("chatupgrade.message.deleted")
            .withStyle(new ChatFormatting[] { ChatFormatting.GRAY, ChatFormatting.ITALIC });

    public RichChatLayout layoutFromStore(Font font, RichChatViewportMetrics metrics, ChatTheme theme) {
        return layout(font, metrics, RichChatStateStore.snapshotNewestFirst(), RichChatStateStore.version(), theme);
    }

    public RichChatLayout layoutFromStore(Font font, RichChatViewportMetrics metrics) {
        return layoutFromStore(font, metrics, ChatThemes.compatibility());
    }

    public RichChatLayout layout(
            Font font,
            RichChatViewportMetrics metrics,
            List<RichChatMessage> newestFirst,
            long storeVersion,
            ChatTheme theme) {
        if (font == null) {
            throw new IllegalArgumentException("font must not be null");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("metrics must not be null");
        }
        ChatTheme activeTheme = theme == null ? ChatThemes.compatibility() : theme;
        ChatLayoutPolicy policy = activeTheme.layout();
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
                cursorY += projection.groupPosition().startsGroup() ? policy.groupGap() : policy.messageGap();
            }
            RichChatMessageLayout layout = layoutMessage(font, metrics, projection, cursorY, policy);
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

    public RichChatLayout layout(
            Font font,
            RichChatViewportMetrics metrics,
            List<RichChatMessage> newestFirst,
            long storeVersion) {
        return layout(font, metrics, newestFirst, storeVersion, ChatThemes.compatibility());
    }

    private RichChatMessageLayout layoutMessage(
            Font font,
            RichChatViewportMetrics metrics,
            ChatTimelineProjection timeline,
            int top,
            ChatLayoutPolicy policy) {
        RichChatMessage message = timeline.message();
        List<RichChatRenderNode> nodes = new ArrayList<>();
        List<RichChatHitBox> hitBoxes = new ArrayList<>();
        Component textComponent = textComponent(message);
        boolean hasText = !textComponent.getString().isBlank() || message.attachments().isEmpty();
        boolean playerMessage = timeline.kind().playerAuthored();
        boolean showIdentity = policy.showIdentity(timeline);
        int identityGutter = playerMessage ? policy.identityGutter() : 0;
        int contentAreaWidth = Math.max(1, metrics.textWidth() - identityGutter);
        int themedContentWidth = Math.max(1, contentAreaWidth * policy.contentWidthPercent() / 100);
        int contentLeft = metrics.textLeft() + identityGutter + policy.bubblePaddingX();
        int contentWidth = Math.max(1, themedContentWidth - policy.bubblePaddingX() * 2);
        RichChatBounds identityBounds = showIdentity
                ? RichChatBounds.ofSize(metrics.textLeft(), top, policy.avatarSize(), policy.avatarSize())
                : null;
        int cursorY = top;
        if (showIdentity) {
            cursorY += metrics.entryHeight();
        }
        int order = 0;
        int visualRight = contentLeft;
        ArrayDeque<InlineEmojiSlot> emojiQueue = new ArrayDeque<>(message.inlineEmojiSlots());

        if (message.replyTo() != null) {
            Component replyComponent = replyComponent(message.replyTo());
            for (FormattedCharSequence line : font.split(replyComponent, contentWidth)) {
                RichChatBounds lineBounds = RichChatBounds.ofSize(
                        contentLeft,
                        cursorY,
                        contentWidth,
                        metrics.entryHeight());
                nodes.add(RichChatRenderNode.reply(
                        message.messageId(),
                        lineBounds,
                        order,
                        line,
                        replyComponent));
                visualRight = Math.max(visualRight, contentLeft + font.width(line));
                cursorY += metrics.entryHeight();
                order++;
            }
        }

        if (hasText) {
            if (message.replyTo() != null) {
                cursorY += policy.nodeGap();
            }
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
                RichChatRenderNode node;
                if (message.status() == RichChatMessageStatus.DELETED) {
                    node = RichChatRenderNode.deleted(
                            message.messageId(),
                            lineBounds,
                            order,
                            line,
                            textComponent);
                } else if (timeline.kind().systemLike()) {
                    node = RichChatRenderNode.system(
                            message.messageId(),
                            lineBounds,
                            order,
                            line,
                            textComponent,
                            lineEmojiSlots);
                } else {
                    node = RichChatRenderNode.text(
                            message.messageId(),
                            lineBounds,
                            order,
                            line,
                            textComponent,
                            lineEmojiSlots);
                }
                nodes.add(node);
                hitBoxes.addAll(styledTextHitBoxes(font, message.messageId(), lineBounds, line));
                hitBoxes.addAll(emojiHitBoxes(font, metrics, message.messageId(), lineBounds, line, lineEmojiSlots));
                visualRight = Math.max(visualRight, contentLeft + font.width(line));
                cursorY += metrics.entryHeight();
                order++;
            }
        }

        for (RichAttachment attachment : message.attachments()) {
            if (!nodes.isEmpty()) {
                cursorY += policy.nodeGap();
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
            visualRight = Math.max(visualRight, attachmentBounds.right());
            order++;
        }

        int messageHeight = Math.max(0, cursorY - top);
        RichChatBounds messageBounds = RichChatBounds.ofSize(
                metrics.backgroundLeft(),
                top,
                metrics.backgroundRight() - metrics.backgroundLeft(),
                messageHeight);
        if (showIdentity) {
            visualRight = Math.max(visualRight, contentLeft + font.width(timeline.author().visibleName()));
        }
        int visualLeft = playerMessage ? metrics.textLeft() + identityGutter : metrics.textLeft();
        int maxVisualRight = metrics.textLeft() + metrics.textWidth();
        int paddedVisualRight = Math.min(
                maxVisualRight,
                Math.max(visualLeft + 1, visualRight + policy.bubblePaddingX()));
        RichChatBounds visualBounds = RichChatBounds.ofSize(
                visualLeft,
                top,
                paddedVisualRight - visualLeft,
                messageHeight);
        return new RichChatMessageLayout(
                message,
                timeline,
                messageBounds,
                visualBounds,
                identityBounds,
                nodes,
                hitBoxes);
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

    private static Component replyComponent(ChatReplySummary reply) {
        String author = reply.author().visibleName();
        String excerpt = reply.excerpt().isBlank()
                ? Component.translatable("chatupgrade.reply.target_unavailable").getString()
                : reply.excerpt();
        String separator = author.isBlank() ? "" : author + ": ";
        return Component.literal("↩ " + separator + excerpt)
                .withStyle(new ChatFormatting[] { ChatFormatting.GRAY, ChatFormatting.ITALIC });
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
            case ATTACHMENT_PENDING, DELETED, REPLY, TEXT, SYSTEM -> RichChatHitBoxKind.ATTACHMENT;
        };
    }

    private static String attachmentActionKey(RichAttachment attachment) {
        if (!attachment.hasRenderableUrl()) {
            return "pending";
        }
        return attachment.type().toWire() + ":" + attachment.requireRenderableUrl();
    }
}