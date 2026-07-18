package com.chat.upgrade.client.ui.chat.interaction;

import java.util.Optional;
import java.util.function.Consumer;

import com.chat.upgrade.client.ui.chat.input.AttachmentSendController;
import com.chat.upgrade.client.ui.chat.input.ChatComposerState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class ChatMessageActionExecutor {
    private ChatMessageActionExecutor() {
    }

    public static Optional<Component> execute(
            ChatContextMenu.Selection selection,
            ChatComposerState composerState,
            Minecraft minecraft) {
        return execute(selection, composerState, minecraft, ignored -> {
        });
    }

    public static Optional<Component> execute(
            ChatContextMenu.Selection selection,
            ChatComposerState composerState,
            Minecraft minecraft,
            Consumer<String> insertText) {
        if (selection == null || composerState == null || minecraft == null) {
            return Optional.empty();
        }
        ChatAction action = selection.action();
        if (action instanceof ChatAction.Reply) {
            composerState.setReplyTarget(ChatMessageActionCatalog.replyTarget(selection.message()));
            ChatTextSelectionState.clear();
            return Optional.empty();
        }
        if (action instanceof ChatAction.Mention mention) {
            if (!mention.authorName().isBlank()) {
                insertText.accept("@" + mention.authorName() + " ");
            }
            ChatTextSelectionState.clear();
            return Optional.empty();
        }
        if (action instanceof ChatAction.ShowProfile) {
            Component profile = profileMessage(selection.message());
            ChatTextSelectionState.clear();
            return Optional.of(profile);
        }
        if (action instanceof ChatAction.HideMessage hide) {
            ChatMessageVisibilityStore.hideMessage(hide.messageId());
            ChatTextSelectionState.clearIfMessage(hide.messageId());
            return Optional.of(Component.translatable("chatupgrade.action.hide.done")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (action instanceof ChatAction.ToggleBlockAuthor) {
            boolean blocked = ChatMessageVisibilityStore.toggleAuthor(selection.message().author());
            if (blocked) {
                ChatTextSelectionState.clearIfAuthor(selection.message().author().identityKey());
            }
            return Optional.of(blockResult(selection.message().author().identityKey(), blocked));
        }
        if (action instanceof ChatAction.CopyText copy) {
            minecraft.keyboardHandler.setClipboard(copy.text());
            ChatTextSelectionState.clear();
            return Optional.of(Component.translatable("chatupgrade.action.copy.done")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (action instanceof ChatAction.DebugInfo) {
            String debug = debugMessage(selection.message());
            minecraft.keyboardHandler.setClipboard(debug);
            ChatTextSelectionState.clear();
            return Optional.of(Component.translatable("chatupgrade.action.debug.done")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (action instanceof ChatAction.Retract retract) {
            if (!AttachmentSendController.retractMessage(retract.messageId())) {
                return Optional.of(Component.translatable("chatupgrade.retract.denied")
                        .withStyle(ChatFormatting.RED));
            }
            ChatTextSelectionState.clearIfMessage(retract.messageId());
        }
        return Optional.empty();
    }

    private static Component blockResult(String authorKey, boolean blocked) {
        if (!blocked || authorKey == null || authorKey.isBlank()) {
            return Component.translatable("chatupgrade.action.unblock.done")
                    .withStyle(ChatFormatting.GRAY);
        }
        Component undo = Component.translatable("chatupgrade.action.block.undo")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.SuggestCommand(
                                "/chatupgrade visibility unblock " + authorKey)));
        return Component.translatable("chatupgrade.action.block.done")
                .withStyle(ChatFormatting.GRAY)
                .append(" ")
                .append(undo);
    }

    private static Component profileMessage(com.chat.upgrade.client.ui.chat.state.RichChatMessage message) {
        String name = message.author().visibleName();
        String key = message.author().identityKey();
        String team = message.author().team().present()
                ? message.author().team().teamName()
                : Component.translatable("chatupgrade.common.na").getString();
        return Component.translatable("chatupgrade.action.profile.details", name, key, team)
                .withStyle(ChatFormatting.GRAY);
    }

    private static String debugMessage(com.chat.upgrade.client.ui.chat.state.RichChatMessage message) {
        return "messageId=" + message.messageId()
                + ", source=" + message.source()
                + ", kind=" + message.kind()
                + ", timestampMs=" + message.serverTimestampMs()
                + ", author=" + message.author().identityKey()
                + ", status=" + message.status();
    }
}