package com.chat.upgrade.fabric;

import com.chat.upgrade.platform.net.ClientReceiver;
import com.chat.upgrade.platform.net.NetworkRegistrar;
import com.chat.upgrade.platform.net.ServerPlayContext;
import com.chat.upgrade.platform.net.ServerReceiver;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric payload registration bridge. {@link #clientHandler} references the client-only
 * {@link ClientPlayNetworking} and must only be called from the client entry point.
 */
public final class FabricNetworkRegistrar implements NetworkRegistrar {
    @Override
    public <P extends CustomPacketPayload> void registerC2SType(
            CustomPacketPayload.Type<P> type, StreamCodec<? super RegistryFriendlyByteBuf, P> codec) {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
    }

    @Override
    public <P extends CustomPacketPayload> void registerS2CType(
            CustomPacketPayload.Type<P> type, StreamCodec<? super RegistryFriendlyByteBuf, P> codec) {
        PayloadTypeRegistry.clientboundPlay().register(type, codec);
    }

    @Override
    public <P extends CustomPacketPayload> void serverHandler(
            CustomPacketPayload.Type<P> type, ServerReceiver<P> handler) {
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, ctx) -> handler.receive(payload, new ServerPlayContext() {
            @Override
            public void execute(Runnable task) {
                ctx.server().execute(task);
            }

            @Override
            public ServerPlayer player() {
                return ctx.player();
            }

            @Override
            public MinecraftServer server() {
                return ctx.server();
            }
        }));
    }

    @Override
    public <P extends CustomPacketPayload> void clientHandler(
            CustomPacketPayload.Type<P> type, ClientReceiver<P> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type,
                (payload, ctx) -> handler.receive(payload, ctx.client()::execute));
    }
}
