package com.chat.upgrade.client.ui.chat;

import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.mixininterface.ImageAttachable;

import net.minecraft.client.multiplayer.chat.GuiMessage;

public final class ChatUpgradeChatPipelineGate {
    private ChatUpgradeChatPipelineGate() {
    }

    public static boolean isTakeoverMode() {
        return ChatUpgradeConfig.get().chatInputMode == ChatUpgradeConfig.ChatInputMode.TAKEOVER;
    }

    public static boolean shouldEnhancePlainTextChat() {
        return isTakeoverMode();
    }

    public static boolean shouldUseRichViewportInteractions() {
        return isTakeoverMode();
    }

    public static boolean shouldUseScrollEnhancements() {
        return isTakeoverMode() && ChatUpgradeConfig.isSmoothScrollEnabled();
    }

    public static boolean shouldRenderLineEnhancements(GuiMessage.Line line) {
        if (isTakeoverMode()) {
            return false;
        }
        if (!(((Object) line) instanceof ImageAttachable attachable)) {
            return false;
        }
        return attachable.chatupgrade$isImagePhantomTop() || attachable.chatupgrade$isImageContinuation();
    }

    public static boolean shouldResolveInlineChatClick() {
        return !isTakeoverMode();
    }
}