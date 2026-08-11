package com.chat.upgrade.client.media.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class MediaFailureKindTest {
    @Test
    void mapsServerFailureCodesToSharedKinds() {
        assertEquals(MediaFailureKind.RESPONSE_BODY_TOO_LARGE,
                MediaFailureKind.fromServerCode("allocation_limits_exceeded"));
        assertEquals(MediaFailureKind.EXPIRED_FILE, MediaFailureKind.fromServerCode("expired"));
        assertEquals(MediaFailureKind.MISSING_FILE, MediaFailureKind.fromServerCode("not_found"));
        assertEquals(MediaFailureKind.UNAVAILABLE_FILE, MediaFailureKind.fromServerCode("access_denied"));
        assertEquals(MediaFailureKind.NETWORK_ERROR, MediaFailureKind.fromServerCode("network_error"));
        assertEquals(MediaFailureKind.INVALID_FILE, MediaFailureKind.fromServerCode("corrupt"));
        assertEquals(MediaFailureKind.UNKNOWN, MediaFailureKind.fromServerCode("unexpected_server_code"));
    }
}