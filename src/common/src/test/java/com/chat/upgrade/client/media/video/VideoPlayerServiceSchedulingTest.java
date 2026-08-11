package com.chat.upgrade.client.media.video;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NavigableMap;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

final class VideoPlayerServiceSchedulingTest {
    @Test
    void returnsNullWhenFrameWindowIsEmpty() {
        assertNull(VideoPlayerService.valueAtOrFirst(new TreeMap<>(), 2_000L));
    }

    @Test
    void retainsPrefetchedFramesDuringContinuousPlayback() {
        assertTrue(VideoPlayerService.shouldRetainCachedFrame(3_500L, 1_500L, 3_463L, false));
        assertFalse(VideoPlayerService.shouldRetainCachedFrame(1_499L, 1_500L, 3_463L, false));
        assertFalse(VideoPlayerService.shouldRetainCachedFrame(3_500L, 1_500L, 3_463L, true));
    }

    @Test
    void releasesPrefetchedFramesOnlyWhenAnExplicitSeekResetsTheDecoder() {
        assertFalse(VideoPlayerService.shouldReleasePrefetchedFrames(false));
        assertTrue(VideoPlayerService.shouldReleasePrefetchedFrames(true));
    }

    @Test
    void restartsDecoderWhenPruningLeavesNoFramesAtDecodedWindowEnd() {
        assertTrue(VideoPlayerService.shouldResetDecoderForCacheMiss(true, false, 5_458L, 5_455L));
        assertTrue(VideoPlayerService.shouldResetDecoderForCacheMiss(true, true, 4_000L, 5_455L));
        assertFalse(VideoPlayerService.shouldResetDecoderForCacheMiss(false, false, 5_458L, 5_455L));
        assertFalse(VideoPlayerService.shouldResetDecoderForCacheMiss(true, false, 5_454L, 5_455L));
    }

    @Test
    void keepsRgbaCacheMemoryAccountingInBytes() {
        assertEquals(4, VideoPlayerService.rgbaByteSize(1, 1));
        assertEquals(1_048_576, VideoPlayerService.rgbaByteSize(512, 512));
    }

    @Test
    void compactsPaddedRgbaRowsBeforeCaching() {
        byte[] source = {
                1, 2, 3, 4, 5, 6, 7, 8, 99, 99, 99, 99,
                9, 10, 11, 12, 13, 14, 15, 16, 99, 99, 99, 99
        };

        assertArrayEquals(
                new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                VideoPlayerService.compactRgbaRows(source, 12, 2, 2));
    }

    @Test
    void selectsPrecedingFrameOrEarliestFallback() {
        NavigableMap<Long, String> frames = new TreeMap<>();
        frames.put(1_000L, "first");
        frames.put(2_000L, "second");

        assertEquals("first", VideoPlayerService.valueAtOrFirst(frames, 500L));
        assertEquals("first", VideoPlayerService.valueAtOrFirst(frames, 1_500L));
        assertEquals("second", VideoPlayerService.valueAtOrFirst(frames, 2_500L));
    }
}