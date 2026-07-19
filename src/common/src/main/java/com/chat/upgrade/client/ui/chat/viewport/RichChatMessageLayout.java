package com.chat.upgrade.client.ui.chat.viewport;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ui.chat.state.ChatTimelineProjection;
import com.chat.upgrade.client.ui.chat.state.RichChatMessage;

public record RichChatMessageLayout(
        RichChatMessage message,
        ChatTimelineProjection timeline,
        RichChatBounds bounds,
        RichChatBounds visualBounds,
        @Nullable RichChatBounds identityBounds,
        @Nullable RichChatBounds metadataBounds,
        List<RichChatRenderNode> nodes,
        List<RichChatHitBox> hitBoxes) {
    public RichChatMessageLayout {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        if (timeline == null) {
            throw new IllegalArgumentException("timeline must not be null");
        }
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        visualBounds = visualBounds == null ? bounds : visualBounds;
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        hitBoxes = List.copyOf(hitBoxes == null ? List.of() : hitBoxes);
    }

    public boolean visibleIn(int topInclusive, int bottomExclusive) {
        return bounds.intersectsVerticalRange(topInclusive, bottomExclusive);
    }
}