package com.chat.upgrade.client.ui.chat.viewport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.ui.chat.InlineEmojiCoordinator;
import com.chat.upgrade.client.ui.chat.InlineEmojiLayout;
import com.chat.upgrade.client.ui.chat.InlineEmojiSlot;
import com.chat.upgrade.client.ui.chat.interaction.ChatHitTarget;
import com.chat.upgrade.client.ui.chat.state.ChatMessageGroupStore;
import com.chat.upgrade.client.ui.chat.state.ChatMessageMetadata;
import com.chat.upgrade.client.ui.chat.state.ChatReplySummary;
import com.chat.upgrade.client.ui.chat.state.ChatTimelineProjection;
import com.chat.upgrade.client.ui.chat.state.ChatTimelineProjector;
import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageStatus;
import com.chat.upgrade.client.ui.chat.state.RichChatStateStore;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceRuntime;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.chat.surface.ChatPresentationMode;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public final class RichChatLayoutEngine {
    private static final Component DELETED_MARKER = Component.translatable("chatupgrade.message.deleted")
            .withStyle(new ChatFormatting[] { ChatFormatting.GRAY, ChatFormatting.ITALIC });

    public RichChatLayout layoutFromStore(
            Font font,
            RichChatViewportMetrics metrics,
            ChatAppearanceSnapshot appearance) {
        return layoutFromStore(font, metrics, appearance, ChatPresentationMode.OPEN_PANEL);
    }

    public RichChatLayout layoutFromStore(
            Font font,
            RichChatViewportMetrics metrics,
            ChatAppearanceSnapshot appearance,
            ChatPresentationMode presentationMode) {
        return layout(
                font,
                metrics,
                ChatMessageGroupStore.filterNewestFirst(RichChatStateStore.snapshotNewestFirst()),
                combinedStoreVersion(),
                appearance,
                presentationMode);
    }

    public RichChatLayout layoutFromStore(Font font, RichChatViewportMetrics metrics) {
        return layoutFromStore(font, metrics, ChatAppearanceRuntime.current());
    }

    private static long combinedStoreVersion() {
        return (RichChatStateStore.version() << 32) ^ ChatMessageGroupStore.version();
    }

    public RichChatLayout layout(
            Font font,
            RichChatViewportMetrics metrics,
            List<RichChatMessage> newestFirst,
            long storeVersion,
            ChatAppearanceSnapshot appearance) {
        return layout(font, metrics, newestFirst, storeVersion, appearance, ChatPresentationMode.OPEN_PANEL);
    }

    public RichChatLayout layout(
            Font font,
            RichChatViewportMetrics metrics,
            List<RichChatMessage> newestFirst,
            long storeVersion,
            ChatAppearanceSnapshot appearance,
            ChatPresentationMode presentationMode) {
        if (font == null) {
            throw new IllegalArgumentException("font must not be null");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("metrics must not be null");
        }
        ChatAppearanceSnapshot policy = appearance == null
                ? ChatAppearanceRuntime.current()
                : appearance;
        ChatPresentationMode mode = presentationMode == null
                ? ChatPresentationMode.CLOSED_HUD
                : presentationMode;
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
        for (int index = 0; index < timeline.size(); index++) {
            ChatTimelineProjection projection = timeline.get(index);
            RichChatMessageLayout layout = layoutMessage(font, metrics, projection, cursorY, policy, mode);
            cursorY = layout.bounds().bottom();
            if (!layout.nodes().isEmpty()) {
                messageLayouts.add(layout);
                allNodes.addAll(layout.nodes());
                allHitBoxes.addAll(layout.hitBoxes());
            }
            if (index + 1 < timeline.size()) {
                ChatTimelineProjection next = timeline.get(index + 1);
                cursorY += next.groupPosition().startsGroup() ? policy.groupGap() : policy.messageGap();
            } else if (policy.messageBubbles() && projection.groupPosition().endsGroup()) {
                cursorY += policy.groupGap();
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
        return layout(font, metrics, newestFirst, storeVersion, ChatAppearanceRuntime.current());
    }

    private RichChatMessageLayout layoutMessage(
            Font font,
            RichChatViewportMetrics metrics,
            ChatTimelineProjection timeline,
            int top,
            ChatAppearanceSnapshot policy,
            ChatPresentationMode presentationMode) {
        RichChatMessage message = timeline.message();
        List<RichChatRenderNode> nodes = new ArrayList<>();
        List<RichChatHitBox> hitBoxes = new ArrayList<>();
        Component textComponent = textComponent(message);
        boolean hasText = !textComponent.getString().isBlank() || message.attachments().isEmpty();
        boolean playerMessage = timeline.kind().playerAuthored();
        boolean alignRight = playerMessage
                && policy.splitOwnMessages()
                && message.authoredByLocalPlayer();
        boolean showIdentity = policy.showIdentity(timeline);
        boolean showMetadata = playerMessage;
        boolean reserveIdentitySpace = playerMessage && policy.showPlayerAvatars();
        int resolvedAvatarSize = policy.avatarSize(metrics.entryHeight());
        int identityGutter = reserveIdentitySpace
                ? policy.identityGutter(metrics.entryHeight())
                : 0;
        int primaryAvailableWidth = Math.max(1, metrics.textWidth() - identityGutter);
        int primaryLaneWidth = Math.max(1, primaryAvailableWidth * policy.contentWidthPercent() / 100);
        int contentRightEdge = metrics.textLeft() + metrics.textWidth();
        int primaryLaneLeft = alignRight
                ? contentRightEdge - identityGutter - primaryLaneWidth
                : metrics.textLeft() + identityGutter;
        int primaryLaneRight = primaryLaneLeft + primaryLaneWidth;
        int continuationLaneLeft = primaryLaneLeft;
        int continuationLaneWidth = primaryLaneWidth;
        int continuationLaneRight = primaryLaneRight;
        int bubblePadding = policy.bubblePadding();
        int contentLeft = primaryLaneLeft + bubblePadding;
        int contentWidth = Math.max(1, primaryLaneWidth - bubblePadding * 2);
        int contentRight = contentLeft + contentWidth;
        int continuationContentLeft = continuationLaneLeft + bubblePadding;
        int continuationContentWidth = Math.max(1, continuationLaneWidth - bubblePadding * 2);
        int continuationContentRight = continuationContentLeft + continuationContentWidth;
        int avatarLeft = alignRight
                ? contentRightEdge - resolvedAvatarSize
                : metrics.textLeft();
        RichChatBounds identityBounds = showIdentity
                ? RichChatBounds.ofSize(avatarLeft, top, resolvedAvatarSize, resolvedAvatarSize)
                : null;
        String rawMetadataLabel = showMetadata ? ChatMessageMetadata.label(timeline) : "";
        String metadataLabel = rawMetadataLabel;
        boolean metadataOnOwnLine = showMetadata && (policy.doubleLineLayout()
                || message.replyTo() != null
                || !message.attachments().isEmpty()
                || font.width(rawMetadataLabel) + 12 >= contentWidth);
        int metadataTextWidth = Math.max(1, font.width(metadataLabel));
        int metadataLeft = alignRight ? contentRight - metadataTextWidth : contentLeft;
        RichChatBounds metadataBounds = showMetadata
                ? RichChatBounds.ofSize(metadataLeft, top + bubblePadding, metadataTextWidth, metrics.entryHeight())
                : null;
        int cursorY = top + bubblePadding;
        int bodyLeft = contentLeft;
        int bodyWidth = contentWidth;
        if (metadataOnOwnLine) {
            cursorY += metrics.entryHeight();
            bodyLeft = continuationContentLeft;
            bodyWidth = continuationContentWidth;
        } else if (showMetadata) {
            int metadataGap = 6;
            int metadataWidth = Math.min(contentWidth - 1, font.width(metadataLabel));
            int metadataSlotWidth = Math.min(contentWidth - 1, metadataWidth + metadataGap);
            if (alignRight) {
                bodyWidth = Math.max(1, contentWidth - Math.max(0, metadataSlotWidth));
                metadataBounds = RichChatBounds.ofSize(
                        contentRight - Math.max(1, metadataWidth),
                        top + bubblePadding,
                        Math.max(1, metadataWidth),
                        metrics.entryHeight());
            } else {
                bodyLeft += Math.max(0, metadataSlotWidth);
                bodyWidth = Math.max(1, contentWidth - Math.max(0, metadataSlotWidth));
                metadataBounds = RichChatBounds.ofSize(
                        contentLeft,
                        top + bubblePadding,
                        Math.max(1, metadataWidth),
                        metrics.entryHeight());
            }
        }
        int order = 0;
        int visualLeft = metadataBounds == null ? contentLeft : metadataBounds.left();
        int visualRight = metadataBounds == null ? contentLeft + 1 : metadataBounds.right();
        ArrayDeque<InlineEmojiSlot> emojiQueue = new ArrayDeque<>(message.inlineEmojiSlots());

        if (message.replyTo() != null) {
            Component replyComponent = replyComponent(message.replyTo());
            for (FormattedCharSequence line : font.split(replyComponent, bodyWidth)) {
                int lineWidth = Math.max(1, font.width(line));
                int lineLeft = alignRight ? bodyLeft + Math.max(0, bodyWidth - lineWidth) : bodyLeft;
                RichChatBounds lineBounds = RichChatBounds.ofSize(
                        lineLeft,
                        cursorY,
                        lineWidth,
                        metrics.entryHeight());
                nodes.add(RichChatRenderNode.reply(
                        message.messageId(),
                        lineBounds,
                        order,
                        line,
                        replyComponent));
                visualLeft = Math.min(visualLeft, lineBounds.left());
                visualRight = Math.max(visualRight, lineBounds.right());
                cursorY += metrics.entryHeight();
                order++;
            }
        }

        if (hasText) {
            if (message.replyTo() != null) {
                cursorY += policy.nodeGap();
            }
            List<FormattedCharSequence> lines = font.split(textComponent, bodyWidth);
            if (lines.isEmpty()) {
                lines = List.of(FormattedCharSequence.EMPTY);
            }
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                FormattedCharSequence line = lines.get(lineIndex);
                int lineAreaLeft = lineIndex == 0 ? bodyLeft : continuationContentLeft;
                int lineAreaWidth = lineIndex == 0 ? bodyWidth : continuationContentWidth;
                int lineWidth = Math.max(1, font.width(line));
                int lineLeft = alignRight
                        ? lineAreaLeft + Math.max(0, lineAreaWidth - lineWidth)
                        : lineAreaLeft;
                RichChatBounds lineBounds = RichChatBounds.ofSize(
                        lineLeft,
                        cursorY,
                        lineWidth,
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
                visualLeft = Math.min(visualLeft, lineBounds.left());
                visualRight = Math.max(visualRight, lineBounds.right());
                cursorY += metrics.entryHeight();
                order++;
            }
        }

        for (RichAttachment attachment : message.attachments()) {
            if (!nodes.isEmpty()) {
                cursorY += policy.nodeGap();
            }
            RichChatMediaBox attachmentLayout = RichChatMediaSizing.measure(
                    continuationContentWidth,
                    metrics.entryHeight(),
                    attachment);
            int attachmentLeft = alignRight
                    ? continuationContentRight - attachmentLayout.width()
                    : continuationContentLeft;
            RichChatBounds attachmentBounds = RichChatBounds.ofSize(
                    attachmentLeft,
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
                    new ChatHitTarget.Attachment(attachment)));
            cursorY += attachmentLayout.height();
            visualLeft = Math.min(visualLeft, attachmentBounds.left());
            visualRight = Math.max(visualRight, attachmentBounds.right());
            order++;
        }

        if (showMetadata && cursorY == top + bubblePadding) {
            cursorY += metrics.entryHeight();
        }
        int visualBottom = cursorY + bubblePadding;
        int messageBottom = identityBounds == null
                ? visualBottom
                : Math.max(visualBottom, identityBounds.bottom());
        int messageHeight = Math.max(0, messageBottom - top);
        RichChatBounds messageBounds = RichChatBounds.ofSize(
                metrics.backgroundLeft(),
                top,
                metrics.backgroundRight() - metrics.backgroundLeft(),
                messageHeight);
        int visualLaneLeft = Math.min(primaryLaneLeft, continuationLaneLeft);
        int visualLaneRight = Math.max(primaryLaneRight, continuationLaneRight);
        int paddedVisualLeft = Math.max(visualLaneLeft, visualLeft - bubblePadding);
        int paddedVisualRight = Math.min(
                visualLaneRight,
                Math.max(paddedVisualLeft + 1, visualRight + bubblePadding));
        RichChatBounds visualBounds = new RichChatBounds(
                paddedVisualLeft,
                top,
                paddedVisualRight,
                Math.max(top + 1, visualBottom));

        int alignmentShift = alignRight
                ? 0
                : nonPlayerAlignmentShift(timeline, policy, metrics, visualBounds, presentationMode);
        if (alignmentShift != 0) {
            visualBounds = visualBounds.translate(alignmentShift, 0);
            identityBounds = translate(identityBounds, alignmentShift, 0);
            metadataBounds = translate(metadataBounds, alignmentShift, 0);
            nodes = translateNodes(nodes, alignmentShift, 0);
            hitBoxes = translateHitBoxes(hitBoxes, alignmentShift, 0);
        }
        return new RichChatMessageLayout(
                message,
                timeline,
                messageBounds,
                visualBounds,
                identityBounds,
                metadataBounds,
                nodes,
                hitBoxes);
    }

    private static int nonPlayerAlignmentShift(
            ChatTimelineProjection timeline,
            ChatAppearanceSnapshot policy,
            RichChatViewportMetrics metrics,
            RichChatBounds visualBounds,
            ChatPresentationMode presentationMode) {
        if (timeline.kind().playerAuthored() || presentationMode == ChatPresentationMode.CLOSED_HUD) {
            return 0;
        }
        int contentLeft = metrics.textLeft();
        int contentRight = metrics.textLeft() + metrics.textWidth();
        int targetLeft = switch (policy.nonPlayerAlignment()) {
            case LEFT -> visualBounds.left();
            case CENTER -> contentLeft + Math.max(0, (metrics.textWidth() - visualBounds.width()) / 2);
            case RIGHT -> contentRight - visualBounds.width();
        };
        return targetLeft - visualBounds.left();
    }

    private static RichChatBounds translate(RichChatBounds bounds, int deltaX, int deltaY) {
        return bounds == null ? null : bounds.translate(deltaX, deltaY);
    }

    private static List<RichChatRenderNode> translateNodes(
            List<RichChatRenderNode> nodes,
            int deltaX,
            int deltaY) {
        return nodes.stream()
                .map(node -> new RichChatRenderNode(
                        node.kind(),
                        node.messageId(),
                        node.bounds().translate(deltaX, deltaY),
                        node.order(),
                        node.text(),
                        node.component(),
                        node.attachment(),
                        node.inlineEmojiSlots()))
                .toList();
    }

    private static List<RichChatHitBox> translateHitBoxes(
            List<RichChatHitBox> hitBoxes,
            int deltaX,
            int deltaY) {
        return hitBoxes.stream()
                .map(hitBox -> new RichChatHitBox(
                        hitBox.kind(),
                        hitBox.messageId(),
                        hitBox.bounds().translate(deltaX, deltaY),
                        hitBox.target()))
                .toList();
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
        for (StyledTextRun run : runs) {
            int startX = lineBounds.left() + InlineEmojiLayout.prefixWidth(font, line, run.startIndex());
            int endX = lineBounds.left() + InlineEmojiLayout.prefixWidth(font, line, run.endIndex());
            int width = Math.max(0, endX - startX);
            if (width > 0 && hasInteractiveStyle(run.style())) {
                hitBoxes.add(new RichChatHitBox(
                        RichChatHitBoxKind.TEXT,
                        messageId,
                        RichChatBounds.ofSize(startX, lineBounds.top(), width, lineBounds.height()),
                        new ChatHitTarget.StyledText(run.style())));
            }
        }
        return hitBoxes;
    }

    private static List<StyledTextRun> styledTextRuns(FormattedCharSequence line) {
        if (line == null) {
            return List.of();
        }
        List<StyledTextRun> runs = new ArrayList<>();
        Style[] currentStyle = new Style[1];
        int[] currentStartIndex = new int[] { 0 };
        int[] logicalIndex = new int[] { 0 };
        line.accept((index, style, codePoint) -> {
            Style safeStyle = style == null ? Style.EMPTY : style;
            if (currentStyle[0] != null && !currentStyle[0].equals(safeStyle)) {
                runs.add(new StyledTextRun(
                        currentStyle[0],
                        currentStartIndex[0],
                        logicalIndex[0]));
                currentStartIndex[0] = logicalIndex[0];
            }
            currentStyle[0] = safeStyle;
            logicalIndex[0] += Character.charCount(codePoint);
            return true;
        });
        if (currentStyle[0] != null && logicalIndex[0] > currentStartIndex[0]) {
            runs.add(new StyledTextRun(
                    currentStyle[0],
                    currentStartIndex[0],
                    logicalIndex[0]));
        }
        return runs;
    }

    private static boolean hasInteractiveStyle(Style style) {
        return style != null && (style.getClickEvent() != null || style.getHoverEvent() != null);
    }

    private record StyledTextRun(Style style, int startIndex, int endIndex) {
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
        int textY = lineBounds.bottom() - metrics.entryBottomToMessageY();
        List<RichChatHitBox> hitBoxes = new ArrayList<>();
        for (InlineEmojiSlot slot : slots) {
            InlineEmojiLayout.Placement placement = InlineEmojiLayout.place(
                    font,
                    line,
                    slot.charIndex(),
                    lineBounds.left(),
                    textY);
            RichAttachment attachment = RichAttachment.structured(
                    InlineResourceType.IMAGE,
                    slot.token(),
                    slot.iconUrl(),
                    null,
                    null);
            hitBoxes.add(new RichChatHitBox(
                    RichChatHitBoxKind.IMAGE,
                    messageId,
                    RichChatBounds.ofSize(
                            placement.x(),
                            placement.y(),
                            placement.size(),
                            placement.size()),
                    new ChatHitTarget.Emoji(attachment)));
        }
        return hitBoxes;
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
}