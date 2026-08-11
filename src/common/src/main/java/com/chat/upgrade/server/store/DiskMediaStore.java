package com.chat.upgrade.server.store;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.chat.upgrade.net.ServerMediaId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class DiskMediaStore implements MediaStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MAX_STORED_MEDIA_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_CONTENT_TYPE_CHARS = 128;

    private final Path rootDir;
    private final ConcurrentHashMap<String, Meta> metaById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> mediaIdByFingerprint = new ConcurrentHashMap<>();
    private final AtomicLong totalBytes = new AtomicLong(0L);

    public DiskMediaStore(Path rootDir) throws Exception {
        this.rootDir = rootDir;
        Files.createDirectories(rootDir);
        loadExisting();
    }

    @Override
    public Optional<StoredMedia> get(String mediaId) {
        if (!ServerMediaId.isValid(mediaId)) {
            return Optional.empty();
        }
        mediaId = mediaId.toLowerCase(java.util.Locale.ROOT);
        Meta meta = metaById.get(mediaId);
        if (meta == null) {
            return Optional.empty();
        }
        Path bin = binPath(mediaId);
        try {
            if (!validMeta(meta, bin)) {
                delete(mediaId);
                return Optional.empty();
            }
            byte[] body = Files.readAllBytes(bin);
            return Optional.of(new StoredMedia(
                    mediaId,
                    meta.typeWire,
                    meta.contentType,
                    meta.fingerprint,
                    body,
                    meta.createdAtMs,
                    meta.expiresAtMs,
                    meta.ownerId));
        } catch (Exception e) {
            delete(mediaId);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> findMediaIdByFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mediaIdByFingerprint.get(fingerprint));
    }

    @Override
    public void put(StoredMedia media) throws Exception {
        if (media == null
                || !ServerMediaId.isValid(media.mediaId())
                || media.byteLength() <= 0
                || media.byteLength() > MAX_STORED_MEDIA_BYTES
                || !validOwnerId(media.ownerId())) {
            throw new IllegalArgumentException("invalid media record");
        }
        String id = media.mediaId().toLowerCase(java.util.Locale.ROOT);
        Path bin = binPath(id);
        Path metaPath = metaPath(id);
        Files.createDirectories(rootDir);
        Meta meta = new Meta(
                id,
                media.typeWire(),
                media.contentType(),
                media.fingerprint(),
                media.createdAtMs(),
                media.expiresAtMs(),
                media.byteLength(),
                media.ownerId());
        writeAtomically(bin, media.body());
        writeAtomically(metaPath, GSON.toJson(meta).getBytes(StandardCharsets.UTF_8));
        Meta prev = metaById.put(id, meta);
        if (prev != null) {
            totalBytes.addAndGet(-prev.byteLen);
            if (prev.fingerprint != null && !prev.fingerprint.isBlank()) {
                mediaIdByFingerprint.remove(prev.fingerprint, prev.mediaId);
            }
        }
        totalBytes.addAndGet(meta.byteLen);
        if (meta.fingerprint != null && !meta.fingerprint.isBlank()) {
            mediaIdByFingerprint.put(meta.fingerprint, id);
        }
    }

    @Override
    public void delete(String mediaId) {
        if (!ServerMediaId.isValid(mediaId)) {
            return;
        }
        mediaId = mediaId.toLowerCase(java.util.Locale.ROOT);
        Meta prev = metaById.remove(mediaId);
        if (prev != null) {
            totalBytes.addAndGet(-prev.byteLen);
            if (prev.fingerprint != null && !prev.fingerprint.isBlank()) {
                mediaIdByFingerprint.remove(prev.fingerprint, prev.mediaId);
            }
        }
        try {
            Files.deleteIfExists(binPath(mediaId));
        } catch (Exception ignored) {
        }
        try {
            Files.deleteIfExists(metaPath(mediaId));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void cleanup(long nowMs, long maxTotalBytes) {
        for (var e : new ArrayList<>(metaById.entrySet())) {
            Meta m = e.getValue();
            if (m != null && m.expiresAtMs > 0 && nowMs >= m.expiresAtMs) {
                delete(e.getKey());
            }
        }
        if (maxTotalBytes <= 0) {
            return;
        }
        if (totalBytes.get() <= maxTotalBytes) {
            return;
        }
        ArrayList<Meta> all = new ArrayList<>(metaById.values());
        all.sort(Comparator.comparingLong(x -> x.createdAtMs));
        for (Meta m : all) {
            if (totalBytes.get() <= maxTotalBytes) {
                break;
            }
            delete(m.mediaId);
        }
    }

    private void loadExisting() throws Exception {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(rootDir, "*.meta.json")) {
            for (Path p : ds) {
                try (Reader r = Files.newBufferedReader(p)) {
                    Meta meta = GSON.fromJson(r, Meta.class);
                    if (meta == null || !ServerMediaId.isValid(meta.mediaId)) {
                        Files.deleteIfExists(p);
                        continue;
                    }
                    meta.mediaId = meta.mediaId.toLowerCase(java.util.Locale.ROOT);
                    Path bin = binPath(meta.mediaId);
                    if (!validMeta(meta, bin)) {
                        Files.deleteIfExists(p);
                        continue;
                    }
                    metaById.put(meta.mediaId, meta);
                    totalBytes.addAndGet(meta.byteLen);
                    if (meta.fingerprint != null && !meta.fingerprint.isBlank()) {
                        mediaIdByFingerprint.put(meta.fingerprint, meta.mediaId);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void writeAtomically(Path target, byte[] body) throws Exception {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, body);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean validMeta(Meta meta, Path bin) {
        if (meta == null
                || !ServerMediaId.isValid(meta.mediaId)
                || !validType(meta.typeWire)
                || !validContentType(meta.contentType)
                || !validFingerprint(meta.fingerprint)
                || (meta.ownerId != null && !meta.ownerId.isBlank() && !validOwnerId(meta.ownerId))
                || meta.createdAtMs < 0L
                || meta.expiresAtMs < 0L
                || (meta.expiresAtMs > 0L && meta.expiresAtMs < meta.createdAtMs)
                || meta.byteLen <= 0
                || meta.byteLen > MAX_STORED_MEDIA_BYTES
                || !Files.isRegularFile(bin)) {
            return false;
        }
        try {
            return Files.size(bin) == meta.byteLen;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean validType(String value) {
        return "image".equals(value) || "audio".equals(value) || "video".equals(value);
    }

    private static boolean validContentType(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_CONTENT_TYPE_CHARS) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isISOControl(current) || Character.isWhitespace(current)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validFingerprint(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (value.length() != 32 && value.length() != 64) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.digit(value.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean validOwnerId(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return java.util.UUID.fromString(value).toString().equals(value.toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private Path binPath(String mediaId) {
        return rootDir.resolve(mediaId + ".bin");
    }

    private Path metaPath(String mediaId) {
        return rootDir.resolve(mediaId + ".meta.json");
    }

    private static final class Meta {
        String mediaId;
        String typeWire;
        String contentType;
        String fingerprint;
        long createdAtMs;
        long expiresAtMs;
        int byteLen;
        String ownerId;

        Meta(
                String mediaId,
                String typeWire,
                String contentType,
                String fingerprint,
                long createdAtMs,
                long expiresAtMs,
                int byteLen,
                String ownerId) {
            this.mediaId = mediaId;
            this.typeWire = typeWire;
            this.contentType = contentType;
            this.fingerprint = fingerprint;
            this.createdAtMs = createdAtMs;
            this.expiresAtMs = expiresAtMs;
            this.byteLen = byteLen;
            this.ownerId = ownerId;
        }
    }
}

