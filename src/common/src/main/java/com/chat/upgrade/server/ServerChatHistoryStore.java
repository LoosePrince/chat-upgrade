package com.chat.upgrade.server;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.net.StructuredChatEnvelope;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** World-scoped, bounded persistent log for structured chat envelopes. */
public final class ServerChatHistoryStore {
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private MinecraftServer server;
    private final List<StructuredChatEnvelope> messages = new ArrayList<>();

    public synchronized void bind(MinecraftServer nextServer) {
        if (server == nextServer) {
            return;
        }
        server = nextServer;
        messages.clear();
        if (server == null || !ServerMediaServerConfig.get().chatHistoryEnabled) {
            return;
        }
        messages.addAll(read(pathFor(server)));
        trim();
    }

    public synchronized void append(MinecraftServer activeServer, StructuredChatEnvelope envelope) {
        bind(activeServer);
        if (!enabled() || envelope == null) {
            return;
        }
        messages.removeIf(entry -> entry.messageId().equals(envelope.messageId()));
        messages.add(envelope);
        trim();
        write();
    }

    public synchronized void retract(MinecraftServer activeServer, String messageId) {
        bind(activeServer);
        if (!enabled() || messageId == null || messageId.isBlank()) {
            return;
        }
        boolean changed = messages.removeIf(entry -> messageId.equals(entry.messageId()));
        if (changed) {
            write();
        }
    }

    public synchronized List<StructuredChatEnvelope> after(MinecraftServer activeServer, long afterTimestampMs, int requestedLimit) {
        bind(activeServer);
        if (!enabled()) {
            return List.of();
        }
        int limit = Math.min(Math.max(1, requestedLimit), ServerMediaServerConfig.get().chatHistoryReplayLimit);
        return messages.stream()
                .filter(entry -> entry.serverTimestampMs() > Math.max(0L, afterTimestampMs))
                .sorted(java.util.Comparator.comparingLong(StructuredChatEnvelope::serverTimestampMs))
                .skip(Math.max(0, messages.stream().filter(entry -> entry.serverTimestampMs() > Math.max(0L, afterTimestampMs)).count() - limit))
                .toList();
    }

    private boolean enabled() {
        return server != null && ServerMediaServerConfig.get().chatHistoryEnabled;
    }

    private void trim() {
        int maxMessages = ServerMediaServerConfig.get().chatHistoryMaxMessages;
        while (messages.size() > maxMessages) {
            messages.removeFirst();
        }
    }

    private void write() {
        if (server == null) {
            return;
        }
        Path path = pathFor(server);
        try {
            Files.createDirectories(path.getParent());
            Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temporary)) {
                    GSON.toJson(new HistoryFile(CURRENT_SCHEMA_VERSION, List.copyOf(messages)), writer);
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
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to write server chat history {}: {}", path, exception.getMessage());
        }
    }

    private static List<StructuredChatEnvelope> read(Path path) {
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            HistoryFile file = GSON.fromJson(reader, HistoryFile.class);
            if (file == null || file.schemaVersion() != CURRENT_SCHEMA_VERSION || file.messages() == null) {
                return List.of();
            }
            return file.messages().stream()
                    .filter(entry -> entry != null && !entry.messageId().isBlank())
                    .sorted(java.util.Comparator.comparingLong(StructuredChatEnvelope::serverTimestampMs))
                    .toList();
        } catch (Exception exception) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to read server chat history {}: {}", path, exception.getMessage());
            return List.of();
        }
    }

    private static Path pathFor(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("chat-upgrade")
                .resolve("chat-history.json");
    }

    private record HistoryFile(int schemaVersion, List<StructuredChatEnvelope> messages) {
    }
}