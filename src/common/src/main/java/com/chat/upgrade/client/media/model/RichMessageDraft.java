package com.chat.upgrade.client.media.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RichMessageDraft(String text, List<RichAttachment> attachments) {
    public RichMessageDraft {
        text = text == null ? "" : text.trim();
        attachments = List.copyOf(Objects.requireNonNullElse(attachments, List.of()));
    }

    public static RichMessageDraft textOnly(String text) {
        return new RichMessageDraft(text, List.of());
    }

    public static RichMessageDraft withAttachment(String text, RichAttachment attachment) {
        return new RichMessageDraft(text, List.of(Objects.requireNonNull(attachment, "attachment")));
    }

    public static RichMessageDraft withAttachments(String text, List<RichAttachment> attachments) {
        return new RichMessageDraft(text, attachments);
    }

    public Optional<RichAttachment> attachment() {
        return attachments.stream().findFirst();
    }

    public boolean hasAttachment() {
        return !attachments.isEmpty();
    }
}
