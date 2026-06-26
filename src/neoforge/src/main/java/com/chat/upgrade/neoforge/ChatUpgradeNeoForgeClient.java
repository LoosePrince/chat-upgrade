package com.chat.upgrade.neoforge;

import com.chat.upgrade.client.ChatUpgradeClientBootstrap;
import com.chat.upgrade.client.ChatUpgradeCommands;
import com.chat.upgrade.client.net.servermedia.ServerMediaNetworking;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge client setup. Kept in a separate, client-only class so the dedicated server never
 * loads client classes (the constructor only references it when {@code Dist.CLIENT}).
 */
public final class ChatUpgradeNeoForgeClient {
    private ChatUpgradeNeoForgeClient() {
    }

    public static void init(IEventBus modBus) {
        ChatUpgradeClientBootstrap.init();

        modBus.addListener(RegisterClientPayloadHandlersEvent.class,
                event -> ServerMediaNetworking.registerClientHandlers(new NeoClientPayloadRegistrar(event)));

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                event -> ChatUpgradeClientBootstrap.onClientTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class,
                event -> ServerMediaNetworking.onClientJoin());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            ServerMediaNetworking.onClientDisconnect();
            ChatUpgradeClientBootstrap.clearAllMediaRuntimeState();
        });
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class,
                event -> event.getDispatcher().register(ChatUpgradeCommands.build(new NeoForgeCommandAdapter())));
    }
}
