package com.chat.upgrade.client.upload;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;

public interface UploadProvider {
    CompletableFuture<Optional<String>> uploadFile(Path file, @Nullable String contentType);

    CompletableFuture<Optional<String>> uploadBytes(byte[] data, String filename, @Nullable String contentType);
}

