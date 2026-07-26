package com.chat.upgrade.client.ui.chat.state;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

public record ChatMessageGroupKey(Type type, @Nullable UUID peerId) {
    public enum Type {
        ALL,
        SYSTEM,
        CHAT,
        PRIVATE_PEER
    }

    public ChatMessageGroupKey {
        type = type == null ? Type.ALL : type;
        if (type != Type.PRIVATE_PEER) {
            peerId = null;
        }
        if (type == Type.PRIVATE_PEER && peerId == null) {
            throw new IllegalArgumentException("private peer group requires a player UUID");
        }
    }

    public static ChatMessageGroupKey all() {
        return new ChatMessageGroupKey(Type.ALL, null);
    }

    public static ChatMessageGroupKey system() {
        return new ChatMessageGroupKey(Type.SYSTEM, null);
    }

    public static ChatMessageGroupKey chat() {
        return new ChatMessageGroupKey(Type.CHAT, null);
    }

    public static ChatMessageGroupKey privatePeer(UUID peerId) {
        return new ChatMessageGroupKey(Type.PRIVATE_PEER, peerId);
    }

    public boolean accepts(RichChatMessage message) {
        if (message == null) {
            return false;
        }
        return switch (type) {
            case ALL -> true;
            case SYSTEM -> !message.privateMessage() && message.kind().systemLike();
            case CHAT -> !message.privateMessage() && message.kind().playerAuthored();
            case PRIVATE_PEER -> peerId.equals(message.privatePeerId());
        };
    }
}