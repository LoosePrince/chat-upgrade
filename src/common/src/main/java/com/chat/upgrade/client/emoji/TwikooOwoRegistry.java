package com.chat.upgrade.client.emoji;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.ChatUpgrade;

public final class TwikooOwoRegistry {
    private static final long REFRESH_THROTTLE_MS = TimeUnit.MINUTES.toMillis(10);

    private static final AtomicReference<EmojiCatalog> CATALOG = new AtomicReference<>(EmojiCatalog.empty());
    private static volatile boolean localCacheLoaded = false;
    private static volatile long lastRefreshAttemptAtMs = 0L;
    private static volatile CompletableFuture<Void> inFlightRefresh;

    private TwikooOwoRegistry() {
    }

    public static @Nullable String resolveIconByToken(String token) {
        EmojiCatalog.Item item = catalog().itemByToken(token);
        return item == null ? null : item.loaderUrl();
    }

    public static EmojiCatalog catalog() {
        ensureLocalCacheLoaded();
        refreshIfExpired();
        return CATALOG.get();
    }

    public static void refreshIfExpired() {
        ensureLocalCacheLoaded();
        long now = System.currentTimeMillis();
        if (now - lastRefreshAttemptAtMs <= REFRESH_THROTTLE_MS) {
            return;
        }
        refreshAsync();
    }

    public static void refreshAsync() {
        CompletableFuture<Void> existing = inFlightRefresh;
        if (existing != null && !existing.isDone()) {
            return;
        }
        synchronized (TwikooOwoRegistry.class) {
            existing = inFlightRefresh;
            if (existing != null && !existing.isDone()) {
                return;
            }
            lastRefreshAttemptAtMs = System.currentTimeMillis();
            inFlightRefresh = CompletableFuture.runAsync(TwikooOwoRegistry::doRefresh)
                    .whenComplete((v, t) -> {
                        synchronized (TwikooOwoRegistry.class) {
                            inFlightRefresh = null;
                        }
                    });
        }
    }

    public static void clearRuntimeState() {
        CATALOG.set(EmojiCatalog.empty());
        localCacheLoaded = false;
        lastRefreshAttemptAtMs = 0L;
        synchronized (TwikooOwoRegistry.class) {
            inFlightRefresh = null;
        }
    }

    private static void ensureLocalCacheLoaded() {
        if (localCacheLoaded) {
            return;
        }
        synchronized (TwikooOwoRegistry.class) {
            if (localCacheLoaded) {
                return;
            }
            EmojiCatalog cached = EmojiCatalogStore.loadCached();
            if (!cached.isEmpty()) {
                CATALOG.set(cached);
            }
            localCacheLoaded = true;
        }
    }

    private static void doRefresh() {
        EmojiCatalog online = EmojiCatalogStore.fetchOnline();
        if (online.isEmpty()) {
            return;
        }
        CATALOG.set(online);
        EmojiCatalogStore.saveCached(online);
        ChatUpgrade.LOGGER.info("chat-upgrade: loaded emoji catalog groups={} items={}",
                online.groups().size(), online.itemCount());
    }
}