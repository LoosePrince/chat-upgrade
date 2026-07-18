package com.chat.upgrade.client.ui.chat.input;

import java.util.function.Consumer;

/**
 * Keeps command execution on the vanilla command pipeline while the takeover
 * surface owns the button, focus and hit testing around it.
 */
public final class ChatCommandBridge {
    private ChatCommandBridge() {
    }

    public static boolean isCommand(String value) {
        return value != null && value.trim().startsWith("/");
    }

    public static boolean execute(String value, Consumer<String> vanillaHandler) {
        if (!isCommand(value) || vanillaHandler == null) {
            return false;
        }
        String command = value.trim();
        if (command.length() <= 1) {
            return false;
        }
        vanillaHandler.accept(value);
        return true;
    }
}