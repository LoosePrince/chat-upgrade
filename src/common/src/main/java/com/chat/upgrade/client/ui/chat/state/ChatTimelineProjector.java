package com.chat.upgrade.client.ui.chat.state;

import com.chat.upgrade.client.ui.chat.interaction.ChatMessageVisibilityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChatTimelineProjector {
    private static final long MAX_SERVER_GROUP_GAP_MS = 2L * 60L * 1000L;
    private static final int MAX_LOCAL_GROUP_GAP_TICKS = 20 * 30;

    private ChatTimelineProjector() {
    }

    public static List<ChatTimelineProjection> projectOldestFirst(List<RichChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ResolvedMessage> resolved = messages.stream()
                .map(ChatTimelineProjector::resolve)
                .filter(resolvedMessage -> ChatMessageVisibilityStore.isVisible(resolvedMessage.message()))
                .toList();
        List<ChatTimelineProjection> projected = new ArrayList<>(resolved.size());
        for (int index = 0; index < resolved.size(); index++) {
            ResolvedMessage current = resolved.get(index);
            boolean joinsPrevious = index > 0 && canGroup(resolved.get(index - 1), current);
            boolean joinsNext = index + 1 < resolved.size() && canGroup(current, resolved.get(index + 1));
            boolean continuesIdentityGroup = index > 0
                    && canContinueIdentityGroup(resolved.get(index - 1), current);
            ChatTimelineGroupPosition position = groupPosition(joinsPrevious, joinsNext);
            projected.add(new ChatTimelineProjection(
                    current.message(),
                    ChatAvatar.forMessage(current.message().author(), current.message().kind()),
                    position,
                    current.groupKey(),
                    continuesIdentityGroup));
        }
        return List.copyOf(projected);
    }

    private static ResolvedMessage resolve(RichChatMessage message) {
        ChatMessageKind kind = ChatMessageClassifier.classify(message.component(), message.kind(), message.source());
        ChatAuthor author = ChatIdentityResolver.resolve(message.author(), message.component(), kind);
        RichChatMessage resolvedMessage = message.withIdentity(author, kind);
        String groupKey = kind.playerAuthored() ? author.identityKey().toLowerCase(Locale.ROOT) : "";
        return new ResolvedMessage(resolvedMessage, groupKey);
    }

    private static boolean canGroup(ResolvedMessage earlier, ResolvedMessage later) {
        return canContinueIdentityGroup(earlier, later)
                && earlier.message().replyTo() == null
                && later.message().replyTo() == null;
    }

    private static boolean canContinueIdentityGroup(ResolvedMessage earlier, ResolvedMessage later) {
        RichChatMessage earlierMessage = earlier.message();
        RichChatMessage laterMessage = later.message();
        if (!earlierMessage.kind().playerAuthored()
                || !laterMessage.kind().playerAuthored()
                || earlierMessage.status() != RichChatMessageStatus.VISIBLE
                || laterMessage.status() != RichChatMessageStatus.VISIBLE
                || earlier.groupKey().isBlank()
                || !earlier.groupKey().equals(later.groupKey())) {
            return false;
        }
        long earlierTimestamp = earlierMessage.serverTimestampMs();
        long laterTimestamp = laterMessage.serverTimestampMs();
        if (earlierTimestamp > 0L && laterTimestamp > 0L) {
            long gap = laterTimestamp - earlierTimestamp;
            return gap >= 0L && gap <= MAX_SERVER_GROUP_GAP_MS;
        }
        int tickGap = laterMessage.addedTime() - earlierMessage.addedTime();
        return tickGap >= 0 && tickGap <= MAX_LOCAL_GROUP_GAP_TICKS;
    }

    private static ChatTimelineGroupPosition groupPosition(boolean joinsPrevious, boolean joinsNext) {
        if (joinsPrevious && joinsNext) {
            return ChatTimelineGroupPosition.MIDDLE;
        }
        if (joinsPrevious) {
            return ChatTimelineGroupPosition.LAST;
        }
        if (joinsNext) {
            return ChatTimelineGroupPosition.FIRST;
        }
        return ChatTimelineGroupPosition.SINGLE;
    }

    private record ResolvedMessage(
            RichChatMessage message,
            String groupKey) {
    }
}