package com.chat.upgrade.client.ui.chat.surface;

import com.chat.upgrade.client.ui.chat.state.ChatTimelineProjection;

public record ChatLayoutPolicy(
        MessageDecoration messageDecoration,
        IdentityPresentation identityPresentation,
        int nodeGap,
        int groupGap,
        int messageGap,
        int identityGutter,
        int avatarSize,
        int bubblePaddingX,
        int contentWidthPercent) {
    public ChatLayoutPolicy {
        messageDecoration = messageDecoration == null ? MessageDecoration.BUBBLE : messageDecoration;
        identityPresentation = identityPresentation == null
                ? IdentityPresentation.GROUP_START
                : identityPresentation;
        nodeGap = Math.max(0, nodeGap);
        groupGap = Math.max(0, groupGap);
        messageGap = Math.max(0, messageGap);
        identityGutter = Math.max(0, identityGutter);
        avatarSize = Math.max(0, avatarSize);
        bubblePaddingX = Math.max(0, bubblePaddingX);
        contentWidthPercent = Math.clamp(contentWidthPercent, 40, 100);
    }

    public boolean showIdentity(ChatTimelineProjection timeline) {
        if (timeline == null || !timeline.kind().playerAuthored()) {
            return false;
        }
        return identityPresentation == IdentityPresentation.EVERY_PLAYER_MESSAGE
                || timeline.groupPosition().startsGroup();
    }

    public enum MessageDecoration {
        BUBBLE,
        FEED_STRIPE,
        NATIVE_CARD
    }

    public enum IdentityPresentation {
        GROUP_START,
        EVERY_PLAYER_MESSAGE
    }
}