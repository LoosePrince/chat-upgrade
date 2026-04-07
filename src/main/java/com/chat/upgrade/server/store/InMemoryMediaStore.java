package com.chat.upgrade.server.store;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryMediaStore implements MediaStore {
    private final ConcurrentHashMap<String, StoredMedia> map = new ConcurrentHashMap<>();
    private final AtomicLong totalBytes = new AtomicLong(0L);

    @Override
    public Optional<StoredMedia> get(String mediaId) {
        if (mediaId == null || mediaId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(map.get(mediaId));
    }

    @Override
    public void put(StoredMedia media) {
        if (media == null || media.mediaId() == null || media.mediaId().isBlank()) {
            return;
        }
        StoredMedia prev = map.put(media.mediaId(), media);
        if (prev != null) {
            totalBytes.addAndGet(-prev.byteLength());
        }
        totalBytes.addAndGet(media.byteLength());
    }

    @Override
    public void delete(String mediaId) {
        StoredMedia prev = map.remove(mediaId);
        if (prev != null) {
            totalBytes.addAndGet(-prev.byteLength());
        }
    }

    @Override
    public void cleanup(long nowMs, long maxTotalBytes) {
        for (var e : new ArrayList<>(map.entrySet())) {
            StoredMedia m = e.getValue();
            if (m != null && m.isExpired(nowMs)) {
                delete(e.getKey());
            }
        }
        if (maxTotalBytes <= 0) {
            return;
        }
        long total = totalBytes.get();
        if (total <= maxTotalBytes) {
            return;
        }
        ArrayList<StoredMedia> all = new ArrayList<>(map.values());
        all.sort(Comparator.comparingLong(StoredMedia::createdAtMs));
        for (StoredMedia m : all) {
            if (totalBytes.get() <= maxTotalBytes) {
                break;
            }
            delete(m.mediaId());
        }
    }
}

