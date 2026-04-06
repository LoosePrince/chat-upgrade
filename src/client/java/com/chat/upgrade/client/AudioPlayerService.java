package com.chat.upgrade.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

import com.chat.upgrade.ChatUpgrade;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;

public final class AudioPlayerService {
    private static final ConcurrentHashMap<String, AudioSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> LOOP_ENABLED = new ConcurrentHashMap<>();
    private static final SingleActivePlaybackCoordinator ACTIVE_PLAYBACK = new SingleActivePlaybackCoordinator();

    private AudioPlayerService() {
    }

    public static long prepare(String url, byte[] audioBytes) throws Exception {
        AudioSession prev = SESSIONS.remove(url);
        if (prev != null) {
            prev.close();
        }
        Clip clip = null;
        try {
            clip = openClipWithAudioSystem(audioBytes);
        } catch (Exception primary) {
            ChatUpgrade.LOGGER.debug("chat-upgrade: AudioSystem decode failed, trying mp3spi fallback: {}",
                    primary.getMessage());
            try {
                clip = openClipWithMp3SpiFallback(audioBytes);
            } catch (Exception mp3spiEx) {
                ChatUpgrade.LOGGER.debug(
                        "chat-upgrade: mp3spi fallback decode failed, trying jlayer direct fallback: {}",
                        mp3spiEx.getMessage());
                clip = openClipWithJLayerDecode(audioBytes);
            }
        }
        if (clip == null) {
            throw new IllegalStateException("No audio decoder available");
        }
        long durationMs = clip.getMicrosecondLength() / 1000L;
        applyVolumePercent(clip, ChatUpgradeConfig.get().audioVolumePercent);
        SESSIONS.put(url, new AudioSession(clip));
        return durationMs;
    }

    private static Clip openClipWithAudioSystem(byte[] audioBytes) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audioBytes));
                AudioInputStream pcm = toPcmIfNeeded(ais)) {
            Clip clip = AudioSystem.getClip();
            clip.open(pcm);
            return clip;
        }
    }

    private static Clip openClipWithMp3SpiFallback(byte[] audioBytes) throws Exception {
        Class<?> readerClass = Class.forName("javazoom.spi.mpeg.sampled.file.MpegAudioFileReader");
        Object reader = readerClass.getDeclaredConstructor().newInstance();
        Method readMethod = readerClass.getMethod("getAudioInputStream", java.io.InputStream.class);
        try (AudioInputStream ais = (AudioInputStream) readMethod.invoke(reader, new ByteArrayInputStream(audioBytes));
                AudioInputStream pcm = toPcmIfNeeded(ais)) {
            Clip clip = AudioSystem.getClip();
            clip.open(pcm);
            return clip;
        }
    }

    private static Clip openClipWithJLayerDecode(byte[] audioBytes) throws Exception {
        DecodedPcm pcm = decodeMp3ToPcmWithJLayer(audioBytes);
        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                pcm.sampleRate(),
                16,
                pcm.channels(),
                pcm.channels() * 2,
                pcm.sampleRate(),
                false);
        try (AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(pcm.bytes()),
                format,
                pcm.bytes().length / Math.max(1, format.getFrameSize()))) {
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            return clip;
        }
    }

    private static DecodedPcm decodeMp3ToPcmWithJLayer(byte[] audioBytes) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes)) {
            Bitstream bitstream = new Bitstream(bais);
            Decoder decoder = new Decoder();
            ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
            int sampleRate = -1;
            int channels = -1;
            try {
                Header header;
                while ((header = bitstream.readFrame()) != null) {
                    SampleBuffer sb = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    if (sampleRate <= 0) {
                        sampleRate = sb.getSampleFrequency();
                    }
                    if (channels <= 0) {
                        channels = sb.getChannelCount();
                    }
                    short[] buf = sb.getBuffer();
                    int len = sb.getBufferLength();
                    for (int i = 0; i < len; i++) {
                        short v = buf[i];
                        pcmOut.write(v & 0xFF);
                        pcmOut.write((v >>> 8) & 0xFF);
                    }
                    bitstream.closeFrame();
                }
            } catch (JavaLayerException e) {
                throw new IllegalStateException("jlayer decode failed: " + e.getMessage(), e);
            } finally {
                try {
                    bitstream.close();
                } catch (BitstreamException ignored) {
                }
            }
            byte[] pcmBytes = pcmOut.toByteArray();
            if (pcmBytes.length == 0 || sampleRate <= 0 || channels <= 0) {
                throw new IllegalStateException("jlayer produced empty pcm");
            }
            return new DecodedPcm(pcmBytes, sampleRate, channels);
        }
    }

    private static AudioInputStream toPcmIfNeeded(AudioInputStream ais) throws Exception {
        AudioFormat base = ais.getFormat();
        if (base.getEncoding() == AudioFormat.Encoding.PCM_SIGNED) {
            return ais;
        }
        AudioFormat decoded = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                base.getSampleRate(),
                16,
                Math.max(1, base.getChannels()),
                Math.max(1, base.getChannels()) * 2,
                base.getSampleRate(),
                false);
        return AudioSystem.getAudioInputStream(decoded, ais);
    }

    public static void clearAll() {
        for (AudioSession s : SESSIONS.values()) {
            s.close();
        }
        SESSIONS.clear();
        LOOP_ENABLED.clear();
        ACTIVE_PLAYBACK.clear();
    }

    public static void setGlobalVolumePercent(int percent) {
        int clamped = Math.clamp(percent, 1, 100);
        for (AudioSession session : SESSIONS.values()) {
            synchronized (session) {
                applyVolumePercent(session.clip, clamped);
            }
        }
    }

    public static boolean toggle(String url) {
        AudioSession s = SESSIONS.get(url);
        if (s == null) {
            return false;
        }
        synchronized (s) {
            if (s.clip.isRunning()) {
                s.clip.stop();
                ACTIVE_PLAYBACK.deactivateIfActive(url);
                return false;
            }
            ACTIVE_PLAYBACK.activate(url, current -> {
                AudioSession cur = SESSIONS.get(current);
                if (cur == null) {
                    return;
                }
                synchronized (cur) {
                    if (cur.clip.isRunning()) {
                        cur.clip.stop();
                    }
                }
            });
            if (s.clip.getMicrosecondPosition() >= s.clip.getMicrosecondLength()) {
                s.clip.setMicrosecondPosition(0L);
            }
            if (isLoopEnabled(url)) {
                s.clip.loop(Clip.LOOP_CONTINUOUSLY);
            } else {
                s.clip.start();
            }
            return true;
        }
    }

    public static boolean toggleLoop(String url) {
        boolean enabled = !isLoopEnabled(url);
        LOOP_ENABLED.put(url, enabled);
        AudioSession s = SESSIONS.get(url);
        if (s != null) {
            synchronized (s) {
                if (s.clip.isRunning()) {
                    if (enabled) {
                        s.clip.loop(Clip.LOOP_CONTINUOUSLY);
                    } else {
                        s.clip.start();
                    }
                }
            }
        }
        return enabled;
    }

    public static boolean isLoopEnabled(String url) {
        return Boolean.TRUE.equals(LOOP_ENABLED.get(url));
    }

    public static void seek(String url, double ratio) {
        AudioSession s = SESSIONS.get(url);
        if (s == null) {
            return;
        }
        synchronized (s) {
            Clip clip = s.clip;
            long len = clip.getMicrosecondLength();
            if (len <= 0L) {
                return;
            }
            double r = Math.clamp(ratio, 0.0, 1.0);
            long target = (long) (len * r);
            boolean running = clip.isRunning();
            if (running) {
                clip.stop();
            }
            clip.setMicrosecondPosition(Math.max(0L, Math.min(len, target)));
            if (running) {
                if (isLoopEnabled(url)) {
                    clip.loop(Clip.LOOP_CONTINUOUSLY);
                } else {
                    clip.start();
                }
            }
        }
    }

    public static boolean isPlaying(String url) {
        AudioSession s = SESSIONS.get(url);
        return s != null && s.clip.isRunning();
    }

    public static long positionMs(String url) {
        AudioSession s = SESSIONS.get(url);
        return s == null ? 0L : s.clip.getMicrosecondPosition() / 1000L;
    }

    public static long durationMs(String url) {
        AudioSession s = SESSIONS.get(url);
        return s == null ? 0L : s.clip.getMicrosecondLength() / 1000L;
    }

    private record AudioSession(Clip clip) {
        void close() {
            try {
                clip.stop();
            } catch (Exception ignored) {
            }
            try {
                clip.close();
            } catch (Exception e) {
                ChatUpgrade.LOGGER.debug("chat-upgrade: close clip: {}", e.getMessage());
            }
        }
    }

    private record DecodedPcm(byte[] bytes, int sampleRate, int channels) {
    }

    private static void applyVolumePercent(Clip clip, int percent) {
        if (clip == null) {
            return;
        }
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return;
        }
        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        float min = gain.getMinimum();
        float max = gain.getMaximum();
        double linear = Math.clamp(percent / 100.0, 0.01, 1.0);
        float db = (float) (20.0 * Math.log10(linear));
        gain.setValue(Math.max(min, Math.min(max, db)));
    }
}
