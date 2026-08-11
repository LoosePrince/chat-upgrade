package com.chat.upgrade.client.media.audio;

import com.chat.upgrade.client.media.model.BaseMediaEntry;
import com.chat.upgrade.client.media.model.MediaFailureKind;

public final class AudioEntry extends BaseMediaEntry<AudioEntry.State, MediaFailureKind, AudioEntry.LoadPhase> {
    public enum State {
        LOADING, LOADED, FAILED
    }

    public enum LoadPhase {
        FETCH, DECODE
    }

    private volatile long durationMs = 0L;

    public AudioEntry() {
        super(State.LOADING, MediaFailureKind.UNKNOWN, LoadPhase.FETCH);
    }

    public void setLoaded(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
        setState(State.LOADED);
    }

    public void setFailed(MediaFailureKind kind) {
        setFailureKind(kind != null ? kind : MediaFailureKind.UNKNOWN);
        setState(State.FAILED);
    }

    public boolean isLoaded() {
        return getState() == State.LOADED;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
