package com.chat.upgrade.client.ui.chat.surface;

import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

public record ChatSurfaceFrame(
        ChatPresentationMode presentationMode,
        ChatPanelGeometry panelGeometry,
        boolean restricted) {
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