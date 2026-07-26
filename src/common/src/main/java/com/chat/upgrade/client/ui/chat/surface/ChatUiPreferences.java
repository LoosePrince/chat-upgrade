package com.chat.upgrade.client.ui.chat.surface;

import com.chat.upgrade.client.ChatUpgradeConfig;

import net.minecraft.network.chat.Component;

/** Immutable non-appearance UI preferences consumed while a chat screen is open. */
public record ChatUiPreferences(
        String inputPlaceholder,
        boolean screenMaskEnabled,
        boolean compactMediaCards,
        ChatUpgradeConfig.MentionNotificationMode mentionNotificationMode,
        boolean messagePassthroughEnabled,
        boolean messageGroupingEnabled,
        ChatUpgradeConfig.MessageGroupPosition messageGroupPosition,
        String privateMessageCommand) {
    public static ChatUiPreferences from(ChatUpgradeConfig config) {
        ChatUpgradeConfig source = config == null ? ChatUpgradeConfig.get() : config;
        String placeholder = source.chatInputPlaceholder == null ? "" : source.chatInputPlaceholder;
        ChatUpgradeConfig.MentionNotificationMode notificationMode = source.mentionNotificationMode == null
                ? ChatUpgradeConfig.MentionNotificationMode.SOUND
                : source.mentionNotificationMode;
        return new ChatUiPreferences(
                placeholder,
                source.usesChatScreenMask(),
                source.compactMediaCards,
                notificationMode,
                Boolean.TRUE.equals(source.messagePassthroughEnabled),
                Boolean.TRUE.equals(source.messageGroupingEnabled),
                source.messageGroupPosition == null
                        ? ChatUpgradeConfig.MessageGroupPosition.LEFT
                        : source.messageGroupPosition,
                source.privateMessageCommand == null
                        ? ChatUpgradeConfig.DEFAULT_PRIVATE_MESSAGE_COMMAND
                        : source.privateMessageCommand);
    }

    public Component resolvedInputPlaceholder() {
        return inputPlaceholder.isBlank()
                ? Component.translatable("chatupgrade.input.placeholder.default")
                : Component.literal(inputPlaceholder);
    }
}