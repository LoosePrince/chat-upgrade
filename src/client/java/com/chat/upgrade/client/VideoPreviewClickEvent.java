package com.chat.upgrade.client;

import java.util.Optional;

import com.chat.upgrade.ChatUpgrade;

import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;

/**
 * Client-side click event for opening the video preview screen.
 */
public final class VideoPreviewClickEvent {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ChatUpgrade.MOD_ID, "video_preview");

    private VideoPreviewClickEvent() {
    }

    public static ClickEvent forUrl(String url) {
        return new ClickEvent.Custom(ID, Optional.of(StringTag.valueOf(url)));
    }

    public static Optional<String> parse(ClickEvent event) {
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
        return Optional.of(raw);
    }
}
