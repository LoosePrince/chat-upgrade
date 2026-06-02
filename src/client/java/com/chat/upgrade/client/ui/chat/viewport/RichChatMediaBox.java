package com.chat.upgrade.client.ui.chat.viewport;

public record RichChatMediaBox(RichChatRenderNodeKind kind, int width, int height) {
    public RichChatMediaBox {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        width = Math.max(1, width);
        height = Math.max(1, height);
    }

    public RichChatBounds at(int left, int top) {
        return RichChatBounds.ofSize(left, top, width, height);
    }
}