package com.chat.upgrade.client.ui.chat.surface;

public record ChatTheme(
        ChatSurfaceThemeId id,
        ChatThemeTokens tokens,
        ChatLayoutPolicy layout) {
    public ChatTheme {
        id = id == null ? ChatSurfaceThemeId.DEFAULT : id;
        if (tokens == null || layout == null) {
            throw new IllegalArgumentException("theme tokens and layout policy must not be null");
        }
    }
}