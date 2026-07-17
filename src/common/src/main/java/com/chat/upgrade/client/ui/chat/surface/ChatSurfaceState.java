package com.chat.upgrade.client.ui.chat.surface;

public final class ChatSurfaceState {
    public enum Overlay {
        NONE,
        EMOJI_PICKER,
        CONTEXT_MENU
    }

    public enum FocusOwner {
        NONE,
        TIMELINE,
        COMPOSER,
        OVERLAY
    }

    private ChatPresentationMode presentationMode = ChatPresentationMode.CLOSED_HUD;
    private ChatPanelGeometry panelGeometry = new ChatPanelGeometry(
            ChatPanelGeometry.DEFAULT_LEFT,
            ChatPanelGeometry.DEFAULT_BOTTOM_OFFSET,
            ChatPanelGeometry.DEFAULT_WIDTH,
            ChatPanelGeometry.DEFAULT_HEIGHT);
    private Overlay overlay = Overlay.NONE;
    private FocusOwner focusOwner = FocusOwner.NONE;
    private boolean restricted;
    private int screenWidth = 1;
    private int screenHeight = 1;

    public ChatPresentationMode presentationMode() {
        return presentationMode;
    }

    public ChatPanelGeometry panelGeometry() {
        return panelGeometry;
    }

    public Overlay overlay() {
        return overlay;
    }

    public FocusOwner focusOwner() {
        return focusOwner;
    }

    public boolean restricted() {
        return restricted;
    }

    public int screenWidth() {
        return screenWidth;
    }

    public int screenHeight() {
        return screenHeight;
    }

    public void setPresentationMode(ChatPresentationMode nextMode) {
        presentationMode = nextMode == null ? ChatPresentationMode.CLOSED_HUD : nextMode;
        if (presentationMode == ChatPresentationMode.CLOSED_HUD) {
            overlay = Overlay.NONE;
            focusOwner = FocusOwner.NONE;
        }
    }

    public boolean updateScreenSize(int nextScreenWidth, int nextScreenHeight) {
        screenWidth = Math.max(1, nextScreenWidth);
        screenHeight = Math.max(1, nextScreenHeight);
        ChatPanelGeometry normalized = panelGeometry.normalized(screenWidth, screenHeight);
        if (normalized.equals(panelGeometry)) {
            return false;
        }
        panelGeometry = normalized;
        return true;
    }

    public void setPanelGeometry(ChatPanelGeometry nextGeometry) {
        if (nextGeometry == null) {
            return;
        }
        panelGeometry = nextGeometry.normalized(screenWidth, screenHeight);
    }

    public void setRestricted(boolean nextRestricted) {
        restricted = nextRestricted;
    }

    public void setOverlay(Overlay nextOverlay) {
        overlay = nextOverlay == null ? Overlay.NONE : nextOverlay;
        if (overlay != Overlay.NONE) {
            focusOwner = FocusOwner.OVERLAY;
        } else if (focusOwner == FocusOwner.OVERLAY) {
            focusOwner = presentationMode == ChatPresentationMode.OPEN_PANEL
                    ? FocusOwner.COMPOSER
                    : FocusOwner.NONE;
        }
    }

    public void setFocusOwner(FocusOwner nextFocusOwner) {
        focusOwner = nextFocusOwner == null ? FocusOwner.NONE : nextFocusOwner;
    }

    public ChatSurfaceFrame frame() {
        return new ChatSurfaceFrame(presentationMode, panelGeometry, restricted);
    }
}