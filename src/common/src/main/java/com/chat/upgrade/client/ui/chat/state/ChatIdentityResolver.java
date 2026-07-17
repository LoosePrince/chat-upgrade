package com.chat.upgrade.client.ui.chat.state;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;

public final class ChatIdentityResolver {
    private ChatIdentityResolver() {
    }

    public static ChatAuthor resolve(
            @Nullable ChatAuthor supplied,
            @Nullable Component message,
            @Nullable ChatMessageKind kind) {
        ChatMessageKind safeKind = kind == null ? ChatMessageKind.SYSTEM : kind;
        if (!safeKind.playerAuthored()) {
            return supplied == null ? ChatAuthor.system() : supplied;
        }
        ChatAuthor candidate = supplied == null ? ChatAuthor.legacy("") : supplied;
        String inferredName = candidate.searchableName();
        if (inferredName.isBlank() || "?".equals(inferredName)) {
            inferredName = ChatLegacyMessageNormalizer.inferAuthorName(message);
            candidate = ChatAuthor.legacy(inferredName);
        }
        AbstractClientPlayer player = candidate.playerId() == null
                ? findLoadedPlayerByName(inferredName)
                : findLoadedPlayerById(candidate.playerId());
        return player == null ? candidate : enrichVisualIdentity(candidate, player);
    }

    private static @Nullable AbstractClientPlayer findLoadedPlayerById(java.util.UUID playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (playerId.equals(player.getUUID())) {
                return player;
            }
        }
        return null;
    }

    private static @Nullable AbstractClientPlayer findLoadedPlayerByName(String playerName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || playerName.isBlank()) {
            return null;
        }
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (playerName.equalsIgnoreCase(player.getScoreboardName())
                    || playerName.equalsIgnoreCase(player.getDisplayName().getString())) {
                return player;
            }
        }
        return null;
    }

    private static ChatAuthor enrichVisualIdentity(ChatAuthor candidate, AbstractClientPlayer player) {
        Component displayName = player.getDisplayName();
        String fallbackName = player.getScoreboardName();
        if (candidate.playerId() == null) {
            return new ChatAuthor(
                    null,
                    displayName,
                    fallbackName,
                    teamSnapshot(player.getTeam()),
                    false);
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean localPlayer = minecraft != null
                && minecraft.player != null
                && candidate.playerId().equals(minecraft.player.getUUID());
        return new ChatAuthor(
                candidate.playerId(),
                displayName,
                fallbackName,
                teamSnapshot(player.getTeam()),
                localPlayer);
    }

    private static ChatTeamSnapshot teamSnapshot(@Nullable PlayerTeam team) {
        if (team == null) {
            return new ChatTeamSnapshot("", "", "", ChatTeamSnapshot.NO_COLOR);
        }
        //? if >=26.2 {
        int color = team.getColor()
                .map(net.minecraft.world.scores.TeamColor::rgb)
                .orElse(ChatTeamSnapshot.NO_COLOR);
        //? } else {
        /* int color = team.getColor().getColor() == null */
        /*         ? ChatTeamSnapshot.NO_COLOR */
        /*         : team.getColor().getColor(); */
        //? }
        return new ChatTeamSnapshot(
                team.getName(),
                team.getPlayerPrefix().getString(),
                team.getPlayerSuffix().getString(),
                color);
    }
}