package com.chat.upgrade.client.emoji;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

public record EmojiCatalog(
        String sourceUrl,
        long updatedAtMs,
        List<Group> groups) {
    public EmojiCatalog {
        sourceUrl = sourceUrl == null ? "" : sourceUrl;
        groups = List.copyOf(Objects.requireNonNullElse(groups, List.of()));
    }

    public static EmojiCatalog empty() {
        return new EmojiCatalog("", 0L, List.of());
    }

    public boolean isEmpty() {
        return groups.isEmpty() || itemCount() == 0;
    }

    public int itemCount() {
        int count = 0;
        for (Group group : groups) {
            count += group.items().size();
        }
        return count;
    }

    public @Nullable Item itemByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        for (Group group : groups) {
            for (Item item : group.items()) {
                if (token.equals(item.token())) {
                    return item;
                }
            }
        }
        return null;
    }

    public record Group(String id, String name, List<Item> items) {
        public Group {
            id = normalize(id, "group");
            name = normalize(name, id);
            items = List.copyOf(Objects.requireNonNullElse(items, List.of()));
        }
    }

    public record Item(String token, String iconUrl, String loaderUrl) {
        public Item {
            token = normalize(token, "");
            iconUrl = normalize(iconUrl, "");
            loaderUrl = normalize(loaderUrl, iconUrl);
        }
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return value;
    }
}