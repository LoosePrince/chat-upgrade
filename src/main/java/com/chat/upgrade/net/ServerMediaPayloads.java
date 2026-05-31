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
        registerPayload(c2s, C2SAttachMetadata.TYPE, C2SAttachMetadata.CODEC);
        registerPayload(c2s, C2SRequestAttachmentMeta.TYPE, C2SRequestAttachmentMeta.CODEC);

        registerPayload(s2c, S2CCapability.TYPE, S2CCapability.CODEC);
        registerPayload(s2c, S2CAttachmentCapability.TYPE, S2CAttachmentCapability.CODEC);
        registerPayload(s2c, S2CStructuredChatAttachment.TYPE, S2CStructuredChatAttachment.CODEC);
        registerPayload(s2c, S2CUploadAck.TYPE, S2CUploadAck.CODEC);
        registerPayload(s2c, S2CAttachmentAck.TYPE, S2CAttachmentAck.CODEC);
        registerPayload(s2c, S2CAttachmentMeta.TYPE, S2CAttachmentMeta.CODEC);
        registerPayload(s2c, S2CAttachmentError.TYPE, S2CAttachmentError.CODEC);
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
        public C2SRequestMedia {
            mediaId = safeWire(mediaId);
        }

        public static final Type<C2SRequestMedia> TYPE = payloadType("c2s_request_media");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestMedia> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, C2SRequestMedia::mediaId,
                C2SRequestMedia::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SAttachMetadata(
            long requestId,
            int schemaVersion,
            String attachmentId,
            String mediaId,
            String typeWire,
            String displayName,
            String fallbackUrl) implements CustomPacketPayload {
        public C2SAttachMetadata {
            attachmentId = safeWire(attachmentId);
            mediaId = safeWire(mediaId);
            typeWire = safeWire(typeWire);
            displayName = safeWire(displayName);
            fallbackUrl = safeWire(fallbackUrl);
        }

        public static final Type<C2SAttachMetadata> TYPE = payloadType("c2s_attach_metadata");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SAttachMetadata> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, C2SAttachMetadata::requestId,
                ByteBufCodecs.VAR_INT, C2SAttachMetadata::schemaVersion,
                ByteBufCodecs.STRING_UTF8, C2SAttachMetadata::attachmentId,
                ByteBufCodecs.STRING_UTF8, C2SAttachMetadata::mediaId,
                ByteBufCodecs.STRING_UTF8, C2SAttachMetadata::typeWire,
                ByteBufCodecs.STRING_UTF8, C2SAttachMetadata::displayName,
                ByteBufCodecs.STRING_UTF8, C2SAttachMetadata::fallbackUrl,
                C2SAttachMetadata::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRequestAttachmentMeta(
            long requestId,
            String attachmentId,
            String mediaId) implements CustomPacketPayload {
        public C2SRequestAttachmentMeta {
            attachmentId = safeWire(attachmentId);
            mediaId = safeWire(mediaId);
        }

        public static final Type<C2SRequestAttachmentMeta> TYPE = payloadType("c2s_request_attachment_meta");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestAttachmentMeta> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, C2SRequestAttachmentMeta::requestId,
                ByteBufCodecs.STRING_UTF8, C2SRequestAttachmentMeta::attachmentId,
                ByteBufCodecs.STRING_UTF8, C2SRequestAttachmentMeta::mediaId,
                C2SRequestAttachmentMeta::new);

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

    public record S2CAttachmentCapability(
            boolean enabled,
            int schemaVersion,
            int ttlSeconds) implements CustomPacketPayload {
        public static final Type<S2CAttachmentCapability> TYPE = payloadType("s2c_attachment_capability");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CAttachmentCapability> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, S2CAttachmentCapability::enabled,
                ByteBufCodecs.VAR_INT, S2CAttachmentCapability::schemaVersion,
                ByteBufCodecs.VAR_INT, S2CAttachmentCapability::ttlSeconds,
                S2CAttachmentCapability::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CStructuredChatAttachment(
            int schemaVersion,
            String senderName,
            String text,
            String attachmentId,
            String mediaId,
            String typeWire,
            String displayName,
            String fallbackUrl) implements CustomPacketPayload {
        public S2CStructuredChatAttachment {
            senderName = safeWire(senderName);
            text = safeWire(text);
            attachmentId = safeWire(attachmentId);
            mediaId = safeWire(mediaId);
            typeWire = safeWire(typeWire);
            displayName = safeWire(displayName);
            fallbackUrl = safeWire(fallbackUrl);
        }

        public static final Type<S2CStructuredChatAttachment> TYPE = payloadType("s2c_structured_chat_attachment");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CStructuredChatAttachment> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, S2CStructuredChatAttachment::schemaVersion,
                ByteBufCodecs.STRING_UTF8, S2CStructuredChatAttachment::senderName,
                ByteBufCodecs.STRING_UTF8, S2CStructuredChatAttachment::text,
                ByteBufCodecs.STRING_UTF8, S2CStructuredChatAttachment::attachmentId,
                ByteBufCodecs.STRING_UTF8, S2CStructuredChatAttachment::mediaId,
                ByteBufCodecs.STRING_UTF8, S2CStructuredChatAttachment::typeWire,
                ByteBufCodecs.STRING_UTF8, S2CStructuredChatAttachment::displayName,
                ByteBufCodecs.STRING_UTF8, S2CStructuredChatAttachment::fallbackUrl,
                S2CStructuredChatAttachment::new);

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
        public S2CUploadAck {
            mediaId = safeWire(mediaId);
            typeWire = safeWire(typeWire);
            specialUrl = safeWire(specialUrl);
        }

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

    public record S2CAttachmentAck(
            long requestId,
            int schemaVersion,
            String attachmentId,
            String mediaId,
            String typeWire,
            String displayName,
            String fallbackUrl) implements CustomPacketPayload {
        public S2CAttachmentAck {
            attachmentId = safeWire(attachmentId);
            mediaId = safeWire(mediaId);
            typeWire = safeWire(typeWire);
            displayName = safeWire(displayName);
            fallbackUrl = safeWire(fallbackUrl);
        }

        public static final Type<S2CAttachmentAck> TYPE = payloadType("s2c_attachment_ack");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CAttachmentAck> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, S2CAttachmentAck::requestId,
                ByteBufCodecs.VAR_INT, S2CAttachmentAck::schemaVersion,
                ByteBufCodecs.STRING_UTF8, S2CAttachmentAck::attachmentId,
                ByteBufCodecs.STRING_UTF8, S2CAttachmentAck::mediaId,
                ByteBufCodecs.STRING_UTF8, S2CAttachmentAck::typeWire,
                ByteBufCodecs.STRING_UTF8, S2CAttachmentAck::displayName,
                ByteBufCodecs.STRING_UTF8, S2CAttachmentAck::fallbackUrl,
                S2CAttachmentAck::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CAttachmentMeta(
            long requestId,
            int schemaVersion,
            String attachmentId,
            String mediaId,
            String typeWire,
            String displayName,
            String fallbackUrl) implements CustomPacketPayload {
        public S2CAttachmentMeta {
            attachmentId = safeWire(attachmentId);
            mediaId = safeWire(mediaId);
            typeWire = safeWire(typeWire);
            displayName = safeWire(displayName);
            fallbackUrl = safeWire(fallbackUrl);
        }

        public static final Type<S2CAttachmentMeta> TYPE = payloadType("s2c_attachment_meta");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CAttachmentMeta> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, S2CAttachmentMeta::requestId,
                ByteBufCodecs.VAR_INT, S2CAttachmentMeta::schemaVersion,
                ByteBufCodecs.STRING_UTF8, S2CAttachmentMeta::attachmentId,
                ByteBufCodecs.STRING_UTF8, S2CAttachmentMeta::mediaId,
                ByteBufCodecs.STRING_UTF8, S2CAttachmentMeta::typeWire,
                ByteBufCodecs.STRING_UTF8, S2CAttachmentMeta::displayName,
                ByteBufCodecs.STRING_UTF8, S2CAttachmentMeta::fallbackUrl,
                S2CAttachmentMeta::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CAttachmentError(
            long requestId,
            String attachmentId,
            String mediaId,
            String message) implements CustomPacketPayload {
        public S2CAttachmentError {
            attachmentId = safeWire(attachmentId);
            mediaId = safeWire(mediaId);
            message = safeWire(message);
        }

        public static final Type<S2CAttachmentError> TYPE = payloadType("s2c_attachment_error");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CAttachmentError> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, S2CAttachmentError::requestId,
                ByteBufCodecs.STRING_UTF8, S2CAttachmentError::attachmentId,
                ByteBufCodecs.STRING_UTF8, S2CAttachmentError::mediaId,
                ByteBufCodecs.STRING_UTF8, S2CAttachmentError::message,
                S2CAttachmentError::new);

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
        public S2CMediaError {
            mediaId = safeWire(mediaId);
            message = safeWire(message);
        }

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

    private static String safeWire(String value) {
        return value == null ? "" : value;
    }
}
