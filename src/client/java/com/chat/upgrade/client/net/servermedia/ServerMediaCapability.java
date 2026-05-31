package com.chat.upgrade.client.net.servermedia;
public record ServerMediaCapability(
        boolean enabled,
        int maxSingleBytes,
        int maxChunkBytes,
        StorageMode storageMode,
        int ttlSeconds,
        boolean attachmentMetadataEnabled,
        int attachmentSchemaVersion) {
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

