package com.chat.upgrade.client;

import com.chat.upgrade.ChatUpgrade;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/** Client-side {@link ClickEvent.Custom} for manual image reveal (no server round-trip). */
public final class ManualRevealClickEvent {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ChatUpgrade.MOD_ID, "manual_reveal");

    private ManualRevealClickEvent() {}

    public static ClickEvent forUrl(String url) {
        return new ClickEvent.Custom(ID, Optional.of(StringTag.valueOf(url)));
    }

    /**
     * @return image URL if this is our reveal click; otherwise empty
     */
    public static Optional<String> parseUrl(ClickEvent event) {
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
            return s.isBlank() ? Optional.empty() : Optional.of(s);
        }
        return Optional.empty();
    }
}
