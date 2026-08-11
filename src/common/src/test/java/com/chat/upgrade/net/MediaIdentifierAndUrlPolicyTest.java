package com.chat.upgrade.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MediaIdentifierAndUrlPolicyTest {
    private static final String MEDIA_ID = "0123456789abcdef0123456789ABCDEF";

    @Test
    void acceptsOnlyFixedLengthHexMediaIdentifiers() {
        assertTrue(ServerMediaId.isValid(MEDIA_ID));
        assertFalse(ServerMediaId.isValid("0123456789abcdef"));
        assertFalse(ServerMediaId.isValid("0123456789abcdef0123456789abcdeg"));
        assertFalse(ServerMediaId.isValid("../0123456789abcdef0123456789abc"));
        assertFalse(ServerMediaId.isValid(null));
    }

    @Test
    void parsesOnlyCanonicalInternalMediaUrls() {
        ServerMediaUrl.Parsed parsed = ServerMediaUrl.parse(
                "chat-upgrade://media/image/" + MEDIA_ID).orElseThrow();
        assertEquals("0123456789abcdef0123456789abcdef", parsed.mediaId());
        assertEquals("image", parsed.typeWire());

        assertTrue(ServerMediaUrl.parse("chat-upgrade://media/audio/" + MEDIA_ID).isPresent());
        assertFalse(ServerMediaUrl.parse("chat-upgrade://media/document/" + MEDIA_ID).isPresent());
        assertFalse(ServerMediaUrl.parse("chat-upgrade://media/image/" + MEDIA_ID + "/extra").isPresent());
        assertFalse(ServerMediaUrl.parse("chat-upgrade://media/image/../" + MEDIA_ID).isPresent());
        assertFalse(ServerMediaUrl.parse("CHAT-UPGRADE://media/image/" + MEDIA_ID).isPresent());
    }

    @Test
    void externalPolicyRequiresCredentialFreeFragmentFreeHttpsUrl() {
        assertTrue(ExternalMediaUrlPolicy.isAllowed("https://cdn.example.com/media/image.png"));
        assertTrue(ExternalMediaUrlPolicy.isAllowed("HTTPS://cdn.example.com:443/media"));

        assertFalse(ExternalMediaUrlPolicy.isAllowed("https://cdn.example.com:8443/media"));

        assertFalse(ExternalMediaUrlPolicy.isAllowed("http://cdn.example.com/media"));
        assertFalse(ExternalMediaUrlPolicy.isAllowed("https://user:secret@cdn.example.com/media"));
        assertFalse(ExternalMediaUrlPolicy.isAllowed("https://cdn.example.com/media#fragment"));
        assertFalse(ExternalMediaUrlPolicy.isAllowed("https://cdn.example.com./media"));
        assertFalse(ExternalMediaUrlPolicy.isAllowed("https://cdn.example.com:0/media"));
        assertFalse(ExternalMediaUrlPolicy.isAllowed("https:///missing-host"));
        assertFalse(ExternalMediaUrlPolicy.isAllowed(" https://cdn.example.com/media "));
    }
}