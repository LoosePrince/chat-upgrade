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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
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
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerPointer;
import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.media.audio.OpenAlPcmPlayer;
import com.chat.upgrade.client.media.audio.SingleActivePlaybackCoordinator;
import com.chat.upgrade.client.media.image.RasterImageDecoder;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

public final class VideoPlayerService {
    private static final int TARGET_FPS = 8;
    private static final int MAX_CACHED_FRAMES = 60;
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
        try {
            DecodedMeta meta = decodeMetaAndFirstFrame(tempFile);
            CachePlan plan = planCache(meta.durationMs());
            Identifier firstFrameId = registerTextureOnRenderThread(meta.firstFrame());
            Identifier[] frameIds = new Identifier[plan.frames()];
            frameIds[0] = firstFrameId;
            VideoAudioTrack audioTrack = decodeAudioTrack(tempFile);
            VideoSession session = new VideoSession(url, meta.durationMs(), frameIds, plan.intervalMs(), tempFile,
                    audioTrack);
            SESSIONS.put(url, session);
            schedulePredecode(url, session, tempFile, meta.durationMs(), plan.intervalMs(), plan.frames());
            return new Prepared(meta.durationMs(), meta.rawWidth(), meta.rawHeight());
        } catch (Exception error) {
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
            }
            s.playStartedAtMs = now - s.pausedPositionMs;
            s.playing = true;
            startAudioLocked(s, s.pausedPositionMs);
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
                restartAudioLocked(s, s.pausedPositionMs);
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

    private static DecodedMeta decodeMetaAndFirstFrame(Path path) throws Exception {
        DecodedFrame frame = decodeFrame(path, 0L, false);
        return new DecodedMeta(frame.durationMs(), frame.rawWidth(), frame.rawHeight(), frame.image());
    }

    private static NativeImage decodeFrameAtMs(Path path, long ms) throws Exception {
        return decodeFrame(path, ms, true).image();
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
            Path videoPath,
            long durationMs,
            long intervalMs,
            int frameCount) {
        PREDECODE_EXECUTOR.execute(() -> {
            for (int i = 1; i < frameCount; i++) {
                if (session.closed) {
                    return;
                }
                long t = Math.min(durationMs, i * intervalMs);
                NativeImage frame;
                try {
                    frame = decodeFrameAtMs(videoPath, t);
                } catch (Exception e) {
                    ChatUpgrade.LOGGER.debug("chat-upgrade: predecode frame {} failed for {}: {}", i, url,
                            e.getMessage());
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
                    ChatUpgrade.LOGGER.debug("chat-upgrade: upload predecoded frame {} failed for {}: {}", i, url,
                            e.getMessage());
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

    private record DecodedMeta(long durationMs, int rawWidth, int rawHeight, NativeImage firstFrame) {
    }

    private record DecodedFrame(NativeImage image, int rawWidth, int rawHeight, long durationMs) {
    }

    private record CachePlan(long intervalMs, int frames) {
    }

    private static final class VideoSession {
        final String url;
        final long durationMs;
        final Identifier[] frameTextureIds;
        final long frameIntervalMs;
        final Path tempFile;
        final VideoAudioTrack audioTrack;
        boolean playing;
        volatile boolean closed;
        long playStartedAtMs;
        long pausedPositionMs;
        @Nullable
        VideoAudioPlayback audioPlayback;

        VideoSession(
                String url,
                long durationMs,
                Identifier[] frameTextureIds,
                long frameIntervalMs,
                Path tempFile,
                @Nullable VideoAudioTrack audioTrack) {
            this.url = url;
            this.durationMs = durationMs;
            this.frameTextureIds = frameTextureIds;
            this.frameIntervalMs = frameIntervalMs;
            this.tempFile = tempFile;
            this.audioTrack = audioTrack;
            this.playing = false;
            this.closed = false;
            this.playStartedAtMs = 0L;
            this.pausedPositionMs = 0L;
            this.audioPlayback = null;
        }

        void close() {
            this.closed = true;
            stopAudioLocked(this);
            Set<Identifier> dedupe = new HashSet<>();
            for (Identifier id : frameTextureIds) {
                if (id != null && dedupe.add(id)) {
                    releaseTexture(id);
                }
            }
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

    private static DecodedFrame decodeFrame(Path path, long targetMs, boolean seek) throws Exception {
        AVFormatContext fmt = new AVFormatContext(null);
        AVCodecContext codecCtx = null;
        AVPacket pkt = null;
        AVFrame frame = null;
        AVFrame rgba = null;
        BytePointer rgbaBuffer = null;
        SwsContext sws = null;
        try {
            if (avformat_open_input(fmt, path.toString(), null, (AVDictionary) null) < 0) {
                throw new IllegalStateException("open input failed");
            }
            if (avformat_find_stream_info(fmt, (PointerPointer<?>) null) < 0) {
                throw new IllegalStateException("stream info failed");
            }
            int videoIdx = av_find_best_stream(fmt, AVMEDIA_TYPE_VIDEO, -1, -1, (AVCodec) null, 0);
            if (videoIdx < 0) {
                throw new IllegalStateException("no video stream");
            }
            AVStream stream = fmt.streams(videoIdx);
            AVCodecParameters codecpar = stream.codecpar();
            AVCodec codec = avcodec_find_decoder(codecpar.codec_id());
            if (codec == null || codec.id() == AV_CODEC_ID_NONE) {
                throw new IllegalStateException("no decoder");
            }
            codecCtx = avcodec_alloc_context3(codec);
            if (codecCtx == null) {
                throw new IllegalStateException("alloc codec ctx failed");
            }
            if (avcodec_parameters_to_context(codecCtx, codecpar) < 0) {
                throw new IllegalStateException("copy codec params failed");
            }
            validateVideoDimensions(codecCtx.width(), codecCtx.height());
            if (avcodec_open2(codecCtx, codec, (AVDictionary) null) < 0) {
                throw new IllegalStateException("open codec failed");
            }

            if (seek) {
                AVRational tb = stream.time_base();
                long ts;
                try (AVRational micros = new AVRational()) {
                    micros.num(1);
                    micros.den(1000000);
                    ts = av_rescale_q(targetMs * 1000L, micros, tb);
                }
                av_seek_frame(fmt, videoIdx, ts, AVSEEK_FLAG_BACKWARD);
                avcodec_flush_buffers(codecCtx);
            }

            pkt = av_packet_alloc();
            frame = av_frame_alloc();
            if (pkt == null || frame == null) {
                throw new IllegalStateException("alloc frame/packet failed");
            }

            long durationMs = durationFrom(fmt, stream);
            if (durationMs <= 0L || durationMs > MAX_DURATION_MS) {
                throw new IllegalStateException("video duration exceeds policy");
            }
            int rawW = codecCtx.width();
            int rawH = codecCtx.height();
            int packets = 0;

            while (av_read_frame(fmt, pkt) >= 0) {
                if (++packets > MAX_DECODE_PACKETS) {
                    throw new IllegalStateException("video packet count exceeds policy");
                }
                try {
                    if (pkt.stream_index() != videoIdx) {
                        continue;
                    }
                    if (avcodec_send_packet(codecCtx, pkt) < 0) {
                        continue;
                    }
                    while (avcodec_receive_frame(codecCtx, frame) >= 0) {
                        if (seek) {
                            long frameMs = timestampMs(frame.best_effort_timestamp(), stream.time_base());
                            if (frameMs + 2 < targetMs) {
                                continue;
                            }
                        }
                        int w = frame.width();
                        int h = frame.height();
                        validateDecodedFrameDimensions(w, h);
                        int[] outputSize = scaledFrameSize(w, h);
                        int outputWidth = outputSize[0];
                        int outputHeight = outputSize[1];
                        sws = sws_getContext(
                                w, h, frame.format(),
                                outputWidth, outputHeight, AV_PIX_FMT_RGBA,
                                SWS_BILINEAR,
                                null, null, (double[]) null);
                        if (sws == null) {
                            throw new IllegalStateException("sws context failed");
                        }
                        rgba = av_frame_alloc();
                        int bufferSize = av_image_get_buffer_size(
                                AV_PIX_FMT_RGBA, outputWidth, outputHeight, 1);
                        if (bufferSize <= 0 || bufferSize > MAX_TEXTURE_DIMENSION * MAX_TEXTURE_DIMENSION * 4) {
                            throw new IllegalStateException("invalid decoded frame buffer size");
                        }
                        rgbaBuffer = new BytePointer(av_malloc(bufferSize));
                        if (rgbaBuffer.isNull()) {
                            throw new IllegalStateException("decoded frame allocation failed");
                        }
                        av_image_fill_arrays(
                                rgba.data(),
                                new IntPointer(rgba.linesize()),
                                rgbaBuffer,
                                AV_PIX_FMT_RGBA,
                                outputWidth,
                                outputHeight,
                                1);
                        sws_scale(sws, frame.data(), frame.linesize(), 0, h, rgba.data(), rgba.linesize());
                        BufferedImage bi = bufferedImageFromRgba(
                                rgbaBuffer, rgba.linesize(0), outputWidth, outputHeight);
                        NativeImage out = RasterImageDecoder.fromBufferedImage(bi);
                        return new DecodedFrame(
                                out,
                                rawW > 0 ? rawW : w,
                                rawH > 0 ? rawH : h,
                                durationMs);
                    }
                } finally {
                    av_packet_unref(pkt);
                }
            }
            throw new IllegalStateException("no decoded video frame");
        } finally {
            if (sws != null) {
                sws_freeContext(sws);
            }
            if (rgbaBuffer != null) {
                av_free(rgbaBuffer);
            }
            if (rgba != null) {
                av_frame_free(rgba);
            }
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
        private volatile int volumePercent;
        private volatile boolean running = true;
        private Thread worker;
        private OpenAlPcmPlayer player;

        VideoAudioPlayback(VideoAudioTrack track, long startMs, int volumePercent) {
            this.track = track;
            this.startMs = Math.max(0L, startMs);
            this.volumePercent = Math.clamp(volumePercent, 1, 100);
        }

        void start() {
            worker = new Thread(this::run, "chat-upgrade-video-audio");
            worker.setDaemon(true);
            worker.start();
        }

        void setVolumePercent(int percent) {
            this.volumePercent = Math.clamp(percent, 1, 100);
            OpenAlPcmPlayer p = this.player;
            if (p != null) {
                p.setVolumePercent(this.volumePercent);
            }
        }

        void stop() {
            running = false;
            try {
                OpenAlPcmPlayer p = this.player;
                if (p != null) {
                    p.stop();
                    p.close();
                }
            } catch (Exception ignored) {
            }
        }

        private void run() {
            try {
                OpenAlPcmPlayer p = new OpenAlPcmPlayer(track.pcmS16Le(), track.sampleRate(), track.channels());
                this.player = p;
                p.setVolumePercent(volumePercent);
                p.playFrom(startMs);
                long dur = p.durationMs();
                while (running) {
                    if (!p.isPlaying()) {
                        break;
                    }
                    if (dur > 0L && p.positionMs() >= dur) {
                        break;
                    }
                    try {
                        Thread.sleep(20L);
                    } catch (InterruptedException ignored) {
                    }
                }
                p.stop();
                p.close();
            } catch (Exception e) {
                ChatUpgrade.LOGGER.debug("chat-upgrade: video audio playback failed: {}", e.getMessage());
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

    private static BufferedImage bufferedImageFromRgba(BytePointer src, int stride, int w, int h) {
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            int row = y * stride;
            for (int x = 0; x < w; x++) {
                int i = row + x * 4;
                int r = src.get(i) & 0xFF;
                int g = src.get(i + 1) & 0xFF;
                int b = src.get(i + 2) & 0xFF;
                int a = src.get(i + 3) & 0xFF;
                bi.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return bi;
    }
}
