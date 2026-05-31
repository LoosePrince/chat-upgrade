package com.chat.upgrade.client.ui.chat;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;

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

    private static @Nullable RichAttachment pendingDecodedAttachment;
    private static @Nullable RichAttachment nextPhantomTopAttachment;
    private static InlineResourceType nextPhantomTopType = InlineResourceType.IMAGE;
    private static boolean nextPhantomContinuation;

    public record PendingDecoded(@Nullable RichAttachment attachment) {
        public @Nullable String url() {
            return attachment == null ? null : attachment.urlOrNull();
        }

        public @Nullable String name() {
            return attachment == null ? null : attachment.displayName();
        }

        public InlineResourceType type() {
            return attachment == null ? InlineResourceType.IMAGE : attachment.type();
        }
    }

    public record PhantomLineHints(
            @Nullable RichAttachment attachment,
            InlineResourceType fallbackType,
            boolean continuation) {
        public @Nullable String topUrl() {
            return continuation || attachment == null ? null : attachment.urlOrNull();
        }

        public @Nullable String topName() {
            return continuation || attachment == null ? null : attachment.displayName();
        }

        public InlineResourceType topType() {
            return attachment == null ? fallbackType : attachment.type();
        }
    }

    public static void setPendingDecoded(RichAttachment attachment) {
        pendingDecodedAttachment = attachment;
    }

    public static void setPendingDecoded(@Nullable String url, @Nullable String name, InlineResourceType type) {
        pendingDecodedAttachment = url == null ? null : RichAttachment.legacyBracket(url, name, type);
    }

    public static PendingDecoded consumePendingDecoded() {
        PendingDecoded pending = new PendingDecoded(pendingDecodedAttachment);
        pendingDecodedAttachment = null;
        return pending;
    }

    public static void prepareNextPhantomTop(RichAttachment attachment) {
        nextPhantomTopAttachment = attachment;
        nextPhantomTopType = attachment.type();
    }

    public static void prepareNextPhantomTop(InlineResourceType type, @Nullable String name, @Nullable String url) {
        nextPhantomTopAttachment = url == null ? null : RichAttachment.legacyBracket(url, name, type);
        nextPhantomTopType = type;
    }

    public static void prepareNextPhantomType(InlineResourceType type) {
        nextPhantomTopType = type;
    }

    public static void prepareNextPhantomTopUrl(@Nullable String url) {
        if (url == null) {
            nextPhantomTopAttachment = null;
            return;
        }
        if (nextPhantomTopAttachment == null) {
            nextPhantomTopAttachment = RichAttachment.legacyBracket(url, null, nextPhantomTopType);
        }
    }

    public static void prepareNextPhantomContinuation() {
        nextPhantomContinuation = true;
    }

    public static PhantomLineHints consumePhantomLineHints() {
        RichAttachment attachment = nextPhantomContinuation ? null : nextPhantomTopAttachment;
        PhantomLineHints hints = new PhantomLineHints(
                attachment,
                nextPhantomTopType,
                nextPhantomContinuation);
        if (!nextPhantomContinuation && nextPhantomTopAttachment != null) {
            nextPhantomTopAttachment = null;
            nextPhantomTopType = InlineResourceType.IMAGE;
        }
        nextPhantomContinuation = false;
        return hints;
    }
}
