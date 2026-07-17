package com.chat.upgrade.net;

import org.jetbrains.annotations.Nullable;

public record StructuredChatAuthor(
        String playerUuid,
        String displayName,
        String teamName,
        String teamPrefix,
        String teamSuffix,
        int teamColorRgb) {
    public static final int NO_TEAM_COLOR = -1;

    public StructuredChatAuthor {
        playerUuid = safe(playerUuid);
        displayName = safe(displayName);
        teamName = safe(teamName);
        teamPrefix = safe(teamPrefix);
        teamSuffix = safe(teamSuffix);
        teamColorRgb = teamColorRgb < 0 ? NO_TEAM_COLOR : teamColorRgb & 0xFFFFFF;
    }

    public static StructuredChatAuthor legacy(@Nullable String displayName) {
        return new StructuredChatAuthor("", displayName, "", "", "", NO_TEAM_COLOR);
    }

    public boolean isPlayer() {
        return !playerUuid.isBlank();
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}