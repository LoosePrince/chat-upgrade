package com.chat.upgrade.neoforge;

import java.util.HashMap;
import java.util.Map;

import com.chat.upgrade.platform.net.ClientReceiver;
import com.chat.upgrade.platform.net.NetworkRegistrar;
import com.chat.upgrade.platform.net.ServerPlayContext;
import com.chat.upgrade.platform.net.ServerReceiver;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers payload types + server handlers inside {@code RegisterPayloadHandlersEvent}.
 * S2C client handlers are registered separately via {@link NeoClientPayloadRegistrar} in
 * {@code RegisterClientPayloadHandlersEvent}. Codecs from {@code registerC2SType} are buffered so
 * the combined NeoForge {@code playToServer(type, codec, handler)} call can be issued from
 * {@code serverHandler}.
 */
public final class NeoPayloadRegistrar implements NetworkRegistrar {
    private final PayloadRegistrar registrar;
    private final Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> c2sCodecs =
            new HashMap<>();

    public NeoPayloadRegistrar(PayloadRegistrar registrar) {
        this.registrar = registrar;
    }

    @Override
    public <P extends CustomPacketPayload> void registerC2SType(
            CustomPacketPayload.Type<P> type, StreamCodec<? super RegistryFriendlyByteBuf, P> codec) {
        c2sCodecs.put(type, codec);
    }

    @Override
    public <P extends CustomPacketPayload> void registerS2CType(
            CustomPacketPayload.Type<P> type, StreamCodec<? super RegistryFriendlyByteBuf, P> codec) {
        registrar.playToClient(type, codec);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <P extends CustomPacketPayload> void serverHandler(
            CustomPacketPayload.Type<P> type, ServerReceiver<P> handler) {
        StreamCodec<? super RegistryFriendlyByteBuf, P> codec =
                (StreamCodec<? super RegistryFriendlyByteBuf, P>) c2sCodecs.get(type);
        registrar.playToServer(type, codec, (payload, ctx) -> handler.receive(payload, new ServerPlayContext() {
            @Override
            public void execute(Runnable task) {
                ctx.enqueueWork(task);
            }

            @Override
            public ServerPlayer player() {
                return (ServerPlayer) ctx.player();
            }

            @Override
            public MinecraftServer server() {
                return ((ServerPlayer) ctx.player()).level().getServer();
            }
        }));
    }

    @Override
    public <P extends CustomPacketPayload> void clientHandler(
            CustomPacketPayload.Type<P> type, ClientReceiver<P> handler) {
        // Client handlers are registered via NeoClientPayloadRegistrar in the client payload event.
    }
}
