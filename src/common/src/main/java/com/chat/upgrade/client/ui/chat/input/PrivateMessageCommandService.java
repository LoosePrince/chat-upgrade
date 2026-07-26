package com.chat.upgrade.client.ui.chat.input;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ChatClientConfigRuntime;
import com.chat.upgrade.client.ui.chat.state.ChatMessageGroupKey;
import com.chat.upgrade.client.ui.chat.state.ChatMessageGroupStore;
import com.chat.upgrade.client.ui.chat.state.ChatPrivateMessageResolver;

/** Builds and observes private commands without routing private text through normal chat submission. */
public final class PrivateMessageCommandService {
    private static final String ID_TOKEN = "<id>";
    private static final String UUID_TOKEN = "<uuid>";
    private static final String MESSAGE_TOKEN = "<message>";

    private PrivateMessageCommandService() {
    }

    public static @Nullable UUID activePeerId() {
        ChatMessageGroupKey selected = ChatMessageGroupStore.selected();
        return selected.type() == ChatMessageGroupKey.Type.PRIVATE_PEER ? selected.peerId() : null;
    }

    public static String activePeerPlayerId() {
        return ChatMessageGroupStore.privatePeerPlayerId(activePeerId());
    }

    public static boolean privateConversationActive() {
        return activePeerId() != null;
    }

    public static boolean sendToActivePeer(String message, Consumer<String> vanillaHandler) {
        UUID peerId = activePeerId();
        String peerPlayerId = activePeerPlayerId();
        String body = safe(message);
        if (peerId == null || body.isBlank() || vanillaHandler == null) {
            return false;
        }
        String command;
        try {
            command = buildCommand(peerId, peerPlayerId, body);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        ChatPrivateMessageResolver.rememberOutgoing(peerId, peerPlayerId, body);
        vanillaHandler.accept(command);
        ChatMessageGroupStore.openPrivate(peerId, peerPlayerId);
        return true;
    }

    public static String buildCommand(UUID peerId, String message) {
        return buildCommand(peerId, ChatMessageGroupStore.privatePeerPlayerId(peerId), message);
    }

    public static String buildCommand(UUID peerId, String peerPlayerId, String message) {
        if (peerId == null) {
            throw new IllegalArgumentException("private message peer must not be null");
        }
        String body = safe(message);
        if (body.isBlank()) {
            throw new IllegalArgumentException("private message body must not be blank");
        }
        String configured = template();
        String normalizedPlayerId = safe(peerPlayerId);
        if (configured.contains(ID_TOKEN) && normalizedPlayerId.isBlank()) {
            throw new IllegalArgumentException("private message peer player ID must not be blank");
        }
        return configured
                .replace(ID_TOKEN, normalizedPlayerId)
                .replace(UUID_TOKEN, peerId.toString())
                .replace(MESSAGE_TOKEN, body);
    }

    public static boolean observeSubmittedCommand(String command) {
        ParsedCommand parsed = parse(command);
        if (parsed == null) {
            return false;
        }
        ChatPrivateMessageResolver.rememberOutgoing(
                parsed.peerId(),
                parsed.peerPlayerId(),
                parsed.message());
        ChatMessageGroupStore.openPrivate(parsed.peerId(), parsed.peerPlayerId());
        return true;
    }

    private static @Nullable ParsedCommand parse(String command) {
        String configured = template();
        boolean targetsPlayerId = configured.contains(ID_TOKEN);
        String targetToken = targetsPlayerId ? ID_TOKEN : UUID_TOKEN;
        int targetIndex = configured.indexOf(targetToken);
        int messageIndex = configured.indexOf(MESSAGE_TOKEN);
        if (targetIndex < 0 || messageIndex <= targetIndex) {
            return null;
        }
        String prefix = configured.substring(0, targetIndex);
        String middle = configured.substring(targetIndex + targetToken.length(), messageIndex);
        String suffix = configured.substring(messageIndex + MESSAGE_TOKEN.length());
        String targetPattern = targetsPlayerId
                ? "([^\\s]+)"
                : "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})";
        Pattern pattern = Pattern.compile(
                "^\\s*" + Pattern.quote(prefix)
                        + targetPattern
                        + Pattern.quote(middle)
                        + "(.+?)"
                        + Pattern.quote(suffix)
                        + "\\s*$",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(command == null ? "" : command);
        if (!matcher.matches()) {
            return null;
        }
        String target = safe(matcher.group(1));
        UUID peerId = ChatPrivateMessageResolver.playerUuid(target);
        String message = safe(matcher.group(2));
        if (peerId == null || message.isBlank()) {
            return null;
        }
        String peerPlayerId = targetsPlayerId
                ? target
                : ChatPrivateMessageResolver.playerName(peerId);
        return new ParsedCommand(peerId, peerPlayerId, message);
    }

    private static String template() {
        return ChatClientConfigRuntime.uiPreferences().privateMessageCommand();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record ParsedCommand(UUID peerId, String peerPlayerId, String message) {
    }
}