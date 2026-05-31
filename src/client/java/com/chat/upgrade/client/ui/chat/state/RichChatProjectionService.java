package com.chat.upgrade.client.ui.chat.state;

import java.util.List;

import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.model.RichAttachment;

import net.minecraft.network.chat.Component;

public final class RichChatProjectionService {
    private RichChatProjectionService() {
    }

    public static RichChatProjection project(RichChatMessage message) {
        ChatProjectionMode mode = ChatUpgradeConfig.get().chatInputMode == ChatUpgradeConfig.ChatInputMode.TAKEOVER
                ? ChatProjectionMode.OWNED
                : ChatProjectionMode.VANILLA_COMPAT;
        return new RichChatProjection(message, mode, message.component(), message.firstRenderableAttachment());
    }

    public static RichChatMessage record(
            String messageId,
            String senderName,
            Component component,
            String fallbackText,
            List<RichAttachment> attachments,
            RichChatMessageSource source) {
        return RichChatStateStore.append(new RichChatMessage(
                messageId,
                senderName,
                component,
                fallbackText,
                attachments,
                source));
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
        RichChatStateStore.clear();
    }
}