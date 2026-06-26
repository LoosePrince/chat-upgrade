package com.chat.upgrade.platform.net;

import java.util.Objects;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Static access point for the loader-provided {@link NetworkSender}. */
public final class Net {
    private static volatile NetworkSender sender;

    private Net() {
    }

    public static void bind(NetworkSender impl) {
        sender = Objects.requireNonNull(impl, "network sender");
    }

    public static void sendToServer(CustomPacketPayload payload) {
        NetworkSender s = sender;
        if (s != null) {
            s.sendToServer(payload);
        }
    }

    public static boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        NetworkSender s = sender;
        return s != null && s.canSendToServer(type);
    }

    public static void sendToClient(ServerPlayer player, CustomPacketPayload payload) {
        NetworkSender s = sender;
        if (s != null) {
            s.sendToClient(player, payload);
        }
    }

    public static boolean canSendToClient(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        NetworkSender s = sender;
        return s != null && s.canSendToClient(player, type);
    }
}
