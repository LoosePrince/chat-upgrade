package com.chat.upgrade.client.ui.chat.interaction;

import java.net.URI;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ui.chat.AudioControlClickEvent;
import com.chat.upgrade.client.ui.chat.AudioFloatingWindowClickEvent;
import com.chat.upgrade.client.ui.chat.AudioOptionsClickEvent;
import com.chat.upgrade.client.ui.chat.ImagePreviewClickEvent;
import com.chat.upgrade.client.ui.chat.VideoControlClickEvent;
import com.chat.upgrade.client.ui.chat.VideoPreviewClickEvent;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;

public final class ChatActionStyleAdapter {
    private ChatActionStyleAdapter() {
    }

    public static @Nullable Style toStyle(@Nullable ChatAction action) {
        if (action instanceof ChatAction.StyledText styled) {
            return styled.style();
        }
        if (action instanceof ChatAction.PreviewImage preview) {
            return Style.EMPTY.withClickEvent(ImagePreviewClickEvent.forUrlAndName(
                    preview.url(), preview.displayName()));
        }
        if (action instanceof ChatAction.ToggleAudio toggle) {
            return Style.EMPTY.withClickEvent(AudioControlClickEvent.forToggle(toggle.url()));
        }
        if (action instanceof ChatAction.ToggleAudioLoop loop) {
            return Style.EMPTY.withClickEvent(AudioControlClickEvent.forToggleLoop(loop.url()));
        }
        if (action instanceof ChatAction.ToggleAudioFloating floating) {
            return Style.EMPTY.withClickEvent(AudioFloatingWindowClickEvent.forToggle(
                    floating.url(), floating.displayName()));
        }
        if (action instanceof ChatAction.ToggleAudioOptions options) {
            return Style.EMPTY.withClickEvent(AudioOptionsClickEvent.forToggle(
                    options.url(),
                    options.displayName(),
                    options.anchorX(),
                    options.anchorY()));
        }
        if (action instanceof ChatAction.SeekAudio seek) {
            return Style.EMPTY.withClickEvent(AudioControlClickEvent.forSeek(seek.url(), seek.ratio()));
        }
        if (action instanceof ChatAction.ToggleVideo toggle) {
            return Style.EMPTY.withClickEvent(VideoControlClickEvent.forToggle(toggle.url()));
        }
        if (action instanceof ChatAction.SeekVideo seek) {
            return Style.EMPTY.withClickEvent(VideoControlClickEvent.forSeek(seek.url(), seek.ratio()));
        }
        if (action instanceof ChatAction.PreviewVideo preview) {
            return Style.EMPTY.withClickEvent(VideoPreviewClickEvent.forUrlAndName(
                    preview.url(), preview.displayName()));
        }
        if (action instanceof ChatAction.OpenUrl openUrl) {
            try {
                return Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(openUrl.url())));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}