package com.chat.upgrade.client.media.video;

import com.chat.upgrade.client.media.model.BaseMediaEntry;
import com.chat.upgrade.client.media.model.MediaFailureKind;

public final class VideoEntry extends BaseMediaEntry<VideoEntry.State, MediaFailureKind, VideoEntry.LoadPhase> {
    public enum State {
        LOADING, LOADED, FAILED
    }

    public enum LoadPhase {
        FETCH, DECODE
    }

    private volatile long durationMs = 0L;
    private volatile int rawWidth = 0;
    private volatile int rawHeight = 0;
    private volatile int displayWidth = 0;
    private volatile int displayHeight = 0;

    public VideoEntry() {
        super(State.LOADING, MediaFailureKind.UNKNOWN, LoadPhase.FETCH);
    }

    public void setLoaded(long durationMs, int rawWidth, int rawHeight, int displayWidth, int displayHeight) {
        this.durationMs = Math.max(0L, durationMs);
        this.rawWidth = Math.max(0, rawWidth);
        this.rawHeight = Math.max(0, rawHeight);
        this.displayWidth = Math.max(0, displayWidth);
        this.displayHeight = Math.max(0, displayHeight);
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

    public int getRawWidth() {
        return rawWidth;
    }

    public int getRawHeight() {
        return rawHeight;
    }

    public int getDisplayWidth() {
        return displayWidth;
    }

    public int getDisplayHeight() {
        return displayHeight;
    }
}
