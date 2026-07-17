package com.chat.upgrade.client.ui.chat.state;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;

public record ChatAuthor(
        @Nullable UUID playerId,
        Component displayName,
        String fallbackName,
        ChatTeamSnapshot team,
        boolean localPlayer) {
    public ChatAuthor {
        fallbackName = safe(fallbackName);
        displayName = displayName == null
                ? Component.literal(fallbackName.isBlank() ? "?" : fallbackName)
                : displayName;
        team = team == null ? new ChatTeamSnapshot("", "", "", ChatTeamSnapshot.NO_COLOR) : team;
    }

    public static ChatAuthor legacy(@Nullable String senderName) {
        String name = safe(senderName);
        return new ChatAuthor(null, Component.literal(name.isBlank() ? "?" : name), name,
                new ChatTeamSnapshot("", "", "", ChatTeamSnapshot.NO_COLOR), false);
    }

    public static ChatAuthor system() {
        return new ChatAuthor(null, Component.empty(), "",
                new ChatTeamSnapshot("", "", "", ChatTeamSnapshot.NO_COLOR), false);
    }

    public String searchableName() {
        String styled = displayName.getString();
        return styled.isBlank() ? fallbackName : styled;
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}