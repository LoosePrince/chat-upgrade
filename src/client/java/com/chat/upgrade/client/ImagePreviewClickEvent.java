package com.chat.upgrade.client;

import java.util.Optional;

import com.chat.upgrade.ChatUpgrade;

import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;

/**
 * Client-side click event for opening the image preview screen.
 */
public final class ImagePreviewClickEvent {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ChatUpgrade.MOD_ID, "image_preview");

    public record Parsed(String url, String name) {
    }

    private ImagePreviewClickEvent() {
    }

    public static ClickEvent forUrl(String url) {
        return new ClickEvent.Custom(ID, Optional.of(StringTag.valueOf(url)));
    }

    public static ClickEvent forUrlAndName(String url, String name) {
        String safeUrl = url == null ? "" : url;
        String safeName = name == null ? "" : name;
        // Backward-compatible: payload may be only url. New format is "url|name".
        return new ClickEvent.Custom(ID, Optional.of(StringTag.valueOf(safeUrl + "|" + safeName)));
    }

    public static Optional<Parsed> parse(ClickEvent event) {
        if (!(event instanceof ClickEvent.Custom custom)) {
            return Optional.empty();
        }
        if (!ID.equals(custom.id()) || custom.payload().isEmpty()) {
            return Optional.empty();
        }
        String raw = custom.payload().get().asString().orElse("");
        if (raw.isBlank()) {
            return Optional.empty();
        }
        String url = raw;
        String name = "";
        int bar = raw.indexOf('|');
        if (bar >= 0) {
            url = raw.substring(0, bar);
            if (bar + 1 < raw.length()) {
                name = raw.substring(bar + 1);
            }
        }
        url = url.trim();
        name = name.trim();
        if (url.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Parsed(url, name));
    }
}
