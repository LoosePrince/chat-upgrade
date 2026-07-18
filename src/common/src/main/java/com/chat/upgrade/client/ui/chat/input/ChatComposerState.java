package com.chat.upgrade.client.ui.chat.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.chat.upgrade.client.ui.chat.state.ChatReplySummary;
import com.chat.upgrade.net.StructuredChatProtocolLimits;

public final class ChatComposerState {
    public static final int MAX_DRAFTS = StructuredChatProtocolLimits.MAX_ATTACHMENTS;

    private List<AttachmentDraft> drafts = List.of();
    private ChatReplySummary replyTarget;
    private boolean uploadBatchActive;

    public synchronized List<AttachmentDraft> drafts() {
        return List.copyOf(drafts);
    }

    /**
     * Compatibility view for callers that only need to inspect the first draft.
     */
    public synchronized Optional<AttachmentDraft> draft() {
        return drafts.stream().findFirst();
    }

    public synchronized boolean hasDraft() {
        return !drafts.isEmpty();
    }

    public synchronized boolean isUploading() {
        return uploadBatchActive || drafts.stream().anyMatch(draft -> draft.status() == AttachmentDraft.Status.UPLOADING);
    }

    public synchronized boolean canAddDraft() {
        return drafts.size() < MAX_DRAFTS;
    }

    public synchronized boolean addDraft(AttachmentDraft nextDraft) {
        if (nextDraft == null || !canAddDraft() || containsIdentity(nextDraft)) {
            return false;
        }
        List<AttachmentDraft> next = new ArrayList<>(drafts);
        next.add(nextDraft);
        drafts = List.copyOf(next);
        return true;
    }

    public synchronized Optional<AttachmentDraft> clearDraft() {
        if (isUploading()) {
            return Optional.empty();
        }
        Optional<AttachmentDraft> previous = draft();
        drafts = List.of();
        return previous;
    }

    public synchronized void clearForScreenClose() {
        drafts = List.of();
        replyTarget = null;
        uploadBatchActive = false;
    }

    public synchronized boolean removeDraft(AttachmentDraft expected) {
        if (expected == null || expected.status() == AttachmentDraft.Status.UPLOADING) {
            return false;
        }
        int index = indexOfIdentity(expected);
        if (index < 0) {
            return false;
        }
        List<AttachmentDraft> next = new ArrayList<>(drafts);
        next.remove(index);
        drafts = List.copyOf(next);
        return true;
    }

    public synchronized boolean moveDraftBefore(AttachmentDraft expected, AttachmentDraft target) {
        if (expected == null || target == null || expected == target || isUploading()) {
            return false;
        }
        int from = indexOfIdentity(expected);
        int to = indexOfIdentity(target);
        if (from < 0 || to < 0) {
            return false;
        }
        List<AttachmentDraft> next = new ArrayList<>(drafts);
        AttachmentDraft moved = next.remove(from);
        int insertion = from < to ? to - 1 : to;
        next.add(insertion, moved);
        drafts = List.copyOf(next);
        return true;
    }

    public synchronized boolean moveDraftToEnd(AttachmentDraft expected) {
        if (expected == null || expected.status() == AttachmentDraft.Status.UPLOADING || isUploading()) {
            return false;
        }
        int from = indexOfIdentity(expected);
        if (from < 0 || from == drafts.size() - 1) {
            return false;
        }
        List<AttachmentDraft> next = new ArrayList<>(drafts);
        next.add(next.remove(from));
        drafts = List.copyOf(next);
        return true;
    }

    public synchronized boolean containsAll(List<AttachmentDraft> expected) {
        return expected != null && expected.stream().allMatch(this::containsIdentity);
    }

    public synchronized boolean containsIdentity(AttachmentDraft expected) {
        return expected != null && indexOfIdentity(expected) >= 0;
    }

    public synchronized boolean replaceIfCurrent(AttachmentDraft expected, AttachmentDraft replacement) {
        int index = indexOfIdentity(expected);
        if (index < 0 || replacement == null) {
            return false;
        }
        List<AttachmentDraft> next = new ArrayList<>(drafts);
        next.set(index, replacement);
        drafts = List.copyOf(next);
        return true;
    }

    public synchronized Optional<List<AttachmentDraft>> beginUploadBatch(List<AttachmentDraft> expected) {
        if (expected == null || expected.size() != drafts.size() || uploadBatchActive) {
            return Optional.empty();
        }
        List<AttachmentDraft> next = new ArrayList<>(drafts.size());
        for (int i = 0; i < expected.size(); i++) {
            AttachmentDraft current = drafts.get(i);
            if (current != expected.get(i) || !current.isSendable()) {
                return Optional.empty();
            }
            next.add(current.uploading());
        }
        drafts = List.copyOf(next);
        uploadBatchActive = true;
        return Optional.of(drafts);
    }

    public synchronized void endUploadBatch() {
        uploadBatchActive = false;
    }

    public synchronized boolean clearIfCurrent(AttachmentDraft expected) {
        int index = indexOfIdentity(expected);
        if (index < 0) {
            return false;
        }
        List<AttachmentDraft> next = new ArrayList<>(drafts);
        next.remove(index);
        drafts = List.copyOf(next);
        return true;
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

    private int indexOfIdentity(AttachmentDraft expected) {
        for (int i = 0; i < drafts.size(); i++) {
            if (drafts.get(i) == expected) {
                return i;
            }
        }
        return -1;
    }
}
