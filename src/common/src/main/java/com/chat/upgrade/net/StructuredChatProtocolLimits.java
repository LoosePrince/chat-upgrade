package com.chat.upgrade.net;

import java.util.List;

import org.jetbrains.annotations.Nullable;

public final class StructuredChatProtocolLimits {
    public static final int MAX_WIRE_JSON_CHARS = 48 * 1024;
    public static final int MAX_JSON_DEPTH = 32;
    public static final int MAX_CLIENT_NONCE_CHARS = 64;
    public static final int MAX_MESSAGE_ID_CHARS = 64;
    public static final int MAX_PLAIN_TEXT_CHARS = 2_048;
    public static final int MAX_FALLBACK_TEXT_CHARS = 4_096;
    public static final int MAX_SEGMENTS = 64;
    public static final int MAX_SEGMENT_TEXT_CHARS = 2_048;
    public static final int MAX_ATTACHMENTS = 8;
    public static final int MAX_ATTACHMENT_ID_CHARS = 128;
    public static final int MAX_MEDIA_ID_CHARS = 128;
    public static final int MAX_DISPLAY_NAME_CHARS = 256;
    public static final int MAX_TEAM_FIELD_CHARS = 256;
    public static final int MAX_URL_CHARS = 2_048;
    public static final int MAX_REPLY_EXCERPT_CHARS = 128;

    private StructuredChatProtocolLimits() {
    }

    public static boolean accepts(StructuredChatMessage message) {
        return message != null
                && supportedSchema(message.schemaVersion(), StructuredChatMessage.CURRENT_SCHEMA_VERSION)
                && fits(message.clientNonce(), MAX_CLIENT_NONCE_CHARS)
                && fits(message.senderName(), MAX_DISPLAY_NAME_CHARS)
                && fits(message.plainText(), MAX_FALLBACK_TEXT_CHARS)
                && fits(message.fallbackText(), MAX_FALLBACK_TEXT_CHARS)
                && acceptsSegments(message.segments())
                && acceptsAttachments(message.attachments())
                && hasVisibleContent(message.plainText(), message.segments(), message.attachments());
    }

    public static boolean accepts(StructuredChatSubmission submission) {
        return submission != null
                && supportedSchema(submission.schemaVersion(), StructuredChatSubmission.CURRENT_SCHEMA_VERSION)
                && fits(submission.clientNonce(), MAX_CLIENT_NONCE_CHARS)
                && fits(submission.plainText(), MAX_PLAIN_TEXT_CHARS)
                && optionalId(submission.replyToMessageId(), MAX_MESSAGE_ID_CHARS)
                && acceptsSegments(submission.segments())
                && acceptsAttachments(submission.attachments())
                && hasVisibleContent(submission.plainText(), submission.segments(), submission.attachments());
    }

    public static boolean accepts(StructuredChatEnvelope envelope) {
        return envelope != null
                && supportedSchema(envelope.schemaVersion(), StructuredChatEnvelope.CURRENT_SCHEMA_VERSION)
                && requiredId(envelope.messageId(), MAX_MESSAGE_ID_CHARS)
                && fits(envelope.clientNonce(), MAX_CLIENT_NONCE_CHARS)
                && fits(envelope.kind(), 32)
                && fits(envelope.plainText(), MAX_PLAIN_TEXT_CHARS)
                && fits(envelope.fallbackText(), MAX_FALLBACK_TEXT_CHARS)
                && accepts(envelope.author())
                && accepts(envelope.replyTo())
                && acceptsSegments(envelope.segments())
                && acceptsAttachments(envelope.attachments());
    }

    public static boolean accepts(StructuredChatMutation mutation) {
        return mutation != null
                && supportedSchema(mutation.schemaVersion(), StructuredChatMutation.CURRENT_SCHEMA_VERSION)
                && requiredId(mutation.messageId(), MAX_MESSAGE_ID_CHARS)
                && StructuredChatMutation.RETRACTED.equals(mutation.mutation());
    }

    public static boolean acceptsWireJson(@Nullable String json) {
        if (json == null || json.isBlank() || json.length() > MAX_WIRE_JSON_CHARS) {
            return false;
        }
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char current = json.charAt(i);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
                continue;
            }
            if (current == '"') {
                quoted = true;
            } else if (current == '{' || current == '[') {
                depth++;
                if (depth > MAX_JSON_DEPTH) {
                    return false;
                }
            } else if (current == '}' || current == ']') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return !quoted && depth == 0;
    }

    public static boolean acceptsSegments(@Nullable List<StructuredChatSegment> segments) {
        if (segments == null || segments.size() > MAX_SEGMENTS) {
            return false;
        }
        int aggregateTextChars = 0;
        for (StructuredChatSegment segment : segments) {
            if (segment == null
                    || !fits(segment.kind(), 16)
                    || !fits(segment.text(), MAX_SEGMENT_TEXT_CHARS)
                    || !optionalId(segment.attachmentId(), MAX_ATTACHMENT_ID_CHARS)) {
                return false;
            }
            aggregateTextChars += segment.text().length();
            if (aggregateTextChars > MAX_FALLBACK_TEXT_CHARS) {
                return false;
            }
        }
        return true;
    }

    public static boolean acceptsAttachments(@Nullable List<StructuredAttachment> attachments) {
        if (attachments == null || attachments.size() > MAX_ATTACHMENTS) {
            return false;
        }
        for (StructuredAttachment attachment : attachments) {
            if (!acceptsAttachment(attachment)) {
                return false;
            }
        }
        return true;
    }

    public static boolean validMessageId(@Nullable String messageId) {
        return requiredId(messageId, MAX_MESSAGE_ID_CHARS);
    }

    private static boolean hasVisibleContent(
            @Nullable String plainText,
            @Nullable List<StructuredChatSegment> segments,
            @Nullable List<StructuredAttachment> attachments) {
        if (plainText != null && !plainText.isBlank()) {
            return true;
        }
        if (attachments != null && !attachments.isEmpty()) {
            return true;
        }
        return segments != null && segments.stream()
                .anyMatch(segment -> segment != null && segment.isText() && !segment.text().isBlank());
    }

    public static boolean acceptsAttachment(@Nullable StructuredAttachment attachment) {
        return attachment != null
                && supportedSchema(attachment.schemaVersion(), StructuredAttachment.CURRENT_SCHEMA_VERSION)
                && optionalServerId(attachment.attachmentId())
                && optionalServerId(attachment.mediaId())
                && fits(attachment.typeWire(), 16)
                && fits(attachment.displayName(), MAX_DISPLAY_NAME_CHARS)
                && fits(attachment.fallbackUrl(), MAX_URL_CHARS);
    }

    private static boolean accepts(@Nullable StructuredChatAuthor author) {
        return author != null
                && optionalId(author.playerUuid(), 64)
                && fits(author.displayName(), MAX_DISPLAY_NAME_CHARS)
                && fits(author.teamName(), MAX_TEAM_FIELD_CHARS)
                && fits(author.teamPrefix(), MAX_TEAM_FIELD_CHARS)
                && fits(author.teamSuffix(), MAX_TEAM_FIELD_CHARS);
    }

    private static boolean accepts(@Nullable StructuredReplySummary reply) {
        return reply == null
                || (requiredId(reply.messageId(), MAX_MESSAGE_ID_CHARS)
                        && fits(reply.authorDisplayName(), MAX_DISPLAY_NAME_CHARS)
                        && fits(reply.excerpt(), MAX_REPLY_EXCERPT_CHARS));
    }

    private static boolean supportedSchema(int value, int current) {
        return value >= 1 && value <= current;
    }

    private static boolean requiredId(@Nullable String value, int maxChars) {
        return value != null && !value.isBlank() && value.length() <= maxChars;
    }

    private static boolean optionalServerId(@Nullable String value) {
        return value == null || value.isBlank() || ServerMediaId.isValid(value);
    }

    private static boolean optionalId(@Nullable String value, int maxChars) {
        return value == null || value.isBlank() || value.length() <= maxChars;
    }

    private static boolean fits(@Nullable String value, int maxChars) {
        return value == null || value.length() <= maxChars;
    }
}