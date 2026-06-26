package com.chat.upgrade.net;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

public final class ServerMediaUrl {
    private static final String PREFIX = "chat-upgrade://media/";

    private ServerMediaUrl() {
    }

    public static String format(String mediaId, @Nullable String typeWire) {
        String safeMediaId = mediaId == null ? "" : mediaId.trim();
        String safeType = (typeWire == null || typeWire.isBlank()) ? "image" : typeWire.trim().toLowerCase();
        return PREFIX + safeType + "/" + safeMediaId;
    }

    public static boolean isServerMediaUrl(@Nullable String url) {
        return parse(url).isPresent();
    }

    public static Optional<Parsed> parse(@Nullable String url) {
        if (url == null || url.isBlank() || !url.startsWith(PREFIX)) {
            return Optional.empty();
        }

        String suffix = url.substring(PREFIX.length()).trim();
        if (suffix.isEmpty()) {
            return Optional.empty();
        }

        int slash = suffix.indexOf('/');
        if (slash <= 0 || slash == suffix.length() - 1) {
            return Optional.empty();
        }

        String typeWire = suffix.substring(0, slash).trim().toLowerCase();
        String mediaId = suffix.substring(slash + 1).trim();
        if (typeWire.isBlank() || mediaId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Parsed(mediaId, typeWire));
    }

    public record Parsed(String mediaId, String typeWire) {
    }
}
