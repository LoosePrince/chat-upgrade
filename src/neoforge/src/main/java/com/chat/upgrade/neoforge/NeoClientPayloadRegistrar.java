package com.chat.upgrade.neoforge;

import com.chat.upgrade.platform.net.ClientReceiver;
import com.chat.upgrade.platform.net.NetworkRegistrar;
import com.chat.upgrade.platform.net.ServerReceiver;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Registers S2C client handlers inside {@code RegisterClientPayloadHandlersEvent}. Type/codec and
 * server handlers are registered separately via {@link NeoPayloadRegistrar}.
 */
public final class NeoClientPayloadRegistrar implements NetworkRegistrar {
    private final RegisterClientPayloadHandlersEvent event;

    public NeoClientPayloadRegistrar(RegisterClientPayloadHandlersEvent event) {
        this.event = event;
    }

    @Override
    public <P extends CustomPacketPayload> void registerC2SType(
            CustomPacketPayload.Type<P> type, StreamCodec<? super RegistryFriendlyByteBuf, P> codec) {
        // handled by NeoPayloadRegistrar
    }

    @Override
    public <P extends CustomPacketPayload> void registerS2CType(
            CustomPacketPayload.Type<P> type, StreamCodec<? super RegistryFriendlyByteBuf, P> codec) {
        // handled by NeoPayloadRegistrar
    }

    @Override
    public <P extends CustomPacketPayload> void serverHandler(
            CustomPacketPayload.Type<P> type, ServerReceiver<P> handler) {
        // handled by NeoPayloadRegistrar
    }

    @Override
    public <P extends CustomPacketPayload> void clientHandler(
            CustomPacketPayload.Type<P> type, ClientReceiver<P> handler) {
        event.register(type, (payload, ctx) -> handler.receive(payload, ctx::enqueueWork));
    }
}
