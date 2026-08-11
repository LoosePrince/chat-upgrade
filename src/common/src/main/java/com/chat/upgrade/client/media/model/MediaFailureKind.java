package com.chat.upgrade.client.media.model;

/** Stable client-side failure classification shared by every media type. */
public enum MediaFailureKind {
    UNKNOWN,
    RESPONSE_BODY_TOO_LARGE,
    INVALID_FILE,
    EXPIRED_FILE,
    MISSING_FILE,
    UNAVAILABLE_FILE,
    NETWORK_ERROR,
    DECODER_UNAVAILABLE,
    UNSUPPORTED_FORMAT;

    public static MediaFailureKind fromServerCode(String failureCode) {
        return switch (failureCode == null ? "" : failureCode) {
            case "response_too_large", "too_large", "allocation_limits_exceeded" -> RESPONSE_BODY_TOO_LARGE;
            case "expired" -> EXPIRED_FILE;
            case "not_found" -> MISSING_FILE;
            case "access_denied", "server_media_disabled", "rate_limited", "request_timeout" -> UNAVAILABLE_FILE;
            case "request_failed", "network_error" -> NETWORK_ERROR;
            case "invalid", "invalid_media", "invalid_metadata", "unexpected_type", "malformed_chunk", "invalid_file",
                    "corrupt" -> INVALID_FILE;
            default -> UNKNOWN;
        };
    }
}