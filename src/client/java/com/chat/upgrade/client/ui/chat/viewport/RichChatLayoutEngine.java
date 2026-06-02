package com.chat.upgrade.client.ui.chat.viewport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.chat.upgrade.client.media.audio.AudioEntry;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.image.ImageEntry;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.media.video.VideoEntry;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.ui.chat.UpgradeHudInlinePaint;
import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageSource;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageStatus;
import com.chat.upgrade.client.ui.chat.state.RichChatStateStore;
import com.chat.upgrade.client.ui.layout.VideoUiLayout;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class RichChatLayoutEngine {
    private static final int NODE_GAP = 2;
    private static final Component DELETED_MARKER = Component.translatable("chat.deleted_marker")
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

        List<RichChatMessageLayout> messageLayouts = new ArrayList<>();
        List<RichChatRenderNode> allNodes = new ArrayList<>();
        List<RichChatHitBox> allHitBoxes = new ArrayList<>();
        int cursorY = 0;
        for (RichChatMessage message : oldestFirst) {
            RichChatMessageLayout layout = layoutMessage(font, metrics, message, cursorY);
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

    private RichChatMessageLayout layoutMessage(Font font, RichChatViewportMetrics metrics, RichChatMessage message, int top) {
        List<RichChatRenderNode> nodes = new ArrayList<>();
        List<RichChatHitBox> hitBoxes = new ArrayList<>();
        Component textComponent = textComponent(message);
        boolean hasText = !textComponent.getString().isBlank() || message.attachments().isEmpty();
        int cursorY = top;
        int order = 0;

        if (hasText) {
            List<FormattedCharSequence> lines = font.split(textComponent, metrics.textWidth());
            if (lines.isEmpty()) {
                lines = List.of(FormattedCharSequence.EMPTY);
            }
            for (FormattedCharSequence line : lines) {
                RichChatBounds lineBounds = RichChatBounds.ofSize(
                        metrics.textLeft(),
                        cursorY,
                        metrics.textWidth(),
                        metrics.entryHeight());
                RichChatRenderNode node = message.source() == RichChatMessageSource.LOCAL_SYSTEM
                        ? RichChatRenderNode.system(message.messageId(), lineBounds, order, line, textComponent)
                        : RichChatRenderNode.text(message.messageId(), lineBounds, order, line, textComponent);
                nodes.add(node);
                cursorY += metrics.entryHeight();
                order++;
            }
        }

        for (RichAttachment attachment : message.attachments()) {
            if (!nodes.isEmpty()) {
                cursorY += NODE_GAP;
            }
            AttachmentLayout attachmentLayout = measureAttachment(metrics, attachment);
            RichChatBounds attachmentBounds = RichChatBounds.ofSize(
                    metrics.textLeft(),
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
        return new RichChatMessageLayout(message, messageBounds, nodes, hitBoxes);
    }

    private static Component textComponent(RichChatMessage message) {
        if (message.status() == RichChatMessageStatus.DELETED) {
            return DELETED_MARKER;
        }
        return message.component();
    }

    private static AttachmentLayout measureAttachment(RichChatViewportMetrics metrics, RichAttachment attachment) {
        if (!attachment.hasRenderableUrl()) {
            return new AttachmentLayout(
                    RichChatRenderNodeKind.ATTACHMENT_PENDING,
                    Math.min(UpgradeHudInlinePaint.AUDIO_WIDTH, metrics.textWidth()),
                    metrics.entryHeight());
        }
        String url = attachment.requireRenderableUrl();
        return switch (attachment.type()) {
            case IMAGE -> measureImage(metrics, url);
            case AUDIO -> measureAudio(metrics, url);
            case VIDEO -> measureVideo(metrics, url);
        };
    }

    private static AttachmentLayout measureImage(RichChatViewportMetrics metrics, String url) {
        ImageEntry entry = ImageLoader.getIfPresent(url);
        if (entry != null && entry.getState() == ImageEntry.State.FAILED) {
            return failedAttachment(metrics);
        }
        if (entry != null && entry.isLoaded() && entry.getWidth() > 0 && entry.getHeight() > 0) {
            return new AttachmentLayout(
                    RichChatRenderNodeKind.IMAGE,
                    clampWidth(entry.getWidth(), metrics.textWidth()),
                    Math.max(1, entry.getHeight()));
        }
        return new AttachmentLayout(
                RichChatRenderNodeKind.IMAGE,
                Math.min(ImageLoader.MAX_PREVIEW_WIDTH, metrics.textWidth()),
                ImageLoader.PREVIEW_HEIGHT);
    }

    private static AttachmentLayout measureAudio(RichChatViewportMetrics metrics, String url) {
        AudioEntry entry = AudioLoader.getIfPresent(url);
        if (entry != null && entry.getState() == AudioEntry.State.FAILED) {
            return failedAttachment(metrics);
        }
        return new AttachmentLayout(
                RichChatRenderNodeKind.AUDIO,
                Math.min(UpgradeHudInlinePaint.AUDIO_WIDTH, metrics.textWidth()),
                UpgradeHudInlinePaint.AUDIO_HEIGHT);
    }

    private static AttachmentLayout measureVideo(RichChatViewportMetrics metrics, String url) {
        VideoEntry entry = VideoLoader.getIfPresent(url);
        if (entry != null && entry.getState() == VideoEntry.State.FAILED) {
            return failedAttachment(metrics);
        }
        return new AttachmentLayout(
                RichChatRenderNodeKind.VIDEO,
                Math.min(VideoUiLayout.WIDTH, metrics.textWidth()),
                VideoUiLayout.HEIGHT);
    }

    private static AttachmentLayout failedAttachment(RichChatViewportMetrics metrics) {
        return new AttachmentLayout(
                RichChatRenderNodeKind.ATTACHMENT_FAILED,
                Math.min(UpgradeHudInlinePaint.AUDIO_WIDTH, metrics.textWidth()),
                metrics.entryHeight());
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

    private static int clampWidth(int preferredWidth, int maxWidth) {
        return Math.max(1, Math.min(Math.max(1, preferredWidth), Math.max(1, maxWidth)));
    }

    private record AttachmentLayout(RichChatRenderNodeKind kind, int width, int height) {
        private AttachmentLayout {
            width = Math.max(1, width);
            height = Math.max(1, height);
        }
    }
}