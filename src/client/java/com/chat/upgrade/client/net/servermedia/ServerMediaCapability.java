package com.chat.upgrade.client.net.servermedia;
public record ServerMediaCapability(
        boolean enabled,
        int maxSingleBytes,
        int maxChunkBytes,
        StorageMode storageMode,
        int ttlSeconds) {
    public static ServerMediaCapability unavailable() {
        return new ServerMediaCapability(false, 0, 0, StorageMode.MEMORY, 0);
    }

    public enum StorageMode {
        MEMORY, DISK
    }
}

