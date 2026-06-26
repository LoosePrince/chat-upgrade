package com.chat.upgrade.platform.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Loader-agnostic payload send/capability surface. */
public interface NetworkSender {
    void sendToServer(CustomPacketPayload payload);

    boolean canSendToServer(CustomPacketPayload.Type<?> type);

    void sendToClient(ServerPlayer player, CustomPacketPayload payload);

    boolean canSendToClient(ServerPlayer player, CustomPacketPayload.Type<?> type);
}
