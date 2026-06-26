package com.chat.upgrade.client.ui.chat.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.UnaryOperator;

public final class RichChatStateStore {
    private static final int MAX_MESSAGES = 500;
    private static final Deque<RichChatMessage> MESSAGES = new ArrayDeque<>();
    private static long version;

    private RichChatStateStore() {
    }

    public static synchronized RichChatMessage append(RichChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        removeById(message.messageId());
        MESSAGES.addFirst(message);
        while (MESSAGES.size() > MAX_MESSAGES) {
            MESSAGES.removeLast();
        }
        version++;
        return message;
    }

    public static synchronized boolean replace(RichChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        return update(message.messageId(), ignored -> message);
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
        return update(messageId, message -> message.withStatus(RichChatMessageStatus.DELETED));
    }

    public static synchronized List<RichChatMessage> snapshotNewestFirst() {
        return List.copyOf(new ArrayList<>(MESSAGES));
    }

    public static synchronized long version() {
        return version;
    }

    public static synchronized void clear() {
        MESSAGES.clear();
        version++;
    }

    private static void removeById(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        MESSAGES.removeIf(message -> messageId.equals(message.messageId()));
    }
}