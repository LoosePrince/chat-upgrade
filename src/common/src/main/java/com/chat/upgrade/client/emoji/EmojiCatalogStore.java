package com.chat.upgrade.client.emoji;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.media.MediaFetchSupport;
import com.chat.upgrade.platform.Platform;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class EmojiCatalogStore {
    public static final String OWO_JSON_URL = "https://looseprince.github.io/Twikoo-Magic/owo.json";
    private static final int MAX_CATALOG_BYTES = 2 * 1024 * 1024;

    private EmojiCatalogStore() {
    }

    public static Path catalogPath() {
        return Platform.configDir().resolve("chat-upgrade").resolve("emoji").resolve("catalog.json");
    }

    public static EmojiCatalog loadCached() {
        Path path = catalogPath();
        if (!Files.isRegularFile(path)) {
            return EmojiCatalog.empty();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            EmojiCatalog catalog = parseCachedCatalog(json);
            if (!catalog.isEmpty()) {
                ChatUpgrade.LOGGER.info("chat-upgrade: loaded emoji catalog cache groups={} items={}",
                        catalog.groups().size(), catalog.itemCount());
            }
            return catalog;
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to load emoji catalog cache: {}", e.getMessage());
            return EmojiCatalog.empty();
        }
    }

    public static EmojiCatalog fetchOnline() {
        HttpResponse<java.io.InputStream> response = MediaFetchSupport.sendGet(OWO_JSON_URL, 15, "emoji catalog");
        if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
            return EmojiCatalog.empty();
        }
        try {
            MediaFetchSupport.FetchPayload payload = MediaFetchSupport.readPayload(response, MAX_CATALOG_BYTES);
            String json = new String(payload.body(), StandardCharsets.UTF_8);
            return parseOwoCatalog(json, System.currentTimeMillis());
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to parse online emoji catalog: {}", e.getMessage());
            return EmojiCatalog.empty();
        }
    }

    public static void saveCached(EmojiCatalog catalog) {
        if (catalog == null || catalog.isEmpty()) {
            return;
        }
        Path path = catalogPath();
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.writeString(temp, toJson(catalog), StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailed) {
            try {
                Files.writeString(path, toJson(catalog), StandardCharsets.UTF_8);
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("chat-upgrade: failed to save emoji catalog cache: {}", e.getMessage());
            }
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to save emoji catalog cache: {}", e.getMessage());
        }
    }

    public static EmojiCatalog parseOwoCatalog(String json, long updatedAtMs) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<EmojiCatalog.Group> groups = new ArrayList<>();
        for (var groupEntry : root.entrySet()) {
            if (!groupEntry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject groupObject = groupEntry.getValue().getAsJsonObject();
            JsonElement containerElement = groupObject.get("container");
            if (containerElement == null || !containerElement.isJsonArray()) {
                continue;
            }
            List<EmojiCatalog.Item> items = new ArrayList<>();
            for (JsonElement itemElement : containerElement.getAsJsonArray()) {
                if (!itemElement.isJsonObject()) {
                    continue;
                }
                JsonObject itemObject = itemElement.getAsJsonObject();
                String token = stringField(itemObject, "text", "");
                String icon = stringField(itemObject, "icon", "");
                if (token.isBlank() || icon.isBlank()) {
                    continue;
                }
                items.add(new EmojiCatalog.Item(token, icon, EmojiImageCache.loaderUrlFor(icon)));
            }
            if (!items.isEmpty()) {
                String groupId = groupEntry.getKey();
                String groupName = firstStringField(groupObject, groupId, "name", "label", "title");
                groups.add(new EmojiCatalog.Group(groupId, groupName, items));
            }
        }
        return new EmojiCatalog(OWO_JSON_URL, updatedAtMs, groups);
    }

    private static EmojiCatalog parseCachedCatalog(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String sourceUrl = stringField(root, "sourceUrl", OWO_JSON_URL);
        long updatedAtMs = longField(root, "updatedAtMs", 0L);
        JsonElement groupsElement = root.get("groups");
        if (groupsElement == null || !groupsElement.isJsonArray()) {
            return EmojiCatalog.empty();
        }
        List<EmojiCatalog.Group> groups = new ArrayList<>();
        for (JsonElement groupElement : groupsElement.getAsJsonArray()) {
            if (!groupElement.isJsonObject()) {
                continue;
            }
            JsonObject groupObject = groupElement.getAsJsonObject();
            String groupId = stringField(groupObject, "id", "group");
            String groupName = stringField(groupObject, "name", groupId);
            JsonElement itemsElement = groupObject.get("items");
            if (itemsElement == null || !itemsElement.isJsonArray()) {
                continue;
            }
            List<EmojiCatalog.Item> items = new ArrayList<>();
            for (JsonElement itemElement : itemsElement.getAsJsonArray()) {
                if (!itemElement.isJsonObject()) {
                    continue;
                }
                JsonObject itemObject = itemElement.getAsJsonObject();
                String token = stringField(itemObject, "token", "");
                String iconUrl = stringField(itemObject, "iconUrl", "");
                if (token.isBlank() || iconUrl.isBlank()) {
                    continue;
                }
                String loaderUrl = EmojiImageCache.loaderUrlFor(iconUrl);
                items.add(new EmojiCatalog.Item(token, iconUrl, loaderUrl));
            }
            if (!items.isEmpty()) {
                groups.add(new EmojiCatalog.Group(groupId, groupName, items));
            }
        }
        return new EmojiCatalog(sourceUrl, updatedAtMs, groups);
    }

    private static String toJson(EmojiCatalog catalog) {
        JsonObject root = new JsonObject();
        root.addProperty("sourceUrl", catalog.sourceUrl());
        root.addProperty("updatedAtMs", catalog.updatedAtMs());
        JsonArray groups = new JsonArray();
        for (EmojiCatalog.Group group : catalog.groups()) {
            JsonObject groupObject = new JsonObject();
            groupObject.addProperty("id", group.id());
            groupObject.addProperty("name", group.name());
            JsonArray items = new JsonArray();
            for (EmojiCatalog.Item item : group.items()) {
                JsonObject itemObject = new JsonObject();
                itemObject.addProperty("token", item.token());
                itemObject.addProperty("iconUrl", item.iconUrl());
                items.add(itemObject);
            }
            groupObject.add("items", items);
            groups.add(groupObject);
        }
        root.add("groups", groups);
        return root.toString();
    }

    private static String firstStringField(JsonObject object, String fallback, String... names) {
        for (String name : names) {
            String value = stringField(object, name, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallback == null ? "" : fallback;
    }

    private static String stringField(JsonObject object, String name, String fallback) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            String value = element.getAsString();
            return value == null ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long longField(JsonObject object, String name, long fallback) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}