package com.chat.upgrade.client.net.servermedia;

import com.chat.upgrade.client.ChatUpgradeConfig;

public record ServerMediaCapability(
        boolean enabled,
        int maxSingleBytes,
        int maxChunkBytes,
        StorageMode storageMode,
        int ttlSeconds,
        boolean attachmentMetadataEnabled,
        int attachmentSchemaVersion) {
    public ServerMediaCapability {
        maxSingleBytes = Math.clamp(maxSingleBytes, 0, ChatUpgradeConfig.ABSOLUTE_MAX_TRANSFER_BYTES);
        maxChunkBytes = Math.clamp(maxChunkBytes, 0, 256 * 1024);
        ttlSeconds = Math.clamp(ttlSeconds, 0, 24 * 60 * 60);
        attachmentSchemaVersion = Math.max(0, attachmentSchemaVersion);
        storageMode = storageMode == null ? StorageMode.MEMORY : storageMode;
        enabled = enabled && maxSingleBytes > 0 && maxChunkBytes > 0;
    }

    public static ServerMediaCapability unavailable() {
        return new ServerMediaCapability(false, 0, 0, StorageMode.MEMORY, 0, false, 0);
    }

    public ServerMediaCapability withAttachmentMetadata(boolean enabled, int schemaVersion) {
        return new ServerMediaCapability(
                this.enabled,
                this.maxSingleBytes,
                this.maxChunkBytes,
                this.storageMode,
                this.ttlSeconds,
                enabled,
                Math.max(0, schemaVersion));
    }

    public enum StorageMode {
        MEMORY, DISK
    }
}

