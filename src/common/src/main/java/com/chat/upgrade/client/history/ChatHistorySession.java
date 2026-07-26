package com.chat.upgrade.client.history;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Resolves the active client session to a stable local-history namespace. */
public final class ChatHistorySession {
    private ChatHistorySession() {
    }

    public static String resolve(Minecraft minecraft) {
        if (minecraft == null) {
            return "unknown";
        }
        MinecraftServer localServer = minecraft.getSingleplayerServer();
        if (localServer != null) {
            return "singleplayer:" + localServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        }
        ServerData server = minecraft.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return "multiplayer:" + server.ip.trim().toLowerCase(java.util.Locale.ROOT);
        }
        return "unknown";
    }
}