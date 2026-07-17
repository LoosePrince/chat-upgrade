package com.chat.upgrade.client.ui.chat.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

public final class RichChatStateStore {
    private static final int MAX_MESSAGES = 500;
    private static final int MAX_DELETED_MESSAGE_IDS = 512;
    private static final Deque<RichChatMessage> MESSAGES = new ArrayDeque<>();
    private static final Set<String> DELETED_MESSAGE_IDS = new LinkedHashSet<>();
    private static long version;

    private RichChatStateStore() {
    }

    public static synchronized RichChatMessage append(RichChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        RichChatMessage stored = DELETED_MESSAGE_IDS.contains(message.messageId())
                ? message.withStatus(RichChatMessageStatus.DELETED)
                : message;
        removeById(stored.messageId());
        MESSAGES.addFirst(stored);
        while (MESSAGES.size() > MAX_MESSAGES) {
            MESSAGES.removeLast();
        }
        version++;
        return stored;
    }

    public static synchronized boolean replace(RichChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        RichChatMessage replacement = DELETED_MESSAGE_IDS.contains(message.messageId())
                ? message.withStatus(RichChatMessageStatus.DELETED)
                : message;
        return update(replacement.messageId(), ignored -> replacement);
    }

    public static synchronized boolean update(String messageId, UnaryOperator<RichChatMessage> updater) {
        if (messageId == null || messageId.isBlank() || updater == null) {
            return false;
        }
        Deque<RichChatMessage> next = new ArrayDeque<>();
        boolean changed = false;
        for (RichChatMessage current : MESSAGES) {
            if (!changed && messageId.equals(current.messageId())) {
                RichChatMessage updated = updater.apply(current);
                if (updated != null) {
                    next.addLast(updated);
                }
                changed = true;
            } else {
                next.addLast(current);
            }
        }
        if (!changed) {
            return false;
        }
        MESSAGES.clear();
        MESSAGES.addAll(next);
        version++;
        return true;
    }

    public static synchronized boolean delete(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        String normalizedId = messageId.trim();
        rememberDeletedId(normalizedId);
        Deque<RichChatMessage> next = new ArrayDeque<>();
        boolean targetFound = false;
        boolean changed = false;
        for (RichChatMessage message : MESSAGES) {
            RichChatMessage updated = message;
            if (normalizedId.equals(message.messageId())) {
                updated = message.withStatus(RichChatMessageStatus.DELETED);
                targetFound = true;
            } else if (message.replyTo() != null && normalizedId.equals(message.replyTo().messageId())) {
                updated = message.withReplyTo(new ChatReplySummary(
                        normalizedId,
                        message.replyTo().author(),
                        ""));
            }
            next.addLast(updated);
            changed |= updated != message;
        }
        if (!changed) {
            version++;
            return false;
        }
        MESSAGES.clear();
        MESSAGES.addAll(next);
        version++;
        return targetFound;
    }

    public static synchronized List<RichChatMessage> snapshotNewestFirst() {
        return List.copyOf(new ArrayList<>(MESSAGES));
    }

    public static synchronized long version() {
        return version;
    }

    public static synchronized void clear() {
        MESSAGES.clear();
        DELETED_MESSAGE_IDS.clear();
        version++;
    }

    private static void rememberDeletedId(String messageId) {
        DELETED_MESSAGE_IDS.remove(messageId);
        DELETED_MESSAGE_IDS.add(messageId);
        while (DELETED_MESSAGE_IDS.size() > MAX_DELETED_MESSAGE_IDS) {
            String eldest = DELETED_MESSAGE_IDS.iterator().next();
            DELETED_MESSAGE_IDS.remove(eldest);
        }
    }

    private static void removeById(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        MESSAGES.removeIf(message -> messageId.equals(message.messageId()));
    }
}