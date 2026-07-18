package com.chat.upgrade.client.ui.chat.interaction;

import net.minecraft.network.chat.Style;

public sealed interface ChatAction {
    record StyledText(Style style) implements ChatAction {
        public StyledText {
            style = style == null ? Style.EMPTY : style;
        }
    }

    record PreviewImage(String url, String displayName) implements ChatAction {
        public PreviewImage {
            url = safe(url);
            displayName = safe(displayName);
        }
    }

    record ToggleAudio(String url) implements ChatAction {
        public ToggleAudio {
            url = safe(url);
        }
    }

    record ToggleAudioLoop(String url) implements ChatAction {
        public ToggleAudioLoop {
            url = safe(url);
        }
    }

    record ToggleAudioFloating(String url, String displayName) implements ChatAction {
        public ToggleAudioFloating {
            url = safe(url);
            displayName = safe(displayName);
        }
    }

    record SeekAudio(String url, double ratio) implements ChatAction {
        public SeekAudio {
            url = safe(url);
            ratio = Math.clamp(ratio, 0.0D, 1.0D);
        }
    }

    record ToggleVideo(String url) implements ChatAction {
        public ToggleVideo {
            url = safe(url);
        }
    }

    record SeekVideo(String url, double ratio) implements ChatAction {
        public SeekVideo {
            url = safe(url);
            ratio = Math.clamp(ratio, 0.0D, 1.0D);
        }
    }

    record PreviewVideo(String url, String displayName) implements ChatAction {
        public PreviewVideo {
            url = safe(url);
            displayName = safe(displayName);
        }
    }

    record OpenUrl(String url) implements ChatAction {
        public OpenUrl {
            url = safe(url);
        }
    }

    record Reply(String messageId) implements ChatAction {
        public Reply {
            messageId = safe(messageId);
        }
    }

    record CopyText(String text) implements ChatAction {
        public CopyText {
            text = text == null ? "" : text;
        }
    }

    record Retract(String messageId) implements ChatAction {
        public Retract {
            messageId = safe(messageId);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}