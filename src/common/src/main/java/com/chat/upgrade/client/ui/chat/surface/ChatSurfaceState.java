package com.chat.upgrade.client.ui.chat.surface;

public final class ChatSurfaceState {
    public enum Overlay {
        NONE,
        EMOJI_PICKER,
        CONTEXT_MENU,
        SETTINGS
    }

    public enum FocusOwner {
        NONE,
        TIMELINE,
        COMPOSER,
        OVERLAY
    }

    private ChatPresentationMode presentationMode = ChatPresentationMode.CLOSED_HUD;
    private ChatAppearanceSnapshot appearance = ChatAppearanceRuntime.current();
    private ChatPanelGeometry panelGeometry = new ChatPanelGeometry(
            ChatPanelGeometry.DEFAULT_LEFT,
            ChatPanelGeometry.DEFAULT_BOTTOM_OFFSET,
            ChatPanelGeometry.DEFAULT_WIDTH,
            ChatPanelGeometry.DEFAULT_HEIGHT);
    private Overlay overlay = Overlay.NONE;
    private FocusOwner focusOwner = FocusOwner.NONE;
    private boolean restricted;
    private boolean messageGroupSidebarExpanded = true;
    private int screenWidth = 1;
    private int screenHeight = 1;
    private int panelBottomInset = ChatPanelGeometry.SCREEN_MARGIN;
    private boolean panelScreenMargins = true;

    public ChatPresentationMode presentationMode() {
        return presentationMode;
    }

    public ChatPanelGeometry panelGeometry() {
        return panelGeometry;
    }

    public ChatAppearanceSnapshot appearance() {
        return appearance;
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

    public boolean messageGroupSidebarExpanded() {
        return messageGroupSidebarExpanded;
    }

    public int screenWidth() {
        return screenWidth;
    }

    public int screenHeight() {
        return screenHeight;
    }

    public int panelBottomInset() {
        return panelBottomInset;
    }

    public void setPresentationMode(ChatPresentationMode nextMode) {
        presentationMode = nextMode == null ? ChatPresentationMode.CLOSED_HUD : nextMode;
        if (presentationMode == ChatPresentationMode.CLOSED_HUD) {
            overlay = Overlay.NONE;
            focusOwner = FocusOwner.NONE;
        }
    }

    public void setAppearance(ChatAppearanceSnapshot nextAppearance) {
        appearance = nextAppearance == null ? ChatAppearanceRuntime.current() : nextAppearance;
    }

    public boolean updateScreenSize(int nextScreenWidth, int nextScreenHeight) {
        screenWidth = Math.max(1, nextScreenWidth);
        screenHeight = Math.max(1, nextScreenHeight);
        panelBottomInset = normalizeBottomInset(panelBottomInset);
        ChatPanelGeometry normalized = panelGeometry.normalized(
                screenWidth,
                screenHeight,
                panelBottomInset,
                panelScreenMargins);
        if (normalized.equals(panelGeometry)) {
            return false;
        }
        panelGeometry = normalized;
        return true;
    }

    public void setPanelGeometry(ChatPanelGeometry nextGeometry) {
        setPanelGeometry(nextGeometry, panelBottomInset);
    }

    public void setPanelGeometry(ChatPanelGeometry nextGeometry, int bottomInset) {
        setPanelGeometry(nextGeometry, bottomInset, true);
    }

    public void setPanelGeometry(
            ChatPanelGeometry nextGeometry,
            int bottomInset,
            boolean screenMarginsEnabled) {
        if (nextGeometry == null) {
            return;
        }
        panelScreenMargins = screenMarginsEnabled;
        panelBottomInset = normalizeBottomInset(bottomInset);
        panelGeometry = nextGeometry.normalized(
                screenWidth,
                screenHeight,
                panelBottomInset,
                panelScreenMargins);
    }

    private int normalizeBottomInset(int bottomInset) {
        int minimum = panelScreenMargins ? ChatPanelGeometry.SCREEN_MARGIN : 0;
        return Math.clamp(
                bottomInset,
                minimum,
                Math.max(minimum, screenHeight - 1));
    }

    public void setRestricted(boolean nextRestricted) {
        restricted = nextRestricted;
    }

    public void toggleMessageGroupSidebar() {
        messageGroupSidebarExpanded = !messageGroupSidebarExpanded;
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
        return new ChatSurfaceFrame(
                presentationMode,
                panelGeometry,
                restricted,
                appearance,
                com.chat.upgrade.client.ChatClientConfigRuntime.uiPreferences(),
                messageGroupSidebarExpanded);
    }
}