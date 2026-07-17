package com.chat.upgrade.client.ui.chat.state;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class ChatMessageClassifier {
    private ChatMessageClassifier() {
    }

    public static ChatMessageKind defaultKind(@Nullable RichChatMessageSource source) {
        return source == RichChatMessageSource.LOCAL_SYSTEM
                ? ChatMessageKind.SYSTEM
                : ChatMessageKind.PLAYER;
    }

    public static ChatMessageKind classify(
            @Nullable Component component,
            @Nullable ChatMessageKind hint,
            @Nullable RichChatMessageSource source) {
        ChatMessageKind declared = hint == null ? defaultKind(source) : hint;
        if (declared == ChatMessageKind.PLAYER || declared == ChatMessageKind.ERROR) {
            return declared;
        }
        Style style = component == null ? Style.EMPTY : component.getStyle();
        if (hasColor(style, 0xFF5555) || hasColor(style, 0xAA0000)) {
            return ChatMessageKind.ERROR;
        }
        if (style.isBold() && declared == ChatMessageKind.SYSTEM) {
            return ChatMessageKind.ANNOUNCEMENT;
        }
        String text = component == null ? "" : component.getString().strip().toLowerCase(Locale.ROOT);
        if (declared == ChatMessageKind.SYSTEM && looksLikeGameMessage(text)) {
            return ChatMessageKind.GAME;
        }
        return declared;
    }

    private static boolean hasColor(Style style, int rgb) {
        return style != null
                && style.getColor() != null
                && style.getColor().getValue() == rgb;
    }

    private static boolean looksLikeGameMessage(String text) {
        return text.startsWith("[") && text.endsWith("]") && text.length() <= 96;
    }
}