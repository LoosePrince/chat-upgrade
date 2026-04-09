package com.chat.upgrade.client.emoji;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.media.MediaFetchSupport;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class TwikooOwoRegistry {
    private static final String OWO_JSON_URL = "https://looseprince.github.io/Twikoo-Magic/owo.json";
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(10);

    private static final AtomicReference<Map<String, String>> TOKEN_TO_ICON = new AtomicReference<>(Map.of());
    private static volatile long lastRefreshAtMs = 0L;
    private static volatile CompletableFuture<Void> inFlightRefresh;

    private TwikooOwoRegistry() {
    }

    public static String resolveIconByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        refreshIfExpired();
        return TOKEN_TO_ICON.get().get(token);
    }

    public static void refreshIfExpired() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshAtMs <= CACHE_TTL_MS && !TOKEN_TO_ICON.get().isEmpty()) {
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
            inFlightRefresh = CompletableFuture.runAsync(TwikooOwoRegistry::doRefresh)
                    .whenComplete((v, t) -> {
                        synchronized (TwikooOwoRegistry.class) {
                            inFlightRefresh = null;
                        }
                    });
        }
    }

    public static void clearRuntimeState() {
        TOKEN_TO_ICON.set(Map.of());
        lastRefreshAtMs = 0L;
        synchronized (TwikooOwoRegistry.class) {
            inFlightRefresh = null;
        }
    }

    private static void doRefresh() {
        var response = MediaFetchSupport.sendGet(OWO_JSON_URL, 15, "application/json");
        if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
            return;
        }
        try {
            MediaFetchSupport.FetchPayload payload = MediaFetchSupport.readPayload(response, 2 * 1024 * 1024);
            String json = new String(payload.body(), java.nio.charset.StandardCharsets.UTF_8);
            Map<String, String> parsed = parseMapping(json);
            if (!parsed.isEmpty()) {
                TOKEN_TO_ICON.set(Map.copyOf(parsed));
                lastRefreshAtMs = System.currentTimeMillis();
                ChatUpgrade.LOGGER.info("chat-upgrade: loaded owo mapping size={}", parsed.size());
            }
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to refresh owo mapping: {}", e.getMessage());
        }
    }

    private static Map<String, String> parseMapping(String json) {
        Map<String, String> out = new HashMap<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (Map.Entry<String, JsonElement> groupEntry : root.entrySet()) {
            JsonObject group = groupEntry.getValue().getAsJsonObject();
            JsonElement containerElement = group.get("container");
            if (containerElement == null || !containerElement.isJsonArray()) {
                continue;
            }
            for (JsonElement itemElement : containerElement.getAsJsonArray()) {
                if (!itemElement.isJsonObject()) {
                    continue;
                }
                JsonObject item = itemElement.getAsJsonObject();
                JsonElement textElement = item.get("text");
                JsonElement iconElement = item.get("icon");
                if (textElement == null || iconElement == null) {
                    continue;
                }
                String text = textElement.getAsString();
                String icon = iconElement.getAsString();
                if (!text.isBlank() && !icon.isBlank()) {
                    out.put(text, icon);
                }
            }
        }
        return out;
    }
}
