package com.chat.upgrade.client.media.audio;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Serializes button and shortcut recording attempts into one microphone session. */
public final class VoiceMessageController {
    public enum Origin {
        BUTTON,
        SHORTCUT
    }

    public record Completion(Origin origin, VoiceRecordingSession.Result result) {
    }

    private final VoiceRecordingSession session = new VoiceRecordingSession();
    private volatile Origin origin;
    private volatile Completion completion;

    public boolean start(Origin nextOrigin) {
        if (nextOrigin == null || session.isRecording() || origin != null) {
            return false;
        }
        origin = nextOrigin;
        CompletableFuture<VoiceRecordingSession.Result> future = session.start();
        future.whenComplete((result, error) -> {
            VoiceRecordingSession.Result safeResult = error == null && result != null
                    ? result
                    : new VoiceRecordingSession.Result(
                            VoiceRecordingSession.ResultKind.FAILED,
                            new byte[0],
                            "",
                            0L,
                            "",
                            error == null ? "unknown failure" : error.getMessage());
            completion = new Completion(nextOrigin, safeResult);
        });
        return true;
    }

    public void release(Origin expectedOrigin) {
        if (origin == expectedOrigin && session.isRecording()) {
            session.stop(VoiceRecordingSession.StopReason.RELEASED);
        }
    }

    public void cancel() {
        session.cancel();
        origin = null;
        completion = null;
    }

    public boolean recording(Origin expectedOrigin) {
        return origin == expectedOrigin && session.isRecording();
    }

    public long elapsedMillis() {
        return session.elapsedMillis();
    }

    public Optional<Completion> takeCompletion() {
        Completion next = completion;
        if (next == null) {
            return Optional.empty();
        }
        completion = null;
        origin = null;
        return Optional.of(next);
    }
}