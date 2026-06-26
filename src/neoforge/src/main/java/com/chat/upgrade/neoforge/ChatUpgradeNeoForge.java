package com.chat.upgrade.neoforge;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.net.ServerMediaPayloads;
import com.chat.upgrade.platform.Platform;
import com.chat.upgrade.platform.net.Net;
import com.chat.upgrade.server.ServerMediaServerNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NeoForge common/server entry point. */
@Mod(ChatUpgrade.MOD_ID)
public final class ChatUpgradeNeoForge {
    public ChatUpgradeNeoForge(IEventBus modBus, Dist dist) {
        Platform.bootstrap(new NeoForgePlatformServices());
        Net.bind(new NeoForgeNetworkSender());
        if (dist == Dist.CLIENT) {
            System.setProperty("java.awt.headless", "false");
        }
        ChatUpgrade.init();

        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            NeoPayloadRegistrar registrar = new NeoPayloadRegistrar(event.registrar("1"));
            ServerMediaPayloads.registerTypes(registrar);
            ServerMediaServerNetworking.registerServerHandlers(registrar);
        });

        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> ServerMediaServerNetworking.onServerTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerMediaServerNetworking.onPlayerJoin(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerMediaServerNetworking.onPlayerDisconnect(player);
            }
        });

        if (dist == Dist.CLIENT) {
            ChatUpgradeNeoForgeClient.init(modBus);
        }
    }
}
