package com.chat.upgrade.client.ui.chat.state;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Stable nickname/time metadata shared by layout measurement and rendering. */
public final class ChatMessageMetadata {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private ChatMessageMetadata() {
    }

    public static String author(ChatTimelineProjection timeline) {
        if (timeline == null || !timeline.kind().playerAuthored()) {
            return "";
        }
        return timeline.author().visibleName();
    }

    public static String timestamp(ChatTimelineProjection timeline) {
        if (timeline == null || timeline.message().serverTimestampMs() <= 0L) {
            return "";
        }
        return TIME.format(Instant.ofEpochMilli(timeline.message().serverTimestampMs()));
    }

    public static String label(ChatTimelineProjection timeline) {
        String author = author(timeline);
        String timestamp = timestamp(timeline);
        if (author.isBlank()) {
            return timestamp;
        }
        return timestamp.isBlank() ? author : author + " · " + timestamp;
    }
}