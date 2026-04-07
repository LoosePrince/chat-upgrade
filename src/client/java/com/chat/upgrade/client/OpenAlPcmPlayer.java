package com.chat.upgrade.client;

import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

/**
 * Minimal OpenAL-based PCM player for S16LE mono/stereo buffers.
 */
public final class OpenAlPcmPlayer implements AutoCloseable {
    private final int sourceId;
    private final int bufferId;
    private final int sampleRate;
    private final int channels;
    private final long durationMs;
    private long lastStartMs = 0L;
    private boolean playing = false;

    public OpenAlPcmPlayer(byte[] pcmS16Le, int sampleRate, int channels) {
        this.sampleRate = Math.max(8000, sampleRate);
        this.channels = channels <= 1 ? 1 : 2;
        int bytesPerFrame = this.channels * 2;
        long frames = pcmS16Le.length / Math.max(1, bytesPerFrame);
        this.durationMs = frames <= 0L ? 0L : (frames * 1000L) / this.sampleRate;

        int format = this.channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
        this.bufferId = AL10.alGenBuffers();
        ByteBuffer data = BufferUtils.createByteBuffer(pcmS16Le.length);
        data.put(pcmS16Le).flip();
        AL10.alBufferData(bufferId, format, data, this.sampleRate);

        this.sourceId = AL10.alGenSources();
        AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferId);
        AL10.alSourcei(sourceId, AL10.AL_LOOPING, AL10.AL_FALSE);
    }

    public long durationMs() {
        return durationMs;
    }

    public void playFrom(long startMs) {
        float sec = Math.max(0.0f, Math.min(durationMs / 1000.0f, startMs / 1000.0f));
        this.lastStartMs = (long) (sec * 1000.0f);
        AL10.alSourceStop(sourceId);
        AL10.alSourcePlay(sourceId);
        this.playing = true;
    }

    public void pause() {
        AL10.alSourcePause(sourceId);
        this.playing = false;
    }

    public void stop() {
        AL10.alSourceStop(sourceId);
        this.playing = false;
    }

    public void seekTo(long targetMs) {
        float sec = Math.max(0.0f, Math.min(durationMs / 1000.0f, targetMs / 1000.0f));
        boolean wasPlaying = isPlaying();
        AL10.alSourceStop(sourceId);
        this.lastStartMs = (long) (sec * 1000.0f);
        if (wasPlaying) {
            AL10.alSourcePlay(sourceId);
            this.playing = true;
        }
    }

    public boolean isPlaying() {
        int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
        return state == AL10.AL_PLAYING && playing;
    }

    public long positionMs() {
        if (!playing) {
            return lastStartMs;
        }
        // We don't have AL_SEC_OFFSET; approximate using source state is limited.
        // For preview/progress UI we can fall back to start offset.
        return lastStartMs;
    }

    public void setLooping(boolean loop) {
        AL10.alSourcei(sourceId, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
    }

    public void setVolumePercent(int percent) {
        int clamped = Math.clamp(percent, 1, 100);
        float gain = (float) Math.clamp(clamped / 100.0, 0.01, 1.0);
        AL10.alSourcef(sourceId, AL10.AL_GAIN, gain);
    }

    @Override
    public void close() {
        AL10.alSourceStop(sourceId);
        AL10.alDeleteSources(sourceId);
        AL10.alDeleteBuffers(bufferId);
    }
}

