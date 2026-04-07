package com.chat.upgrade.server.store;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class DiskMediaStore implements MediaStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path rootDir;
    private final ConcurrentHashMap<String, Meta> metaById = new ConcurrentHashMap<>();
    private final AtomicLong totalBytes = new AtomicLong(0L);

    public DiskMediaStore(Path rootDir) throws Exception {
        this.rootDir = rootDir;
        Files.createDirectories(rootDir);
        loadExisting();
    }

    @Override
    public Optional<StoredMedia> get(String mediaId) {
        if (mediaId == null || mediaId.isBlank()) {
            return Optional.empty();
        }
        Meta meta = metaById.get(mediaId);
        if (meta == null) {
            return Optional.empty();
        }
        Path bin = binPath(mediaId);
        try {
            if (!Files.isRegularFile(bin)) {
                delete(mediaId);
                return Optional.empty();
            }
            byte[] body = Files.readAllBytes(bin);
            return Optional.of(new StoredMedia(mediaId, meta.typeWire, meta.contentType, body, meta.createdAtMs,
                    meta.expiresAtMs));
        } catch (Exception e) {
            delete(mediaId);
            return Optional.empty();
        }
    }

    @Override
    public void put(StoredMedia media) throws Exception {
        if (media == null || media.mediaId() == null || media.mediaId().isBlank()) {
            return;
        }
        String id = media.mediaId();
        Path bin = binPath(id);
        Path metaPath = metaPath(id);
        Files.createDirectories(rootDir);
        Files.write(bin, media.body());
        Meta meta = new Meta(id, media.typeWire(), media.contentType(), media.createdAtMs(), media.expiresAtMs(),
                media.byteLength());
        try (Writer w = Files.newBufferedWriter(metaPath)) {
            GSON.toJson(meta, w);
        }
        Meta prev = metaById.put(id, meta);
        if (prev != null) {
            totalBytes.addAndGet(-prev.byteLen);
        }
        totalBytes.addAndGet(meta.byteLen);
    }

    @Override
    public void delete(String mediaId) {
        if (mediaId == null || mediaId.isBlank()) {
            return;
        }
        Meta prev = metaById.remove(mediaId);
        if (prev != null) {
            totalBytes.addAndGet(-prev.byteLen);
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
                    if (meta == null || meta.mediaId == null || meta.mediaId.isBlank()) {
                        continue;
                    }
                    Path bin = binPath(meta.mediaId);
                    if (!Files.isRegularFile(bin)) {
                        Files.deleteIfExists(p);
                        continue;
                    }
                    metaById.put(meta.mediaId, meta);
                    totalBytes.addAndGet(meta.byteLen);
                } catch (Exception ignored) {
                }
            }
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
        long createdAtMs;
        long expiresAtMs;
        int byteLen;

        Meta(String mediaId, String typeWire, String contentType, long createdAtMs, long expiresAtMs, int byteLen) {
            this.mediaId = mediaId;
            this.typeWire = typeWire;
            this.contentType = contentType;
            this.createdAtMs = createdAtMs;
            this.expiresAtMs = expiresAtMs;
            this.byteLen = byteLen;
        }
    }
}

