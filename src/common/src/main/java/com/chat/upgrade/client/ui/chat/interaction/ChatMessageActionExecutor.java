package com.chat.upgrade.client.ui.chat.interaction;

import java.util.Optional;

import com.chat.upgrade.client.ui.chat.input.AttachmentSendController;
import com.chat.upgrade.client.ui.chat.input.ChatComposerState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ChatMessageActionExecutor {
    private ChatMessageActionExecutor() {
    }

    public static Optional<Component> execute(
            ChatContextMenu.Selection selection,
            ChatComposerState composerState,
            Minecraft minecraft) {
        if (selection == null || composerState == null || minecraft == null) {
            return Optional.empty();
        }
        ChatAction action = selection.action();
        if (action instanceof ChatAction.Reply) {
            composerState.setReplyTarget(ChatMessageActionCatalog.replyTarget(selection.message()));
            return Optional.empty();
        }
        if (action instanceof ChatAction.CopyText copy) {
            minecraft.keyboardHandler.setClipboard(copy.text());
            return Optional.of(Component.translatable("chatupgrade.action.copy.done")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (action instanceof ChatAction.Retract retract
                && !AttachmentSendController.retractMessage(retract.messageId())) {
            return Optional.of(Component.translatable("chatupgrade.retract.denied")
                    .withStyle(ChatFormatting.RED));
        }
        return Optional.empty();
    }
}