package com.chat.upgrade.client.media.video;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_NONE;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_flush_buffers;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_open2;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet;
import static org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD;
import static org.bytedeco.ffmpeg.global.avformat.av_find_best_stream;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.av_seek_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLT;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLTP;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16P;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_free;
import static org.bytedeco.ffmpeg.global.avutil.av_image_fill_arrays;
import static org.bytedeco.ffmpeg.global.avutil.av_image_get_buffer_size;
import static org.bytedeco.ffmpeg.global.avutil.av_malloc;
import static org.bytedeco.ffmpeg.global.avutil.av_q2d;
import static org.bytedeco.ffmpeg.global.avutil.av_rescale_q;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR;
import static org.bytedeco.ffmpeg.global.swscale.sws_freeContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_getContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.media.audio.OpenAlPcmPlayer;
import com.chat.upgrade.client.media.audio.SingleActivePlaybackCoordinator;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

public final class VideoPlayerService {
    private static final long FRAME_CACHE_AHEAD_MS = 1_500L;
    private static final long FRAME_CACHE_BEHIND_MS = 500L;
    private static final long MAX_CACHE_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_DECODE_DIMENSION = 4_096;
    private static final long MAX_DECODE_PIXELS = 8_500_000L;
    private static final int MAX_TEXTURE_DIMENSION = 512;
    private static final long MAX_DURATION_MS = 5L * 60L * 1_000L;
    private static final int MAX_AUDIO_PCM_BYTES = 32 * 1024 * 1024;
    private static final int MAX_DECODE_PACKETS = 100_000;
    private static final ConcurrentHashMap<String, VideoSession> SESSIONS = new ConcurrentHashMap<>();
    private static final AtomicInteger TEXTURE_COUNTER = new AtomicInteger(0);
    private static final ExecutorService PREDECODE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-upgrade-video-predecode");
        t.setDaemon(true);
        return t;
    });
    private static final SingleActivePlaybackCoordinator ACTIVE_PLAYBACK = new SingleActivePlaybackCoordinator();
    private static volatile int globalVolumePercent = 100;

    private VideoPlayerService() {
    }

    public record Prepared(long durationMs, int rawWidth, int rawHeight) {
    }

    public static Prepared prepare(String url, byte[] videoBytes) throws Exception {
        VideoSession prev = SESSIONS.remove(url);
        if (prev != null) {
            prev.close();
        }

        Path tempFile = writeTempVideoFile(videoBytes);
        StreamingVideoDecoder decoder = null;
        VideoTexture videoTexture = null;
        try {
            decoder = StreamingVideoDecoder.open(tempFile);
            DecodedFrame firstFrame = decoder.nextFrame();
            if (firstFrame == null) {
                throw new IllegalStateException("no decoded video frame");
            }
            videoTexture = registerTextureOnRenderThread(
                    firstFrame.rgbaPixels(), firstFrame.outputWidth(), firstFrame.outputHeight());
            NavigableMap<Long, CachedFrame> frameIds = new TreeMap<>();
            frameIds.put(firstFrame.presentationMs(),
                    new CachedFrame(firstFrame.presentationMs(), firstFrame.rgbaPixels(), firstFrame.byteSize()));
            VideoAudioTrack audioTrack = decodeAudioTrack(tempFile);
            VideoSession session = new VideoSession(
                    url,
                    decoder.durationMs(),
                    decoder.rawWidth(),
                    decoder.rawHeight(),
                    decoder,
                    frameIds,
                    videoTexture,
                    tempFile,
                    audioTrack);
            decoder = null;
            videoTexture = null;
            SESSIONS.put(url, session);
            synchronized (session) {
                requestFramesAround(session, 0L, false);
            }
            return new Prepared(session.durationMs, session.rawWidth, session.rawHeight);
        } catch (Exception error) {
            if (videoTexture != null) {
                releaseTexture(videoTexture.textureId());
            }
            if (decoder != null) {
                decoder.close();
            }
            Files.deleteIfExists(tempFile);
            throw error;
        }
    }

    public static void clearAll() {
        for (VideoSession s : SESSIONS.values()) {
            s.close();
        }
        SESSIONS.clear();
        ACTIVE_PLAYBACK.clear();
    }

    public static void remove(String url) {
        VideoSession s = SESSIONS.remove(url);
        if (s != null) {
            s.close();
        }
        ACTIVE_PLAYBACK.deactivateIfActive(url);
    }

    public static Identifier textureIdAtMillis(String url, long nowMs) {
        VideoSession s = SESSIONS.get(url);
        if (s == null) {
            return null;
        }
        synchronized (s) {
            long positionMs = positionMsLocked(s, nowMs);
            requestFramesAround(s, positionMs, false);
            if (s.frameTextureIds.isEmpty()) {
                return s.videoTexture.textureId();
            }
            CachedFrame frame = valueAtOrFirst(s.frameTextureIds, positionMs);
            if (frame == null) {
                return null;
            }
            if (s.displayedFrameMs != frame.presentationMs()) {
                s.videoTexture.upload(frame.rgbaPixels());
                s.displayedFrameMs = frame.presentationMs();
            }
            return s.videoTexture.textureId();
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
                ChatUpgrade.LOGGER.debug(
                        "chat-upgrade: video pause url={} position={}ms cache={}",
                        url,
                        s.pausedPositionMs,
                        cacheSummary(s));
                s.playing = false;
                stopAudioLocked(s);
                ACTIVE_PLAYBACK.deactivateIfActive(url);
                return false;
            }
            ACTIVE_PLAYBACK.activate(url, current -> {
                VideoSession other = SESSIONS.get(current);
                if (other == null) {
                    return;
                }
                synchronized (other) {
                    if (other.playing) {
                        other.pausedPositionMs = positionMsLocked(other, now);
                        other.playing = false;
                        stopAudioLocked(other);
                    }
                }
            });
            if (s.pausedPositionMs >= s.durationMs) {
                s.pausedPositionMs = 0L;
                s.seekPendingMs = 0L;
                s.decoderEof = false;
                s.lastDecodedPositionMs = 0L;
            }
            s.playing = true;
            startAudioLocked(s, s.pausedPositionMs);
            s.playStartedAtMs = Util.getMillis() - s.pausedPositionMs;
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
            ChatUpgrade.LOGGER.debug(
                    "chat-upgrade: video seek requested url={} ratio={} target={}ms playing={} lastDecoded={}ms eof={} cache={}",
                    url,
                    r,
                    s.pausedPositionMs,
                    s.playing,
                    s.lastDecodedPositionMs,
                    s.decoderEof,
                    cacheSummary(s));
            if (s.playing) {
                restartAudioLocked(s, s.pausedPositionMs);
                s.playStartedAtMs = Util.getMillis() - s.pausedPositionMs;
            }
            requestFramesAround(s, s.pausedPositionMs, true);
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

    public static void setGlobalVolumePercent(int percent) {
        int clamped = Math.clamp(percent, 1, 100);
        globalVolumePercent = clamped;
        for (VideoSession session : SESSIONS.values()) {
            synchronized (session) {
                if (session.audioPlayback != null) {
                    session.audioPlayback.setVolumePercent(clamped);
                }
            }
        }
    }

    public static int getGlobalVolumePercent() {
        return globalVolumePercent;
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
            stopAudioLocked(s);
            ACTIVE_PLAYBACK.deactivateIfActive(s.url);
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

    private static void requestFramesAround(VideoSession session, long positionMs, boolean resetDecoder) {
        long minMs = Math.max(0L, positionMs - FRAME_CACHE_BEHIND_MS);
        long maxMs = Math.min(session.durationMs, positionMs + FRAME_CACHE_AHEAD_MS);
        session.requestedPositionMs = positionMs;
        if (resetDecoder) {
            session.seekPendingMs = positionMs;
            session.lastDecodedPositionMs = positionMs;
            session.decoderEof = false;
        }
        releaseFramesOutsideWindow(session, minMs, maxMs, shouldReleasePrefetchedFrames(resetDecoder));
        boolean resetForCacheMiss = shouldResetDecoderForCacheMiss(
                session.frameTextureIds.isEmpty(),
                session.decoderEof,
                session.lastDecodedPositionMs,
                maxMs);
        if (resetForCacheMiss) {
            session.seekPendingMs = positionMs;
            session.lastDecodedPositionMs = positionMs;
            session.decoderEof = false;
        }
        boolean needsDecode = !session.decoderEof
                && (session.frameTextureIds.isEmpty()
                || session.lastDecodedPositionMs < maxMs
                || session.seekPendingMs != null);
        if (needsDecode) {
            schedulePredecode(session);
        }
    }

    static boolean shouldReleasePrefetchedFrames(boolean resetDecoder) {
        return resetDecoder;
    }

    static boolean shouldRetainCachedFrame(
            long presentationMs,
            long windowStartMs,
            long windowEndMs,
            boolean releasePrefetchedFrames) {
        return presentationMs >= windowStartMs
                && (!releasePrefetchedFrames || presentationMs <= windowEndMs);
    }

    static boolean shouldResetDecoderForCacheMiss(
            boolean cacheEmpty,
            boolean decoderEof,
            long lastDecodedPositionMs,
            long windowEndMs) {
        return cacheEmpty && (decoderEof || lastDecodedPositionMs >= windowEndMs);
    }

    static <T> @Nullable T valueAtOrFirst(NavigableMap<Long, T> values, long positionMs) {
        Map.Entry<Long, T> value = values.floorEntry(positionMs);
        if (value != null) {
            return value.getValue();
        }
        Map.Entry<Long, T> firstValue = values.firstEntry();
        return firstValue == null ? null : firstValue.getValue();
    }

    private static void schedulePredecode(VideoSession session) {
        if (session.predecodeScheduled) {
            return;
        }
        session.predecodeScheduled = true;
        PREDECODE_EXECUTOR.execute(() -> predecodeRequestedFrames(session));
    }

    private static void predecodeRequestedFrames(VideoSession session) {
        while (true) {
            Long seekMs;
            long targetMs;
            synchronized (session) {
                if (session.closed) {
                    session.predecodeScheduled = false;
                    return;
                }
                targetMs = session.requestedPositionMs;
                seekMs = session.seekPendingMs;
                session.seekPendingMs = null;
            }
            try {
                if (seekMs != null) {
                    session.decoder.seekTo(seekMs);
                }
                DecodedFrame decoded = session.decoder.nextFrame();
                if (decoded == null) {
                    synchronized (session) {
                        session.decoderEof = true;
                        session.predecodeScheduled = false;
                        ChatUpgrade.LOGGER.debug(
                                "chat-upgrade: video decoder EOF url={} target={}ms lastDecoded={}ms cache={}",
                                session.url,
                                targetMs,
                                session.lastDecodedPositionMs,
                                cacheSummary(session));
                    }
                    return;
                }
                synchronized (session) {
                    if (session.closed) {
                        return;
                    }
                    session.lastDecodedPositionMs = Math.max(session.lastDecodedPositionMs, decoded.presentationMs());
                    long minMs = Math.max(0L, session.requestedPositionMs - FRAME_CACHE_BEHIND_MS);
                    long maxMs = Math.min(session.durationMs,
                            session.requestedPositionMs + FRAME_CACHE_AHEAD_MS);
                    boolean retained = shouldRetainCachedFrame(decoded.presentationMs(), minMs, maxMs, false);
                    if (retained) {
                        CachedFrame previous = session.frameTextureIds.put(
                                decoded.presentationMs(),
                                new CachedFrame(
                                        decoded.presentationMs(), decoded.rgbaPixels(), decoded.byteSize()));
                        if (previous != null) {
                            session.cachedBytes -= previous.byteSize();
                        }
                        session.cachedBytes += decoded.byteSize();
                    }
                    releaseFramesOutsideWindow(session, minMs, maxMs, false);
                    if (session.lastDecodedPositionMs >= maxMs && session.seekPendingMs == null) {
                        session.predecodeScheduled = false;
                        return;
                    }
                }
            } catch (Exception error) {
                ChatUpgrade.LOGGER.debug("chat-upgrade: streaming decode failed url={}", session.url, error);
                synchronized (session) {
                    session.predecodeScheduled = false;
                    ChatUpgrade.LOGGER.debug(
                            "chat-upgrade: video predecode stopped after failure url={} requested={}ms lastDecoded={}ms eof={} cache={}",
                            session.url,
                            session.requestedPositionMs,
                            session.lastDecodedPositionMs,
                            session.decoderEof,
                            cacheSummary(session));
                }
                return;
            }
        }
    }

    private static void releaseFramesOutsideWindow(
            VideoSession session,
            long minMs,
            long maxMs,
            boolean releasePrefetchedFrames) {
        session.frameTextureIds.entrySet().removeIf(entry -> {
            if (shouldRetainCachedFrame(entry.getKey(), minMs, maxMs, releasePrefetchedFrames)) {
                return false;
            }
            session.cachedBytes -= entry.getValue().byteSize();
            return true;
        });
        while (session.cachedBytes > MAX_CACHE_BYTES && session.frameTextureIds.size() > 1) {
            Map.Entry<Long, CachedFrame> oldest = session.frameTextureIds.firstEntry();
            session.cachedBytes -= oldest.getValue().byteSize();
            session.frameTextureIds.remove(oldest.getKey());
        }
    }

    private static String cacheSummary(VideoSession session) {
        Map.Entry<Long, CachedFrame> first = session.frameTextureIds.firstEntry();
        Map.Entry<Long, CachedFrame> last = session.frameTextureIds.lastEntry();
        if (first == null || last == null) {
            return "empty(bytes=" + session.cachedBytes + ")";
        }
        return "frames=" + session.frameTextureIds.size()
                + ",range=[" + first.getKey() + "," + last.getKey() + "]ms,bytes=" + session.cachedBytes;
    }

    private static VideoTexture registerTexture(byte[] rgbaPixels, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        int id = TEXTURE_COUNTER.getAndIncrement();
        Identifier location = Identifier.fromNamespaceAndPath(ChatUpgrade.MOD_ID, "upgrade_video_" + id);
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        ByteBuffer uploadBuffer = ByteBuffer.allocateDirect(rgbaByteSize(width, height));
        copyRgbaToNativeImage(rgbaPixels, image, uploadBuffer);
        DynamicTexture texture = new DynamicTexture(() -> "upgrade_video_" + id, image);
        mc.getTextureManager().register(location, texture);
        return new VideoTexture(location, texture, image, uploadBuffer);
    }

    private static VideoTexture registerTextureOnRenderThread(byte[] rgbaPixels, int width, int height) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture<VideoTexture> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(registerTexture(rgbaPixels, width, height));
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

    private record DecodedFrame(
            byte[] rgbaPixels,
            int outputWidth,
            int outputHeight,
            long durationMs,
            long presentationMs,
            int byteSize) {
    }

    private static final class StreamingVideoDecoder {
        private final AVFormatContext formatContext;
        private final AVCodecContext codecContext;
        private final AVPacket packet;
        private final AVFrame frame;
        private final int videoStreamIndex;
        private final AVRational timeBase;
        private final long durationMs;
        private final int rawWidth;
        private final int rawHeight;
        private final int outputWidth;
        private final int outputHeight;
        private SwsContext sws;
        private AVFrame rgba;
        private BytePointer rgbaBuffer;
        private byte[] rgbaBytes;
        private int rgbaStride;
        private boolean inputEof;
        private boolean closed;

        private StreamingVideoDecoder(
                AVFormatContext formatContext,
                AVCodecContext codecContext,
                AVPacket packet,
                AVFrame frame,
                int videoStreamIndex,
                AVRational timeBase,
                long durationMs,
                int rawWidth,
                int rawHeight,
                int outputWidth,
                int outputHeight) {
            this.formatContext = formatContext;
            this.codecContext = codecContext;
            this.packet = packet;
            this.frame = frame;
            this.videoStreamIndex = videoStreamIndex;
            this.timeBase = timeBase;
            this.durationMs = durationMs;
            this.rawWidth = rawWidth;
            this.rawHeight = rawHeight;
            this.outputWidth = outputWidth;
            this.outputHeight = outputHeight;
            this.sws = null;
            this.rgba = null;
            this.rgbaBuffer = null;
            this.rgbaBytes = null;
            this.rgbaStride = 0;
            this.inputEof = false;
        }

        static StreamingVideoDecoder open(Path path) throws Exception {
            AVFormatContext formatContext = new AVFormatContext(null);
            AVCodecContext codecContext = null;
            AVPacket packet = null;
            AVFrame frame = null;
            try {
                if (avformat_open_input(formatContext, path.toString(), null, (AVDictionary) null) < 0) {
                    throw new IllegalStateException("open input failed");
                }
                if (avformat_find_stream_info(formatContext, (PointerPointer<?>) null) < 0) {
                    throw new IllegalStateException("stream info failed");
                }
                int videoStreamIndex = av_find_best_stream(
                        formatContext, AVMEDIA_TYPE_VIDEO, -1, -1, (AVCodec) null, 0);
                if (videoStreamIndex < 0) {
                    throw new IllegalStateException("no video stream");
                }
                AVStream stream = formatContext.streams(videoStreamIndex);
                AVCodecParameters parameters = stream.codecpar();
                AVCodec codec = avcodec_find_decoder(parameters.codec_id());
                if (codec == null || codec.id() == AV_CODEC_ID_NONE) {
                    throw new IllegalStateException("no decoder");
                }
                codecContext = avcodec_alloc_context3(codec);
                if (codecContext == null
                        || avcodec_parameters_to_context(codecContext, parameters) < 0
                        || avcodec_open2(codecContext, codec, (AVDictionary) null) < 0) {
                    throw new IllegalStateException("open video decoder failed");
                }
                validateVideoDimensions(codecContext.width(), codecContext.height());
                long durationMs = durationFrom(formatContext, stream);
                if (durationMs <= 0L || durationMs > MAX_DURATION_MS) {
                    throw new IllegalStateException("video duration exceeds policy");
                }
                packet = av_packet_alloc();
                frame = av_frame_alloc();
                if (packet == null || frame == null) {
                    throw new IllegalStateException("alloc frame/packet failed");
                }
                int[] outputSize = scaledFrameSize(codecContext.width(), codecContext.height());
                return new StreamingVideoDecoder(
                        formatContext,
                        codecContext,
                        packet,
                        frame,
                        videoStreamIndex,
                        stream.time_base(),
                        durationMs,
                        codecContext.width(),
                        codecContext.height(),
                        outputSize[0],
                        outputSize[1]);
            } catch (Exception error) {
                if (frame != null) {
                    av_frame_free(frame);
                }
                if (packet != null) {
                    av_packet_free(packet);
                }
                if (codecContext != null) {
                    avcodec_free_context(codecContext);
                }
                avformat_close_input(formatContext);
                throw error;
            }
        }

        synchronized long durationMs() {
            return durationMs;
        }

        synchronized int rawWidth() {
            return rawWidth;
        }

        synchronized int rawHeight() {
            return rawHeight;
        }

        synchronized void seekTo(long positionMs) throws Exception {
            requireOpen();
            try (AVRational micros = new AVRational()) {
                micros.num(1);
                micros.den(1_000_000);
                long timestamp = av_rescale_q(Math.max(0L, positionMs) * 1_000L, micros, timeBase);
                ChatUpgrade.LOGGER.debug(
                        "chat-upgrade: FFmpeg seek request position={}ms streamTimestamp={} timeBase={}/{}",
                        positionMs,
                        timestamp,
                        timeBase.num(),
                        timeBase.den());
                if (av_seek_frame(formatContext, videoStreamIndex, timestamp, AVSEEK_FLAG_BACKWARD) < 0) {
                    throw new IllegalStateException("video seek failed");
                }
            }
            avcodec_flush_buffers(codecContext);
            inputEof = false;
        }

        synchronized @Nullable DecodedFrame nextFrame() throws Exception {
            requireOpen();
            int packets = 0;
            while (true) {
                int receive = avcodec_receive_frame(codecContext, frame);
                if (receive >= 0) {
                    return copyFrame(frame);
                }
                if (inputEof) {
                    return null;
                }
                int read = av_read_frame(formatContext, packet);
                if (read < 0) {
                    avcodec_send_packet(codecContext, null);
                    inputEof = true;
                    continue;
                }
                try {
                    if (++packets > MAX_DECODE_PACKETS) {
                        throw new IllegalStateException("video packet count exceeds policy");
                    }
                    if (packet.stream_index() == videoStreamIndex) {
                        avcodec_send_packet(codecContext, packet);
                    }
                } finally {
                    av_packet_unref(packet);
                }
            }
        }

        synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (sws != null) {
                sws_freeContext(sws);
                sws = null;
            }
            if (rgbaBuffer != null) {
                av_free(rgbaBuffer);
                rgbaBuffer = null;
            }
            rgbaBytes = null;
            if (rgba != null) {
                av_frame_free(rgba);
                rgba = null;
            }
            av_frame_free(frame);
            av_packet_free(packet);
            avcodec_free_context(codecContext);
            avformat_close_input(formatContext);
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("video decoder closed");
            }
        }

        private DecodedFrame copyFrame(AVFrame source) throws Exception {
            int width = source.width();
            int height = source.height();
            validateDecodedFrameDimensions(width, height);
            if (width != rawWidth || height != rawHeight) {
                throw new IllegalStateException("video frame dimensions changed");
            }
            if (sws == null) {
                sws = sws_getContext(
                        width, height, source.format(),
                        outputWidth, outputHeight, AV_PIX_FMT_RGBA,
                        SWS_BILINEAR,
                        null, null, (double[]) null);
                if (sws == null) {
                    throw new IllegalStateException("sws context failed");
                }
                rgba = av_frame_alloc();
                if (rgba == null) {
                    throw new IllegalStateException("alloc rgba frame failed");
                }
                int bufferSize = av_image_get_buffer_size(AV_PIX_FMT_RGBA, outputWidth, outputHeight, 1);
                if (bufferSize <= 0 || bufferSize > MAX_TEXTURE_DIMENSION * MAX_TEXTURE_DIMENSION * 4) {
                    throw new IllegalStateException("invalid decoded frame buffer size");
                }
                rgbaBuffer = new BytePointer(av_malloc(bufferSize));
                if (rgbaBuffer.isNull()) {
                    throw new IllegalStateException("decoded frame allocation failed");
                }
                av_image_fill_arrays(
                        rgba.data(), rgba.linesize(), rgbaBuffer,
                        AV_PIX_FMT_RGBA, outputWidth, outputHeight, 1);
                rgbaStride = rgba.linesize(0);
                rgbaBytes = new byte[rgbaStride * outputHeight];
            }
            sws_scale(sws, source.data(), source.linesize(), 0, height, rgba.data(), rgba.linesize());
            byte[] pixels = copyRgbaRows(rgbaBuffer, rgbaBytes, rgbaStride, outputWidth, outputHeight);
            long presentationMs = timestampMs(source.best_effort_timestamp(), timeBase);
            return new DecodedFrame(
                    pixels,
                    outputWidth,
                    outputHeight,
                    durationMs,
                    presentationMs,
                    pixels.length);
        }
    }

    private record VideoTexture(
            Identifier textureId,
            DynamicTexture texture,
            NativeImage pixels,
            ByteBuffer uploadBuffer) {
        void upload(byte[] rgbaPixels) {
            copyRgbaToNativeImage(rgbaPixels, pixels, uploadBuffer);
            texture.upload();
        }
    }

    private record CachedFrame(long presentationMs, byte[] rgbaPixels, int byteSize) {
    }

    private static final class VideoSession {
        final String url;
        final long durationMs;
        final int rawWidth;
        final int rawHeight;
        final StreamingVideoDecoder decoder;
        final NavigableMap<Long, CachedFrame> frameTextureIds;
        final VideoTexture videoTexture;
        final Path tempFile;
        final VideoAudioTrack audioTrack;
        boolean playing;
        boolean predecodeScheduled;
        boolean decoderEof;
        volatile boolean closed;
        long playStartedAtMs;
        long pausedPositionMs;
        long requestedPositionMs;
        long lastDecodedPositionMs;
        long displayedFrameMs = Long.MIN_VALUE;
        long cachedBytes;
        @Nullable Long seekPendingMs;
        @Nullable VideoAudioPlayback audioPlayback;

        VideoSession(
                String url,
                long durationMs,
                int rawWidth,
                int rawHeight,
                StreamingVideoDecoder decoder,
                NavigableMap<Long, CachedFrame> frameTextureIds,
                VideoTexture videoTexture,
                Path tempFile,
                @Nullable VideoAudioTrack audioTrack) {
            this.url = url;
            this.durationMs = durationMs;
            this.rawWidth = rawWidth;
            this.rawHeight = rawHeight;
            this.decoder = decoder;
            this.frameTextureIds = frameTextureIds;
            this.videoTexture = videoTexture;
            this.tempFile = tempFile;
            this.audioTrack = audioTrack;
            this.playing = false;
            this.predecodeScheduled = false;
            this.decoderEof = false;
            this.closed = false;
            this.playStartedAtMs = 0L;
            this.pausedPositionMs = 0L;
            this.requestedPositionMs = 0L;
            this.lastDecodedPositionMs = frameTextureIds.lastKey();
            this.cachedBytes = frameTextureIds.values().stream().mapToLong(CachedFrame::byteSize).sum();
            this.seekPendingMs = null;
            this.audioPlayback = null;
        }

        synchronized void close() {
            this.closed = true;
            ChatUpgrade.LOGGER.debug(
                    "chat-upgrade: video session closing url={} playing={} requested={}ms lastDecoded={}ms eof={} cache={}",
                    url,
                    playing,
                    requestedPositionMs,
                    lastDecodedPositionMs,
                    decoderEof,
                    cacheSummary(this));
            stopAudioLocked(this);
            decoder.close();
            frameTextureIds.clear();
            releaseTexture(videoTexture.textureId());
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception ignored) {
            }
        }
    }

    private static Path writeTempVideoFile(byte[] bytes) throws Exception {
        Path path = Files.createTempFile("chat-upgrade-video-", ".bin");
        Files.write(path, bytes);
        return path;
    }

    private static void validateVideoDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        validateDecodedFrameDimensions(width, height);
    }

    private static void validateDecodedFrameDimensions(int width, int height) {
        if (width <= 0
                || height <= 0
                || width > MAX_DECODE_DIMENSION
                || height > MAX_DECODE_DIMENSION
                || (long) width * height > MAX_DECODE_PIXELS) {
            throw new IllegalStateException("video dimensions exceed policy");
        }
    }

    private static int[] scaledFrameSize(int width, int height) {
        if (width <= MAX_TEXTURE_DIMENSION && height <= MAX_TEXTURE_DIMENSION) {
            return new int[] { width, height };
        }
        double scale = Math.min(
                MAX_TEXTURE_DIMENSION / (double) width,
                MAX_TEXTURE_DIMENSION / (double) height);
        return new int[] {
                Math.max(1, (int) Math.floor(width * scale)),
                Math.max(1, (int) Math.floor(height * scale))
        };
    }

    private static @Nullable VideoAudioTrack decodeAudioTrack(Path path) {
        AVFormatContext fmt = new AVFormatContext(null);
        AVCodecContext codecCtx = null;
        AVPacket pkt = null;
        AVFrame frame = null;
        try {
            if (avformat_open_input(fmt, path.toString(), null, (AVDictionary) null) < 0) {
                return null;
            }
            if (avformat_find_stream_info(fmt, (PointerPointer<?>) null) < 0) {
                return null;
            }
            int audioIdx = av_find_best_stream(fmt, org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO, -1, -1,
                    (AVCodec) null, 0);
            if (audioIdx < 0) {
                return null;
            }
            AVStream stream = fmt.streams(audioIdx);
            AVCodecParameters codecpar = stream.codecpar();
            AVCodec codec = avcodec_find_decoder(codecpar.codec_id());
            if (codec == null || codec.id() == AV_CODEC_ID_NONE) {
                return null;
            }
            codecCtx = avcodec_alloc_context3(codec);
            if (codecCtx == null || avcodec_parameters_to_context(codecCtx, codecpar) < 0
                    || avcodec_open2(codecCtx, codec, (AVDictionary) null) < 0) {
                return null;
            }
            pkt = av_packet_alloc();
            frame = av_frame_alloc();
            if (pkt == null || frame == null) {
                return null;
            }
            int channels = codecCtx.ch_layout().nb_channels();
            int sampleRate = codecCtx.sample_rate();
            if (channels <= 0 || channels > 2 || sampleRate < 8_000 || sampleRate > 48_000) {
                return null;
            }
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            int packets = 0;
            while (av_read_frame(fmt, pkt) >= 0) {
                if (++packets > MAX_DECODE_PACKETS) {
                    throw new IllegalStateException("video audio packet count exceeds policy");
                }
                try {
                    if (pkt.stream_index() != audioIdx) {
                        continue;
                    }
                    if (avcodec_send_packet(codecCtx, pkt) < 0) {
                        continue;
                    }
                    while (avcodec_receive_frame(codecCtx, frame) >= 0) {
                        appendFrameAsS16Le(pcm, frame, channels);
                    }
                } finally {
                    av_packet_unref(pkt);
                }
            }
            avcodec_send_packet(codecCtx, null);
            while (avcodec_receive_frame(codecCtx, frame) >= 0) {
                appendFrameAsS16Le(pcm, frame, channels);
            }
            byte[] audioBytes = pcm.toByteArray();
            if (audioBytes.length == 0) {
                return null;
            }
            return new VideoAudioTrack(audioBytes, sampleRate, channels);
        } catch (Exception e) {
            ChatUpgrade.LOGGER.debug("chat-upgrade: decode video audio failed: {}", e.getMessage());
            return null;
        } finally {
            if (frame != null) {
                av_frame_free(frame);
            }
            if (pkt != null) {
                av_packet_free(pkt);
            }
            if (codecCtx != null) {
                avcodec_free_context(codecCtx);
            }
            avformat_close_input(fmt);
        }
    }

    private static void appendFrameAsS16Le(ByteArrayOutputStream out, AVFrame frame, int channels) {
        int fmt = frame.format();
        int samples = frame.nb_samples();
        if (samples <= 0) {
            return;
        }
        long outputBytes = (long) samples * channels * 2L;
        if (outputBytes > MAX_AUDIO_PCM_BYTES || out.size() + outputBytes > MAX_AUDIO_PCM_BYTES) {
            throw new IllegalStateException("decoded audio exceeds memory policy");
        }
        if (fmt == AV_SAMPLE_FMT_S16) {
            BytePointer data = frame.data(0);
            int bytes = samples * channels * 2;
            byte[] buf = new byte[bytes];
            data.position(0).get(buf, 0, bytes);
            out.writeBytes(buf);
            return;
        }
        if (fmt == AV_SAMPLE_FMT_S16P) {
            for (int i = 0; i < samples; i++) {
                for (int c = 0; c < channels; c++) {
                    BytePointer plane = frame.data(c);
                    short v = plane.getShort((long) i * 2L);
                    writeS16Le(out, v);
                }
            }
            return;
        }
        if (fmt == AV_SAMPLE_FMT_FLT) {
            BytePointer data = frame.data(0);
            for (int i = 0; i < samples * channels; i++) {
                float f = data.getFloat((long) i * 4L);
                writeS16Le(out, floatToS16(f));
            }
            return;
        }
        if (fmt == AV_SAMPLE_FMT_FLTP) {
            for (int i = 0; i < samples; i++) {
                for (int c = 0; c < channels; c++) {
                    BytePointer plane = frame.data(c);
                    float f = plane.getFloat((long) i * 4L);
                    writeS16Le(out, floatToS16(f));
                }
            }
            return;
        }
        throw new IllegalStateException("unsupported decoded video audio sample format");
    }

    private static short floatToS16(float f) {
        float clamped = Math.clamp(f, -1.0f, 1.0f);
        return (short) Math.round(clamped * 32767.0f);
    }

    private static void writeS16Le(ByteArrayOutputStream out, short v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void startAudioLocked(VideoSession session, long startMs) {
        if (session.audioTrack == null) {
            return;
        }
        stopAudioLocked(session);
        VideoAudioPlayback playback = new VideoAudioPlayback(session.audioTrack, startMs, globalVolumePercent);
        session.audioPlayback = playback;
        playback.start();
    }

    private static void restartAudioLocked(VideoSession session, long startMs) {
        if (session.audioTrack == null) {
            return;
        }
        startAudioLocked(session, startMs);
    }

    private static void stopAudioLocked(VideoSession session) {
        VideoAudioPlayback playback = session.audioPlayback;
        if (playback != null) {
            playback.stop();
            session.audioPlayback = null;
        }
    }

    private record VideoAudioTrack(byte[] pcmS16Le, int sampleRate, int channels) {
    }

    private static final class VideoAudioPlayback {
        private final VideoAudioTrack track;
        private final long startMs;
        private int volumePercent;
        @Nullable private OpenAlPcmPlayer player;

        VideoAudioPlayback(VideoAudioTrack track, long startMs, int volumePercent) {
            this.track = track;
            this.startMs = Math.max(0L, startMs);
            this.volumePercent = Math.clamp(volumePercent, 1, 100);
        }

        void start() {
            try {
                OpenAlPcmPlayer p = new OpenAlPcmPlayer(track.pcmS16Le(), track.sampleRate(), track.channels());
                p.setVolumePercent(volumePercent);
                p.playFrom(startMs);
                player = p;
            } catch (Exception e) {
                ChatUpgrade.LOGGER.debug("chat-upgrade: video audio playback failed: {}", e.getMessage());
            }
        }

        void setVolumePercent(int percent) {
            volumePercent = Math.clamp(percent, 1, 100);
            if (player != null) {
                player.setVolumePercent(volumePercent);
            }
        }

        void stop() {
            if (player == null) {
                return;
            }
            try {
                player.stop();
                player.close();
            } catch (Exception ignored) {
            } finally {
                player = null;
            }
        }
    }

    private static long durationFrom(AVFormatContext fmt, AVStream stream) {
        try {
            if (stream != null && stream.duration() > 0 && stream.time_base() != null) {
                return timestampMs(stream.duration(), stream.time_base());
            }
        } catch (Exception ignored) {
        }
        long d = fmt.duration();
        if (d <= 0) {
            return 0L;
        }
        return d / 1000L;
    }

    private static long timestampMs(long pts, AVRational tb) {
        if (pts == AV_NOPTS_VALUE || tb == null) {
            return 0L;
        }
        return Math.max(0L, (long) (pts * av_q2d(tb) * 1000.0));
    }

    private static byte[] copyRgbaRows(
            BytePointer source, byte[] sourceBytes, int sourceStride, int width, int height) {
        source.position(0).get(sourceBytes, 0, sourceBytes.length);
        return compactRgbaRows(sourceBytes, sourceStride, width, height);
    }

    static byte[] compactRgbaRows(byte[] source, int sourceStride, int width, int height) {
        int rowBytes = rgbaByteSize(width, 1);
        if (sourceStride < rowBytes || source.length < (long) sourceStride * height) {
            throw new IllegalArgumentException("invalid video RGBA row stride");
        }
        byte[] compact = new byte[rgbaByteSize(width, height)];
        for (int y = 0; y < height; y++) {
            System.arraycopy(source, y * sourceStride, compact, y * rowBytes, rowBytes);
        }
        return compact;
    }

    static int rgbaByteSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("invalid video frame dimensions");
        }
        try {
            return Math.multiplyExact(Math.multiplyExact(width, height), 4);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("video RGBA frame is too large", e);
        }
    }

    private static void copyRgbaToNativeImage(byte[] rgbaPixels, NativeImage image, ByteBuffer uploadBuffer) {
        int byteSize = rgbaByteSize(image.getWidth(), image.getHeight());
        if (rgbaPixels.length != byteSize || uploadBuffer.capacity() != byteSize) {
            throw new IllegalArgumentException("video frame dimensions changed");
        }
        uploadBuffer.clear();
        uploadBuffer.put(rgbaPixels);
        uploadBuffer.flip();
        MemoryUtil.memCopy(MemoryUtil.memAddress(uploadBuffer), image.getPointer(), byteSize);
    }
}
