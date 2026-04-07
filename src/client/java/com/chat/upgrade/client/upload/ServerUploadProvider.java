package com.chat.upgrade.client.upload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.InlineResourceType;
import com.chat.upgrade.client.ServerMediaClient;
import com.chat.upgrade.client.ServerMediaNetworking;

public final class ServerUploadProvider implements UploadProvider {
    private final InlineResourceType type;

    public ServerUploadProvider(InlineResourceType type) {
        this.type = type;
    }

    @Override
    public CompletableFuture<Optional<String>> uploadFile(Path file, @Nullable String contentType) {
        try {
            byte[] data = Files.readAllBytes(file);
            String name = file.getFileName().toString();
            return uploadBytes(data, name, contentType);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    @Override
    public CompletableFuture<Optional<String>> uploadBytes(byte[] data, String filename, @Nullable String contentType) {
        if (!ServerMediaClient.capability().enabled()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return ServerMediaNetworking.uploadBytes(type, data, filename, contentType);
    }
}

