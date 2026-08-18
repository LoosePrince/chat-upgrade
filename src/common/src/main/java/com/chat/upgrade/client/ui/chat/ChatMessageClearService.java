package com.chat.upgrade.client.ui.chat;

import com.chat.upgrade.client.net.servermedia.ServerMediaNetworking;
import com.chat.upgrade.client.ui.chat.interaction.ChatTextSelectionState;
import com.chat.upgrade.client.ui.chat.notification.MentionNotificationService;
import com.chat.upgrade.client.ui.chat.state.ChatMessageGroupStore;
import com.chat.upgrade.client.ui.chat.state.ChatPrivateMessageResolver;
import com.chat.upgrade.client.ui.chat.state.RichChatIngress;
import com.chat.upgrade.client.ui.chat.state.RichChatProjectionCoordinator;
import com.chat.upgrade.client.ui.chat.surface.ChatMessageGroupSidebar;
import com.chat.upgrade.client.ui.chat.viewport.RichChatInteractionRouter;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewport;

/** Coordinates state owned by the vanilla and rich chat pipelines. */
public final class ChatMessageClearService {
    private ChatMessageClearService() {
    }

    public static void clearRuntimeState() {
        RichChatIngress.clear();
        RichChatProjectionCoordinator.clear();
        ChatMessageGroupStore.clearSession();
        ChatMessageGroupSidebar.clearSession();
        ChatPrivateMessageResolver.clearSession();
        RichChatInteractionRouter.clear();
        ChatTextSelectionState.clear();
        MentionNotificationService.clear();
        RichChatViewport.invalidateAll();
        RichChatViewport.state().clear();
        ChatUpgradeChatRenderState.cancelWheelOverscroll();
        UpgradePhantomCoordinator.clear();
        UpgradePhantomHudLayout.clearLayoutRegistrations();
    }

    public static void clearMessagesAndHistory() {
        clearRuntimeState();
        ServerMediaNetworking.clearClientChatHistory();
    }
}