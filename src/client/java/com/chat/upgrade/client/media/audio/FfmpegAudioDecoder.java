package com.chat.upgrade.client.media.audio;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_NONE;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_open2;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet;
import static org.bytedeco.ffmpeg.global.avformat.av_find_best_stream;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;

import java.io.ByteArrayOutputStream;
import java.nio.ByteOrder;
import java.nio.file.Path;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.javacpp.BytePointer;

/**
 * FFmpeg-based audio decoder that produces PCM S16LE interleaved buffers
 * suitable for OpenAL playback.
 */
public final class FfmpegAudioDecoder {
    private FfmpegAudioDecoder() {
    }

    public record DecodedAudio(byte[] pcmS16Le, int sampleRate, int channels) {
    }

    public static DecodedAudio decodeToS16Le(Path path) throws Exception {
        AVFormatContext fmt = new AVFormatContext(null);
        AVCodecContext codecCtx = null;
        AVPacket pkt = null;
        AVFrame frame = null;
        try {
            if (avformat_open_input(fmt, path.toString(), null, null) < 0) {
                throw new IllegalStateException("open input failed");
            }
            if (avformat_find_stream_info(fmt, (org.bytedeco.javacpp.PointerPointer<?>) null) < 0) {
                throw new IllegalStateException("stream info failed");
            }
            int audioIdx = av_find_best_stream(fmt, AVMEDIA_TYPE_AUDIO, -1, -1, (AVCodec) null, 0);
            if (audioIdx < 0) {
                throw new IllegalStateException("no audio stream");
            }
            AVStream stream = fmt.streams(audioIdx);
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
            if (avcodec_open2(codecCtx, codec, (org.bytedeco.ffmpeg.avutil.AVDictionary) null) < 0) {
                throw new IllegalStateException("open codec failed");
            }
            pkt = av_packet_alloc();
            frame = org.bytedeco.ffmpeg.global.avutil.av_frame_alloc();
            if (pkt == null || frame == null) {
                throw new IllegalStateException("alloc frame/packet failed");
            }
            int channels = Math.max(1, Math.min(2, codecCtx.ch_layout().nb_channels()));
            int sampleRate = Math.max(8000, codecCtx.sample_rate());
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            while (av_read_frame(fmt, pkt) >= 0) {
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
                throw new IllegalStateException("no decoded audio");
            }
            return new DecodedAudio(audioBytes, sampleRate, channels);
        } finally {
            if (frame != null) {
                org.bytedeco.ffmpeg.global.avutil.av_frame_free(frame);
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
        if (fmt == org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16) {
            BytePointer data = frame.data(0);
            int bytes = samples * frame.ch_layout().nb_channels() * 2;
            byte[] buf = new byte[bytes];
            data.position(0).get(buf, 0, bytes);
            if (frame.ch_layout().nb_channels() == channels) {
                out.writeBytes(buf);
                return;
            }
            downmixToChannels(out, buf, frame.ch_layout().nb_channels(), channels);
            return;
        }
        if (fmt == org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16P) {
            for (int i = 0; i < samples; i++) {
                for (int c = 0; c < channels; c++) {
                    BytePointer plane = frame.data(c);
                    short v = plane.getShort((long) i * 2L);
                    writeS16Le(out, v);
                }
            }
            return;
        }
        if (fmt == org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLT) {
            BytePointer data = frame.data(0);
            int totalChannels = frame.ch_layout().nb_channels();
            for (int i = 0; i < samples; i++) {
                for (int c = 0; c < channels; c++) {
                    float f = data.getFloat((long) ((i * totalChannels) + c) * 4L);
                    writeS16Le(out, floatToS16(f));
                }
            }
            return;
        }
        if (fmt == org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLTP) {
            for (int i = 0; i < samples; i++) {
                for (int c = 0; c < channels; c++) {
                    BytePointer plane = frame.data(c);
                    float f = plane.getFloat((long) i * 4L);
                    writeS16Le(out, floatToS16(f));
                }
            }
        }
    }

    private static void downmixToChannels(ByteArrayOutputStream out, byte[] buf, int srcChannels, int dstChannels) {
        if (srcChannels == dstChannels) {
            out.writeBytes(buf);
            return;
        }
        if (dstChannels == 1) {
            for (int i = 0; i + srcChannels * 2 <= buf.length; i += srcChannels * 2) {
                int sample = 0;
                for (int c = 0; c < srcChannels; c++) {
                    int lo = buf[i + c * 2] & 0xFF;
                    int hi = buf[i + c * 2 + 1];
                    short v = (short) ((hi << 8) | lo);
                    sample += v;
                }
                short mixed = (short) (sample / srcChannels);
                writeS16Le(out, mixed);
            }
        } else {
            for (int i = 0; i + srcChannels * 2 <= buf.length; i += srcChannels * 2) {
                int loL = buf[i] & 0xFF;
                int hiL = buf[i + 1];
                short l = (short) ((hiL << 8) | loL);
                short r;
                if (srcChannels > 1) {
                    int loR = buf[i + 2] & 0xFF;
                    int hiR = buf[i + 3];
                    r = (short) ((hiR << 8) | loR);
                } else {
                    r = l;
                }
                writeS16Le(out, l);
                writeS16Le(out, r);
            }
        }
    }

    private static short floatToS16(float f) {
        float clamped = Math.clamp(f, -1.0f, 1.0f);
        return (short) Math.round(clamped * 32767.0f);
    }

    private static void writeS16Le(ByteArrayOutputStream out, short v) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            out.write(v & 0xFF);
            out.write((v >> 8) & 0xFF);
            return;
        }
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }
}

