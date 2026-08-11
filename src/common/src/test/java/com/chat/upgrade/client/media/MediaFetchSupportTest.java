package com.chat.upgrade.client.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.Test;

final class MediaFetchSupportTest {
    @Test
    void rejectsOversizedDeclaredLengthBeforeRequestingBody() {
        MediaFetchSupport.CappedBodySubscriber subscriber =
                new MediaFetchSupport.CappedBodySubscriber(10, OptionalLong.of(11));
        TestSubscription subscription = new TestSubscription();
        subscriber.onSubscribe(subscription);

        assertTrue(subscription.cancelled);
        assertBodyTooLarge(subscriber);
    }

    @Test
    void cancelsStreamAsSoonAsReceivedBytesExceedLimit() {
        MediaFetchSupport.CappedBodySubscriber subscriber =
                new MediaFetchSupport.CappedBodySubscriber(10, OptionalLong.empty());
        TestSubscription subscription = new TestSubscription();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] { 1, 2, 3, 4, 5, 6 })));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] { 7, 8, 9, 10, 11 })));

        assertTrue(subscription.cancelled);
        assertBodyTooLarge(subscriber);
    }

    @Test
    void acceptsBodyExactlyAtLimitAcrossMultipleBuffers() {
        MediaFetchSupport.CappedBodySubscriber subscriber =
                new MediaFetchSupport.CappedBodySubscriber(10, OptionalLong.of(10));
        TestSubscription subscription = new TestSubscription();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(
                ByteBuffer.wrap(new byte[] { 1, 2, 3 }),
                ByteBuffer.wrap(new byte[] { 4, 5, 6, 7 })));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] { 8, 9, 10 })));
        subscriber.onComplete();

        assertArrayEquals(
                new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 },
                subscriber.getBody().toCompletableFuture().join());
        assertTrue(subscription.requested > 0L);
    }

    private static void assertBodyTooLarge(MediaFetchSupport.CappedBodySubscriber subscriber) {
        try {
            subscriber.getBody().toCompletableFuture().join();
            throw new AssertionError("expected response body limit failure");
        } catch (CompletionException exception) {
            assertInstanceOf(MediaFetchSupport.ResponseBodyTooLarge.class, exception.getCause());
        }
    }

    private static final class TestSubscription implements Flow.Subscription {
        private long requested;
        private boolean cancelled;

        @Override
        public void request(long count) {
            requested += count;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}