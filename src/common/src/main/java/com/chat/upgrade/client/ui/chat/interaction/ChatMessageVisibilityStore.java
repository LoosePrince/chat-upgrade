package com.chat.upgrade.client.ui.chat.interaction;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.chat.upgrade.client.ui.chat.state.ChatAuthor;
import com.chat.upgrade.client.ui.chat.state.RichChatMessage;

public final class ChatMessageVisibilityStore {
    private static final Set<String> HIDDEN_MESSAGE_IDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> BLOCKED_AUTHOR_KEYS = ConcurrentHashMap.newKeySet();

    private ChatMessageVisibilityStore() {
    }

    public static boolean isVisible(RichChatMessage message) {
        if (message == null || HIDDEN_MESSAGE_IDS.contains(message.messageId())) {
            return false;
        }
        String authorKey = authorKey(message.author());
        return authorKey.isBlank() || !BLOCKED_AUTHOR_KEYS.contains(authorKey);
    }

    public static boolean hideMessage(String messageId) {
        String normalized = normalize(messageId);
        return !normalized.isBlank() && HIDDEN_MESSAGE_IDS.add(normalized);
    }

    public static boolean isAuthorBlocked(ChatAuthor author) {
        String key = authorKey(author);
        return !key.isBlank() && BLOCKED_AUTHOR_KEYS.contains(key);
    }

    public static boolean toggleAuthor(ChatAuthor author) {
        String key = authorKey(author);
        if (key.isBlank()) {
            return false;
        }
        if (BLOCKED_AUTHOR_KEYS.remove(key)) {
            return false;
        }
        BLOCKED_AUTHOR_KEYS.add(key);
        return true;
    }

    public static boolean unblockAuthor(String authorKey) {
        String key = normalize(authorKey).toLowerCase(java.util.Locale.ROOT);
        return !key.isBlank() && BLOCKED_AUTHOR_KEYS.remove(key);
    }

    public static void clearSession() {
        HIDDEN_MESSAGE_IDS.clear();
        BLOCKED_AUTHOR_KEYS.clear();
    }

    private static String authorKey(ChatAuthor author) {
        return author == null ? "" : normalize(author.identityKey()).toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}