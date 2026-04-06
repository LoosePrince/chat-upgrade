package com.chat.upgrade.client;

import org.jetbrains.annotations.Nullable;

public final class VideoEntry {
    public enum State { LOADING, LOADED, FAILED }

    public enum FailureKind {
        UNKNOWN,
        RESPONSE_BODY_TOO_LARGE,
        UNSUPPORTED_VIDEO_FORMAT
    }

    public enum LoadPhase { FETCH, DECODE }

    private volatile State state = State.LOADING;
    private volatile FailureKind failureKind = FailureKind.UNKNOWN;
    private volatile LoadPhase loadPhase = LoadPhase.FETCH;
    private volatile int fetchedByteLength = -1;
    private volatile @Nullable String contentType;
    private volatile @Nullable String md5Hex;
    private volatile long durationMs = 0L;
    private volatile int rawWidth = 0;
    private volatile int rawHeight = 0;
    private volatile int displayWidth = 0;
    private volatile int displayHeight = 0;

    public void setTransferMetadata(int byteLength, @Nullable String contentType, @Nullable String md5Hex) {
        this.fetchedByteLength = byteLength;
        this.contentType = contentType;
        this.md5Hex = md5Hex;
    }

    public void setLoadPhase(LoadPhase loadPhase) {
        this.loadPhase = loadPhase;
    }

    public void setLoaded(long durationMs, int rawWidth, int rawHeight, int displayWidth, int displayHeight) {
        this.durationMs = Math.max(0L, durationMs);
        this.rawWidth = Math.max(0, rawWidth);
        this.rawHeight = Math.max(0, rawHeight);
        this.displayWidth = Math.max(0, displayWidth);
        this.displayHeight = Math.max(0, displayHeight);
        this.state = State.LOADED;
    }

    public void setFailed(FailureKind kind) {
        this.failureKind = kind != null ? kind : FailureKind.UNKNOWN;
        this.state = State.FAILED;
    }

    public State getState() {
        return state;
    }

    public boolean isLoaded() {
        return state == State.LOADED;
    }

    public FailureKind getFailureKind() {
        return failureKind;
    }

    public LoadPhase getLoadPhase() {
        return loadPhase;
    }

    public int getFetchedByteLength() {
        return fetchedByteLength;
    }

    public @Nullable String getContentType() {
        return contentType;
    }

    public @Nullable String getMd5Hex() {
        return md5Hex;
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
