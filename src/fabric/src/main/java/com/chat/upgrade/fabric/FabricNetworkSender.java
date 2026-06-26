package com.chat.upgrade.fabric;

import com.chat.upgrade.platform.net.NetworkSender;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric send/capability bridge. The client-to-server methods reference {@link ClientPlayNetworking}
 * (a client-only class) but are only invoked on the client, so the class is never resolved on a
 * dedicated server (lazy linkage).
 */
public final class FabricNetworkSender implements NetworkSender {
    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        return ClientPlayNetworking.canSend(type);
    }

    @Override
    public void sendToClient(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public boolean canSendToClient(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type);
    }
}
