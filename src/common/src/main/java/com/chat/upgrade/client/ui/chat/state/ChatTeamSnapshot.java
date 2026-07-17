package com.chat.upgrade.client.ui.chat.state;

import org.jetbrains.annotations.Nullable;

public record ChatTeamSnapshot(
        String teamName,
        String prefix,
        String suffix,
        int colorRgb) {
    public static final int NO_COLOR = -1;

    public ChatTeamSnapshot {
        teamName = safe(teamName);
        prefix = safe(prefix);
        suffix = safe(suffix);
        colorRgb = colorRgb < 0 ? NO_COLOR : colorRgb & 0xFFFFFF;
    }

    public boolean present() {
        return !teamName.isBlank() || !prefix.isBlank() || !suffix.isBlank() || colorRgb >= 0;
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }
}