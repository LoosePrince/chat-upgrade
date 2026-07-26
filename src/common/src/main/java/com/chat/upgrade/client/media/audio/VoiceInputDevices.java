package com.chat.upgrade.client.media.audio;

import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/** Resolves Java Sound capture mixers into stable configuration values. */
public final class VoiceInputDevices {
    public static final String DEFAULT_DEVICE = "";

    private static final long REFRESH_INTERVAL_NANOS = 3_000_000_000L;
    private static volatile Snapshot snapshot = new Snapshot(0L, List.of());

    private VoiceInputDevices() {
    }

    public static List<Device> available(AudioFormat format) {
        Snapshot current = snapshot;
        long now = System.nanoTime();
        if (now < current.refreshAfterNanos()) {
            return current.devices();
        }
        synchronized (VoiceInputDevices.class) {
            current = snapshot;
            if (now < current.refreshAfterNanos()) {
                return current.devices();
            }
            List<Device> found = new ArrayList<>();
            DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                try {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    if (mixer.isLineSupported(lineInfo)) {
                        found.add(new Device(deviceId(mixerInfo), mixerInfo.getName(), mixerInfo));
                    }
                } catch (IllegalArgumentException | SecurityException ignored) {
                    // A disconnected device may disappear while Java Sound enumerates it.
                }
            }
            snapshot = new Snapshot(now + REFRESH_INTERVAL_NANOS, List.copyOf(found));
            return snapshot.devices();
        }
    }

    public static OpenedLine open(AudioFormat format, String requestedId) throws LineUnavailableException {
        String selection = requestedId == null ? DEFAULT_DEVICE : requestedId.trim();
        if (!selection.isBlank()) {
            for (Device device : available(format)) {
                if (!selection.equals(device.id())) {
                    continue;
                }
                Mixer mixer = AudioSystem.getMixer(device.mixerInfo());
                TargetDataLine line = (TargetDataLine) mixer.getLine(new DataLine.Info(TargetDataLine.class, format));
                line.open(format);
                return new OpenedLine(line, device.name());
            }
        }
        TargetDataLine line = AudioSystem.getTargetDataLine(format);
        line.open(format);
        return new OpenedLine(line, "系统默认输入设备");
    }

    public static String displayName(String requestedId) {
        return displayName(requestedId, VoiceRecordingSession.captureFormat());
    }

    public static String displayName(String requestedId, AudioFormat format) {
        String selection = requestedId == null ? DEFAULT_DEVICE : requestedId.trim();
        List<Device> devices = available(format);
        if (selection.isBlank()) {
            return devices.isEmpty() ? "系统默认输入设备（未发现可用设备）" : "系统默认输入设备";
        }
        return devices.stream()
                .filter(device -> selection.equals(device.id()))
                .map(Device::name)
                .findFirst()
                .orElse("设备不可用");
    }

    public static String nextDeviceId(String currentId) {
        return nextDeviceId(currentId, VoiceRecordingSession.captureFormat());
    }

    public static String nextDeviceId(String currentId, AudioFormat format) {
        List<Device> devices = available(format);
        if (devices.isEmpty()) {
            return DEFAULT_DEVICE;
        }
        String selection = currentId == null ? DEFAULT_DEVICE : currentId.trim();
        if (selection.isBlank()) {
            return devices.getFirst().id();
        }
        for (int index = 0; index < devices.size(); index++) {
            if (selection.equals(devices.get(index).id())) {
                return index + 1 < devices.size() ? devices.get(index + 1).id() : DEFAULT_DEVICE;
            }
        }
        return DEFAULT_DEVICE;
    }

    private static String deviceId(Mixer.Info info) {
        return info.getName() + '\u0000' + info.getVendor() + '\u0000' + info.getDescription() + '\u0000' + info.getVersion();
    }

    public record Device(String id, String name, Mixer.Info mixerInfo) {
    }

    public record OpenedLine(TargetDataLine line, String deviceName) {
    }

    private record Snapshot(long refreshAfterNanos, List<Device> devices) {
    }
}