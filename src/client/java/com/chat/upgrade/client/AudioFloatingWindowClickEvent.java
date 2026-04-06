package com.chat.upgrade.client;

import java.util.Optional;

import com.chat.upgrade.ChatUpgrade;

import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;

/**
 * Client-side click event to toggle the floating audio player.
 */
public final class AudioFloatingWindowClickEvent {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ChatUpgrade.MOD_ID, "audio_floating_window");

    private AudioFloatingWindowClickEvent() {
    }

    public static ClickEvent forToggle(String url) {
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
