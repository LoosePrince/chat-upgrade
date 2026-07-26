package com.chat.upgrade.client.ui.chat.state;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.MinecraftGuiBridge;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.ui.chat.InlineEmojiSlot;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

public final class RichChatIngress {
    private RichChatIngress() {
    }

    public static RichChatMessage record(
            String messageId,
            String senderName,
            Component component,
            String fallbackText,
            List<RichAttachment> attachments,
            RichChatMessageSource source) {
        return record(
                messageId,
                senderName,
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

    public static RichChatMessage record(
            String messageId,
            String senderName,
            Component component,
            String fallbackText,
            List<RichAttachment> attachments,
            List<InlineEmojiSlot> inlineEmojiSlots,
            RichChatMessageSource source) {
        return record(
                messageId,
                senderName,
                currentGuiTicks(),
                component,
                component,
                component == null ? "" : component.getString(),
                fallbackText,
                attachments,
                inlineEmojiSlots,
                source,
                null,
                RichChatMessageStatus.VISIBLE);
    }

    public static RichChatMessage record(
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
        ChatMessageKind classified = ChatMessageClassifier.classify(component, null, source);
        ChatAuthor author = ChatIdentityResolver.resolve(
                ChatAuthor.legacy(senderName),
                component,
                classified);
        ChatPrivateMessageResolver.Resolution privateResolution = ChatPrivateMessageResolver.resolve(component, author);
        if (privateResolution != null) {
            ChatMessageGroupStore.rememberPeer(privateResolution.peerId(), privateResolution.peerPlayerId());
            author = privateResolution.author();
            classified = ChatMessageKind.PLAYER;
        }
        return RichChatStateStore.append(new RichChatMessage(
                messageId,
                author,
                classified,
                privateResolution == null ? null : privateResolution.peerId(),
                privateResolution == null ? null : privateResolution.body(),
                currentTimestampMs(),
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
                status));
    }

    public static RichChatMessage recordLegacy(
            String messageId,
            ChatMessageKind kind,
            Component component,
            String fallbackText,
            List<RichAttachment> attachments,
            List<InlineEmojiSlot> inlineEmojiSlots,
            RichChatMessageSource source) {
        return recordLegacy(
                messageId,
                "",
                kind,
                component,
                fallbackText,
                attachments,
                inlineEmojiSlots,
                source);
    }

    public static RichChatMessage recordLegacy(
            String messageId,
            String senderName,
            ChatMessageKind kind,
            Component component,
            String fallbackText,
            List<RichAttachment> attachments,
            List<InlineEmojiSlot> inlineEmojiSlots,
            RichChatMessageSource source) {
        ChatMessageKind classified = ChatMessageClassifier.classify(component, kind, source);
        ChatLegacyMessageNormalizer.Normalized normalized = ChatLegacyMessageNormalizer.normalize(
                component,
                inlineEmojiSlots,
                classified);
        String suppliedName = senderName == null || senderName.isBlank()
                ? normalized.authorName()
                : senderName;
        ChatAuthor author = ChatIdentityResolver.resolve(
                ChatAuthor.legacy(suppliedName),
                component,
                classified);
        ChatPrivateMessageResolver.Resolution privateResolution = ChatPrivateMessageResolver.resolve(component, author);
        if (privateResolution != null) {
            ChatMessageGroupStore.rememberPeer(privateResolution.peerId(), privateResolution.peerPlayerId());
            author = privateResolution.author();
            classified = ChatMessageKind.PLAYER;
        }
        return recordStructured(
                messageId,
                author,
                classified,
                privateResolution == null ? null : privateResolution.peerId(),
                privateResolution == null ? null : privateResolution.body(),
                currentTimestampMs(),
                null,
                normalized.body(),
                normalized.body().getString(),
                fallbackText,
                attachments,
                normalized.inlineEmojiSlots(),
                source);
    }

    public static RichChatMessage recordStructured(
            String messageId,
            ChatAuthor author,
            ChatMessageKind kind,
            long serverTimestampMs,
            @Nullable ChatReplySummary replyTo,
            Component component,
            String plainText,
            String fallbackText,
            List<RichAttachment> attachments,
            List<InlineEmojiSlot> inlineEmojiSlots,
            RichChatMessageSource source) {
        return recordStructured(
                messageId,
                author,
                kind,
                null,
                null,
                serverTimestampMs,
                replyTo,
                component,
                plainText,
                fallbackText,
                attachments,
                inlineEmojiSlots,
                source);
    }

    public static RichChatMessage recordStructured(
            String messageId,
            ChatAuthor author,
            ChatMessageKind kind,
            @Nullable java.util.UUID privatePeerId,
            @Nullable Component privateBodyComponent,
            long serverTimestampMs,
            @Nullable ChatReplySummary replyTo,
            Component component,
            String plainText,
            String fallbackText,
            List<RichAttachment> attachments,
            List<InlineEmojiSlot> inlineEmojiSlots,
            RichChatMessageSource source) {
        ChatPrivateMessageResolver.Resolution privateResolution = privatePeerId == null
                ? ChatPrivateMessageResolver.resolve(component, author)
                : null;
        if (privateResolution != null) {
            ChatMessageGroupStore.rememberPeer(privateResolution.peerId(), privateResolution.peerPlayerId());
            author = privateResolution.author();
            kind = ChatMessageKind.PLAYER;
            privatePeerId = privateResolution.peerId();
            privateBodyComponent = privateResolution.body();
        }
        return RichChatStateStore.append(new RichChatMessage(
                messageId,
                author,
                kind,
                privatePeerId,
                privateBodyComponent,
                serverTimestampMs > 0L ? serverTimestampMs : currentTimestampMs(),
                replyTo,
                currentGuiTicks(),
                component,
                component,
                plainText,
                fallbackText,
                attachments,
                inlineEmojiSlots,
                source,
                null,
                RichChatMessageStatus.VISIBLE));
    }

    public static RichChatMessage recordVanilla(
            Component component,
            @Nullable MessageSignature signature,
            int addedTime) {
        return record(
                "",
                "",
                addedTime,
                component,
                component,
                component == null ? "" : component.getString(),
                component == null ? "" : component.getString(),
                List.of(),
                List.of(),
                RichChatMessageSource.VANILLA_TEXT,
                signature,
                RichChatMessageStatus.VISIBLE);
    }

    public static void clear() {
        RichChatStateStore.clear();
    }

    private static long currentTimestampMs() {
        return System.currentTimeMillis();
    }

    private static int currentGuiTicks() {
        Minecraft minecraft = Minecraft.getInstance();
        return MinecraftGuiBridge.guiTicks(minecraft);
    }
}