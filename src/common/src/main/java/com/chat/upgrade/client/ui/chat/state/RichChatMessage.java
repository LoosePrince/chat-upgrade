package com.chat.upgrade.client.ui.chat.state;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.MinecraftGuiBridge;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.ui.chat.InlineEmojiSlot;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

public record RichChatMessage(
        String messageId,
        ChatAuthor author,
        ChatMessageKind kind,
        long serverTimestampMs,
        @Nullable ChatReplySummary replyTo,
        int addedTime,
        Component component,
        Component originalComponent,
        String plainText,
        String fallbackText,
        List<RichAttachment> attachments,
        List<InlineEmojiSlot> inlineEmojiSlots,
        RichChatMessageSource source,
        @Nullable MessageSignature signature,
        RichChatMessageStatus status) {
    private static final AtomicLong LOCAL_IDS = new AtomicLong();

    public RichChatMessage(
            String messageId,
            String senderName,
            Component component,
            String fallbackText,
            List<RichAttachment> attachments,
            RichChatMessageSource source) {
        this(
                messageId,
                ChatAuthor.legacy(senderName),
                defaultKind(source),
                0L,
                null,
                currentGuiTicks(),
                component,
                component,
                component == null ? "" : component.getString(),
                fallbackText,
                attachments,
                List.of(),
                source,
                null,
                RichChatMessageStatus.VISIBLE);
    }

    public RichChatMessage(
            String messageId,
            String senderName,
            int addedTime,
            Component component,
            Component originalComponent,
            String plainText,
            String fallbackText,
            List<RichAttachment> attachments,
            List<InlineEmojiSlot> inlineEmojiSlots,
            RichChatMessageSource source,
            @Nullable MessageSignature signature,
            RichChatMessageStatus status) {
        this(
                messageId,
                ChatAuthor.legacy(senderName),
                defaultKind(source),
                0L,
                null,
                addedTime,
                component,
                originalComponent,
                plainText,
                fallbackText,
                attachments,
                inlineEmojiSlots,
                source,
                signature,
                status);
    }

    public RichChatMessage {
        messageId = normalizeId(messageId);
        author = author == null ? ChatAuthor.system() : author;
        kind = kind == null ? defaultKind(source) : kind;
        serverTimestampMs = Math.max(0L, serverTimestampMs);
        component = component == null ? Component.empty() : component;
        originalComponent = originalComponent == null ? component : originalComponent;
        plainText = safeText(plainText);
        fallbackText = safeText(fallbackText);
        attachments = List.copyOf(Objects.requireNonNullElse(attachments, List.of()));
        inlineEmojiSlots = List.copyOf(Objects.requireNonNullElse(inlineEmojiSlots, List.of()));
        source = source == null ? RichChatMessageSource.VANILLA_TEXT : source;
        status = status == null ? RichChatMessageStatus.VISIBLE : status;
        if (status == RichChatMessageStatus.DELETED) {
            replyTo = null;
            component = Component.empty();
            originalComponent = Component.empty();
            plainText = "";
            fallbackText = "";
            attachments = List.of();
            inlineEmojiSlots = List.of();
            signature = null;
        }
    }

    public String senderName() {
        return author.searchableName();
    }

    public boolean authoredByLocalPlayer() {
        return author.playerId() != null && author.localPlayer();
    }

    public boolean hasRenderableAttachment() {
        return attachments.stream().anyMatch(RichAttachment::hasRenderableUrl);
    }

    public @Nullable RichAttachment firstRenderableAttachment() {
        return attachments.stream()
                .filter(RichAttachment::hasRenderableUrl)
                .findFirst()
                .orElse(null);
    }

    public RichChatMessage withIdentity(ChatAuthor nextAuthor, ChatMessageKind nextKind) {
        return new RichChatMessage(
                messageId,
                nextAuthor,
                nextKind,
                serverTimestampMs,
                replyTo,
                addedTime,
                component,
                originalComponent,
                plainText,
                fallbackText,
                attachments,
                inlineEmojiSlots,
                source,
                signature,
                status);
    }

    public RichChatMessage withReplyTo(@Nullable ChatReplySummary nextReplyTo) {
        return new RichChatMessage(
                messageId,
                author,
                kind,
                serverTimestampMs,
                nextReplyTo,
                addedTime,
                component,
                originalComponent,
                plainText,
                fallbackText,
                attachments,
                inlineEmojiSlots,
                source,
                signature,
                status);
    }

    public RichChatMessage withStatus(RichChatMessageStatus nextStatus) {
        return new RichChatMessage(
                messageId,
                author,
                kind,
                serverTimestampMs,
                replyTo,
                addedTime,
                component,
                originalComponent,
                plainText,
                fallbackText,
                attachments,
                inlineEmojiSlots,
                source,
                signature,
                nextStatus);
    }

    public RichChatMessage withComponent(Component nextComponent, String nextPlainText, String nextFallbackText) {
        Component safeComponent = nextComponent == null ? Component.empty() : nextComponent;
        return new RichChatMessage(
                messageId,
                author,
                kind,
                serverTimestampMs,
                replyTo,
                addedTime,
                safeComponent,
                originalComponent,
                safeText(nextPlainText),
                safeText(nextFallbackText),
                attachments,
                inlineEmojiSlots,
                source,
                signature,
                status);
    }

    private static ChatMessageKind defaultKind(@Nullable RichChatMessageSource source) {
        return ChatMessageClassifier.defaultKind(source);
    }

    private static int currentGuiTicks() {
        Minecraft minecraft = Minecraft.getInstance();
        return MinecraftGuiBridge.guiTicks(minecraft);
    }

    private static String normalizeId(@Nullable String value) {
        String normalized = safeName(value);
        return normalized.isBlank()
                ? "local-" + Long.toUnsignedString(LOCAL_IDS.incrementAndGet(), 36)
                : normalized;
    }

    private static String safeName(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeText(@Nullable String value) {
        return value == null ? "" : value;
    }
}