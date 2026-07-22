package com.chat.upgrade.client.ui.chat.surface;

import java.io.IOException;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.ui.chat.interaction.ChatGestureArena;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

public final class ChatSurfaceController {
    private enum PointerOperation {
        NONE,
        MOVE,
        RESIZE
    }

    private static final ChatSurfaceState STATE = new ChatSurfaceState();

    private static ChatUpgradeConfig geometryConfigSource;
    private static ChatUpgradeConfig previewGeometryConfig;
    private static PointerOperation pointerOperation = PointerOperation.NONE;
    private static ChatPanelGeometry pointerStartGeometry;
    private static int pointerStartX;
    private static int pointerStartY;
    private static int resizeEdges;
    private static int vanillaComposerTop = -1;

    private ChatSurfaceController() {
    }

    public static ChatSurfaceState state() {
        return STATE;
    }

    public static ChatSurfaceFrame synchronize(
            int screenWidth,
            int screenHeight,
            boolean chatOpen,
            boolean restricted) {
        STATE.setAppearance(ChatAppearanceRuntime.current());
        ChatUpgradeConfig geometryConfig = activeGeometryConfig();
        ensureGeometryLoaded(geometryConfig, screenWidth, screenHeight);
        boolean normalized = STATE.updateScreenSize(screenWidth, screenHeight);
        applyGeometryConstraints(geometryConfig, screenWidth, screenHeight);
        STATE.setPresentationMode(chatOpen ? ChatPresentationMode.OPEN_PANEL : ChatPresentationMode.CLOSED_HUD);
        STATE.setRestricted(restricted);
        if (normalized && pointerOperation == PointerOperation.NONE) {
            persistGeometry();
        }
        return STATE.frame();
    }

    public static void onChatScreenOpened(int screenWidth, int screenHeight) {
        STATE.setAppearance(ChatAppearanceRuntime.current());
        ChatUpgradeConfig geometryConfig = activeGeometryConfig();
        ensureGeometryLoaded(geometryConfig, screenWidth, screenHeight);
        boolean normalized = STATE.updateScreenSize(screenWidth, screenHeight);
        applyGeometryConstraints(geometryConfig, screenWidth, screenHeight);
        STATE.setPresentationMode(ChatPresentationMode.OPEN_PANEL);
        STATE.setFocusOwner(ChatSurfaceState.FocusOwner.COMPOSER);
        if (normalized) {
            persistGeometry();
        }
    }

    public static void onChatScreenClosed() {
        boolean changed = pointerOperation != PointerOperation.NONE;
        boolean resizedHeight = pointerOperation == PointerOperation.RESIZE
                && (resizeEdges & (ChatPanelGeometry.EDGE_TOP | ChatPanelGeometry.EDGE_BOTTOM)) != 0;
        clearPointerOperation();
        ChatGestureArena.resetPointerState();
        STATE.setOverlay(ChatSurfaceState.Overlay.NONE);
        STATE.setFocusOwner(ChatSurfaceState.FocusOwner.NONE);
        STATE.setPresentationMode(ChatPresentationMode.CLOSED_HUD);
        vanillaComposerTop = -1;
        if (changed) {
            persistGeometry(resizedHeight);
        }
    }

    public static ChatPanelGeometry panelGeometry(int screenWidth, int screenHeight) {
        STATE.setAppearance(ChatAppearanceRuntime.current());
        ChatUpgradeConfig geometryConfig = activeGeometryConfig();
        ensureGeometryLoaded(geometryConfig, screenWidth, screenHeight);
        boolean normalized = STATE.updateScreenSize(screenWidth, screenHeight);
        applyGeometryConstraints(geometryConfig, screenWidth, screenHeight);
        if (normalized && pointerOperation == PointerOperation.NONE) {
            persistGeometry();
        }
        return STATE.panelGeometry();
    }

    public static void updateVanillaComposerTop(int composerTop, int screenWidth, int screenHeight) {
        vanillaComposerTop = Math.clamp(composerTop, 0, Math.max(0, screenHeight));
        STATE.updateScreenSize(screenWidth, screenHeight);
        applyGeometryConstraints(activeGeometryConfig(), screenWidth, screenHeight);
    }

    public static boolean pointerPressed(double pointerX, double pointerY, int button) {
        if (button != 0 || STATE.presentationMode() != ChatPresentationMode.OPEN_PANEL
                || pointerOperation != PointerOperation.NONE) {
            return false;
        }
        ChatPanelGeometry geometry = STATE.panelGeometry();
        int edges = geometry.resizeEdgesAt(pointerX, pointerY);
        if (edges != ChatPanelGeometry.EDGE_NONE) {
            if (!ChatGestureArena.tryCapture(
                    ChatGestureArena.Owner.PANEL,
                    ChatSurfaceController::cancelPointerOperation)) {
                return false;
            }
            beginPointerOperation(PointerOperation.RESIZE, geometry, pointerX, pointerY, edges);
            STATE.setOverlay(ChatSurfaceState.Overlay.NONE);
            STATE.setFocusOwner(ChatSurfaceState.FocusOwner.TIMELINE);
            return true;
        }
        if (isOverTimelineScrollbar(pointerX, pointerY)) {
            return false;
        }
        if (geometry.headerBounds().contains((int) Math.round(pointerX), (int) Math.round(pointerY))) {
            if (!ChatGestureArena.tryCapture(
                    ChatGestureArena.Owner.PANEL,
                    ChatSurfaceController::cancelPointerOperation)) {
                return false;
            }
            beginPointerOperation(PointerOperation.MOVE, geometry, pointerX, pointerY, ChatPanelGeometry.EDGE_NONE);
            STATE.setOverlay(ChatSurfaceState.Overlay.NONE);
            STATE.setFocusOwner(ChatSurfaceState.FocusOwner.TIMELINE);
            return true;
        }
        return false;
    }

    public static boolean pointerDragged(double pointerX, double pointerY) {
        if (pointerOperation == PointerOperation.NONE || pointerStartGeometry == null) {
            return false;
        }
        int deltaX = (int) Math.round(pointerX) - pointerStartX;
        int deltaY = (int) Math.round(pointerY) - pointerStartY;
        ChatPanelGeometry nextGeometry = switch (pointerOperation) {
            case MOVE -> pointerStartGeometry.moveBy(
                    deltaX,
                    deltaY,
                    STATE.screenWidth(),
                    STATE.screenHeight(),
                    STATE.panelBottomInset());
            case RESIZE -> pointerStartGeometry.resizeFrom(
                    resizeEdges,
                    deltaX,
                    deltaY,
                    STATE.screenWidth(),
                    STATE.screenHeight(),
                    STATE.panelBottomInset());
            case NONE -> pointerStartGeometry;
        };
        STATE.setPanelGeometry(nextGeometry);
        return true;
    }

    public static boolean pointerReleased(int button) {
        if (button != 0 || pointerOperation == PointerOperation.NONE) {
            return false;
        }
        boolean resizedHeight = pointerOperation == PointerOperation.RESIZE
                && (resizeEdges & (ChatPanelGeometry.EDGE_TOP | ChatPanelGeometry.EDGE_BOTTOM)) != 0;
        clearPointerOperation();
        STATE.setFocusOwner(ChatSurfaceState.FocusOwner.COMPOSER);
        persistGeometry(resizedHeight);
        return true;
    }

    public static boolean hasPointerCapture() {
        return pointerOperation != PointerOperation.NONE;
    }

    public static boolean isOverTimelineScrollbar(double pointerX, double pointerY) {
        if (STATE.presentationMode() != ChatPresentationMode.OPEN_PANEL) {
            return false;
        }
        RichChatBounds viewport = STATE.frame().messageViewportBounds();
        if (STATE.panelGeometry().resizeEdgesAt(pointerX, pointerY) != ChatPanelGeometry.EDGE_NONE) {
            return false;
        }
        int left = Math.max(viewport.left(), viewport.right() - 8);
        return pointerX >= left && pointerX <= viewport.right()
                && pointerY >= viewport.top() && pointerY <= viewport.bottom();
    }

    public static RichChatBounds messageViewportBounds() {
        return STATE.frame().messageViewportBounds();
    }

    public static void previewPanelGeometry(
            ChatUpgradeConfig config,
            int screenWidth,
            int screenHeight) {
        if (config == null || config.chatPanel == null) {
            return;
        }
        previewGeometryConfig = config;
        geometryConfigSource = null;
        ensureGeometryLoaded(config, screenWidth, screenHeight);
        applyGeometryConstraints(config, screenWidth, screenHeight);
    }

    public static void finishPanelGeometryPreview() {
        previewGeometryConfig = null;
        geometryConfigSource = null;
    }

    public static void setOverlay(ChatSurfaceState.Overlay overlay) {
        STATE.setOverlay(overlay);
    }

    public static void setFocusOwner(ChatSurfaceState.FocusOwner focusOwner) {
        STATE.setFocusOwner(focusOwner);
    }

    private static ChatUpgradeConfig activeGeometryConfig() {
        ChatUpgradeConfig preview = previewGeometryConfig;
        return preview == null ? ChatUpgradeConfig.get() : preview;
    }

    private static void ensureGeometryLoaded(
            ChatUpgradeConfig config,
            int screenWidth,
            int screenHeight) {
        ChatUpgradeConfig source = config == null ? ChatUpgradeConfig.get() : config;
        if (source.chatPanel == null || geometryConfigSource == source) {
            return;
        }
        STATE.updateScreenSize(screenWidth, screenHeight);
        int bottomInset = requiredBottomInset(source, screenHeight);
        ChatPanelGeometry restored = ChatPanelGeometry.restore(
                screenWidth,
                screenHeight,
                source.chatPanel.left,
                source.chatPanel.bottomOffset,
                source.chatPanel.width,
                source.chatPanel.height,
                source.chatPanel.usesAutomaticHeight(),
                bottomInset);
        STATE.setPanelGeometry(restored, bottomInset);
        geometryConfigSource = source;
    }

    private static void applyGeometryConstraints(
            ChatUpgradeConfig config,
            int screenWidth,
            int screenHeight) {
        if (config == null
                || config.chatPanel == null
                || pointerOperation != PointerOperation.NONE) {
            return;
        }
        int bottomInset = requiredBottomInset(config, screenHeight);
        ChatPanelGeometry restored = ChatPanelGeometry.restore(
                screenWidth,
                screenHeight,
                config.chatPanel.left,
                config.chatPanel.bottomOffset,
                config.chatPanel.width,
                config.chatPanel.height,
                config.chatPanel.usesAutomaticHeight(),
                bottomInset);
        STATE.setPanelGeometry(restored, bottomInset);
    }

    private static int requiredBottomInset(ChatUpgradeConfig config, int screenHeight) {
        ChatUpgradeConfig.AppearanceConfig appearance = config == null || config.appearance == null
                ? ChatUpgradeConfig.defaultAppearance()
                : config.appearance;
        return automaticBottomOffset(appearance.vanillaStyleInput, screenHeight);
    }

    private static int automaticBottomOffset(boolean vanillaStyleInput, int screenHeight) {
        if (!vanillaStyleInput) {
            return ChatPanelGeometry.MERGED_BOTTOM_OFFSET;
        }
        if (vanillaComposerTop < 0 || vanillaComposerTop > screenHeight) {
            return ChatPanelGeometry.DEFAULT_BOTTOM_OFFSET;
        }
        return Math.max(
                ChatPanelGeometry.DEFAULT_BOTTOM_OFFSET,
                screenHeight - vanillaComposerTop + ChatPanelGeometry.SCREEN_MARGIN);
    }

    private static void beginPointerOperation(
            PointerOperation operation,
            ChatPanelGeometry geometry,
            double pointerX,
            double pointerY,
            int edges) {
        pointerOperation = operation;
        pointerStartGeometry = geometry;
        pointerStartX = (int) Math.round(pointerX);
        pointerStartY = (int) Math.round(pointerY);
        resizeEdges = edges;
    }

    private static void cancelPointerOperation() {
        if (pointerOperation == PointerOperation.NONE) {
            return;
        }
        boolean resizedHeight = pointerOperation == PointerOperation.RESIZE
                && (resizeEdges & (ChatPanelGeometry.EDGE_TOP | ChatPanelGeometry.EDGE_BOTTOM)) != 0;
        clearPointerOperation();
        STATE.setFocusOwner(ChatSurfaceState.FocusOwner.COMPOSER);
        persistGeometry(resizedHeight);
    }

    private static void clearPointerOperation() {
        pointerOperation = PointerOperation.NONE;
        pointerStartGeometry = null;
        resizeEdges = ChatPanelGeometry.EDGE_NONE;
        ChatGestureArena.release(ChatGestureArena.Owner.PANEL);
    }

    private static void persistGeometry() {
        persistGeometry(false);
    }

    private static void persistGeometry(boolean disableAutomaticHeight) {
        if (previewGeometryConfig != null) {
            return;
        }
        ChatPanelGeometry geometry = STATE.panelGeometry();
        ChatUpgradeConfig.ChatPanelConfig panel = ChatUpgradeConfig.get().chatPanel;
        boolean automaticHeight = panel != null
                && panel.usesAutomaticHeight()
                && !disableAutomaticHeight;
        try {
            ChatUpgradeConfig.setChatPanelGeometryAndSave(
                    geometry.x(),
                    geometry.bottomOffset(STATE.screenHeight()),
                    geometry.width(),
                    geometry.height(),
                    automaticHeight);
            geometryConfigSource = ChatUpgradeConfig.get();
        } catch (IOException e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to persist chat panel geometry: {}", e.getMessage());
        }
    }
}