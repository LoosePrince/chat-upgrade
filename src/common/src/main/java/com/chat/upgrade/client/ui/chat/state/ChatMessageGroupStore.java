package com.chat.upgrade.client.ui.chat.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ChatClientConfigRuntime;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewport;

public final class ChatMessageGroupStore {
    private static final Map<UUID, String> PRIVATE_PEERS = new LinkedHashMap<>();
    private static ChatMessageGroupKey selected = ChatMessageGroupKey.all();
    private static long version;

    private ChatMessageGroupStore() {
    }

    public static synchronized ChatMessageGroupKey selected() {
        return groupingEnabled() ? selected : ChatMessageGroupKey.all();
    }

    public static synchronized List<ChatMessageGroupKey> groups() {
        List<ChatMessageGroupKey> groups = new ArrayList<>();
        groups.add(ChatMessageGroupKey.all());
        groups.add(ChatMessageGroupKey.system());
        groups.add(ChatMessageGroupKey.chat());
        PRIVATE_PEERS.keySet().stream()
                .map(ChatMessageGroupKey::privatePeer)
                .forEach(groups::add);
        return List.copyOf(groups);
    }

    public static synchronized void rememberPeer(@Nullable UUID peerId) {
        rememberPeer(peerId, ChatPrivateMessageResolver.playerName(peerId));
    }

    public static synchronized void rememberPeer(@Nullable UUID peerId, @Nullable String playerId) {
        if (peerId == null) {
            return;
        }
        String normalizedPlayerId = playerId == null ? "" : playerId.trim();
        String previousPlayerId = PRIVATE_PEERS.get(peerId);
        if (previousPlayerId == null || (!normalizedPlayerId.isBlank() && !normalizedPlayerId.equals(previousPlayerId))) {
            PRIVATE_PEERS.put(peerId, normalizedPlayerId);
            version++;
        }
    }

    public static synchronized String privatePeerPlayerId(@Nullable UUID peerId) {
        if (peerId == null) {
            return "";
        }
        String playerId = PRIVATE_PEERS.getOrDefault(peerId, "");
        if (!playerId.isBlank()) {
            return playerId;
        }
        String resolved = ChatPrivateMessageResolver.playerName(peerId);
        if (!resolved.isBlank()) {
            rememberPeer(peerId, resolved);
            return resolved;
        }
        return "";
    }

    public static synchronized boolean select(ChatMessageGroupKey group) {
        ChatMessageGroupKey next = group == null ? ChatMessageGroupKey.all() : group;
        if (next.type() == ChatMessageGroupKey.Type.PRIVATE_PEER) {
            rememberPeer(next.peerId());
        }
        if (next.equals(selected)) {
            return false;
        }
        selected = next;
        version++;
        RichChatViewport.state().scrollToBottom();
        RichChatViewport.invalidateAll();
        return true;
    }

    public static synchronized void openPrivate(@Nullable UUID peerId) {
        if (peerId != null) {
            select(ChatMessageGroupKey.privatePeer(peerId));
        }
    }

    public static synchronized void openPrivate(@Nullable UUID peerId, @Nullable String playerId) {
        if (peerId != null) {
            rememberPeer(peerId, playerId);
            select(ChatMessageGroupKey.privatePeer(peerId));
        }
    }

    public static synchronized List<RichChatMessage> filterNewestFirst(List<RichChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        ChatMessageGroupKey group = selected();
        return messages.stream()
                .filter(group::accepts)
                .map(message -> group.type() == ChatMessageGroupKey.Type.PRIVATE_PEER
                        ? message.forPrivateGroupDisplay()
                        : message)
                .toList();
    }

    public static synchronized long version() {
        return version;
    }

    public static synchronized void clearSession() {
        PRIVATE_PEERS.clear();
        selected = ChatMessageGroupKey.all();
        version++;
    }

    private static boolean groupingEnabled() {
        return ChatClientConfigRuntime.uiPreferences().messageGroupingEnabled();
    }
}