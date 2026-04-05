package com.chat.upgrade.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Holds the active {@link GuiGraphicsExtractor} while {@link net.minecraft.client.gui.components.ChatComponent}
 * runs {@code extractRenderState} (and clears during {@code captureClickableText}). Uses a per-thread stack so
 * nested extract paths stay balanced.
 */
public final class ChatUpgradeRenderScope {
    private static final ThreadLocal<Deque<GuiGraphicsExtractor>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private ChatUpgradeRenderScope() {}

    public static void push(@Nullable GuiGraphicsExtractor extractor) {
        STACK.get().push(extractor);
    }

    public static void pop() {
        Deque<GuiGraphicsExtractor> d = STACK.get();
        if (!d.isEmpty()) {
            d.pop();
        }
    }

    /** Clears all stacked frames (e.g. before clickable-text hit testing). */
    public static void clear() {
        STACK.get().clear();
    }

    public static @Nullable GuiGraphicsExtractor current() {
        Deque<GuiGraphicsExtractor> d = STACK.get();
        return d.isEmpty() ? null : d.peek();
    }
}
