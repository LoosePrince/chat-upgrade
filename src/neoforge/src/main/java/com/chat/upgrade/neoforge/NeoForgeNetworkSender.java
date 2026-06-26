package com.chat.upgrade.neoforge;

import com.chat.upgrade.platform.net.NetworkSender;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge send/capability bridge. The client-to-server methods reference client-only
 * NeoForge/MC classes and are only invoked on the client (lazy linkage on a dedicated server).
 */
public final class NeoForgeNetworkSender implements NetworkSender {
    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    @Override
    public boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null && mc.getConnection().hasChannel(type);
    }

    @Override
    public void sendToClient(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public boolean canSendToClient(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection.hasChannel(type);
    }
}
