package com.chat.upgrade.client.media.audio;

import org.lwjgl.glfw.GLFW;

/** Normalizes persisted GLFW keys and provides stable labels for UI hints. */
public final class VoiceShortcutKey {
    public static final int UNBOUND = -1;

    private VoiceShortcutKey() {
    }

    public static boolean isBindable(int key) {
        return key >= GLFW.GLFW_KEY_SPACE
                && key <= GLFW.GLFW_KEY_MENU
                && key != GLFW.GLFW_KEY_LEFT_SHIFT
                && key != GLFW.GLFW_KEY_RIGHT_SHIFT
                && key != GLFW.GLFW_KEY_LEFT_CONTROL
                && key != GLFW.GLFW_KEY_RIGHT_CONTROL
                && key != GLFW.GLFW_KEY_LEFT_ALT
                && key != GLFW.GLFW_KEY_RIGHT_ALT
                && key != GLFW.GLFW_KEY_LEFT_SUPER
                && key != GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    public static String label(int key) {
        String named = switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_ESCAPE -> "Esc";
            case GLFW.GLFW_KEY_UP -> "↑";
            case GLFW.GLFW_KEY_DOWN -> "↓";
            case GLFW.GLFW_KEY_LEFT -> "←";
            case GLFW.GLFW_KEY_RIGHT -> "→";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_PAGE_UP -> "Page Up";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "Page Down";
            default -> null;
        };
        if (named != null) {
            return named;
        }
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }
        String glfwName = GLFW.glfwGetKeyName(key, 0);
        if (glfwName != null && !glfwName.isBlank()) {
            return glfwName.toUpperCase(java.util.Locale.ROOT);
        }
        return "Key " + key;
    }
}