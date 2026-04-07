package com.chat.upgrade.server.store;

import java.util.Optional;

public interface MediaStore {
    Optional<StoredMedia> get(String mediaId);

    void put(StoredMedia media) throws Exception;

    void delete(String mediaId);

    void cleanup(long nowMs, long maxTotalBytes);
}

