package com.chat.upgrade.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import com.chat.upgrade.ChatUpgrade;

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
        if (!FfmpegNativeBootstrap.ensureReady()) {
            throw new IllegalStateException("FFmpeg natives not ready");
        }
        Path temp = writeTempAudioFile(audioBytes);
        FfmpegAudioDecoder.DecodedAudio decoded = FfmpegAudioDecoder.decodeToS16Le(temp);
        try {
            Files.deleteIfExists(temp);
        } catch (Exception ignored) {
        }
        OpenAlPcmPlayer player = new OpenAlPcmPlayer(decoded.pcmS16Le(), decoded.sampleRate(), decoded.channels());
        player.setVolumePercent(ChatUpgradeConfig.get().audioVolumePercent);
        boolean loop = isLoopEnabled(url);
        player.setLooping(loop);
        SESSIONS.put(url, new AudioSession(player));
        return player.durationMs();
    }

    private static Path writeTempAudioFile(byte[] bytes) throws Exception {
        Path path = Files.createTempFile("chat-upgrade-audio-", ".bin");
        Files.write(path, bytes);
        return path;
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
                session.player.setVolumePercent(clamped);
            }
        }
    }

    public static boolean toggle(String url) {
        AudioSession s = SESSIONS.get(url);
        if (s == null) {
            return false;
        }
        synchronized (s) {
            if (s.player.isPlaying()) {
                s.player.pause();
                ACTIVE_PLAYBACK.deactivateIfActive(url);
                return false;
            }
            ACTIVE_PLAYBACK.activate(url, current -> {
                AudioSession cur = SESSIONS.get(current);
                if (cur == null) {
                    return;
                }
                synchronized (cur) {
                    if (cur.player.isPlaying()) {
                        cur.player.pause();
                    }
                }
            });
            long pos = s.player.positionMs();
            if (pos >= s.player.durationMs()) {
                pos = 0L;
            }
            s.player.playFrom(pos);
            return true;
        }
    }

    public static boolean toggleLoop(String url) {
        boolean enabled = !isLoopEnabled(url);
        LOOP_ENABLED.put(url, enabled);
        AudioSession s = SESSIONS.get(url);
        if (s != null) {
            synchronized (s) {
                s.player.setLooping(enabled);
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
            long len = s.player.durationMs();
            if (len <= 0L) {
                return;
            }
            double r = Math.clamp(ratio, 0.0, 1.0);
            long target = (long) (len * r);
            s.player.seekTo(target);
        }
    }

    public static boolean isPlaying(String url) {
        AudioSession s = SESSIONS.get(url);
        return s != null && s.player.isPlaying();
    }

    public static long positionMs(String url) {
        AudioSession s = SESSIONS.get(url);
        return s == null ? 0L : s.player.positionMs();
    }

    public static long durationMs(String url) {
        AudioSession s = SESSIONS.get(url);
        return s == null ? 0L : s.player.durationMs();
    }

    private record AudioSession(OpenAlPcmPlayer player) {
        void close() {
            try {
                player.close();
            } catch (Exception e) {
                ChatUpgrade.LOGGER.debug("chat-upgrade: close audio player: {}", e.getMessage());
            }
        }
    }
}
