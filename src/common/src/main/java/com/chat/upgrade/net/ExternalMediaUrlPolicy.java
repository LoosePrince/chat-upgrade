package com.chat.upgrade.net;

import java.net.URI;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

public final class ExternalMediaUrlPolicy {
    private ExternalMediaUrlPolicy() {
    }

    public static Optional<URI> parseHttps(@Nullable String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank() || rawUrl.length() > StructuredChatProtocolLimits.MAX_URL_CHARS) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(rawUrl).normalize();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getHost().endsWith(".")) {
                return Optional.empty();
            }
            int port = uri.getPort();
            if (port != -1 && port != 443) {
                return Optional.empty();
            }
            return Optional.of(uri);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static boolean isAllowed(@Nullable String rawUrl) {
        return parseHttps(rawUrl).isPresent();
    }
}