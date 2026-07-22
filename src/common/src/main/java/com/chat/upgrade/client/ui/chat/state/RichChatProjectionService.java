package com.chat.upgrade.client.ui.chat.state;

import java.util.List;

import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.model.RichAttachment;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class RichChatProjectionService {
    private RichChatProjectionService() {
    }

    public static RichChatProjection project(RichChatMessage message) {
        ChatProjectionMode mode = ChatUpgradeConfig.get().chatInputMode == ChatUpgradeConfig.ChatInputMode.TAKEOVER
                ? ChatProjectionMode.OWNED
                : ChatProjectionMode.VANILLA_COMPAT;
        return new RichChatProjection(message, mode, textProjection(message), message.firstRenderableAttachment());
    }

    private static Component textProjection(RichChatMessage message) {
        if (message.source() != RichChatMessageSource.STRUCTURED_PACKET) {
            return message.originalComponent();
        }
        MutableComponent projected = Component.empty();
        if (message.replyTo() != null) {
            String author = message.replyTo().author().visibleName();
            projected.append(Component.literal("↪ " + author + ": " + message.replyTo().excerpt() + "\n"));
        }
        if (message.kind().playerAuthored()) {
            projected.append(Component.literal("<" + message.author().visibleName() + "> "));
        }
        projected.append(message.component());
        for (RichAttachment attachment : message.attachments()) {
            if (attachment == null || !attachment.hasRenderableUrl()) {
                continue;
            }
            if (!projected.getString().isBlank() && !Character.isWhitespace(projected.getString().charAt(projected.getString().length() - 1))) {
                projected.append(Component.literal(" "));
            }
            projected.append(com.chat.upgrade.client.ui.chat.UpgradeBracketCodec.buildPlaceholderComponent(
                    attachment.type(),
                    attachment.displayName(),
                    attachment.requireRenderableUrl()));
        }
        return projected;
    }

    public static RichChatMessage record(
            String messageId,
            String senderName,
            Component component,
            String fallbackText,
            List<RichAttachment> attachments,
            RichChatMessageSource source) {
        return RichChatIngress.record(
                messageId,
                senderName,
                component,
                fallbackText,
                attachments,
                source);
    }

    public static RichChatProjection recordAndProject(
            String messageId,
            String senderName,
            Component component,
            String fallbackText,
            List<RichAttachment> attachments,
            RichChatMessageSource source) {
        return project(record(messageId, senderName, component, fallbackText, attachments, source));
    }

    public static void clear() {
        RichChatIngress.clear();
    }
}