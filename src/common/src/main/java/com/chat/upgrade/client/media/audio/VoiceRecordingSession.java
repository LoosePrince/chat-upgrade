package com.chat.upgrade.client.media.audio;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.TargetDataLine;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.ChatUpgradeConfig;

/**
 * Captures a bounded, mono PCM voice clip from the system default microphone.
 * A 16 kHz WAV clip stays below the default 2 MiB upload limit for 60 seconds.
 */
public final class VoiceRecordingSession {
    public static final int MIN_DURATION_MILLIS = 2_000;
    public static final int MAX_DURATION_MILLIS = 60_000;

    private static final AudioFormat FORMAT = new AudioFormat(16_000.0F, 16, 1, true, false);
    private static final int BYTES_PER_SECOND = 32_000;
    private static final int MAX_PCM_BYTES = BYTES_PER_SECOND * MAX_DURATION_MILLIS / 1_000;
    // Kept deliberately close to digital silence so very quiet speech remains sendable.
    private static final int SILENCE_PEAK_THRESHOLD = 16;
    private static final double SILENCE_RMS_THRESHOLD = 4.0D;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public enum StopReason {
        RELEASED,
        TIME_LIMIT,
        CANCELLED
    }

    public enum ResultKind {
        READY,
        TOO_SHORT,
        SILENT,
        CANCELLED,
        FAILED
    }

    public record Result(
            ResultKind kind,
            byte[] wavBytes,
            String fileName,
            long durationMillis,
            String inputDevice,
            String failureReason) {
        public Result {
            wavBytes = wavBytes == null ? new byte[0] : wavBytes.clone();
            fileName = fileName == null ? "" : fileName;
            inputDevice = inputDevice == null ? "" : inputDevice;
            failureReason = failureReason == null ? "" : failureReason;
        }

        public boolean ready() {
            return kind == ResultKind.READY;
        }
    }

    private final Object lock = new Object();
    private TargetDataLine line;
    private CompletableFuture<Result> completion;
    private boolean recording;
    private volatile boolean stopRequested;
    private StopReason stopReason = StopReason.CANCELLED;
    private long startedAtNanos;

    public static AudioFormat captureFormat() {
        return FORMAT;
    }

    public CompletableFuture<Result> start() {
        synchronized (lock) {
            if (recording) {
                return completion;
            }
            CompletableFuture<Result> next = new CompletableFuture<>();
            TargetDataLine openedLine = null;
            try {
                VoiceInputDevices.OpenedLine opened = VoiceInputDevices.open(FORMAT, ChatUpgradeConfig.get().voiceInputDevice);
                openedLine = opened.line();
                openedLine.start();
                line = openedLine;
                completion = next;
                recording = true;
                stopRequested = false;
                stopReason = StopReason.CANCELLED;
                startedAtNanos = System.nanoTime();
                ChatUpgrade.LOGGER.info("chat-upgrade: voice capture started with input device '{}'", opened.deviceName());
                TargetDataLine activeLine = openedLine;
                CompletableFuture.runAsync(() -> capture(activeLine, opened.deviceName(), next));
                return next;
            } catch (Exception exception) {
                if (openedLine != null) {
                    try {
                        openedLine.stop();
                        openedLine.close();
                    } catch (RuntimeException ignored) {
                        // Opening can fail after the native line has been partially initialized.
                    }
                }
                line = null;
                completion = null;
                recording = false;
                next.complete(new Result(ResultKind.FAILED, new byte[0], "", 0L, "", safeMessage(exception)));
                return next;
            }
        }
    }

    public CompletableFuture<Result> stop(StopReason reason) {
        TargetDataLine activeLine;
        CompletableFuture<Result> activeCompletion;
        synchronized (lock) {
            if (!recording || completion == null) {
                return CompletableFuture.completedFuture(new Result(ResultKind.CANCELLED, new byte[0], "", 0L, "", ""));
            }
            stopRequested = true;
            stopReason = reason == null ? StopReason.CANCELLED : reason;
            activeLine = line;
            activeCompletion = completion;
        }
        if (activeLine != null) {
            activeLine.stop();
        }
        return activeCompletion;
    }

    public boolean isRecording() {
        synchronized (lock) {
            return recording;
        }
    }

    public long elapsedMillis() {
        synchronized (lock) {
            if (!recording) {
                return 0L;
            }
            return Math.clamp((System.nanoTime() - startedAtNanos) / 1_000_000L, 0L, (long) MAX_DURATION_MILLIS);
        }
    }

    public void cancel() {
        stop(StopReason.CANCELLED);
    }

    private void capture(TargetDataLine activeLine, String inputDevice, CompletableFuture<Result> target) {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream(Math.min(MAX_PCM_BYTES, BYTES_PER_SECOND * 10));
        byte[] buffer = new byte[2_048];
        boolean capped = false;
        try {
            while (!stopRequested && pcm.size() < MAX_PCM_BYTES) {
                int remaining = MAX_PCM_BYTES - pcm.size();
                int read = activeLine.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read > 0) {
                    pcm.write(buffer, 0, read);
                }
            }
            capped = pcm.size() >= MAX_PCM_BYTES;
            long durationMillis = pcm.size() * 1_000L / BYTES_PER_SECOND;
            if (stopRequested && stopReason == StopReason.CANCELLED) {
                target.complete(new Result(ResultKind.CANCELLED, new byte[0], "", durationMillis, inputDevice, ""));
            } else if (durationMillis < MIN_DURATION_MILLIS) {
                target.complete(new Result(ResultKind.TOO_SHORT, new byte[0], "", durationMillis, inputDevice, ""));
            } else {
                SignalLevel signal = signalLevel(pcm.toByteArray());
                String diagnostic = "device='" + inputDevice + "', peak=" + signal.peak()
                        + ", rms=" + String.format(java.util.Locale.ROOT, "%.2f", signal.rms());
                ChatUpgrade.LOGGER.info("chat-upgrade: voice capture completed in {} ms ({})", durationMillis, diagnostic);
                if (signal.isSilent()) {
                    ChatUpgrade.LOGGER.warn("chat-upgrade: microphone recording contains no audible signal ({})", diagnostic);
                    target.complete(new Result(ResultKind.SILENT, new byte[0], "", durationMillis, inputDevice, diagnostic));
                } else {
                    target.complete(new Result(
                            ResultKind.READY,
                            wav(pcm.toByteArray()),
                            "语音消息-" + FILE_TIME.format(LocalDateTime.now()) + ".wav",
                            durationMillis,
                            inputDevice,
                            diagnostic));
                }
            }
        } catch (Exception exception) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: microphone capture failed on input device '{}': {}", inputDevice, exception.toString());
            target.complete(new Result(ResultKind.FAILED, new byte[0], "", 0L, inputDevice, safeMessage(exception)));
        } finally {
            try {
                activeLine.stop();
                activeLine.close();
            } catch (RuntimeException ignored) {
                // The audio line may already have been closed by the releasing input event.
            }
            synchronized (lock) {
                if (line == activeLine) {
                    line = null;
                    recording = false;
                    stopRequested = false;
                }
            }
            if (capped) {
                ChatUpgrade.LOGGER.debug("chat-upgrade: voice recording reached its {} ms limit", MAX_DURATION_MILLIS);
            }
        }
    }

    private static SignalLevel signalLevel(byte[] pcm) {
        long samples = 0L;
        long sumSquares = 0L;
        int peak = 0;
        for (int offset = 0; offset + 1 < pcm.length; offset += 2) {
            int sample = (short) ((pcm[offset] & 0xFF) | (pcm[offset + 1] << 8));
            int magnitude = sample == Short.MIN_VALUE ? 32_768 : Math.abs(sample);
            peak = Math.max(peak, magnitude);
            sumSquares += (long) sample * sample;
            samples++;
        }
        double rms = samples == 0L ? 0.0D : Math.sqrt((double) sumSquares / samples);
        return new SignalLevel(peak, rms);
    }

    private record SignalLevel(int peak, double rms) {
        private boolean isSilent() {
            return peak <= SILENCE_PEAK_THRESHOLD && rms <= SILENCE_RMS_THRESHOLD;
        }
    }

    private static byte[] wav(byte[] pcm) {
        Objects.requireNonNull(pcm, "pcm");
        int dataSize = pcm.length;
        int fileSize = 36 + dataSize;
        byte[] result = new byte[44 + dataSize];
        putAscii(result, 0, "RIFF");
        putInt(result, 4, fileSize);
        putAscii(result, 8, "WAVEfmt ");
        putInt(result, 16, 16);
        putShort(result, 20, 1);
        putShort(result, 22, 1);
        putInt(result, 24, 16_000);
        putInt(result, 28, BYTES_PER_SECOND);
        putShort(result, 32, 2);
        putShort(result, 34, 16);
        putAscii(result, 36, "data");
        putInt(result, 40, dataSize);
        System.arraycopy(pcm, 0, result, 44, dataSize);
        return result;
    }

    private static void putAscii(byte[] target, int offset, String value) {
        for (int index = 0; index < value.length(); index++) {
            target[offset + index] = (byte) value.charAt(index);
        }
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
        target[offset + 3] = (byte) (value >>> 24);
    }

    private static void putShort(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}