package com.chat.upgrade.client.ui.chat.viewport;

import java.util.List;

import com.chat.upgrade.client.ui.chat.state.RichChatMessage;

public record RichChatMessageLayout(
        RichChatMessage message,
        RichChatBounds bounds,
        List<RichChatRenderNode> nodes,
        List<RichChatHitBox> hitBoxes) {
    public RichChatMessageLayout {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        hitBoxes = List.copyOf(hitBoxes == null ? List.of() : hitBoxes);
    }

    public boolean visibleIn(int topInclusive, int bottomExclusive) {
        return bounds.intersectsVerticalRange(topInclusive, bottomExclusive);
    }
}