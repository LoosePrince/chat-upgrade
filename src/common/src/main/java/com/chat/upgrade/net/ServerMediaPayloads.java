package com.chat.upgrade.net;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.platform.net.NetworkRegistrar;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class ServerMediaPayloads {

    private static final StreamCodec<ByteBuf, String> STRUCTURED_JSON_CODEC =
            ByteBufCodecs.stringUtf8(StructuredChatProtocolLimits.MAX_WIRE_JSON_CHARS);
    private static final StreamCodec<ByteBuf, String> STRUCTURED_NONCE_CODEC =
            ByteBufCodecs.stringUtf8(StructuredChatProtocolLimits.MAX_CLIENT_NONCE_CHARS);
    private static final StreamCodec<ByteBuf, String> STRUCTURED_TEXT_CODEC =
            ByteBufCodecs.stringUtf8(StructuredChatProtocolLimits.MAX_FALLBACK_TEXT_CHARS);
    private static final StreamCodec<ByteBuf, String> STRUCTURED_NAME_CODEC =
            ByteBufCodecs.stringUtf8(StructuredChatProtocolLimits.MAX_DISPLAY_NAME_CHARS);
    private static final StreamCodec<ByteBuf, String> STRUCTURED_MESSAGE_ID_CODEC =
            ByteBufCodecs.stringUtf8(StructuredChatProtocolLimits.MAX_MESSAGE_ID_CHARS);

    private ServerMediaPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ChatUpgrade.MOD_ID, path));
    }

    /** Registers every payload type+codec (both directions) through the loader registrar. */
    public static void registerTypes(NetworkRegistrar r) {
        r.registerC2SType(C2SUploadInit.TYPE, C2SUploadInit.CODEC);
        r.registerC2SType(C2SUploadChunk.TYPE, C2SUploadChunk.CODEC);
        r.registerC2SType(C2SRequestMedia.TYPE, C2SRequestMedia.CODEC);
        r.registerC2SType(C2SAttachMetadata.TYPE, C2SAttachMetadata.CODEC);
        r.registerC2SType(C2SRequestAttachmentMeta.TYPE, C2SRequestAttachmentMeta.CODEC);
        r.registerC2SType(C2SChatInputMode.TYPE, C2SChatInputMode.CODEC);
        r.registerC2SType(C2SStructuredChatMessage.TYPE, C2SStructuredChatMessage.CODEC);
        r.registerC2SType(C2SStructuredChatV2.TYPE, C2SStructuredChatV2.CODEC);
        r.registerC2SType(C2SRetractChatMessage.TYPE, C2SRetractChatMessage.CODEC);

        r.registerS2CType(S2CCapability.TYPE, S2CCapability.CODEC);
        r.registerS2CType(S2CAttachmentCapability.TYPE, S2CAttachmentCapability.CODEC);
        r.registerS2CType(S2CStructuredChatAttachment.TYPE, S2CStructuredChatAttachment.CODEC);
        r.registerS2CType(S2CStructuredChatMessage.TYPE, S2CStructuredChatMessage.CODEC);
        r.registerS2CType(S2CStructuredChatV2.TYPE, S2CStructuredChatV2.CODEC);
        r.registerS2CType(S2CChatMutation.TYPE, S2CChatMutation.CODEC);
        r.registerS2CType(S2CUploadAck.TYPE, S2CUploadAck.CODEC);
        r.registerS2CType(S2CAttachmentAck.TYPE, S2CAttachmentAck.CODEC);
        r.registerS2CType(S2CAttachmentMeta.TYPE, S2CAttachmentMeta.CODEC);
        r.registerS2CType(S2CAttachmentError.TYPE, S2CAttachmentError.CODEC);
        r.registerS2CType(S2CMediaInit.TYPE, S2CMediaInit.CODEC);
        r.registerS2CType(S2CMediaChunk.TYPE, S2CMediaChunk.CODEC);
        r.registerS2CType(S2CMediaError.TYPE, S2CMediaError.CODEC);
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

    public record C2SChatInputMode(String mode) implements CustomPacketPayload {
        public C2SChatInputMode {
            mode = safeWire(mode);
        }

        public static final Type<C2SChatInputMode> TYPE = payloadType("c2s_chat_input_mode");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SChatInputMode> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, C2SChatInputMode::mode,
                C2SChatInputMode::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SStructuredChatMessage(
            int schemaVersion,
            String clientNonce,
            String plainText,
            String segmentsJson,
            String attachmentsJson,
            String fallbackText,
            int compatFlags) implements CustomPacketPayload {
        public C2SStructuredChatMessage {
            clientNonce = safeWire(clientNonce);
            plainText = safeWire(plainText);
            segmentsJson = safeWire(segmentsJson);
            attachmentsJson = safeWire(attachmentsJson);
            fallbackText = safeWire(fallbackText);
        }

        public static final Type<C2SStructuredChatMessage> TYPE = payloadType("c2s_structured_chat_message");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SStructuredChatMessage> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, C2SStructuredChatMessage::schemaVersion,
                STRUCTURED_NONCE_CODEC, C2SStructuredChatMessage::clientNonce,
                STRUCTURED_TEXT_CODEC, C2SStructuredChatMessage::plainText,
                STRUCTURED_JSON_CODEC, C2SStructuredChatMessage::segmentsJson,
                STRUCTURED_JSON_CODEC, C2SStructuredChatMessage::attachmentsJson,
                STRUCTURED_TEXT_CODEC, C2SStructuredChatMessage::fallbackText,
                ByteBufCodecs.VAR_INT, C2SStructuredChatMessage::compatFlags,
                C2SStructuredChatMessage::new);

        public static C2SStructuredChatMessage fromMessage(StructuredChatMessage message) {
            if (!StructuredChatProtocolLimits.accepts(message)) {
                throw new IllegalArgumentException("structured chat message exceeds protocol limits");
            }
            return new C2SStructuredChatMessage(
                    message.schemaVersion(),
                    message.clientNonce(),
                    message.plainText(),
                    StructuredChatWireCodec.encodeSegments(message.segments()),
                    StructuredChatWireCodec.encodeAttachments(message.attachments()),
                    message.fallbackText(),
                    message.compatFlags());
        }

        public java.util.Optional<StructuredChatMessage> toMessage() {
            java.util.Optional<java.util.List<StructuredChatSegment>> segments =
                    StructuredChatWireCodec.decodeSegments(segmentsJson);
            java.util.Optional<java.util.List<StructuredAttachment>> attachments =
                    StructuredChatWireCodec.decodeAttachments(attachmentsJson);
            if (schemaVersion < 1
                    || schemaVersion > StructuredChatMessage.CURRENT_SCHEMA_VERSION
                    || segments.isEmpty()
                    || attachments.isEmpty()) {
                return java.util.Optional.empty();
            }
            StructuredChatMessage message = new StructuredChatMessage(
                    schemaVersion,
                    clientNonce,
                    "",
                    plainText,
                    segments.get(),
                    attachments.get(),
                    fallbackText,
                    compatFlags);
            return StructuredChatProtocolLimits.accepts(message)
                    ? java.util.Optional.of(message)
                    : java.util.Optional.empty();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SStructuredChatV2(String envelopeJson) implements CustomPacketPayload {
        public C2SStructuredChatV2 {
            envelopeJson = safeWire(envelopeJson);
        }

        public static final Type<C2SStructuredChatV2> TYPE = payloadType("c2s_structured_chat_v2");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SStructuredChatV2> CODEC = StreamCodec.composite(
                STRUCTURED_JSON_CODEC, C2SStructuredChatV2::envelopeJson,
                C2SStructuredChatV2::new);

        public static C2SStructuredChatV2 fromSubmission(StructuredChatSubmission submission) {
            return new C2SStructuredChatV2(StructuredChatWireCodec.encodeSubmission(submission));
        }

        public java.util.Optional<StructuredChatSubmission> toSubmission() {
            return StructuredChatWireCodec.decodeSubmission(envelopeJson);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record C2SRetractChatMessage(String messageId) implements CustomPacketPayload {
        public C2SRetractChatMessage {
            messageId = safeWire(messageId).trim();
        }

        public static final Type<C2SRetractChatMessage> TYPE = payloadType("c2s_retract_chat_message");
        public static final StreamCodec<RegistryFriendlyByteBuf, C2SRetractChatMessage> CODEC = StreamCodec.composite(
                STRUCTURED_MESSAGE_ID_CODEC, C2SRetractChatMessage::messageId,
                C2SRetractChatMessage::new);

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

    public record S2CStructuredChatMessage(
            int schemaVersion,
            String clientNonce,
            String senderName,
            String plainText,
            String segmentsJson,
            String attachmentsJson,
            String fallbackText,
            int compatFlags) implements CustomPacketPayload {
        public S2CStructuredChatMessage {
            clientNonce = safeWire(clientNonce);
            senderName = safeWire(senderName);
            plainText = safeWire(plainText);
            segmentsJson = safeWire(segmentsJson);
            attachmentsJson = safeWire(attachmentsJson);
            fallbackText = safeWire(fallbackText);
        }

        public static final Type<S2CStructuredChatMessage> TYPE = payloadType("s2c_structured_chat_message");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CStructuredChatMessage> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, S2CStructuredChatMessage::schemaVersion,
                STRUCTURED_NONCE_CODEC, S2CStructuredChatMessage::clientNonce,
                STRUCTURED_NAME_CODEC, S2CStructuredChatMessage::senderName,
                STRUCTURED_TEXT_CODEC, S2CStructuredChatMessage::plainText,
                STRUCTURED_JSON_CODEC, S2CStructuredChatMessage::segmentsJson,
                STRUCTURED_JSON_CODEC, S2CStructuredChatMessage::attachmentsJson,
                STRUCTURED_TEXT_CODEC, S2CStructuredChatMessage::fallbackText,
                ByteBufCodecs.VAR_INT, S2CStructuredChatMessage::compatFlags,
                S2CStructuredChatMessage::new);

        public static S2CStructuredChatMessage fromMessage(StructuredChatMessage message) {
            if (!StructuredChatProtocolLimits.accepts(message)) {
                throw new IllegalArgumentException("structured chat message exceeds protocol limits");
            }
            return new S2CStructuredChatMessage(
                    message.schemaVersion(),
                    message.clientNonce(),
                    message.senderName(),
                    message.plainText(),
                    StructuredChatWireCodec.encodeSegments(message.segments()),
                    StructuredChatWireCodec.encodeAttachments(message.attachments()),
                    message.fallbackText(),
                    message.compatFlags());
        }

        public java.util.Optional<StructuredChatMessage> toMessage() {
            java.util.Optional<java.util.List<StructuredChatSegment>> segments =
                    StructuredChatWireCodec.decodeSegments(segmentsJson);
            java.util.Optional<java.util.List<StructuredAttachment>> attachments =
                    StructuredChatWireCodec.decodeAttachments(attachmentsJson);
            if (schemaVersion < 1
                    || schemaVersion > StructuredChatMessage.CURRENT_SCHEMA_VERSION
                    || segments.isEmpty()
                    || attachments.isEmpty()) {
                return java.util.Optional.empty();
            }
            StructuredChatMessage message = new StructuredChatMessage(
                    schemaVersion,
                    clientNonce,
                    senderName,
                    plainText,
                    segments.get(),
                    attachments.get(),
                    fallbackText,
                    compatFlags);
            return StructuredChatProtocolLimits.accepts(message)
                    ? java.util.Optional.of(message)
                    : java.util.Optional.empty();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CStructuredChatV2(String envelopeJson) implements CustomPacketPayload {
        public S2CStructuredChatV2 {
            envelopeJson = safeWire(envelopeJson);
        }

        public static final Type<S2CStructuredChatV2> TYPE = payloadType("s2c_structured_chat_v2");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CStructuredChatV2> CODEC = StreamCodec.composite(
                STRUCTURED_JSON_CODEC, S2CStructuredChatV2::envelopeJson,
                S2CStructuredChatV2::new);

        public static S2CStructuredChatV2 fromEnvelope(StructuredChatEnvelope envelope) {
            return new S2CStructuredChatV2(StructuredChatWireCodec.encodeEnvelope(envelope));
        }

        public java.util.Optional<StructuredChatEnvelope> toEnvelope() {
            return StructuredChatWireCodec.decodeEnvelope(envelopeJson);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record S2CChatMutation(String envelopeJson) implements CustomPacketPayload {
        public S2CChatMutation {
            envelopeJson = safeWire(envelopeJson);
        }

        public static final Type<S2CChatMutation> TYPE = payloadType("s2c_chat_mutation");
        public static final StreamCodec<RegistryFriendlyByteBuf, S2CChatMutation> CODEC = StreamCodec.composite(
                STRUCTURED_JSON_CODEC, S2CChatMutation::envelopeJson,
                S2CChatMutation::new);

        public static S2CChatMutation fromMutation(StructuredChatMutation mutation) {
            return new S2CChatMutation(StructuredChatWireCodec.encodeMutation(mutation));
        }

        public java.util.Optional<StructuredChatMutation> toMutation() {
            return StructuredChatWireCodec.decodeMutation(envelopeJson);
        }

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
