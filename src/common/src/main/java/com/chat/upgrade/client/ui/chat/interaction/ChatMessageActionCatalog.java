package com.chat.upgrade.client.ui.chat.interaction;

import java.util.ArrayList;
import java.util.List;

import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.ui.chat.state.ChatReplySummary;
import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageSource;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageStatus;

import net.minecraft.network.chat.Component;

public final class ChatMessageActionCatalog {
    private static final int MAX_REPLY_EXCERPT_CHARS = 96;

    public record Item(Component label, ChatAction action, boolean destructive) {
        public Item {
            if (label == null || action == null) {
                throw new IllegalArgumentException("label and action must not be null");
            }
        }
    }

    private ChatMessageActionCatalog() {
    }

    public static List<Item> actionsFor(RichChatMessage message) {
        if (message == null || message.status() == RichChatMessageStatus.DELETED) {
            return List.of();
        }
        List<Item> actions = new ArrayList<>();
        if (hasTrustedServerIdentity(message)) {
            actions.add(new Item(
                    Component.translatable("chatupgrade.action.reply"),
                    new ChatAction.Reply(message.messageId()),
                    false));
        }
        String copyText = copyText(message);
        if (!copyText.isBlank()) {
            actions.add(new Item(
                    Component.translatable("chatupgrade.action.copy"),
                    new ChatAction.CopyText(copyText),
                    false));
        }
        String authorName = message.author().searchableName().trim();
        String authorKey = message.author().identityKey();
        if (message.kind().playerAuthored() && !authorName.isBlank()) {
            actions.add(new Item(
                    Component.translatable("chatupgrade.action.mention"),
                    new ChatAction.Mention(authorName),
                    false));
        }
        if (message.kind().playerAuthored() && !authorKey.isBlank()) {
            actions.add(new Item(
                    Component.translatable("chatupgrade.action.profile"),
                    new ChatAction.ShowProfile(authorKey),
                    false));
            if (!message.authoredByLocalPlayer()) {
                boolean blocked = ChatMessageVisibilityStore.isAuthorBlocked(message.author());
                actions.add(new Item(
                        Component.translatable(blocked
                                ? "chatupgrade.action.unblock"
                                : "chatupgrade.action.block"),
                        new ChatAction.ToggleBlockAuthor(authorKey),
                        !blocked));
            }
        }
        actions.add(new Item(
                Component.translatable("chatupgrade.action.hide"),
                new ChatAction.HideMessage(message.messageId()),
                true));
        if (hasTrustedServerIdentity(message) && message.authoredByLocalPlayer()) {
            actions.add(new Item(
                    Component.translatable("chatupgrade.action.retract"),
                    new ChatAction.Retract(message.messageId()),
                    true));
        }
        if (ChatUpgradeConfig.get().debugChatActions) {
            actions.add(new Item(
                    Component.translatable("chatupgrade.action.debug"),
                    new ChatAction.DebugInfo(message.messageId()),
                    false));
        }
        return List.copyOf(actions);
    }

    public static ChatReplySummary replyTarget(RichChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        return new ChatReplySummary(message.messageId(), message.author(), replyExcerpt(message));
    }

    private static boolean hasTrustedServerIdentity(RichChatMessage message) {
        return message.source() == RichChatMessageSource.STRUCTURED_PACKET
                && message.serverTimestampMs() > 0L
                && message.status() == RichChatMessageStatus.VISIBLE;
    }

    private static String replyExcerpt(RichChatMessage message) {
        String excerpt = message.plainText().replaceAll("\\s+", " ").trim();
        if (excerpt.isBlank() && !message.attachments().isEmpty()) {
            RichAttachment attachment = message.attachments().getFirst();
            excerpt = "[" + attachment.displayName() + "]";
        }
        if (excerpt.length() > MAX_REPLY_EXCERPT_CHARS) {
            return excerpt.substring(0, MAX_REPLY_EXCERPT_CHARS - 1) + "…";
        }
        return excerpt;
    }

    private static String copyText(RichChatMessage message) {
        String selected = ChatTextSelectionState.selectedTextFor(message.messageId());
        if (!selected.isEmpty()) {
            return selected;
        }
        List<String> lines = new ArrayList<>();
        String text = message.plainText().trim();
        if (!text.isBlank()) {
            lines.add(text);
        }
        message.attachments().stream()
                .filter(RichAttachment::hasRenderableUrl)
                .map(RichAttachment::requireRenderableUrl)
                .forEach(lines::add);
        return String.join("\n", lines);
    }
}