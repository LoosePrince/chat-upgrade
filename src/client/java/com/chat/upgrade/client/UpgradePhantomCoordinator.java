package com.chat.upgrade.client;

import org.jetbrains.annotations.Nullable;

/**
 * Cross-mixin coordination for extra chat rows: pending URL attachment while messages are being appended, and
 * per-constructor hints when {@link net.minecraft.client.multiplayer.chat.GuiMessage.Line} instances are fabricated.
 */
public final class UpgradePhantomCoordinator {
    private UpgradePhantomCoordinator() {}

    public static @Nullable String pendingDecodedUrl;
    public static InlineResourceType pendingDecodedType = InlineResourceType.IMAGE;
    public static @Nullable String nextPhantomTopUrl;
    public static InlineResourceType nextPhantomTopType = InlineResourceType.IMAGE;
    public static boolean nextPhantomContinuation;
}
