package com.chat.upgrade.client.ui.chat.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class RichChatStateStore {
    private static final int MAX_MESSAGES = 500;
    private static final Deque<RichChatMessage> MESSAGES = new ArrayDeque<>();

    private RichChatStateStore() {
    }

    public static synchronized RichChatMessage append(RichChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        MESSAGES.addFirst(message);
        while (MESSAGES.size() > MAX_MESSAGES) {
            MESSAGES.removeLast();
        }
        return message;
    }

    public static synchronized List<RichChatMessage> snapshotNewestFirst() {
        return List.copyOf(new ArrayList<>(MESSAGES));
    }

    public static synchronized void clear() {
        MESSAGES.clear();
    }
}