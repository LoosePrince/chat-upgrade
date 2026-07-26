package com.chat.upgrade.client.history;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.ui.chat.InlineEmojiSlot;
import com.chat.upgrade.client.ui.chat.state.ChatAuthor;
import com.chat.upgrade.client.ui.chat.state.ChatMessageKind;
import com.chat.upgrade.client.ui.chat.state.ChatReplySummary;
import com.chat.upgrade.client.ui.chat.state.ChatTeamSnapshot;
import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageSource;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageStatus;
import com.chat.upgrade.platform.Platform;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.network.chat.Component;

/** Persistent client chat snapshots, isolated by a stable session key. */
public final class ChatHistoryStore {
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ChatHistoryStore() {
    }

    public static HistorySnapshot load(String sessionKey) {
        Path path = pathFor(sessionKey);
        if (!Files.isRegularFile(path)) {
            return HistorySnapshot.empty();
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            HistorySnapshot snapshot = GSON.fromJson(reader, HistorySnapshot.class);
            return snapshot == null || snapshot.schemaVersion() != CURRENT_SCHEMA_VERSION
                    ? HistorySnapshot.empty()
                    : snapshot.normalized();
        } catch (Exception exception) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to read chat history {}: {}", path, exception.getMessage());
            return HistorySnapshot.empty();
        }
    }

    public static void save(String sessionKey, long lastExitAtMs, List<RichChatMessage> newestFirst, int maxMessages) {
        int limit = Math.clamp(maxMessages, 10, 500);
        List<HistoryMessage> messages = newestFirst == null
                ? List.of()
                : newestFirst.stream()
                        .filter(message -> message.status() == RichChatMessageStatus.VISIBLE)
                        .filter(message -> message.source() != com.chat.upgrade.client.ui.chat.state.RichChatMessageSource.LOCAL_SYSTEM)
                        .map(HistoryMessage::from)
                        .limit(limit)
                        .toList();
        write(pathFor(sessionKey), new HistorySnapshot(CURRENT_SCHEMA_VERSION, Math.max(0L, lastExitAtMs), messages));
    }

    public static void clear(String sessionKey) {
        try {
            Files.deleteIfExists(pathFor(sessionKey));
        } catch (IOException exception) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to delete chat history: {}", exception.getMessage());
        }
    }

    private static void write(Path path, HistorySnapshot snapshot) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temporary)) {
                    GSON.toJson(snapshot, writer);
                }
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (Exception exception) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to write chat history {}: {}", path, exception.getMessage());
        }
    }

    private static Path pathFor(String sessionKey) {
        return Platform.configDir()
                .resolve("chat-upgrade")
                .resolve("chat-history")
                .resolve(hash(sessionKey == null ? "unknown" : sessionKey) + ".json");
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte valueByte : digest) {
                hex.append(String.format("%02x", valueByte));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record HistorySnapshot(int schemaVersion, long lastExitAtMs, List<HistoryMessage> messages) {
        public static HistorySnapshot empty() {
            return new HistorySnapshot(CURRENT_SCHEMA_VERSION, 0L, List.of());
        }

        public HistorySnapshot normalized() {
            List<HistoryMessage> safeMessages = messages == null
                    ? List.of()
                    : messages.stream()
                            .filter(HistoryMessage::valid)
                            .sorted(Comparator.comparingLong(HistoryMessage::timestampMs).reversed())
                            .limit(500)
                            .toList();
            return new HistorySnapshot(CURRENT_SCHEMA_VERSION, Math.max(0L, lastExitAtMs), safeMessages);
        }
    }

    public record HistoryMessage(
            String messageId,
            String authorUuid,
            String authorName,
            boolean localPlayer,
            String kind,
            long timestampMs,
            String replyToMessageId,
            String replyToAuthor,
            String replyToExcerpt,
            String text,
            String fallbackText,
            String source,
            List<HistoryAttachment> attachments,
            List<HistoryEmojiSlot> emojiSlots) {
        static HistoryMessage from(RichChatMessage message) {
            ChatAuthor author = message.author();
            ChatReplySummary reply = message.replyTo();
            return new HistoryMessage(
                    message.messageId(),
                    author.playerId() == null ? "" : author.playerId().toString(),
                    author.searchableName(),
                    author.localPlayer(),
                    message.kind().name(),
                    message.serverTimestampMs(),
                    reply == null ? "" : reply.messageId(),
                    reply == null ? "" : reply.author().searchableName(),
                    reply == null ? "" : reply.excerpt(),
                    message.component().getString(),
                    message.fallbackText(),
                    message.source().name(),
                    message.attachments().stream().map(HistoryAttachment::from).toList(),
                    message.inlineEmojiSlots().stream().map(HistoryEmojiSlot::from).toList());
        }

        boolean valid() {
            return messageId != null && !messageId.isBlank() && text != null;
        }

        public RichChatMessage toMessage() {
            UUID playerId = parseUuid(authorUuid);
            ChatAuthor author = new ChatAuthor(
                    playerId,
                    Component.literal(safe(authorName)),
                    safe(authorName),
                    new ChatTeamSnapshot("", "", "", ChatTeamSnapshot.NO_COLOR),
                    localPlayer);
            ChatReplySummary reply = replyToMessageId == null || replyToMessageId.isBlank()
                    ? null
                    : new ChatReplySummary(replyToMessageId, ChatAuthor.legacy(replyToAuthor), safe(replyToExcerpt));
            List<RichAttachment> restoredAttachments = attachments == null
                    ? List.of()
                    : attachments.stream().map(HistoryAttachment::toAttachment).flatMap(java.util.Optional::stream).toList();
            List<InlineEmojiSlot> restoredSlots = emojiSlots == null
                    ? List.of()
                    : emojiSlots.stream().map(HistoryEmojiSlot::toSlot).toList();
            return new RichChatMessage(
                    messageId,
                    author,
                    enumValue(ChatMessageKind.class, kind, ChatMessageKind.SYSTEM),
                    null,
                    null,
                    Math.max(0L, timestampMs),
                    reply,
                    0,
                    Component.literal(safe(text)),
                    Component.literal(safe(text)),
                    safe(text),
                    safe(fallbackText),
                    restoredAttachments,
                    restoredSlots,
                    enumValue(RichChatMessageSource.class, source, RichChatMessageSource.VANILLA_TEXT),
                    null,
                    RichChatMessageStatus.VISIBLE);
        }
    }

    public record HistoryAttachment(String type, String displayName, String url, String mediaId, String attachmentId) {
        static HistoryAttachment from(RichAttachment attachment) {
            return new HistoryAttachment(
                    attachment.type().name(),
                    attachment.displayName(),
                    attachment.urlOrNull(),
                    attachment.mediaId().orElse(null),
                    attachment.attachmentId().orElse(null));
        }

        java.util.Optional<RichAttachment> toAttachment() {
            try {
                return java.util.Optional.of(RichAttachment.structured(
                        enumValue(InlineResourceType.class, type, InlineResourceType.IMAGE),
                        displayName,
                        url,
                        mediaId,
                        attachmentId));
            } catch (IllegalArgumentException ignored) {
                return java.util.Optional.empty();
            }
        }
    }

    public record HistoryEmojiSlot(int charIndex, String iconUrl, String token) {
        static HistoryEmojiSlot from(InlineEmojiSlot slot) {
            return new HistoryEmojiSlot(slot.charIndex(), slot.iconUrl(), slot.token());
        }

        InlineEmojiSlot toSlot() {
            return new InlineEmojiSlot(Math.max(0, charIndex), safe(iconUrl), safe(token));
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return value == null ? fallback : Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}