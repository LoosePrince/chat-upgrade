package com.chat.upgrade.client.media.audio;

import com.chat.upgrade.client.media.model.BaseMediaEntry;

public final class AudioEntry extends BaseMediaEntry<AudioEntry.State, AudioEntry.FailureKind, AudioEntry.LoadPhase> {
    public enum State {
        LOADING, LOADED, FAILED
    }

    public enum FailureKind {
        UNKNOWN,
        RESPONSE_BODY_TOO_LARGE,
        UNSUPPORTED_AUDIO_FORMAT
    }

    public enum LoadPhase {
        FETCH, DECODE
    }

    private volatile long durationMs = 0L;

    public AudioEntry() {
        super(State.LOADING, FailureKind.UNKNOWN, LoadPhase.FETCH);
    }

    public void setLoaded(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
        setState(State.LOADED);
    }

    public void setFailed(FailureKind kind) {
        setFailureKind(kind != null ? kind : FailureKind.UNKNOWN);
        setState(State.FAILED);
    }

    public boolean isLoaded() {
        return getState() == State.LOADED;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
