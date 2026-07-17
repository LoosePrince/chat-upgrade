package com.chat.upgrade.net;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

final class StructuredChatWireCodec {
    private static final Gson GSON = new Gson();
    private static final Type SEGMENT_LIST = new TypeToken<List<SegmentWire>>() {
    }.getType();
    private static final Type ATTACHMENT_LIST = new TypeToken<List<AttachmentWire>>() {
    }.getType();

    private StructuredChatWireCodec() {
    }

    static String encodeSegments(List<StructuredChatSegment> segments) {
        List<StructuredChatSegment> safeSegments = Objects.requireNonNullElse(segments, List.of());
        if (!StructuredChatProtocolLimits.acceptsSegments(safeSegments)) {
            throw new IllegalArgumentException("structured chat segments exceed protocol limits");
        }
        List<SegmentWire> wire = safeSegments.stream()
                .map(SegmentWire::from)
                .toList();
        return GSON.toJson(wire);
    }

    static Optional<List<StructuredChatSegment>> decodeSegments(String json) {
        if (json == null || json.isBlank()) {
            return Optional.of(List.of());
        }
        if (!StructuredChatProtocolLimits.acceptsWireJson(json)) {
            return Optional.empty();
        }
        try {
            List<SegmentWire> wire = GSON.fromJson(json, SEGMENT_LIST);
            if (wire == null
                    || wire.size() > StructuredChatProtocolLimits.MAX_SEGMENTS
                    || wire.stream().anyMatch(Objects::isNull)) {
                return Optional.empty();
            }
            List<StructuredChatSegment> segments = wire.stream()
                    .map(SegmentWire::toDomain)
                    .toList();
            return StructuredChatProtocolLimits.acceptsSegments(segments)
                    ? Optional.of(segments)
                    : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    static String encodeAttachments(List<StructuredAttachment> attachments) {
        List<StructuredAttachment> safeAttachments = Objects.requireNonNullElse(attachments, List.of());
        if (!StructuredChatProtocolLimits.acceptsAttachments(safeAttachments)) {
            throw new IllegalArgumentException("structured chat attachments exceed protocol limits");
        }
        List<AttachmentWire> wire = safeAttachments.stream()
                .map(AttachmentWire::from)
                .toList();
        return GSON.toJson(wire);
    }

    static Optional<List<StructuredAttachment>> decodeAttachments(String json) {
        if (json == null || json.isBlank()) {
            return Optional.of(List.of());
        }
        if (!StructuredChatProtocolLimits.acceptsWireJson(json)) {
            return Optional.empty();
        }
        try {
            List<AttachmentWire> wire = GSON.fromJson(json, ATTACHMENT_LIST);
            if (wire == null
                    || wire.size() > StructuredChatProtocolLimits.MAX_ATTACHMENTS
                    || wire.stream().anyMatch(Objects::isNull)) {
                return Optional.empty();
            }
            List<StructuredAttachment> attachments = wire.stream()
                    .map(AttachmentWire::toDomainOrNull)
                    .toList();
            if (attachments.stream().anyMatch(Objects::isNull)) {
                return Optional.empty();
            }
            return StructuredChatProtocolLimits.acceptsAttachments(attachments)
                    ? Optional.of(attachments)
                    : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    static String encodeSubmission(StructuredChatSubmission submission) {
        Objects.requireNonNull(submission, "submission");
        if (!StructuredChatProtocolLimits.accepts(submission)) {
            throw new IllegalArgumentException("structured chat submission exceeds protocol limits");
        }
        return GSON.toJson(SubmissionWire.from(submission));
    }

    static Optional<StructuredChatSubmission> decodeSubmission(String json) {
        if (!StructuredChatProtocolLimits.acceptsWireJson(json)) {
            return Optional.empty();
        }
        try {
            SubmissionWire wire = GSON.fromJson(json, SubmissionWire.class);
            return wire == null ? Optional.empty() : wire.toDomain();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    static String encodeEnvelope(StructuredChatEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (!StructuredChatProtocolLimits.accepts(envelope)) {
            throw new IllegalArgumentException("structured chat envelope exceeds protocol limits");
        }
        return GSON.toJson(EnvelopeWire.from(envelope));
    }

    static Optional<StructuredChatEnvelope> decodeEnvelope(String json) {
        if (!StructuredChatProtocolLimits.acceptsWireJson(json)) {
            return Optional.empty();
        }
        try {
            EnvelopeWire wire = GSON.fromJson(json, EnvelopeWire.class);
            return wire == null ? Optional.empty() : wire.toDomain();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    static String encodeMutation(StructuredChatMutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (!StructuredChatProtocolLimits.accepts(mutation)) {
            throw new IllegalArgumentException("structured chat mutation exceeds protocol limits");
        }
        return GSON.toJson(mutation);
    }

    static Optional<StructuredChatMutation> decodeMutation(String json) {
        if (!StructuredChatProtocolLimits.acceptsWireJson(json)) {
            return Optional.empty();
        }
        try {
            StructuredChatMutation mutation = GSON.fromJson(json, StructuredChatMutation.class);
            return StructuredChatProtocolLimits.accepts(mutation) ? Optional.of(mutation) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private record SegmentWire(String kind, String text, String attachmentId) {
        static SegmentWire from(StructuredChatSegment segment) {
            return new SegmentWire(segment.kind(), segment.text(), segment.attachmentId());
        }

        StructuredChatSegment toDomain() {
            return new StructuredChatSegment(kind, text, attachmentId);
        }
    }

    private record AttachmentWire(
            int schemaVersion,
            String attachmentId,
            String mediaId,
            String typeWire,
            String displayName,
            String fallbackUrl) {
        static AttachmentWire from(StructuredAttachment attachment) {
            return new AttachmentWire(
                    attachment.schemaVersion(),
                    attachment.attachmentId(),
                    attachment.mediaId(),
                    attachment.typeWire(),
                    attachment.displayName(),
                    attachment.fallbackUrl());
        }

        StructuredAttachment toDomainOrNull() {
            try {
                return new StructuredAttachment(
                        schemaVersion,
                        attachmentId,
                        mediaId,
                        typeWire,
                        displayName,
                        fallbackUrl);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private record AuthorWire(
            String playerUuid,
            String displayName,
            String teamName,
            String teamPrefix,
            String teamSuffix,
            int teamColorRgb) {
        static AuthorWire from(StructuredChatAuthor author) {
            return new AuthorWire(
                    author.playerUuid(),
                    author.displayName(),
                    author.teamName(),
                    author.teamPrefix(),
                    author.teamSuffix(),
                    author.teamColorRgb());
        }

        StructuredChatAuthor toDomain() {
            return new StructuredChatAuthor(
                    playerUuid,
                    displayName,
                    teamName,
                    teamPrefix,
                    teamSuffix,
                    teamColorRgb);
        }
    }

    private record ReplyWire(String messageId, String authorDisplayName, String excerpt) {
        static ReplyWire from(StructuredReplySummary reply) {
            return reply == null ? null : new ReplyWire(
                    reply.messageId(),
                    reply.authorDisplayName(),
                    reply.excerpt());
        }

        StructuredReplySummary toDomain() {
            return new StructuredReplySummary(messageId, authorDisplayName, excerpt);
        }
    }

    private record SubmissionWire(
            int schemaVersion,
            String clientNonce,
            String plainText,
            List<SegmentWire> segments,
            List<AttachmentWire> attachments,
            String replyToMessageId) {
        static SubmissionWire from(StructuredChatSubmission submission) {
            return new SubmissionWire(
                    submission.schemaVersion(),
                    submission.clientNonce(),
                    submission.plainText(),
                    submission.segments().stream().map(SegmentWire::from).toList(),
                    submission.attachments().stream().map(AttachmentWire::from).toList(),
                    submission.replyToMessageId());
        }

        Optional<StructuredChatSubmission> toDomain() {
            if (segments != null && segments.size() > StructuredChatProtocolLimits.MAX_SEGMENTS) {
                return Optional.empty();
            }
            if (attachments != null && attachments.size() > StructuredChatProtocolLimits.MAX_ATTACHMENTS) {
                return Optional.empty();
            }
            List<StructuredChatSegment> safeSegments = Objects.requireNonNullElse(segments, List.<SegmentWire>of())
                    .stream()
                    .filter(Objects::nonNull)
                    .map(SegmentWire::toDomain)
                    .toList();
            List<StructuredAttachment> safeAttachments = Objects.requireNonNullElse(attachments, List.<AttachmentWire>of())
                    .stream()
                    .filter(Objects::nonNull)
                    .map(AttachmentWire::toDomainOrNull)
                    .filter(Objects::nonNull)
                    .toList();
            StructuredChatSubmission submission = new StructuredChatSubmission(
                    schemaVersion,
                    clientNonce,
                    plainText,
                    safeSegments,
                    safeAttachments,
                    replyToMessageId);
            return StructuredChatProtocolLimits.accepts(submission)
                    ? Optional.of(submission)
                    : Optional.empty();
        }
    }

    private record EnvelopeWire(
            int schemaVersion,
            String messageId,
            String clientNonce,
            long serverTimestampMs,
            AuthorWire author,
            String kind,
            String plainText,
            List<SegmentWire> segments,
            List<AttachmentWire> attachments,
            String fallbackText,
            int compatFlags,
            ReplyWire replyTo) {
        static EnvelopeWire from(StructuredChatEnvelope envelope) {
            return new EnvelopeWire(
                    envelope.schemaVersion(),
                    envelope.messageId(),
                    envelope.clientNonce(),
                    envelope.serverTimestampMs(),
                    AuthorWire.from(envelope.author()),
                    envelope.kind(),
                    envelope.plainText(),
                    envelope.segments().stream().map(SegmentWire::from).toList(),
                    envelope.attachments().stream().map(AttachmentWire::from).toList(),
                    envelope.fallbackText(),
                    envelope.compatFlags(),
                    ReplyWire.from(envelope.replyTo()));
        }

        Optional<StructuredChatEnvelope> toDomain() {
            if (author == null) {
                return Optional.empty();
            }
            if (segments != null && segments.size() > StructuredChatProtocolLimits.MAX_SEGMENTS) {
                return Optional.empty();
            }
            if (attachments != null && attachments.size() > StructuredChatProtocolLimits.MAX_ATTACHMENTS) {
                return Optional.empty();
            }
            List<StructuredChatSegment> safeSegments = Objects.requireNonNullElse(segments, List.<SegmentWire>of())
                    .stream()
                    .filter(Objects::nonNull)
                    .map(SegmentWire::toDomain)
                    .toList();
            List<StructuredAttachment> safeAttachments = Objects.requireNonNullElse(attachments, List.<AttachmentWire>of())
                    .stream()
                    .filter(Objects::nonNull)
                    .map(AttachmentWire::toDomainOrNull)
                    .filter(Objects::nonNull)
                    .toList();
            StructuredChatEnvelope envelope = new StructuredChatEnvelope(
                    schemaVersion,
                    messageId,
                    clientNonce,
                    serverTimestampMs,
                    author.toDomain(),
                    kind,
                    plainText,
                    safeSegments,
                    safeAttachments,
                    fallbackText,
                    compatFlags,
                    replyTo == null ? null : replyTo.toDomain());
            return StructuredChatProtocolLimits.accepts(envelope)
                    ? Optional.of(envelope)
                    : Optional.empty();
        }
    }
}