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
    private static PointerOperation pointerOperation = PointerOperation.NONE;
    private static ChatPanelGeometry pointerStartGeometry;
    private static int pointerStartX;
    private static int pointerStartY;
    private static int resizeEdges;

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
        ensureGeometryLoaded(screenWidth, screenHeight);
        STATE.setTheme(ChatThemes.resolve(ChatUpgradeConfig.get().chatTheme));
        boolean normalized = STATE.updateScreenSize(screenWidth, screenHeight);
        STATE.setPresentationMode(chatOpen ? ChatPresentationMode.OPEN_PANEL : ChatPresentationMode.CLOSED_HUD);
        STATE.setRestricted(restricted);
        if (normalized && pointerOperation == PointerOperation.NONE) {
            persistGeometry();
        }
        return STATE.frame();
    }

    public static void onChatScreenOpened(int screenWidth, int screenHeight) {
        ensureGeometryLoaded(screenWidth, screenHeight);
        STATE.setTheme(ChatThemes.resolve(ChatUpgradeConfig.get().chatTheme));
        boolean normalized = STATE.updateScreenSize(screenWidth, screenHeight);
        STATE.setPresentationMode(ChatPresentationMode.OPEN_PANEL);
        STATE.setFocusOwner(ChatSurfaceState.FocusOwner.COMPOSER);
        if (normalized) {
            persistGeometry();
        }
    }

    public static void onChatScreenClosed() {
        boolean changed = pointerOperation != PointerOperation.NONE;
        clearPointerOperation();
        ChatGestureArena.resetPointerState();
        STATE.setOverlay(ChatSurfaceState.Overlay.NONE);
        STATE.setFocusOwner(ChatSurfaceState.FocusOwner.NONE);
        STATE.setPresentationMode(ChatPresentationMode.CLOSED_HUD);
        if (changed) {
            persistGeometry();
        }
    }

    public static ChatPanelGeometry panelGeometry(int screenWidth, int screenHeight) {
        ensureGeometryLoaded(screenWidth, screenHeight);
        if (STATE.updateScreenSize(screenWidth, screenHeight) && pointerOperation == PointerOperation.NONE) {
            persistGeometry();
        }
        return STATE.panelGeometry();
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
                    STATE.screenHeight());
            case RESIZE -> pointerStartGeometry.resizeFrom(
                    resizeEdges,
                    deltaX,
                    deltaY,
                    STATE.screenWidth(),
                    STATE.screenHeight());
            case NONE -> pointerStartGeometry;
        };
        STATE.setPanelGeometry(nextGeometry);
        return true;
    }

    public static boolean pointerReleased(int button) {
        if (button != 0 || pointerOperation == PointerOperation.NONE) {
            return false;
        }
        clearPointerOperation();
        STATE.setFocusOwner(ChatSurfaceState.FocusOwner.COMPOSER);
        persistGeometry();
        return true;
    }

    public static boolean hasPointerCapture() {
        return pointerOperation != PointerOperation.NONE;
    }

    public static boolean isOverTimelineScrollbar(double pointerX, double pointerY) {
        if (STATE.presentationMode() != ChatPresentationMode.OPEN_PANEL) {
            return false;
        }
        RichChatBounds viewport = STATE.panelGeometry().messageViewportBounds();
        if (STATE.panelGeometry().resizeEdgesAt(pointerX, pointerY) != ChatPanelGeometry.EDGE_NONE) {
            return false;
        }
        int left = Math.max(viewport.left(), viewport.right() - 8);
        return pointerX >= left && pointerX <= viewport.right()
                && pointerY >= viewport.top() && pointerY <= viewport.bottom();
    }

    public static RichChatBounds messageViewportBounds() {
        return STATE.panelGeometry().messageViewportBounds();
    }

    public static void setOverlay(ChatSurfaceState.Overlay overlay) {
        STATE.setOverlay(overlay);
    }

    public static void setFocusOwner(ChatSurfaceState.FocusOwner focusOwner) {
        STATE.setFocusOwner(focusOwner);
    }

    private static void ensureGeometryLoaded(int screenWidth, int screenHeight) {
        ChatUpgradeConfig config = ChatUpgradeConfig.get();
        if (geometryConfigSource == config) {
            return;
        }
        STATE.updateScreenSize(screenWidth, screenHeight);
        STATE.setPanelGeometry(ChatPanelGeometry.restore(
                screenWidth,
                screenHeight,
                config.chatPanel.left,
                config.chatPanel.bottomOffset,
                config.chatPanel.width,
                config.chatPanel.height));
        geometryConfigSource = config;
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
        clearPointerOperation();
        STATE.setFocusOwner(ChatSurfaceState.FocusOwner.COMPOSER);
        persistGeometry();
    }

    private static void clearPointerOperation() {
        pointerOperation = PointerOperation.NONE;
        pointerStartGeometry = null;
        resizeEdges = ChatPanelGeometry.EDGE_NONE;
        ChatGestureArena.release(ChatGestureArena.Owner.PANEL);
    }

    private static void persistGeometry() {
        ChatPanelGeometry geometry = STATE.panelGeometry();
        try {
            ChatUpgradeConfig.setChatPanelGeometryAndSave(
                    geometry.x(),
                    geometry.bottomOffset(STATE.screenHeight()),
                    geometry.width(),
                    geometry.height());
            geometryConfigSource = ChatUpgradeConfig.get();
        } catch (IOException e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to persist chat panel geometry: {}", e.getMessage());
        }
    }
}