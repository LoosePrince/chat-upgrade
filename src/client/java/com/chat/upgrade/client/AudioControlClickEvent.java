package com.chat.upgrade.client;

import com.chat.upgrade.ChatUpgrade;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class AudioControlClickEvent {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(ChatUpgrade.MOD_ID, "audio_control");

    public enum Action { TOGGLE, TOGGLE_LOOP, SEEK }

    public record Parsed(Action action, String url, double ratio) {}

    private AudioControlClickEvent() {}

    public static ClickEvent forToggle(String url) {
        return new ClickEvent.Custom(ID, Optional.of(StringTag.valueOf("toggle|" + url)));
    }

    public static ClickEvent forSeek(String url, double ratio) {
        double r = Math.clamp(ratio, 0.0, 1.0);
        return new ClickEvent.Custom(ID, Optional.of(StringTag.valueOf("seek|" + url + "|" + r)));
    }

    public static ClickEvent forToggleLoop(String url) {
        return new ClickEvent.Custom(ID, Optional.of(StringTag.valueOf("loop|" + url)));
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
        String[] p = raw.split("\\|", 3);
        if (p.length < 2) {
            return Optional.empty();
        }
        String action = p[0];
        String url = p[1];
        if (url.isBlank()) {
            return Optional.empty();
        }
        if ("toggle".equalsIgnoreCase(action)) {
            return Optional.of(new Parsed(Action.TOGGLE, url, 0));
        }
        if ("seek".equalsIgnoreCase(action)) {
            double ratio = 0.0;
            if (p.length >= 3) {
                try {
                    ratio = Double.parseDouble(p[2]);
                } catch (Exception ignored) {
                }
            }
            return Optional.of(new Parsed(Action.SEEK, url, Math.clamp(ratio, 0.0, 1.0)));
        }
        if ("loop".equalsIgnoreCase(action)) {
            return Optional.of(new Parsed(Action.TOGGLE_LOOP, url, 0));
        }
        return Optional.empty();
    }
}
