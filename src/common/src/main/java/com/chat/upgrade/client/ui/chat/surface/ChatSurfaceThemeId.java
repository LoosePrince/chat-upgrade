package com.chat.upgrade.client.ui.chat.surface;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

public enum ChatSurfaceThemeId {
    MODERN_BUBBLE("modern_bubble"),
    COMPACT_FEED("compact_feed"),
    NATIVE_ENHANCED("native_enhanced");

    public static final ChatSurfaceThemeId DEFAULT = MODERN_BUBBLE;

    private final String serializedName;

    ChatSurfaceThemeId(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static ChatSurfaceThemeId parse(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (ChatSurfaceThemeId candidate : values()) {
            if (candidate.serializedName.equals(normalized)) {
                return candidate;
            }
        }
        return DEFAULT;
    }
}