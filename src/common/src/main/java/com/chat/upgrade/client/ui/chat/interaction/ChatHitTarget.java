package com.chat.upgrade.client.ui.chat.interaction;

import java.util.Objects;

import com.chat.upgrade.client.media.model.RichAttachment;

import net.minecraft.network.chat.Style;

public sealed interface ChatHitTarget {
    record StyledText(Style style) implements ChatHitTarget {
        public StyledText {
            style = Objects.requireNonNull(style, "style");
        }
    }

    record Attachment(RichAttachment attachment) implements ChatHitTarget {
        public Attachment {
            attachment = Objects.requireNonNull(attachment, "attachment");
        }
    }

    record Emoji(RichAttachment attachment) implements ChatHitTarget {
        public Emoji {
            attachment = Objects.requireNonNull(attachment, "attachment");
        }
    }
}