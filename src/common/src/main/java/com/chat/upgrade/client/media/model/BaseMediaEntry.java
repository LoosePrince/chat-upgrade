package com.chat.upgrade.client.media.model;

import org.jetbrains.annotations.Nullable;

public abstract class BaseMediaEntry<S extends Enum<S>, F extends Enum<F>, L extends Enum<L>> {
    private volatile S state;
    private volatile F failureKind;
    private volatile L loadPhase;
    private volatile int fetchedByteLength = -1;
    private volatile @Nullable String contentType;
    private volatile @Nullable String md5Hex;

    protected BaseMediaEntry(S initialState, F initialFailureKind, L initialLoadPhase) {
        this.state = initialState;
        this.failureKind = initialFailureKind;
        this.loadPhase = initialLoadPhase;
    }

    public final void setTransferMetadata(int byteLength, @Nullable String contentType, @Nullable String md5Hex) {
        this.fetchedByteLength = byteLength;
        this.contentType = contentType;
        this.md5Hex = md5Hex;
    }

    public final void setLoadPhase(L loadPhase) {
        this.loadPhase = loadPhase;
    }

    protected final void setState(S state) {
        this.state = state;
    }

    protected final void setFailureKind(F failureKind) {
        this.failureKind = failureKind;
    }

    public final S getState() {
        return state;
    }

    public final F getFailureKind() {
        return failureKind;
    }

    public final L getLoadPhase() {
        return loadPhase;
    }

    public final int getFetchedByteLength() {
        return fetchedByteLength;
    }

    public final @Nullable String getContentType() {
        return contentType;
    }

    public final @Nullable String getMd5Hex() {
        return md5Hex;
    }
}
