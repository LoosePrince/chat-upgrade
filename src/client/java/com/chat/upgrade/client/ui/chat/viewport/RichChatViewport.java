package com.chat.upgrade.client.ui.chat.viewport;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RichChatViewport {
    private static final Set<String> INVALIDATED_MEDIA_URLS = ConcurrentHashMap.newKeySet();
    private static final AtomicLong INVALIDATION_VERSION = new AtomicLong();

    private RichChatViewport() {
    }

    public static void invalidateAll() {
        INVALIDATED_MEDIA_URLS.clear();
        INVALIDATION_VERSION.incrementAndGet();
    }

    public static void invalidateMedia(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        INVALIDATED_MEDIA_URLS.add(url);
        INVALIDATION_VERSION.incrementAndGet();
    }

    public static long invalidationVersion() {
        return INVALIDATION_VERSION.get();
    }

    public static Set<String> invalidatedMediaSnapshot() {
        return Set.copyOf(INVALIDATED_MEDIA_URLS);
    }

    public static void clearInvalidatedMedia() {
        INVALIDATED_MEDIA_URLS.clear();
    }
}