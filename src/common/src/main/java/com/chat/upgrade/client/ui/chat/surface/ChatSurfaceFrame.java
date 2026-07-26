package com.chat.upgrade.client.ui.chat.surface;

import com.chat.upgrade.client.ChatClientConfigRuntime;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

public record ChatSurfaceFrame(
        ChatPresentationMode presentationMode,
        ChatPanelGeometry panelGeometry,
        boolean restricted,
        ChatAppearanceSnapshot appearance,
        ChatUiPreferences preferences,
        boolean messageGroupSidebarExpanded) {
    public static final int DEFAULT_GROUP_SIDEBAR_WIDTH = 104;

    public ChatSurfaceFrame {
        presentationMode = presentationMode == null ? ChatPresentationMode.CLOSED_HUD : presentationMode;
        panelGeometry = panelGeometry == null
                ? new ChatPanelGeometry(
                        ChatPanelGeometry.DEFAULT_LEFT,
                        ChatPanelGeometry.DEFAULT_BOTTOM_OFFSET,
                        ChatPanelGeometry.DEFAULT_WIDTH,
                        ChatPanelGeometry.DEFAULT_HEIGHT)
                : panelGeometry;
        appearance = appearance == null ? ChatAppearanceRuntime.current() : appearance;
        preferences = preferences == null ? ChatClientConfigRuntime.uiPreferences() : preferences;
    }
    public boolean isOpenPanel() {
        return presentationMode == ChatPresentationMode.OPEN_PANEL;
    }

    public boolean messageGroupingEnabled() {
        return isOpenPanel() && preferences.messageGroupingEnabled();
    }

    public boolean messageGroupingVisible() {
        return messageGroupingEnabled() && messageGroupSidebarExpanded;
    }

    public int messageGroupSidebarWidth() {
        if (!messageGroupingVisible()) {
            return 0;
        }
        int preferred = Math.min(DEFAULT_GROUP_SIDEBAR_WIDTH, Math.max(72, panelGeometry.width() / 3));
        return Math.min(preferred, Math.max(0, panelGeometry.width() - 1));
    }

    public RichChatBounds panelBounds() {
        return panelGeometry.panelBounds();
    }

    public RichChatBounds messageGroupSidebarBounds() {
        if (!messageGroupingVisible()) {
            return RichChatBounds.ofSize(panelGeometry.x(), panelGeometry.y(), 0, 0);
        }
        RichChatBounds panel = panelBounds();
        int width = messageGroupSidebarWidth();
        if (preferences.messageGroupPosition() == ChatUpgradeConfig.MessageGroupPosition.RIGHT) {
            return new RichChatBounds(panel.right() - width, panel.top(), panel.right(), panel.bottom());
        }
        return RichChatBounds.ofSize(panel.left(), panel.top(), width, panel.height());
    }

    public RichChatBounds contentBounds() {
        RichChatBounds panel = panelBounds();
        if (!messageGroupingVisible()) {
            return panel;
        }
        int width = messageGroupSidebarWidth();
        if (preferences.messageGroupPosition() == ChatUpgradeConfig.MessageGroupPosition.RIGHT) {
            return new RichChatBounds(panel.left(), panel.top(), panel.right() - width, panel.bottom());
        }
        return new RichChatBounds(panel.left() + width, panel.top(), panel.right(), panel.bottom());
    }

    public RichChatBounds headerBounds() {
        RichChatBounds content = contentBounds();
        return RichChatBounds.ofSize(
                content.left(),
                content.top(),
                content.width(),
                Math.min(ChatPanelGeometry.HEADER_HEIGHT, content.height()));
    }

    public RichChatBounds messageGroupToggleButtonBounds() {
        if (!messageGroupingEnabled()) {
            return RichChatBounds.ofSize(panelGeometry.x(), panelGeometry.y(), 0, 0);
        }
        RichChatBounds header = headerBounds();
        return RichChatBounds.ofSize(header.right() - 17, header.top() + 2, 14, 14);
    }

    public RichChatBounds messageViewportBounds() {
        RichChatBounds content = contentBounds();
        int top = Math.min(content.bottom(), content.top() + ChatPanelGeometry.HEADER_HEIGHT);
        if (appearance.vanillaStyleInput()) {
            return new RichChatBounds(content.left(), top, content.right(), content.bottom());
        }
        int viewportHeight = Math.max(0, content.bottom() - ChatPanelGeometry.COMPOSER_HEIGHT - top);
        return RichChatBounds.ofSize(content.left(), top, content.width(), viewportHeight);
    }

    public RichChatBounds composerBounds() {
        RichChatBounds content = contentBounds();
        if (appearance.vanillaStyleInput()) {
            return RichChatBounds.ofSize(content.left(), content.bottom(), content.width(), 0);
        }
        int composerHeight = Math.min(ChatPanelGeometry.COMPOSER_HEIGHT, content.height());
        return RichChatBounds.ofSize(
                content.left(),
                content.bottom() - composerHeight,
                content.width(),
                composerHeight);
    }
}