package com.chat.upgrade.client.ui.chat.surface;

import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

public record ChatSurfaceFrame(
        ChatPresentationMode presentationMode,
        ChatPanelGeometry panelGeometry,
        boolean restricted,
        ChatAppearanceSnapshot appearance) {
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
    }
    public boolean isOpenPanel() {
        return presentationMode == ChatPresentationMode.OPEN_PANEL;
    }

    public RichChatBounds panelBounds() {
        return panelGeometry.panelBounds();
    }

    public RichChatBounds messageViewportBounds() {
        if (appearance.vanillaStyleInput()) {
            int top = Math.min(panelGeometry.bottom(), panelGeometry.y() + ChatPanelGeometry.HEADER_HEIGHT);
            return new RichChatBounds(
                    panelGeometry.x(),
                    top,
                    panelGeometry.right(),
                    panelGeometry.bottom());
        }
        return panelGeometry.messageViewportBounds();
    }

    public RichChatBounds composerBounds() {
        if (appearance.vanillaStyleInput()) {
            return RichChatBounds.ofSize(panelGeometry.x(), panelGeometry.bottom(), panelGeometry.width(), 0);
        }
        return panelGeometry.composerBounds();
    }
}