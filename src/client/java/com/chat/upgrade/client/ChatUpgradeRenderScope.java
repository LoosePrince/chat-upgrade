package com.chat.upgrade.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Holds the active {@link GuiGraphicsExtractor} while {@link net.minecraft.client.gui.components.ChatComponent}
 * runs {@code extractRenderState}. Uses a per-thread stack so nested extract paths stay balanced.
 * <p>
 * {@link ChatComponent}'s {@code captureClickableText} also calls {@code extractRenderState} with a
 * {@code ChatGraphicsAccess} that does not wrap a {@link GuiGraphicsExtractor}; we push a sentinel instead of
 * {@code null} because {@link ArrayDeque} rejects null elements.
 */
public final class ChatUpgradeRenderScope {
    private static final Object NO_EXTRACTOR = new Object();

    private static final ThreadLocal<Deque<Object>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private ChatUpgradeRenderScope() {}

    public static void push(@Nullable GuiGraphicsExtractor extractor) {
        STACK.get().push(extractor != null ? extractor : NO_EXTRACTOR);
    }

    public static void pop() {
        Deque<Object> d = STACK.get();
        if (!d.isEmpty()) {
            d.pop();
        }
    }

    /** Clears all stacked frames (e.g. before clickable-text hit testing). */
    public static void clear() {
        STACK.get().clear();
    }

    public static @Nullable GuiGraphicsExtractor current() {
        Deque<Object> d = STACK.get();
        if (d.isEmpty()) {
            return null;
        }
        Object top = d.peek();
        return top instanceof GuiGraphicsExtractor g ? g : null;
    }
}
