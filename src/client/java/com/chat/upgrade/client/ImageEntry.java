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

    public ImageEntry() {
        this.state = State.LOADING;
    }

    public void setLoaded(Identifier textureId, int drawWidth, int drawHeight, int texWidth, int texHeight) {
        this.textureId = textureId;
        this.width = drawWidth;
        this.height = drawHeight;
        this.textureWidth = texWidth;
        this.textureHeight = texHeight;
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
}
