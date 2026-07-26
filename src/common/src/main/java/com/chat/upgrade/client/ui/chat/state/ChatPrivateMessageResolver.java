package com.chat.upgrade.client.ui.chat.state;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Resolves private-message ownership once, before the message enters the state store. */
public final class ChatPrivateMessageResolver {
    private static final long PENDING_TTL_MS = 10_000L;
    private static final Deque<PendingOutgoing> PENDING_OUTGOING = new ArrayDeque<>();

    public record Resolution(
            UUID peerId,
            String peerPlayerId,
            ChatAuthor author,
            @Nullable Component body) {
        public Resolution {
            peerPlayerId = safe(peerPlayerId);
            if (peerId == null || author == null) {
                throw new IllegalArgumentException("private message resolution requires peer and author");
            }
        }
    }

    private ChatPrivateMessageResolver() {
    }

    public static synchronized void rememberOutgoing(UUID peerId, String message) {
        rememberOutgoing(peerId, playerName(peerId), message);
    }

    public static synchronized void rememberOutgoing(UUID peerId, String peerPlayerId, String message) {
        String body = safe(message);
        if (peerId == null || body.isBlank()) {
            return;
        }
        pruneExpired(System.currentTimeMillis());
        PENDING_OUTGOING.addFirst(new PendingOutgoing(
                peerId,
                safe(peerPlayerId),
                body,
                System.currentTimeMillis()));
        while (PENDING_OUTGOING.size() > 16) {
            PENDING_OUTGOING.removeLast();
        }
    }

    public static @Nullable Resolution resolve(@Nullable Component component, @Nullable ChatAuthor fallbackAuthor) {
        if (component == null) {
            return null;
        }
        Translation privateTranslation = findPrivateTranslation(component);
        if (privateTranslation != null) {
            UUID peerId = playerUuid(privateTranslation.peerIdentity());
            if (peerId != null) {
                boolean incoming = "commands.message.display.incoming".equals(privateTranslation.key());
                ChatAuthor author = incoming
                        ? playerAuthor(peerId, privateTranslation.peerIdentity(), component, fallbackAuthor)
                        : localAuthor(component, fallbackAuthor);
                return new Resolution(
                        peerId,
                        resolvedPlayerName(peerId, privateTranslation.peerIdentity()),
                        author,
                        privateTranslation.body());
            }
        }

        PendingOutgoing pending = consumePendingFor(component.getString());
        if (pending != null) {
            String peerPlayerId = pending.peerPlayerId().isBlank()
                    ? playerName(pending.peerId())
                    : pending.peerPlayerId();
            return new Resolution(
                    pending.peerId(),
                    peerPlayerId,
                    localAuthor(component, fallbackAuthor),
                    Component.literal(pending.message()).withStyle(component.getStyle()));
        }

        String text = component.getString();
        if (!looksPrivate(text)) {
            return null;
        }
        PlayerInfo peer = uniqueMentionedPlayer(text);
        if (peer == null || peer.getProfile() == null || peer.getProfile().id() == null) {
            return null;
        }
        UUID peerId = peer.getProfile().id();
        return new Resolution(
                peerId,
                peer.getProfile().name(),
                playerAuthor(peerId, peer.getProfile().name(), component, fallbackAuthor),
                null);
    }

    public static synchronized void clearSession() {
        PENDING_OUTGOING.clear();
    }

    private static @Nullable Translation findPrivateTranslation(Component component) {
        if (component.getContents() instanceof TranslatableContents translated) {
            String key = translated.getKey();
            if (("commands.message.display.incoming".equals(key)
                    || "commands.message.display.outgoing".equals(key))
                    && translated.getArgs().length > 0) {
                return new Translation(
                        key,
                        argumentText(translated.getArgs()[0]),
                        translated.getArgs().length > 1
                                ? argumentComponent(translated.getArgs()[1])
                                : null);
            }
            for (Object argument : translated.getArgs()) {
                if (argument instanceof Component child) {
                    Translation nested = findPrivateTranslation(child);
                    if (nested != null) {
                        return nested;
                    }
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            Translation nested = findPrivateTranslation(sibling);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static String argumentText(Object argument) {
        return argument instanceof Component component
                ? component.getString()
                : safe(String.valueOf(argument));
    }

    private static Component argumentComponent(Object argument) {
        return argument instanceof Component component
                ? component
                : Component.literal(String.valueOf(argument));
    }

    private static @Nullable PendingOutgoing consumePendingFor(String renderedText) {
        String normalized = safe(renderedText).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }
        synchronized (ChatPrivateMessageResolver.class) {
            pruneExpired(System.currentTimeMillis());
            for (var iterator = PENDING_OUTGOING.iterator(); iterator.hasNext();) {
                PendingOutgoing pending = iterator.next();
                if (normalized.contains(pending.message().toLowerCase(Locale.ROOT))) {
                    iterator.remove();
                    return pending;
                }
            }
        }
        return null;
    }

    private static void pruneExpired(long nowMs) {
        PENDING_OUTGOING.removeIf(pending -> nowMs - pending.createdAtMs() > PENDING_TTL_MS);
    }

    public static @Nullable UUID playerUuid(String identity) {
        String normalized = safe(identity);
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ignored) {
        }
        PlayerInfo player = playerInfo(normalized);
        return player == null || player.getProfile() == null ? null : player.getProfile().id();
    }

    public static String playerName(@Nullable UUID playerId) {
        PlayerInfo player = playerId == null ? null : playerInfo(playerId.toString());
        return player == null || player.getProfile() == null
                ? ""
                : safe(player.getProfile().name());
    }

    private static String resolvedPlayerName(UUID playerId, String identity) {
        String onlineName = playerName(playerId);
        if (!onlineName.isBlank()) {
            return onlineName;
        }
        String candidate = safe(identity);
        try {
            UUID.fromString(candidate);
            return "";
        } catch (IllegalArgumentException ignored) {
            return candidate;
        }
    }

    private static ChatAuthor playerAuthor(
            UUID playerId,
            String identity,
            Component component,
            @Nullable ChatAuthor fallbackAuthor) {
        PlayerInfo player = playerInfo(playerId.toString());
        if (player != null && player.getProfile() != null) {
            return ChatIdentityResolver.resolve(
                    new ChatAuthor(
                            playerId,
                            player.getTabListDisplayName(),
                            player.getProfile().name(),
                            null,
                            false),
                    component,
                    ChatMessageKind.PLAYER);
        }
        String name = safe(identity);
        ChatAuthor resolved = new ChatAuthor(
                playerId,
                Component.literal(name.isBlank() ? "?" : name),
                name,
                null,
                false);
        return fallbackAuthor != null && playerId.equals(fallbackAuthor.playerId())
                ? fallbackAuthor
                : resolved;
    }

    private static ChatAuthor localAuthor(Component component, @Nullable ChatAuthor fallbackAuthor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null) {
            return ChatIdentityResolver.resolve(
                    new ChatAuthor(
                            minecraft.player.getUUID(),
                            minecraft.player.getDisplayName(),
                            minecraft.player.getScoreboardName(),
                            null,
                            true),
                    component,
                    ChatMessageKind.PLAYER);
        }
        return fallbackAuthor == null ? ChatAuthor.legacy("") : fallbackAuthor;
    }

    private static @Nullable PlayerInfo playerInfo(String identity) {
        ClientPacketListener connection = connection();
        if (connection == null || identity == null || identity.isBlank()) {
            return null;
        }
        String normalized = identity.trim();
        for (PlayerInfo player : connection.getOnlinePlayers()) {
            if (player == null || player.getProfile() == null) {
                continue;
            }
            if (normalized.equalsIgnoreCase(player.getProfile().name())
                    || normalized.equalsIgnoreCase(player.getProfile().id().toString())) {
                return player;
            }
        }
        return null;
    }

    private static @Nullable PlayerInfo uniqueMentionedPlayer(String renderedText) {
        ClientPacketListener connection = connection();
        Minecraft minecraft = Minecraft.getInstance();
        if (connection == null || renderedText == null || renderedText.isBlank()) {
            return null;
        }
        String normalized = renderedText.toLowerCase(Locale.ROOT);
        PlayerInfo match = null;
        for (PlayerInfo player : connection.getOnlinePlayers()) {
            if (player == null || player.getProfile() == null || player.getProfile().name() == null) {
                continue;
            }
            UUID playerId = player.getProfile().id();
            if (minecraft != null && minecraft.player != null && playerId.equals(minecraft.player.getUUID())) {
                continue;
            }
            String name = player.getProfile().name();
            if (!normalized.contains(name.toLowerCase(Locale.ROOT))
                    && !normalized.contains(playerId.toString().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (match != null && !match.getProfile().id().equals(playerId)) {
                return null;
            }
            match = player;
        }
        return match;
    }

    private static boolean looksPrivate(String value) {
        String text = safe(value).toLowerCase(Locale.ROOT);
        return text.contains("whisper")
                || text.contains("private message")
                || text.contains("私聊")
                || text.contains("悄悄地对")
                || text.contains("悄悄告诉")
                || text.contains(" -> ")
                || text.contains(" → ");
    }

    private static @Nullable ClientPacketListener connection() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.getConnection();
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private record Translation(String key, String peerIdentity, @Nullable Component body) {
    }

    private record PendingOutgoing(UUID peerId, String peerPlayerId, String message, long createdAtMs) {
    }
}