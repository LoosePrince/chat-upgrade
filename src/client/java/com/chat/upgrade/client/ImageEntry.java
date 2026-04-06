package com.chat.upgrade.client;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public final class ImageEntry {
    public enum State { LOADING, LOADED, FAILED }

    /** Sub-state while {@link State#LOADING}; only for UI hints. */
    public enum LoadPhase {
        /** HTTP 请求进行中 */
        FETCH,
        /** 字节已收到，正在解码 / 即将上传 GPU */
        DECODE
    }

    private volatile State state;
    private volatile LoadPhase loadPhase = LoadPhase.FETCH;
    private @Nullable Identifier textureId;
    /** Drawn width/height on screen (preview size). */
    private int width;
    private int height;
    /** Actual GPU texture pixel size (may match width/height after CPU scale). */
    private int textureWidth;
    private int textureHeight;

    /** Original decoded image size (pixels). */
    private int rawPixelWidth;
    private int rawPixelHeight;

    /** HTTP body length used for decode; {@code -1} if unknown. */
    private volatile int fetchedByteLength = -1;
    private volatile @Nullable String contentType;
    private volatile @Nullable String md5Hex;
    /** {@link com.mojang.blaze3d.platform.NativeImage.Format} name after decode. */
    private volatile @Nullable String decodedFormatName;

    public ImageEntry() {
        this.state = State.LOADING;
    }

    /**
     * Called on the worker thread after the response body is fully read (before decode).
     */
    public void setTransferMetadata(int byteLength, @Nullable String contentType, @Nullable String md5Hex) {
        this.fetchedByteLength = byteLength;
        this.contentType = contentType;
        this.md5Hex = md5Hex;
    }

    public void setDecodedFormatName(@Nullable String decodedFormatName) {
        this.decodedFormatName = decodedFormatName;
    }

    public void setLoaded(
            Identifier textureId,
            int drawWidth,
            int drawHeight,
            int texWidth,
            int texHeight,
            int rawPixelWidth,
            int rawPixelHeight
    ) {
        this.textureId = textureId;
        this.width = drawWidth;
        this.height = drawHeight;
        this.textureWidth = texWidth;
        this.textureHeight = texHeight;
        this.rawPixelWidth = rawPixelWidth;
        this.rawPixelHeight = rawPixelHeight;
        this.state = State.LOADED;
    }

    public void setFailed() {
        this.state = State.FAILED;
    }

    public void setLoadPhase(LoadPhase loadPhase) {
        this.loadPhase = loadPhase;
    }

    public LoadPhase getLoadPhase() {
        return loadPhase;
    }

    public State getState() {
        return state;
    }

    public boolean isLoaded() {
        return state == State.LOADED;
    }

    public @Nullable Identifier getTextureId() {
        return textureId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getTextureWidth() {
        return textureWidth;
    }

    public int getTextureHeight() {
        return textureHeight;
    }

    public int getRawPixelWidth() {
        return rawPixelWidth;
    }

    public int getRawPixelHeight() {
        return rawPixelHeight;
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

    public @Nullable String getDecodedFormatName() {
        return decodedFormatName;
    }
}
