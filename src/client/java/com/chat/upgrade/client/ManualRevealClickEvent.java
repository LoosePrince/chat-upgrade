package com.chat.upgrade.client;

import java.util.Optional;

import com.chat.upgrade.ChatUpgrade;

import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;

/**
 * Client-side {@link ClickEvent.Custom} for manual image reveal (no server
 * round-trip).
 */
public final class ManualRevealClickEvent {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ChatUpgrade.MOD_ID, "manual_reveal");

    private ManualRevealClickEvent() {
    }

    public static ClickEvent forUrl(String url) {
        return forResource(InlineResourceType.IMAGE, url);
    }

    public static ClickEvent forResource(InlineResourceType type, String url) {
        return new ClickEvent.Custom(ID, Optional.of(StringTag.valueOf(type.name() + "|" + url)));
    }

    public record Parsed(InlineResourceType type, String url) {
    }

    public static Optional<Parsed> parse(ClickEvent event) {
        if (!(event instanceof ClickEvent.Custom custom)) {
            return Optional.empty();
        }
        if (!ID.equals(custom.id())) {
            return Optional.empty();
        }
        Optional<Tag> payload = custom.payload();
        if (payload.isEmpty()) {
            return Optional.empty();
        }
        Tag tag = payload.get();
        if (tag instanceof StringTag st) {
            String s = st.asString().orElse("");
            if (s.isBlank()) {
                return Optional.empty();
            }
            int sep = s.indexOf('|');
            if (sep <= 0 || sep >= s.length() - 1) {
                return Optional.of(new Parsed(InlineResourceType.IMAGE, s));
            }
            try {
                InlineResourceType type = InlineResourceType.valueOf(s.substring(0, sep));
                String url = s.substring(sep + 1);
                if (url.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(new Parsed(type, url));
            } catch (Exception ignored) {
                return Optional.of(new Parsed(InlineResourceType.IMAGE, s));
            }
        }
        return Optional.empty();
    }
}
