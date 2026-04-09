package com.chat.upgrade.client.ui.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.FormattedCharSequence;

public final class InlineEmojiCoordinator {
    private static final ThreadLocal<ArrayDeque<InlineEmojiSlot>> PENDING_SLOTS = ThreadLocal
            .withInitial(ArrayDeque::new);

    private InlineEmojiCoordinator() {
    }

    public static void setPendingSlots(List<InlineEmojiSlot> slots) {
        ArrayDeque<InlineEmojiSlot> queue = PENDING_SLOTS.get();
        queue.clear();
        queue.addAll(slots);
    }

    public static void clearPendingSlots() {
        PENDING_SLOTS.get().clear();
    }

    public static List<InlineEmojiSlot> consumeForLine(FormattedCharSequence lineContent) {
        List<Integer> placeholderIndexes = findPlaceholderIndexes(lineContent);
        if (placeholderIndexes.isEmpty()) {
            return List.of();
        }
        ArrayDeque<InlineEmojiSlot> queue = PENDING_SLOTS.get();
        if (queue.isEmpty()) {
            return List.of();
        }
        List<InlineEmojiSlot> out = new ArrayList<>();
        for (int idx : placeholderIndexes) {
            InlineEmojiSlot next = queue.pollFirst();
            if (next == null) {
                break;
            }
            out.add(new InlineEmojiSlot(idx, next.iconUrl(), next.token()));
        }
        return out.isEmpty() ? List.of() : Collections.unmodifiableList(out);
    }

    private static List<Integer> findPlaceholderIndexes(FormattedCharSequence seq) {
        List<Integer> out = new ArrayList<>();
        final boolean[] inMarkerRun = new boolean[] { false };
        final int[] logicalIndex = new int[] { 0 };
        seq.accept((index, style, codePoint) -> {
            boolean isMarker = InlineEmojiCodec.isEmojiSlotStyle(style);
            if (isMarker && !inMarkerRun[0]) {
                out.add(logicalIndex[0]);
            }
            inMarkerRun[0] = isMarker;
            logicalIndex[0] += Character.charCount(codePoint);
            return true;
        });
        return out;
    }

}
