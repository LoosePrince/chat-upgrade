package com.chat.upgrade.client.media.model;

import java.util.Objects;
import java.util.Optional;

public record RichMessageDraft(String text, Optional<RichAttachment> attachment) {
    public RichMessageDraft {
        text = text == null ? "" : text.trim();
        attachment = Objects.requireNonNull(attachment, "attachment");
    }

    public static RichMessageDraft textOnly(String text) {
        return new RichMessageDraft(text, Optional.empty());
    }

    public static RichMessageDraft withAttachment(String text, RichAttachment attachment) {
        return new RichMessageDraft(text, Optional.of(Objects.requireNonNull(attachment, "attachment")));
    }

    public boolean hasAttachment() {
        return attachment.isPresent();
    }
}