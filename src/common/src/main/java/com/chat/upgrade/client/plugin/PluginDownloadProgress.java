package com.chat.upgrade.client.plugin;

@FunctionalInterface
public interface PluginDownloadProgress {
    PluginDownloadProgress NONE = (artifact, downloadedBytes, totalBytes) -> {
    };

    void update(String artifact, long downloadedBytes, long totalBytes);
}