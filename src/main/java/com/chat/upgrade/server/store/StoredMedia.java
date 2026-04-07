package com.chat.upgrade.server.store;

public record StoredMedia(
        String mediaId,
        String typeWire,
        String contentType,
        String fingerprint,
        byte[] body,
        long createdAtMs,
        long expiresAtMs) {
    public boolean isExpired(long nowMs) {
        return expiresAtMs > 0 && nowMs >= expiresAtMs;
    }

    public int byteLength() {
        return body == null ? 0 : body.length;
    }
}

