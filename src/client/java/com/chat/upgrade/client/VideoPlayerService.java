package com.chat.upgrade.client;

import com.chat.upgrade.ChatUpgrade;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Size;
import org.jcodec.scale.AWTUtil;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class VideoPlayerService {
    private static final int TARGET_FPS = 12;
    private static final int MAX_CACHED_FRAMES = 240;
    private static final ConcurrentHashMap<String, VideoSession> SESSIONS = new ConcurrentHashMap<>();
    private static final AtomicInteger TEXTURE_COUNTER = new AtomicInteger(0);
    private static final ExecutorService PREDECODE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-upgrade-video-predecode");
        t.setDaemon(true);
        return t;
    });
    private static volatile String activeUrl;

    private VideoPlayerService() {}

    public record Prepared(long durationMs, int rawWidth, int rawHeight) {}

    public static Prepared prepare(String url, byte[] videoBytes) throws Exception {
        VideoSession prev = SESSIONS.remove(url);
        if (prev != null) {
            prev.close();
        }

        DecodedMeta meta = decodeMetaAndFirstFrame(videoBytes);
        CachePlan plan = planCache(meta.durationMs());
        Identifier firstFrameId = registerTextureOnRenderThread(meta.firstFrame());
        Identifier[] frameIds = new Identifier[plan.frames()];
        frameIds[0] = firstFrameId;
        VideoSession session = new VideoSession(url, meta.durationMs(), frameIds, plan.intervalMs());
        SESSIONS.put(url, session);
        schedulePredecode(url, session, videoBytes, meta.durationMs(), plan.intervalMs(), plan.frames());
        return new Prepared(meta.durationMs(), meta.rawWidth(), meta.rawHeight());
    }

    public static void clearAll() {
        for (VideoSession s : SESSIONS.values()) {
            s.close();
        }
        SESSIONS.clear();
        activeUrl = null;
    }

    public static void remove(String url) {
        VideoSession s = SESSIONS.remove(url);
        if (s != null) {
            s.close();
        }
        if (url.equals(activeUrl)) {
            activeUrl = null;
        }
    }

    public static Identifier textureIdAtMillis(String url, long nowMs) {
        VideoSession s = SESSIONS.get(url);
        if (s == null) {
            return null;
        }
        synchronized (s) {
            if (s.frameTextureIds.length == 0) {
                return null;
            }
            long pos = positionMsLocked(s, nowMs);
            if (s.frameIntervalMs <= 0L) {
                return s.frameTextureIds[0];
            }
            int idx = (int) Math.min(s.frameTextureIds.length - 1, Math.max(0L, pos / s.frameIntervalMs));
            Identifier id = s.frameTextureIds[idx];
            if (id != null) {
                return id;
            }
            for (int i = idx - 1; i >= 0; i--) {
                if (s.frameTextureIds[i] != null) {
                    return s.frameTextureIds[i];
                }
            }
            return s.frameTextureIds[0];
        }
    }

    public static boolean toggle(String url) {
        VideoSession s = SESSIONS.get(url);
        if (s == null) {
            return false;
        }
        long now = Util.getMillis();
        synchronized (s) {
            if (s.playing) {
                s.pausedPositionMs = positionMsLocked(s, now);
                s.playing = false;
                if (url.equals(activeUrl)) {
                    activeUrl = null;
                }
                return false;
            }
            String current = activeUrl;
            if (current != null && !current.equals(url)) {
                VideoSession other = SESSIONS.get(current);
                if (other != null) {
                    synchronized (other) {
                        if (other.playing) {
                            other.pausedPositionMs = positionMsLocked(other, now);
                            other.playing = false;
                        }
                    }
                }
            }
            if (s.pausedPositionMs >= s.durationMs) {
                s.pausedPositionMs = 0L;
            }
            s.playStartedAtMs = now - s.pausedPositionMs;
            s.playing = true;
            activeUrl = url;
            return true;
        }
    }

    public static void seek(String url, double ratio) {
        VideoSession s = SESSIONS.get(url);
        if (s == null) {
            return;
        }
        long now = Util.getMillis();
        synchronized (s) {
            double r = Math.clamp(ratio, 0.0, 1.0);
            s.pausedPositionMs = (long) (s.durationMs * r);
            if (s.playing) {
                s.playStartedAtMs = now - s.pausedPositionMs;
            }
        }
    }

    public static boolean isPlaying(String url) {
        VideoSession s = SESSIONS.get(url);
        return s != null && s.playing;
    }

    public static long positionMs(String url) {
        VideoSession s = SESSIONS.get(url);
        if (s == null) {
            return 0L;
        }
        synchronized (s) {
            return positionMsLocked(s, Util.getMillis());
        }
    }

    public static long durationMs(String url) {
        VideoSession s = SESSIONS.get(url);
        return s == null ? 0L : s.durationMs;
    }

    private static long positionMsLocked(VideoSession s, long nowMs) {
        if (!s.playing) {
            return clampDuration(s.pausedPositionMs, s.durationMs);
        }
        long pos = nowMs - s.playStartedAtMs;
        if (s.durationMs <= 0L) {
            return 0L;
        }
        if (pos >= s.durationMs) {
            s.playing = false;
            s.pausedPositionMs = s.durationMs;
            if (s.url.equals(activeUrl)) {
                activeUrl = null;
            }
            return s.durationMs;
        }
        return clampDuration(pos, s.durationMs);
    }

    private static long clampDuration(long pos, long duration) {
        if (duration <= 0L) {
            return 0L;
        }
        return Math.max(0L, Math.min(duration, pos));
    }

    private static DecodedMeta decodeMetaAndFirstFrame(byte[] bytes) throws Exception {
        try (ByteBufferSeekableByteChannel ch = new ByteBufferSeekableByteChannel(ByteBuffer.wrap(bytes), bytes.length)) {
            FrameGrab grab = FrameGrab.createFrameGrab(ch);
            Picture first = grab.getNativeFrame();
            if (first == null) {
                throw new IllegalStateException("No video frame");
            }
            BufferedImage bi = AWTUtil.toBufferedImage(first);
            NativeImage ni = RasterImageDecoder.fromBufferedImage(bi);

            double durationSec = 0.0;
            int width = bi.getWidth();
            int height = bi.getHeight();
            try {
                var meta = grab.getVideoTrack().getMeta();
                durationSec = meta.getTotalDuration();
                if (meta.getVideoCodecMeta() != null) {
                    Size size = meta.getVideoCodecMeta().getSize();
                    if (size != null) {
                        width = size.getWidth();
                        height = size.getHeight();
                    }
                }
            } catch (Exception ignored) {
            }
            long durationMs = Math.max(0L, (long) (durationSec * 1000.0));
            return new DecodedMeta(durationMs, width, height, ni);
        } catch (JCodecException e) {
            throw new IllegalStateException("Unsupported video format", e);
        }
    }

    private static NativeImage decodeFrameAtMs(byte[] bytes, long ms) throws Exception {
        double sec = Math.max(0.0, ms / 1000.0);
        try (ByteBufferSeekableByteChannel ch = new ByteBufferSeekableByteChannel(ByteBuffer.wrap(bytes), bytes.length)) {
            FrameGrab grab = FrameGrab.createFrameGrab(ch);
            grab.seekToSecondPrecise(sec);
            Picture picture = grab.getNativeFrame();
            if (picture == null) {
                throw new IllegalStateException("No decoded frame");
            }
            return RasterImageDecoder.fromBufferedImage(AWTUtil.toBufferedImage(picture));
        } catch (JCodecException e) {
            throw new IllegalStateException("Unsupported video format", e);
        }
    }

    private static CachePlan planCache(long durationMs) {
        long interval = Math.max(1L, 1000L / TARGET_FPS);
        int frames = Math.max(1, (int) Math.ceil(durationMs / (double) interval) + 1);
        if (frames > MAX_CACHED_FRAMES) {
            interval = Math.max(interval, (long) Math.ceil(durationMs / (double) (MAX_CACHED_FRAMES - 1)));
            frames = Math.max(1, (int) Math.ceil(durationMs / (double) interval) + 1);
        }
        return new CachePlan(interval, frames);
    }

    private static void schedulePredecode(
            String url,
            VideoSession session,
            byte[] videoBytes,
            long durationMs,
            long intervalMs,
            int frameCount
    ) {
        PREDECODE_EXECUTOR.execute(() -> {
            for (int i = 1; i < frameCount; i++) {
                if (session.closed) {
                    return;
                }
                long t = Math.min(durationMs, i * intervalMs);
                NativeImage frame;
                try {
                    frame = decodeFrameAtMs(videoBytes, t);
                } catch (Exception e) {
                    ChatUpgrade.LOGGER.debug("chat-upgrade: predecode frame {} failed for {}: {}", i, url, e.getMessage());
                    continue;
                }
                try {
                    Identifier id = registerTextureOnRenderThread(frame);
                    synchronized (session) {
                        if (session.closed) {
                            releaseTexture(id);
                            return;
                        }
                        session.frameTextureIds[i] = id;
                    }
                } catch (Exception e) {
                    ChatUpgrade.LOGGER.debug("chat-upgrade: upload predecoded frame {} failed for {}: {}", i, url, e.getMessage());
                    try {
                        frame.close();
                    } catch (Exception ignored) {
                    }
                    return;
                }
            }
        });
    }

    private static Identifier registerTexture(NativeImage image) {
        Minecraft mc = Minecraft.getInstance();
        int id = TEXTURE_COUNTER.getAndIncrement();
        Identifier location = Identifier.fromNamespaceAndPath(ChatUpgrade.MOD_ID, "upgrade_video_" + id);
        DynamicTexture texture = new DynamicTexture(() -> "upgrade_video_" + id, image);
        mc.getTextureManager().register(location, texture);
        return location;
    }

    private static Identifier registerTextureOnRenderThread(NativeImage image) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture<Identifier> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(registerTexture(image));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.get(20, TimeUnit.SECONDS);
    }

    private static void releaseTexture(Identifier textureId) {
        if (textureId == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getTextureManager().release(textureId));
    }

    private record DecodedMeta(long durationMs, int rawWidth, int rawHeight, NativeImage firstFrame) {}

    private record CachePlan(long intervalMs, int frames) {}

    private static final class VideoSession {
        final String url;
        final long durationMs;
        final Identifier[] frameTextureIds;
        final long frameIntervalMs;
        boolean playing;
        volatile boolean closed;
        long playStartedAtMs;
        long pausedPositionMs;

        VideoSession(String url, long durationMs, Identifier[] frameTextureIds, long frameIntervalMs) {
            this.url = url;
            this.durationMs = durationMs;
            this.frameTextureIds = frameTextureIds;
            this.frameIntervalMs = frameIntervalMs;
            this.playing = false;
            this.closed = false;
            this.playStartedAtMs = 0L;
            this.pausedPositionMs = 0L;
        }

        void close() {
            this.closed = true;
            Set<Identifier> dedupe = new HashSet<>();
            for (Identifier id : frameTextureIds) {
                if (id != null && dedupe.add(id)) {
                    releaseTexture(id);
                }
            }
        }
    }
}
