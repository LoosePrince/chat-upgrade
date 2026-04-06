package com.chat.upgrade.client;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class ImageEntry {
    public enum State { LOADING, LOADED, FAILED }

    /** Why {@link State#FAILED} was set (for chat line replacement text). */
    public enum FailureKind {
        UNKNOWN,
        /** HTTP body exceeded {@link ChatUpgradeConfig#maxReceiveBytes}. */
        RESPONSE_BODY_TOO_LARGE
    }

    /** Sub-state while {@link State#LOADING}; only for UI hints. */
    public enum LoadPhase {
        /** HTTP 请求进行中 */
        FETCH,
        /** 字节已收到，正在解码 / 即将上传 GPU */
        DECODE
    }

    private volatile State state;
    private volatile FailureKind failureKind = FailureKind.UNKNOWN;
    private volatile LoadPhase loadPhase = LoadPhase.FETCH;
    /** Static preview; null when {@link #frameTextureIds} is used for animation. */
    private @Nullable Identifier textureId;
    /** Multiple frames for animated GIF; when non-null and length &gt; 1, use {@link #textureIdAtMillis(long)}. */
    private @Nullable Identifier[] frameTextureIds;
    private @Nullable int[] frameDelayMs;
    private long totalLoopDurationMs;
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
        this.frameTextureIds = null;
        this.frameDelayMs = null;
        this.totalLoopDurationMs = 0L;
        this.width = drawWidth;
        this.height = drawHeight;
        this.textureWidth = texWidth;
        this.textureHeight = texHeight;
        this.rawPixelWidth = rawPixelWidth;
        this.rawPixelHeight = rawPixelHeight;
        this.state = State.LOADED;
    }

    /**
     * Registers animated preview: {@code frameTextureIds.length} must match {@code frameDelayMs.length}, ≥ 2.
     */
    public void setLoadedAnimated(
            Identifier[] frameTextureIds,
            int[] frameDelayMs,
            int drawWidth,
            int drawHeight,
            int texWidth,
            int texHeight,
            int rawPixelWidth,
            int rawPixelHeight
    ) {
        this.textureId = null;
        this.frameTextureIds = Arrays.copyOf(frameTextureIds, frameTextureIds.length);
        this.frameDelayMs = Arrays.copyOf(frameDelayMs, frameDelayMs.length);
        long total = 0L;
        for (int d : this.frameDelayMs) {
            total += d;
        }
        this.totalLoopDurationMs = total;
        this.width = drawWidth;
        this.height = drawHeight;
        this.textureWidth = texWidth;
        this.textureHeight = texHeight;
        this.rawPixelWidth = rawPixelWidth;
        this.rawPixelHeight = rawPixelHeight;
        this.state = State.LOADED;
    }

    /**
     * Whether this entry uses multiple GPU textures cycled by delay (animated GIF).
     */
    public boolean isAnimated() {
        return frameTextureIds != null && frameTextureIds.length > 1;
    }

    /**
     * Chooses the frame texture for the given wall-clock time (looping).
     */
    public @Nullable Identifier textureIdAtMillis(long millis) {
        if (frameTextureIds == null || frameTextureIds.length == 0) {
            return textureId;
        }
        if (frameTextureIds.length == 1) {
            return frameTextureIds[0];
        }
        long total = totalLoopDurationMs;
        if (total <= 0L) {
            return frameTextureIds[0];
        }
        long t = millis % total;
        long acc = 0L;
        int[] delays = frameDelayMs;
        if (delays == null || delays.length != frameTextureIds.length) {
            return frameTextureIds[0];
        }
        for (int i = 0; i < delays.length; i++) {
            long d = delays[i];
            if (t < acc + d) {
                return frameTextureIds[i];
            }
            acc += d;
        }
        return frameTextureIds[frameTextureIds.length - 1];
    }

    /** Frame count for tooltips: 1 for static loaded image, N for animated GIF. */
    public int getAnimationFrameCount() {
        if (frameTextureIds != null) {
            return frameTextureIds.length;
        }
        return isLoaded() ? 1 : 0;
    }

    /** For cache invalidation: every registered texture identifier. */
    public void forEachRegisteredTexture(java.util.function.Consumer<Identifier> consumer) {
        if (frameTextureIds != null) {
            for (Identifier id : frameTextureIds) {
                if (id != null) {
                    consumer.accept(id);
                }
            }
        } else if (textureId != null) {
            consumer.accept(textureId);
        }
    }

    public void setFailed() {
        setFailed(FailureKind.UNKNOWN);
    }

    public void setFailed(FailureKind kind) {
        this.failureKind = kind != null ? kind : FailureKind.UNKNOWN;
        this.state = State.FAILED;
    }

    public FailureKind getFailureKind() {
        return failureKind;
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
