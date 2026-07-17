package com.chat.upgrade.client.ui.chat.state;

import java.util.Locale;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

public record ChatAvatar(
        @Nullable UUID playerId,
        String glyph,
        int backgroundRgb,
        int foregroundRgb) {
    private static final int[] FALLBACK_PALETTE = {
            0x5B7DB1,
            0x7B68A6,
            0x4F8A72,
            0xA06A5B,
            0x8B6F47,
            0x4F7F91,
            0x8A5F82,
            0x657A4D
    };

    public ChatAvatar {
        glyph = normalizeGlyph(glyph);
        backgroundRgb &= 0xFFFFFF;
        foregroundRgb &= 0xFFFFFF;
    }

    public static ChatAvatar forMessage(ChatAuthor author, ChatMessageKind kind) {
        ChatAuthor safeAuthor = author == null ? ChatAuthor.system() : author;
        ChatMessageKind safeKind = kind == null ? ChatMessageKind.SYSTEM : kind;
        String identity = safeAuthor.identityKey();
        String glyph = safeKind.playerAuthored()
                ? firstGlyph(safeAuthor.searchableName())
                : systemGlyph(safeKind);
        int color = safeAuthor.team().colorRgb() >= 0
                ? safeAuthor.team().colorRgb()
                : paletteColor(identity.isBlank() ? safeKind.name() : identity);
        return new ChatAvatar(safeAuthor.playerId(), glyph, color, 0xFFFFFF);
    }

    public boolean playerBacked() {
        return playerId != null;
    }

    private static int paletteColor(String identity) {
        int index = Math.floorMod(identity.toLowerCase(Locale.ROOT).hashCode(), FALLBACK_PALETTE.length);
        return FALLBACK_PALETTE[index];
    }

    private static String firstGlyph(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty()) {
            return "?";
        }
        int end = normalized.offsetByCodePoints(0, 1);
        return normalized.substring(0, end).toUpperCase(Locale.ROOT);
    }

    private static String systemGlyph(ChatMessageKind kind) {
        return switch (kind) {
            case ERROR -> "!";
            case ANNOUNCEMENT -> "◆";
            case GAME -> "•";
            case PLAYER -> "?";
            case SYSTEM -> "i";
        };
    }

    private static String normalizeGlyph(String value) {
        String normalized = value == null ? "" : value.strip();
        return normalized.isEmpty() ? "?" : firstGlyph(normalized);
    }
}