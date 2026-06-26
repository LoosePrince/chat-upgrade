package com.chat.upgrade.client.ui.chat.viewport;

import java.util.ArrayList;
import java.util.List;

public record RichChatLayout(
        List<RichChatMessageLayout> messages,
        List<RichChatRenderNode> nodes,
        List<RichChatHitBox> hitBoxes,
        int totalHeight,
        long storeVersion,
        int viewportWidth) {
    public RichChatLayout {
        messages = List.copyOf(messages == null ? List.of() : messages);
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        hitBoxes = List.copyOf(hitBoxes == null ? List.of() : hitBoxes);
        totalHeight = Math.max(0, totalHeight);
        viewportWidth = Math.max(1, viewportWidth);
    }

    public static RichChatLayout empty(long storeVersion, int viewportWidth) {
        return new RichChatLayout(List.of(), List.of(), List.of(), 0, storeVersion, viewportWidth);
    }

    public List<RichChatRenderNode> visibleNodes(RichChatViewportState state) {
        if (state == null) {
            return nodes;
        }
        int top = state.visibleTop();
        int bottom = state.visibleBottom();
        List<RichChatRenderNode> visible = new ArrayList<>();
        for (RichChatRenderNode node : nodes) {
            if (node.bounds().intersectsVerticalRange(top, bottom)) {
                visible.add(node);
            }
        }
        return List.copyOf(visible);
    }

    public List<RichChatHitBox> visibleHitBoxes(RichChatViewportState state) {
        if (state == null) {
            return hitBoxes;
        }
        int top = state.visibleTop();
        int bottom = state.visibleBottom();
        List<RichChatHitBox> visible = new ArrayList<>();
        for (RichChatHitBox hitBox : hitBoxes) {
            if (hitBox.bounds().intersectsVerticalRange(top, bottom)) {
                visible.add(hitBox);
            }
        }
        return List.copyOf(visible);
    }
}