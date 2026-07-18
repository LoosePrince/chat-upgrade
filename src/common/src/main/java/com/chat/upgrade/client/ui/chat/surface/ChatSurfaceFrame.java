package com.chat.upgrade.client.ui.chat.surface;

import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

public record ChatSurfaceFrame(
        ChatPresentationMode presentationMode,
        ChatPanelGeometry panelGeometry,
        boolean restricted,
        ChatTheme theme) {
    public ChatSurfaceFrame {
        presentationMode = presentationMode == null ? ChatPresentationMode.CLOSED_HUD : presentationMode;
        panelGeometry = panelGeometry == null
                ? new ChatPanelGeometry(
                        ChatPanelGeometry.DEFAULT_LEFT,
                        ChatPanelGeometry.DEFAULT_BOTTOM_OFFSET,
                        ChatPanelGeometry.DEFAULT_WIDTH,
                        ChatPanelGeometry.DEFAULT_HEIGHT)
                : panelGeometry;
        theme = theme == null ? ChatThemes.resolve(ChatSurfaceThemeId.DEFAULT) : theme;
    }
    public boolean isOpenPanel() {
        return presentationMode == ChatPresentationMode.OPEN_PANEL;
    }

    public RichChatBounds panelBounds() {
        return panelGeometry.panelBounds();
    }

    public RichChatBounds messageViewportBounds() {
        return panelGeometry.messageViewportBounds();
    }

    public RichChatBounds composerBounds() {
        return panelGeometry.composerBounds();
    }
}