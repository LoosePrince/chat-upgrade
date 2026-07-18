package com.chat.upgrade.client.ui.chat.input;

import java.util.Optional;

import com.chat.upgrade.client.ui.chat.state.ChatReplySummary;

public final class ChatComposerState {
    private AttachmentDraft attachmentDraft;
    private ChatReplySummary replyTarget;

    public synchronized Optional<AttachmentDraft> draft() {
        return Optional.ofNullable(attachmentDraft);
    }

    public synchronized boolean hasDraft() {
        return attachmentDraft != null;
    }

    public synchronized boolean isUploading() {
        return attachmentDraft != null && attachmentDraft.status() == AttachmentDraft.Status.UPLOADING;
    }

    public synchronized boolean isCurrent(AttachmentDraft expected) {
        return attachmentDraft == expected;
    }

    public synchronized void setDraft(AttachmentDraft nextDraft) {
        attachmentDraft = nextDraft;
    }

    public synchronized Optional<AttachmentDraft> clearDraft() {
        AttachmentDraft previous = attachmentDraft;
        attachmentDraft = null;
        return Optional.ofNullable(previous);
    }

    public synchronized boolean clearIfCurrent(AttachmentDraft expected) {
        if (attachmentDraft != expected) {
            return false;
        }
        attachmentDraft = null;
        return true;
    }

    public synchronized boolean replaceIfCurrent(AttachmentDraft expected, AttachmentDraft replacement) {
        if (attachmentDraft != expected) {
            return false;
        }
        attachmentDraft = replacement;
        return true;
    }

    public synchronized Optional<AttachmentDraft> markUploading(AttachmentDraft expected) {
        if (attachmentDraft != expected || !attachmentDraft.isSendable()) {
            return Optional.empty();
        }
        attachmentDraft = attachmentDraft.uploading();
        return Optional.of(attachmentDraft);
    }

    public synchronized Optional<AttachmentDraft> currentUploadingDraft() {
        if (attachmentDraft == null || attachmentDraft.status() != AttachmentDraft.Status.UPLOADING) {
            return Optional.empty();
        }
        return Optional.of(attachmentDraft);
    }

    public synchronized Optional<ChatReplySummary> replyTarget() {
        return Optional.ofNullable(replyTarget);
    }

    public synchronized boolean hasReplyTarget() {
        return replyTarget != null;
    }

    public synchronized void setReplyTarget(ChatReplySummary nextTarget) {
        replyTarget = nextTarget;
    }

    public synchronized Optional<ChatReplySummary> clearReplyTarget() {
        ChatReplySummary previous = replyTarget;
        replyTarget = null;
        return Optional.ofNullable(previous);
    }

    public synchronized boolean clearReplyIfCurrent(ChatReplySummary expected) {
        if (replyTarget != expected) {
            return false;
        }
        replyTarget = null;
        return true;
    }
}