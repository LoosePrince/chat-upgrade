package com.chat.upgrade.client.ui.chat.state;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.media.model.RichAttachment;

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
            RichChatMessageSource source,
            @Nullable MessageSignature signature,
            RichChatMessageStatus status) {
        return RichChatStateStore.append(new RichChatMessage(
                messageId,
                senderName,
                addedTime,
                component,
                originalComponent,
                plainText,
                fallbackText,
                attachments,
                source,
                signature,
                status));
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
                RichChatMessageSource.VANILLA_TEXT,
                signature,
                RichChatMessageStatus.VISIBLE);
    }

    public static void clear() {
        RichChatStateStore.clear();
    }

    private static int currentGuiTicks() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.gui == null ? 0 : minecraft.gui.getGuiTicks();
    }
}