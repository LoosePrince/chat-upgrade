package com.chat.upgrade.client.net.servermedia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

final class PendingClientRequestRegistryTest {
    @Test
    void enforcesPendingQuotaAndReleasesCapacityOnCompletion() {
        PendingClientRequestRegistry<String> registry = new PendingClientRequestRegistry<>(2, 30_000L);
        AtomicLong ids = new AtomicLong(1L);
        var first = registry.begin(ids::getAndIncrement, 1_000L);
        var second = registry.begin(ids::getAndIncrement, 1_000L);

        assertEquals(2, registry.size());
        assertNull(registry.begin(ids::getAndIncrement, 1_000L));

        registry.complete(first.requestId(), Optional.of("done"));
        assertEquals(Optional.of("done"), first.future().join());
        assertEquals(1, registry.size());
        assertTrue(registry.begin(ids::getAndIncrement, 1_001L) != null);
        assertFalse(second.future().isDone());
    }

    @Test
    void expiresRequestsWithoutTreatingClockRollbackAsElapsedTime() {
        PendingClientRequestRegistry<String> registry = new PendingClientRequestRegistry<>(1, 30_000L);
        var pending = registry.begin(() -> 1L, 10_000L);

        registry.cleanup(1_000L);
        assertFalse(pending.future().isDone());
        registry.cleanup(39_999L);
        assertFalse(pending.future().isDone());
        registry.cleanup(40_000L);

        assertEquals(Optional.empty(), pending.future().join());
        assertEquals(0, registry.size());
    }

    @Test
    void failAndClearCompleteAllWaitersEmpty() {
        PendingClientRequestRegistry<String> registry = new PendingClientRequestRegistry<>(3, 30_000L);
        AtomicLong ids = new AtomicLong(1L);
        var failed = registry.begin(ids::getAndIncrement, 1_000L);
        var cleared = registry.begin(ids::getAndIncrement, 1_000L);

        registry.fail(failed.requestId());
        assertEquals(Optional.empty(), failed.future().join());
        assertFalse(cleared.future().isDone());

        registry.clear();
        assertEquals(Optional.empty(), cleared.future().join());
        assertEquals(0, registry.size());
    }
}