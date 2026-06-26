package com.chat.upgrade.client.media.image;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.model.BaseMediaEntry;

import net.minecraft.resources.Identifier;

public final class ImageEntry extends BaseMediaEntry<ImageEntry.State, ImageEntry.FailureKind, ImageEntry.LoadPhase> {
    public enum State {
        LOADING, LOADED, FAILED
    }

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

    /** Static preview; null when {@link #frameTextureIds} is used for animation. */
    private @Nullable Identifier textureId;
    /** Optional higher-resolution texture for full preview screen (static images only). */
    private @Nullable Identifier fullTextureId;
    /**
     * Multiple frames for animated GIF; when non-null and length &gt; 1, use
     * {@link #textureIdAtMillis(long)}.
     */
    private @Nullable Identifier[] frameTextureIds;
    private @Nullable int[] frameDelayMs;
    private long totalLoopDurationMs;
    /** Drawn width/height on screen (preview size). */
    private int width;
    private int height;
    /** Actual GPU texture pixel size (may match width/height after CPU scale). */
    private int textureWidth;
    private int textureHeight;
    /** Full preview texture size (0 means unavailable). */
    private int fullTextureWidth;
    private int fullTextureHeight;

    /** Original decoded image size (pixels). */
    private int rawPixelWidth;
    private int rawPixelHeight;

    /** {@link com.mojang.blaze3d.platform.NativeImage.Format} name after decode. */
    private volatile @Nullable String decodedFormatName;

    public ImageEntry() {
        super(State.LOADING, FailureKind.UNKNOWN, LoadPhase.FETCH);
    }

    public void setDecodedFormatName(@Nullable String decodedFormatName) {
        this.decodedFormatName = decodedFormatName;
    }

    public void setLoaded(
            Identifier textureId,
            @Nullable Identifier fullTextureId,
            int drawWidth,
            int drawHeight,
            int texWidth,
            int texHeight,
            int fullTexWidth,
            int fullTexHeight,
            int rawPixelWidth,
            int rawPixelHeight) {
        this.textureId = textureId;
        this.fullTextureId = fullTextureId;
        this.frameTextureIds = null;
        this.frameDelayMs = null;
        this.totalLoopDurationMs = 0L;
        this.width = drawWidth;
        this.height = drawHeight;
        this.textureWidth = texWidth;
        this.textureHeight = texHeight;
        this.fullTextureWidth = fullTexWidth;
        this.fullTextureHeight = fullTexHeight;
        this.rawPixelWidth = rawPixelWidth;
        this.rawPixelHeight = rawPixelHeight;
        setState(State.LOADED);
    }

    /**
     * Registers animated preview: {@code frameTextureIds.length} must match
     * {@code frameDelayMs.length}, ≥ 2.
     */
    public void setLoadedAnimated(
            Identifier[] frameTextureIds,
            int[] frameDelayMs,
            int drawWidth,
            int drawHeight,
            int texWidth,
            int texHeight,
            int rawPixelWidth,
            int rawPixelHeight) {
        this.textureId = null;
        this.fullTextureId = null;
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
        this.fullTextureWidth = 0;
        this.fullTextureHeight = 0;
        this.rawPixelWidth = rawPixelWidth;
        this.rawPixelHeight = rawPixelHeight;
        setState(State.LOADED);
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
        if (fullTextureId != null) {
            consumer.accept(fullTextureId);
        }
    }

    public void setFailed(FailureKind kind) {
        setFailureKind(kind != null ? kind : FailureKind.UNKNOWN);
        setState(State.FAILED);
    }

    public boolean isLoaded() {
        return getState() == State.LOADED;
    }

    public @Nullable Identifier getTextureId() {
        return textureId;
    }

    public @Nullable Identifier getFullTextureId() {
        return fullTextureId;
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

    public int getFullTextureWidth() {
        return fullTextureWidth;
    }

    public int getFullTextureHeight() {
        return fullTextureHeight;
    }

    public int getRawPixelWidth() {
        return rawPixelWidth;
    }

    public int getRawPixelHeight() {
        return rawPixelHeight;
    }

    public @Nullable String getDecodedFormatName() {
        return decodedFormatName;
    }
}
