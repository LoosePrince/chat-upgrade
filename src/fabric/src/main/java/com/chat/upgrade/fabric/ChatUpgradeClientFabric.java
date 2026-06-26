package com.chat.upgrade.fabric;

import com.chat.upgrade.client.ChatUpgradeClientBootstrap;
import com.chat.upgrade.client.ChatUpgradeCommands;
import com.chat.upgrade.client.net.servermedia.ServerMediaNetworking;
import com.chat.upgrade.platform.Platform;
import com.chat.upgrade.platform.net.Net;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Fabric client entry point. */
public final class ChatUpgradeClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Platform.bootstrap(new FabricPlatformServices());
        Net.bind(new FabricNetworkSender());
        ChatUpgradeClientBootstrap.init();

        ServerMediaNetworking.registerClientHandlers(new FabricNetworkRegistrar());

        ClientTickEvents.END_CLIENT_TICK.register(ChatUpgradeClientBootstrap::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(
                client -> ChatUpgradeClientBootstrap.clearAllMediaRuntimeState());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            ServerMediaNetworking.onClientDisconnect();
            ChatUpgradeClientBootstrap.clearAllMediaRuntimeState();
        }));
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> client.execute(ServerMediaNetworking::onClientJoin));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ChatUpgradeCommands.build(new FabricCommandAdapter())));
    }
}
