package com.chat.upgrade.client.ui.chat.input;

import java.util.Optional;

public final class AttachmentComposerState {
    private AttachmentDraft draft;

    public synchronized Optional<AttachmentDraft> draft() {
        return Optional.ofNullable(draft);
    }

    public synchronized boolean hasDraft() {
        return draft != null;
    }

    public synchronized boolean isUploading() {
        return draft != null && draft.status() == AttachmentDraft.Status.UPLOADING;
    }

    public synchronized boolean isCurrent(AttachmentDraft expected) {
        return draft == expected;
    }

    public synchronized void setDraft(AttachmentDraft nextDraft) {
        draft = nextDraft;
    }

    public synchronized Optional<AttachmentDraft> clearDraft() {
        AttachmentDraft previous = draft;
        draft = null;
        return Optional.ofNullable(previous);
    }

    public synchronized boolean clearIfCurrent(AttachmentDraft expected) {
        if (draft != expected) {
            return false;
        }
        draft = null;
        return true;
    }

    public synchronized boolean replaceIfCurrent(AttachmentDraft expected, AttachmentDraft replacement) {
        if (draft != expected) {
            return false;
        }
        draft = replacement;
        return true;
    }

    public synchronized Optional<AttachmentDraft> markUploading(AttachmentDraft expected) {
        if (draft != expected || !draft.isSendable()) {
            return Optional.empty();
        }
        draft = draft.uploading();
        return Optional.of(draft);
    }

    public synchronized Optional<AttachmentDraft> currentUploadingDraft() {
        if (draft == null || draft.status() != AttachmentDraft.Status.UPLOADING) {
            return Optional.empty();
        }
        return Optional.of(draft);
    }
}