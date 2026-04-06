package com.chat.upgrade.client;

import org.jetbrains.annotations.Nullable;

/**
 * Cross-mixin coordination for extra chat rows: pending URL attachment while
 * messages are being appended, and
 * per-constructor hints when
 * {@link net.minecraft.client.multiplayer.chat.GuiMessage.Line} instances are
 * fabricated.
 */
public final class UpgradePhantomCoordinator {
    private UpgradePhantomCoordinator() {
    }

    private static @Nullable String pendingDecodedUrl;
    private static @Nullable String pendingDecodedName;
    private static InlineResourceType pendingDecodedType = InlineResourceType.IMAGE;
    private static @Nullable String nextPhantomTopUrl;
    private static @Nullable String nextPhantomTopName;
    private static InlineResourceType nextPhantomTopType = InlineResourceType.IMAGE;
    private static boolean nextPhantomContinuation;

    public record PendingDecoded(@Nullable String url, @Nullable String name, InlineResourceType type) {
    }

    public record PhantomLineHints(
            @Nullable String topUrl,
            @Nullable String topName,
            InlineResourceType topType,
            boolean continuation) {
    }

    public static void setPendingDecoded(@Nullable String url, @Nullable String name, InlineResourceType type) {
        pendingDecodedUrl = url;
        pendingDecodedName = name;
        pendingDecodedType = type;
    }

    public static PendingDecoded consumePendingDecoded() {
        PendingDecoded pending = new PendingDecoded(pendingDecodedUrl, pendingDecodedName, pendingDecodedType);
        pendingDecodedUrl = null;
        pendingDecodedName = null;
        pendingDecodedType = InlineResourceType.IMAGE;
        return pending;
    }

    public static void prepareNextPhantomTop(InlineResourceType type, @Nullable String name, @Nullable String url) {
        nextPhantomTopType = type;
        nextPhantomTopName = name;
        nextPhantomTopUrl = url;
    }

    public static void prepareNextPhantomType(InlineResourceType type) {
        nextPhantomTopType = type;
    }

    public static void prepareNextPhantomTopUrl(@Nullable String url) {
        nextPhantomTopUrl = url;
    }

    public static void prepareNextPhantomContinuation() {
        nextPhantomContinuation = true;
    }

    public static PhantomLineHints consumePhantomLineHints() {
        PhantomLineHints hints = new PhantomLineHints(
                nextPhantomTopUrl,
                nextPhantomTopName,
                nextPhantomTopType,
                nextPhantomContinuation);
        if (nextPhantomTopUrl != null) {
            nextPhantomTopUrl = null;
            nextPhantomTopName = null;
            nextPhantomTopType = InlineResourceType.IMAGE;
        }
        nextPhantomContinuation = false;
        return hints;
    }
}
