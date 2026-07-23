package com.chat.upgrade.client.ui.chat;

import java.util.Optional;

import com.chat.upgrade.ChatUpgrade;

import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;

/** Client-side click event that anchors the compact audio options popover. */
public final class AudioOptionsClickEvent {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ChatUpgrade.MOD_ID, "audio_options");
    private static final char SEP = '\u001f';

    private AudioOptionsClickEvent() {
    }

    public static ClickEvent forToggle(String url, String name, int anchorX, int anchorY) {
        String safeUrl = url == null ? "" : url;
        String safeName = name == null ? "" : name;
        String payload = safeName + SEP + safeUrl + SEP + anchorX + SEP + anchorY;
        return new ClickEvent.Custom(ID, Optional.of(StringTag.valueOf(payload)));
    }

    public record Parsed(String url, String name, int anchorX, int anchorY) {
    }

    public static Optional<Parsed> parse(ClickEvent event) {
        if (!(event instanceof ClickEvent.Custom custom)
                || !ID.equals(custom.id())
                || custom.payload().isEmpty()) {
            return Optional.empty();
        }
        String raw = custom.payload().get().asString().orElse("");
        String[] parts = raw.split(String.valueOf(SEP), 4);
        if (parts.length != 4 || parts[1].isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Parsed(
                    parts[1],
                    parts[0],
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}