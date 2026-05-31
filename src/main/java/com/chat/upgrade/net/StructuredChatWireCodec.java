package com.chat.upgrade.net;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

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
        List<SegmentWire> wire = Objects.requireNonNullElse(segments, List.<StructuredChatSegment>of()).stream()
                .map(SegmentWire::from)
                .toList();
        return GSON.toJson(wire);
    }

    static List<StructuredChatSegment> decodeSegments(String json) {
        try {
            List<SegmentWire> wire = GSON.fromJson(safeJsonArray(json), SEGMENT_LIST);
            if (wire == null) {
                return List.of();
            }
            return wire.stream()
                    .map(SegmentWire::toDomain)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    static String encodeAttachments(List<StructuredAttachment> attachments) {
        List<AttachmentWire> wire = Objects.requireNonNullElse(attachments, List.<StructuredAttachment>of()).stream()
                .map(AttachmentWire::from)
                .toList();
        return GSON.toJson(wire);
    }

    static List<StructuredAttachment> decodeAttachments(String json) {
        try {
            List<AttachmentWire> wire = GSON.fromJson(safeJsonArray(json), ATTACHMENT_LIST);
            if (wire == null) {
                return List.of();
            }
            return wire.stream()
                    .map(AttachmentWire::toDomainOrNull)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String safeJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return "[]";
        }
        return json;
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
}