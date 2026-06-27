package com.chat.upgrade.client.emoji;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.media.MediaFetchSupport;
import com.chat.upgrade.platform.Platform;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class EmojiImageCache {
    private static final String LOADER_PREFIX = "chatupgrade-emoji://";
    private static final ConcurrentHashMap<String, String> LOADER_TO_SOURCE = new ConcurrentHashMap<>();

    private EmojiImageCache() {
    }

    public record CachedPayload(byte[] body, @Nullable String contentType, @Nullable String md5Hex) {
    }

    public static String loaderUrlFor(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return "";
        }
        String loaderUrl = LOADER_PREFIX + sha256Hex(sourceUrl);
        LOADER_TO_SOURCE.put(loaderUrl, sourceUrl);
        return loaderUrl;
    }

    public static boolean isEmojiLoaderUrl(String url) {
        return url != null && url.startsWith(LOADER_PREFIX);
    }

    public static Optional<CachedPayload> readOrDownload(String loaderUrl, int maxBytes)
            throws IOException, MediaFetchSupport.ResponseBodyTooLarge {
        if (!isEmojiLoaderUrl(loaderUrl)) {
            return Optional.empty();
        }
        String hash = hashFromLoaderUrl(loaderUrl);
        Optional<CachedPayload> local = readLocal(hash, maxBytes);
        if (local.isPresent()) {
            return local;
        }
        String sourceUrl = sourceUrlFor(loaderUrl, hash);
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }
        HttpResponse<InputStream> response = MediaFetchSupport.sendGet(sourceUrl, 15, "emoji image");
        if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
            return Optional.empty();
        }
        MediaFetchSupport.FetchPayload payload = MediaFetchSupport.readPayload(response, maxBytes);
        writeLocal(hash, sourceUrl, payload);
        return Optional.of(new CachedPayload(payload.body(), payload.contentType(), payload.md5Hex()));
    }

    public static @Nullable String originalUrlFor(String loaderUrl) {
        if (!isEmojiLoaderUrl(loaderUrl)) {
            return loaderUrl;
        }
        return sourceUrlFor(loaderUrl, hashFromLoaderUrl(loaderUrl));
    }

    private static Optional<CachedPayload> readLocal(String hash, int maxBytes)
            throws IOException, MediaFetchSupport.ResponseBodyTooLarge {
        Path bodyPath = bodyPath(hash);
        if (!Files.isRegularFile(bodyPath)) {
            return Optional.empty();
        }
        long size = Files.size(bodyPath);
        if (size > maxBytes) {
            throw new MediaFetchSupport.ResponseBodyTooLarge();
        }
        byte[] body = Files.readAllBytes(bodyPath);
        JsonObject meta = readMeta(hash);
        String contentType = meta == null ? null : stringField(meta, "contentType", null);
        String md5Hex = meta == null ? null : stringField(meta, "md5Hex", null);
        return Optional.of(new CachedPayload(body, contentType, md5Hex));
    }

    private static void writeLocal(String hash, String sourceUrl, MediaFetchSupport.FetchPayload payload) {
        try {
            Files.createDirectories(imageDir());
            Files.write(bodyPath(hash), payload.body());
            JsonObject meta = new JsonObject();
            meta.addProperty("iconUrl", sourceUrl);
            meta.addProperty("contentType", payload.contentType() == null ? "unknown" : payload.contentType());
            meta.addProperty("md5Hex", payload.md5Hex() == null ? "" : payload.md5Hex());
            meta.addProperty("byteLength", payload.body().length);
            meta.addProperty("fetchedAtMs", System.currentTimeMillis());
            Files.writeString(metaPath(hash), meta.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to write emoji image cache {}: {}", hash, e.getMessage());
        }
    }

    private static @Nullable String sourceUrlFor(String loaderUrl, String hash) {
        String mapped = LOADER_TO_SOURCE.get(loaderUrl);
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }
        JsonObject meta = readMeta(hash);
        if (meta == null) {
            return null;
        }
        String sourceUrl = stringField(meta, "iconUrl", null);
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            LOADER_TO_SOURCE.put(loaderUrl, sourceUrl);
        }
        return sourceUrl;
    }

    private static @Nullable JsonObject readMeta(String hash) {
        Path path = metaPath(hash);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Path imageDir() {
        return Platform.configDir().resolve("chat-upgrade").resolve("emoji").resolve("images");
    }

    private static Path bodyPath(String hash) {
        return imageDir().resolve(hash + ".bin");
    }

    private static Path metaPath(String hash) {
        return imageDir().resolve(hash + ".json");
    }

    private static String hashFromLoaderUrl(String loaderUrl) {
        return loaderUrl.substring(LOADER_PREFIX.length());
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static @Nullable String stringField(JsonObject object, String name, @Nullable String fallback) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            String value = element.getAsString();
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}