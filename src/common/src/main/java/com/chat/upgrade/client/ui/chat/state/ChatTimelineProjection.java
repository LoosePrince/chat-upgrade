package com.chat.upgrade.client.ui.chat.state;

public record ChatTimelineProjection(
        RichChatMessage message,
        ChatAvatar avatar,
        ChatTimelineGroupPosition groupPosition,
        String groupKey,
        boolean continuesIdentityGroup) {
    public ChatTimelineProjection {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        avatar = avatar == null ? ChatAvatar.forMessage(message.author(), message.kind()) : avatar;
        groupPosition = groupPosition == null ? ChatTimelineGroupPosition.SINGLE : groupPosition;
        groupKey = groupKey == null ? "" : groupKey;
    }

    public ChatAuthor author() {
        return message.author();
    }

    public ChatMessageKind kind() {
        return message.kind();
    }

    public boolean startsIdentityGroup() {
        return kind().playerAuthored() && !continuesIdentityGroup;
    }

    public boolean groupedWithPrevious() {
        return groupPosition == ChatTimelineGroupPosition.MIDDLE
                || groupPosition == ChatTimelineGroupPosition.LAST;
    }
}