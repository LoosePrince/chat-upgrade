package com.chat.upgrade.client.ui.screen;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.ui.chat.state.ChatAvatar;

/** Immutable presentation data consumed by the modal details screen. */
public record ChatDetailsModel(
        Kind kind,
        String title,
        String subtitle,
        String badge,
        int accentColor,
        HeroVisual hero,
        HeroStats stats,
        List<Section> sections,
        @Nullable Preview preview) {
    public ChatDetailsModel {
        kind = kind == null ? Kind.PROFILE : kind;
        title = safe(title);
        subtitle = safe(subtitle);
        badge = safe(badge);
        if (hero == null) {
            throw new IllegalArgumentException("hero must not be null");
        }
        stats = stats == null ? new HeroStats("0", "0", "0") : stats;
        sections = List.copyOf(sections == null ? List.of() : sections);
    }

    public String copyText() {
        StringBuilder text = new StringBuilder(title);
        if (!subtitle.isBlank()) {
            text.append('\n').append(subtitle);
        }
        for (Section section : sections) {
            text.append("\n\n[").append(section.title()).append(']');
            for (Field field : section.fields()) {
                if (!field.copyValue().isBlank()) {
                    text.append('\n').append(field.label()).append(": ").append(field.copyValue());
                }
            }
        }
        return text.toString();
    }

    public enum Kind {
        PROFILE,
        ATTACHMENT
    }

    public record HeroVisual(@Nullable ChatAvatar playerAvatar, @Nullable InlineResourceType mediaType) {
        public HeroVisual {
            if ((playerAvatar == null) == (mediaType == null)) {
                throw new IllegalArgumentException("hero must contain exactly one visual type");
            }
        }

        public static HeroVisual player(ChatAvatar avatar) {
            if (avatar == null) {
                throw new IllegalArgumentException("avatar must not be null");
            }
            return new HeroVisual(avatar, null);
        }

        public static HeroVisual media(InlineResourceType type) {
            if (type == null) {
                throw new IllegalArgumentException("media type must not be null");
            }
            return new HeroVisual(null, type);
        }
    }

    public record HeroStats(String primary, String secondary, String tertiary) {
        public HeroStats {
            primary = safe(primary);
            secondary = safe(secondary);
            tertiary = safe(tertiary);
        }
    }

    public record Section(String title, List<Field> fields) {
        public Section {
            title = safe(title);
            fields = List.copyOf(fields == null ? List.of() : fields);
        }
    }

    public record Field(String key, String label, String value, String copyValue) {
        public Field {
            key = safe(key);
            label = safe(label);
            value = safe(value);
            copyValue = safe(copyValue);
        }

        public static Field of(String key, String label, @Nullable Object value) {
            String text = value == null ? "" : String.valueOf(value);
            return new Field(key, label, text, text);
        }
    }

    public record Preview(InlineResourceType type, String url, String displayName) {
        public Preview {
            if (type != InlineResourceType.IMAGE && type != InlineResourceType.VIDEO) {
                throw new IllegalArgumentException("preview only supports image or video");
            }
            url = safe(url);
            displayName = safe(displayName);
        }
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}