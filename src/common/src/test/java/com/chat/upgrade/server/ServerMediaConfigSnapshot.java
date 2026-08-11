package com.chat.upgrade.server;

record ServerMediaConfigSnapshot(
        int maxSingleBytes,
        int maxChunkBytes,
        int ttlSeconds,
        int uploadTimeoutSeconds,
        int maxPendingUploadsPerPlayer,
        int maxPendingUploadsGlobal,
        long maxPendingBytesPerPlayer,
        long maxPendingBytesGlobal,
        int maxStructuredMessagesPer10Seconds,
        int maxUploadPacketsPer10Seconds,
        int maxMediaRequestsPer10Seconds,
        int maxAttachmentWritesPerMinute,
        int maxHistoryRequestsPerMinute,
        boolean allowExternalAttachmentUrls) {
    static ServerMediaConfigSnapshot capture() {
        ServerMediaServerConfig config = ServerMediaServerConfig.get();
        return new ServerMediaConfigSnapshot(
                config.maxSingleBytes,
                config.maxChunkBytes,
                config.ttlSeconds,
                config.uploadTimeoutSeconds,
                config.maxPendingUploadsPerPlayer,
                config.maxPendingUploadsGlobal,
                config.maxPendingBytesPerPlayer,
                config.maxPendingBytesGlobal,
                config.maxStructuredMessagesPer10Seconds,
                config.maxUploadPacketsPer10Seconds,
                config.maxMediaRequestsPer10Seconds,
                config.maxAttachmentWritesPerMinute,
                config.maxHistoryRequestsPerMinute,
                config.allowExternalAttachmentUrls);
    }

    void restore() {
        ServerMediaServerConfig config = ServerMediaServerConfig.get();
        config.maxSingleBytes = maxSingleBytes;
        config.maxChunkBytes = maxChunkBytes;
        config.ttlSeconds = ttlSeconds;
        config.uploadTimeoutSeconds = uploadTimeoutSeconds;
        config.maxPendingUploadsPerPlayer = maxPendingUploadsPerPlayer;
        config.maxPendingUploadsGlobal = maxPendingUploadsGlobal;
        config.maxPendingBytesPerPlayer = maxPendingBytesPerPlayer;
        config.maxPendingBytesGlobal = maxPendingBytesGlobal;
        config.maxStructuredMessagesPer10Seconds = maxStructuredMessagesPer10Seconds;
        config.maxUploadPacketsPer10Seconds = maxUploadPacketsPer10Seconds;
        config.maxMediaRequestsPer10Seconds = maxMediaRequestsPer10Seconds;
        config.maxAttachmentWritesPerMinute = maxAttachmentWritesPerMinute;
        config.maxHistoryRequestsPerMinute = maxHistoryRequestsPerMinute;
        config.allowExternalAttachmentUrls = allowExternalAttachmentUrls;
    }
}