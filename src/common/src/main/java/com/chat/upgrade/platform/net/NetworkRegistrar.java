package com.chat.upgrade.platform.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Loader-agnostic payload registration surface.
 *
 * <p>Type registration is separated from handler registration so each loader can map it to
 * its native model: Fabric registers types in {@code PayloadTypeRegistry} and handlers in
 * {@code Server/ClientPlayNetworking}; NeoForge buffers the codecs and binds everything inside
 * {@code RegisterPayloadHandlersEvent}. Handler registration is also split by side so the
 * client-only handlers are never wired on a dedicated server.
 */
public interface NetworkRegistrar {
    <P extends CustomPacketPayload> void registerC2SType(
            CustomPacketPayload.Type<P> type, StreamCodec<? super RegistryFriendlyByteBuf, P> codec);

    <P extends CustomPacketPayload> void registerS2CType(
            CustomPacketPayload.Type<P> type, StreamCodec<? super RegistryFriendlyByteBuf, P> codec);

    <P extends CustomPacketPayload> void serverHandler(
            CustomPacketPayload.Type<P> type, ServerReceiver<P> handler);

    <P extends CustomPacketPayload> void clientHandler(
            CustomPacketPayload.Type<P> type, ClientReceiver<P> handler);
}
