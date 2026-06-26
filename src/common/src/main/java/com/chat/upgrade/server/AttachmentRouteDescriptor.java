package com.chat.upgrade.server;

import java.util.Optional;

import com.chat.upgrade.net.ServerMediaUrl;
import com.chat.upgrade.net.StructuredAttachment;

public record AttachmentRouteDescriptor(
        String bracketMessage,
        String visibleText,
        String typeWire,
        String typeLabel,
        String name,
        String url) {
    public Optional<StructuredAttachment> structuredAttachment() {
        if (url.isBlank()) {
            return Optional.empty();
        }
        Optional<ServerMediaUrl.Parsed> parsed = ServerMediaUrl.parse(url);
        if (parsed.isPresent()) {
            ServerMediaUrl.Parsed serverMedia = parsed.get();
            return Optional.of(StructuredAttachment.serverMedia(null, serverMedia.mediaId(), serverMedia.typeWire(), name));
        }
        return Optional.of(StructuredAttachment.externalUrl(null, typeWire, name, url));
    }
}