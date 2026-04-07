package com.chat.upgrade.net;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import com.chat.upgrade.ChatUpgrade;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ServerMediaPayloads {
    private static boolean registered = false;

    private ServerMediaPayloads() {
    }

    public static void registerAll() {
        if (registered) {
            return;
        }
        registered = true;

        Object c2s = resolveRegistry("serverboundPlay", "playC2S", "c2s", "serverbound");
        Object s2c = resolveRegistry("clientboundPlay", "playS2C", "s2c", "clientbound");
        if (c2s == null || s2c == null) {
            throw new IllegalStateException("chat-upgrade: cannot resolve payload registries for current Fabric API");
        }

        registerPayload(c2s, C2SUploadInit.TYPE, C2SUploadInit.CODEC);
        registerPayload(c2s, C2SUploadChunk.TYPE, C2SUploadChunk.CODEC);
        registerPayload(c2s, C2SRequestMedia.TYPE, C2SRequestMedia.CODEC);

        registerPayload(s2c, S2CCapability.TYPE, S2CCapability.CODEC);
        registerPayload(s2c, S2CUploadAck.TYPE, S2CUploadAck.CODEC);
        registerPayload(s2c, S2CMediaInit.TYPE, S2CMediaInit.CODEC);
        registerPayload(s2c, S2CMediaChunk.TYPE, S2CMediaChunk.CODEC);
        registerPayload(s2c, S2CMediaError.TYPE, S2CMediaError.CODEC);
    }

    private static Object resolveRegistry(String... candidates) {
        for (String name : candidates) {
            try {
                Method method = PayloadTypeRegistry.class.getMethod(name);
                return method.invoke(null);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static void registerPayload(
            Object registry,
            CustomPacketPayload.Type<?> type,
            StreamCodec<RegistryFriendlyByteBuf, ?> codec) {
        try {
            Method register = registry.getClass().getMethod("register", type.getClass(), codec.getClass());
            register.invoke(registry, type, codec);
            return;
        } catch (ReflectiveOperationException ignored) {
        }

        for (Method method : registry.getClass().getMethods()) {
            if (!"register".equals(method.getName()) || method.getParameterCount() != 2) {
                continue;
            }
            try {
                method.invoke(registry, type, codec);
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        throw new IllegalStateException("chat-upgrade: cannot register payload " + type.id());
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        String full = ChatUpgrade.MOD_ID + ":" + path;
        for (Constructor<?> ctor : CustomPacketPayload.Type.class.getConstructors()) {
            if (ctor.getParameterCount() != 1) {
                continue;
            }
            Class<?> idClass = ctor.getParameterTypes()[0];
            Object id = buildIdentifier(idClass, full);
            if (id == null) {
                continue;
            }
            try {
                return (CustomPacketPayload.Type<T>) ctor.newInstance(id);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        throw new IllegalStateException("chat-upgrade: cannot construct payload type for " + full);
    }

    private static Object buildIdentifier(Class<?> idClass, String full) {
        if (idClass == String.class) {
            return full;
        }
        int colon = full.indexOf(':');
        String namespace = colon > 0 ? full.substring(0, colon) : ChatUpgrade.MOD_ID;
        String path = colon > 0 && colon < full.length() - 1 ? full.substring(colon + 1) : full;

        for (String staticFactory : new String[] { "fromNamespaceAndPath", "of", "parse", "tryParse" }) {
            try {
                Method m = idClass.getMethod(staticFactory, String.class, String.class);
                return m.invoke(null, namespace, path);
            } catch (ReflectiveOperationException ignored) {
            }
            try {
                Method m = idClass.getMethod(staticFactory, String.class);
                Object out = m.invoke(null, full);
                if (out != null) {
                    return out;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        try {
            Constructor<?> ctor = idClass.getConstructor(String.class, String.class);
            return ctor.newInstance(namespace, path);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Constructor<?> ctor = idClass.getConstructor(String.class);
            return ctor.newInstance(full);
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    public record C2SUploadInit(
            long uploadId,
            String typeWire,
            String fileName,
            String contentType,
            int totalLen,
            int totalChunks) implements CustomPacketPayload {
        public static final Type<C2SUploadInit> TYPE = payloadType("c2s_upload_init");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SUploadInit> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, C2SUploadInit::uploadId,
                ByteBufCodecs.STRING_UTF8, C2SUploadInit::typeWire,
                ByteBufCodecs.STRING_UTF8, C2SUploadInit::fileName,
                ByteBufCodecs.STRING_UTF8, C2SUploadInit::contentType,
                ByteBufCodecs.VAR_INT, C2SUploadInit::totalLen,
                ByteBufCodecs.VAR_INT, C2SUploadInit::totalChunks,
                C2SUploadInit::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SUploadChunk(
            long uploadId,
            int idx,
            byte[] chunk) implements CustomPacketPayload {
        public static final Type<C2SUploadChunk> TYPE = payloadType("c2s_upload_chunk");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SUploadChunk> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, C2SUploadChunk::uploadId,
                ByteBufCodecs.VAR_INT, C2SUploadChunk::idx,
                ByteBufCodecs.BYTE_ARRAY, C2SUploadChunk::chunk,
                C2SUploadChunk::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRequestMedia(String mediaId) implements CustomPacketPayload {
        public static final Type<C2SRequestMedia> TYPE = payloadType("c2s_request_media");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestMedia> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, C2SRequestMedia::mediaId,
                C2SRequestMedia::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CCapability(
            boolean enabled,
            int maxSingleBytes,
            int maxChunkBytes,
            byte storageMode,
            int ttlSeconds) implements CustomPacketPayload {
        public static final Type<S2CCapability> TYPE = payloadType("s2c_capability");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CCapability> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, S2CCapability::enabled,
                ByteBufCodecs.VAR_INT, S2CCapability::maxSingleBytes,
                ByteBufCodecs.VAR_INT, S2CCapability::maxChunkBytes,
                ByteBufCodecs.BYTE, S2CCapability::storageMode,
                ByteBufCodecs.VAR_INT, S2CCapability::ttlSeconds,
                S2CCapability::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CUploadAck(
            long uploadId,
            String mediaId,
            String typeWire,
            String specialUrl) implements CustomPacketPayload {
        public static final Type<S2CUploadAck> TYPE = payloadType("s2c_upload_ack");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CUploadAck> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, S2CUploadAck::uploadId,
                ByteBufCodecs.STRING_UTF8, S2CUploadAck::mediaId,
                ByteBufCodecs.STRING_UTF8, S2CUploadAck::typeWire,
                ByteBufCodecs.STRING_UTF8, S2CUploadAck::specialUrl,
                S2CUploadAck::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CMediaInit(
            String mediaId,
            String typeWire,
            String contentType,
            String md5Hex,
            int totalLen,
            int totalChunks) implements CustomPacketPayload {
        public static final Type<S2CMediaInit> TYPE = payloadType("s2c_media_init");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CMediaInit> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, S2CMediaInit::mediaId,
                ByteBufCodecs.STRING_UTF8, S2CMediaInit::typeWire,
                ByteBufCodecs.STRING_UTF8, S2CMediaInit::contentType,
                ByteBufCodecs.STRING_UTF8, S2CMediaInit::md5Hex,
                ByteBufCodecs.VAR_INT, S2CMediaInit::totalLen,
                ByteBufCodecs.VAR_INT, S2CMediaInit::totalChunks,
                S2CMediaInit::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CMediaChunk(
            String mediaId,
            int idx,
            byte[] chunk) implements CustomPacketPayload {
        public static final Type<S2CMediaChunk> TYPE = payloadType("s2c_media_chunk");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CMediaChunk> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, S2CMediaChunk::mediaId,
                ByteBufCodecs.VAR_INT, S2CMediaChunk::idx,
                ByteBufCodecs.BYTE_ARRAY, S2CMediaChunk::chunk,
                S2CMediaChunk::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CMediaError(
            String mediaId,
            String message) implements CustomPacketPayload {
        public static final Type<S2CMediaError> TYPE = payloadType("s2c_media_error");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CMediaError> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, S2CMediaError::mediaId,
                ByteBufCodecs.STRING_UTF8, S2CMediaError::message,
                S2CMediaError::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
