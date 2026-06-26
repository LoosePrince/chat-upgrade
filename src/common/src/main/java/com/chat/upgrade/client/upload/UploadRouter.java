package com.chat.upgrade.client.upload;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.net.servermedia.ServerMediaClient;

public final class UploadRouter {
    private static final UploadProvider THIRD_PARTY = new ThirdPartyUploadProvider();

    private UploadRouter() {
    }

    public static CompletableFuture<Optional<String>> uploadBytes(
            InlineResourceType type,
            byte[] data,
            String filename,
            String contentType) {
        ChatUpgradeConfig.UploadMode mode = ChatUpgradeConfig.get().uploadMode;
        return switch (mode) {
            case THIRD_PARTY -> THIRD_PARTY.uploadBytes(data, filename, contentType);
            case SERVER -> new ServerUploadProvider(type).uploadBytes(data, filename, contentType);
            case AUTO -> {
                if (ServerMediaClient.capability().enabled()) {
                    yield new ServerUploadProvider(type).uploadBytes(data, filename, contentType);
                }
                yield THIRD_PARTY.uploadBytes(data, filename, contentType);
            }
        };
    }
}

