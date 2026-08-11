package com.chat.upgrade.client.net.servermedia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

import org.jetbrains.annotations.Nullable;

final class PendingClientRequestRegistry<T> {
    private final int maxPending;
    private final long timeoutMs;
    private final HashMap<Long, Pending<T>> requests = new HashMap<>();

    PendingClientRequestRegistry(int maxPending, long timeoutMs) {
        if (maxPending <= 0 || timeoutMs <= 0L) {
            throw new IllegalArgumentException("invalid pending request policy");
        }
        this.maxPending = maxPending;
        this.timeoutMs = timeoutMs;
    }

    @Nullable Registration<T> begin(LongSupplier requestIds, long nowMs) {
        List<Pending<T>> expired;
        Registration<T> registration = null;
        synchronized (requests) {
            expired = removeExpiredLocked(nowMs);
            if (requests.size() < maxPending) {
                long requestId;
                do {
                    requestId = requestIds.getAsLong();
                } while (requestId == 0L || requests.containsKey(requestId));
                var future = new CompletableFuture<Optional<T>>();
                requests.put(requestId, new Pending<>(future, nowMs));
                registration = new Registration<>(requestId, future);
            }
        }
        completeEmpty(expired);
        return registration;
    }

    void complete(long requestId, Optional<T> result) {
        Pending<T> pending;
        synchronized (requests) {
            pending = requests.remove(requestId);
        }
        if (pending != null) {
            pending.future().complete(result == null ? Optional.empty() : result);
        }
    }

    void fail(long requestId) {
        complete(requestId, Optional.empty());
    }

    void cleanup(long nowMs) {
        List<Pending<T>> expired;
        synchronized (requests) {
            expired = removeExpiredLocked(nowMs);
        }
        completeEmpty(expired);
    }

    void clear() {
        List<Pending<T>> pending;
        synchronized (requests) {
            pending = new ArrayList<>(requests.values());
            requests.clear();
        }
        completeEmpty(pending);
    }

    int size() {
        synchronized (requests) {
            return requests.size();
        }
    }

    private List<Pending<T>> removeExpiredLocked(long nowMs) {
        List<Pending<T>> expired = new ArrayList<>();
        requests.entrySet().removeIf(entry -> {
            Pending<T> pending = entry.getValue();
            long ageMs = nowMs >= pending.createdAtMs() ? nowMs - pending.createdAtMs() : 0L;
            if (ageMs < timeoutMs) {
                return false;
            }
            expired.add(pending);
            return true;
        });
        return expired;
    }

    private static <T> void completeEmpty(List<Pending<T>> requests) {
        requests.forEach(pending -> pending.future().complete(Optional.empty()));
    }

    record Registration<T>(long requestId, CompletableFuture<Optional<T>> future) {
    }

    private record Pending<T>(CompletableFuture<Optional<T>> future, long createdAtMs) {
    }
}