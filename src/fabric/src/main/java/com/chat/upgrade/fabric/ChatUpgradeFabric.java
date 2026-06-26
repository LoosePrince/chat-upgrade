package com.chat.upgrade.fabric;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.net.ServerMediaPayloads;
import com.chat.upgrade.platform.Platform;
import com.chat.upgrade.platform.net.Net;
import com.chat.upgrade.server.ServerMediaServerNetworking;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** Fabric common/server entry point. */
public final class ChatUpgradeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Platform.bootstrap(new FabricPlatformServices());
        Net.bind(new FabricNetworkSender());
        ChatUpgrade.init();

        FabricNetworkRegistrar registrar = new FabricNetworkRegistrar();
        ServerMediaPayloads.registerTypes(registrar);
        ServerMediaServerNetworking.registerServerHandlers(registrar);

        ServerTickEvents.END_SERVER_TICK.register(ServerMediaServerNetworking::onServerTick);
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> ServerMediaServerNetworking.onPlayerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> ServerMediaServerNetworking.onPlayerDisconnect(handler.player));
    }
}
