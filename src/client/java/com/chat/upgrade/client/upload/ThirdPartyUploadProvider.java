package com.chat.upgrade.client.upload;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;

// same package

public final class ThirdPartyUploadProvider implements UploadProvider {
    @Override
    public CompletableFuture<Optional<String>> uploadFile(Path file, @Nullable String contentType) {
        return CatboxUploader.uploadFile(file);
    }

    @Override
    public CompletableFuture<Optional<String>> uploadBytes(byte[] data, String filename, @Nullable String contentType) {
        return CatboxUploader.uploadBytes(data, filename);
    }
}

