package com.chat.upgrade.client.media.audio;

import java.util.Optional;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.MinecraftGuiBridge;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.ui.chat.input.AttachmentDraft;
import com.chat.upgrade.client.ui.chat.input.AttachmentSendController;
import com.chat.upgrade.client.ui.chat.input.ChatComposerState;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.render.UiPrimitives;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

/**
 * Owns the keyboard-initiated voice state independently of the active screen.
 * A completed global recording is sent as a standalone attachment, because there
 * is no chat composer to attach to while the player is in the world.
 */
public final class VoiceShortcutService {
    private static final long CONFIRMATION_WINDOW_NANOS = 10_000_000_000L;

    private static final VoiceMessageController RECORDING = new VoiceMessageController();
    private static final ChatComposerState SEND_STATE = new ChatComposerState();

    private static boolean shortcutHeld;
    private static VoiceRecordingSession.Result pending;
    private static long pendingDeadlineNanos;

    private VoiceShortcutService() {
    }

    public static boolean handleKeyPressed(int key, boolean shiftDown) {
        if (!isGameInputAvailable()) {
            return false;
        }
        int shortcut = ChatUpgradeConfig.get().voiceShortcutKey;
        if (!VoiceShortcutKey.isBindable(shortcut) || key != shortcut) {
            return false;
        }
        if (shortcutHeld) {
            return true;
        }
        shortcutHeld = true;
        if (pending != null) {
            if (shiftDown) {
                sendPending();
            } else {
                clearPending();
                systemMessage(Component.translatable("chatupgrade.voice.cancelled").withStyle(ChatFormatting.GRAY));
            }
            return true;
        }
        if (!RECORDING.start(VoiceMessageController.Origin.SHORTCUT)) {
            systemMessage(Component.translatable("chatupgrade.voice.busy").withStyle(ChatFormatting.GRAY));
            return true;
        }
        systemMessage(Component.translatable("chatupgrade.voice.started").withStyle(ChatFormatting.GRAY));
        return true;
    }

    public static boolean handleKeyReleased(int key) {
        int shortcut = ChatUpgradeConfig.get().voiceShortcutKey;
        if (key != shortcut || !shortcutHeld) {
            return false;
        }
        shortcutHeld = false;
        RECORDING.release(VoiceMessageController.Origin.SHORTCUT);
        return true;
    }

    /** Runs on the client tick so completion and upload state stay on the client thread. */
    public static void tick() {
        RECORDING.takeCompletion().ifPresent(VoiceShortcutService::handleCompletion);
        if (pending != null && System.nanoTime() >= pendingDeadlineNanos) {
            clearPending();
            systemMessage(Component.translatable("chatupgrade.voice.timeout").withStyle(ChatFormatting.GRAY));
        }
    }

    public static boolean isRecording() {
        return RECORDING.recording(VoiceMessageController.Origin.SHORTCUT);
    }

    public static long elapsedMillis() {
        return RECORDING.elapsedMillis();
    }

    public static String prompt() {
        if (isRecording()) {
            long seconds = Math.max(0L, (elapsedMillis() + 999L) / 1_000L);
            return Component.translatable("chatupgrade.voice.recording", seconds).getString();
        }
        if (pending != null) {
            return Component.translatable(
                    "chatupgrade.voice.pending",
                    VoiceShortcutKey.label(ChatUpgradeConfig.get().voiceShortcutKey),
                    VoiceShortcutKey.label(ChatUpgradeConfig.get().voiceShortcutKey)).getString();
        }
        return "";
    }

    public static void clear() {
        RECORDING.cancel();
        shortcutHeld = false;
        clearPending();
        SEND_STATE.clearForScreenClose();
    }

    public static void renderPrompt(GuiGraphicsExtractor graphics, Font font, int width, int height) {
        String text = prompt();
        if (graphics == null || font == null || text.isBlank()) {
            return;
        }
        int promptWidth = Math.min(width - 8, font.width(text) + 12);
        RichChatBounds bounds = RichChatBounds.ofSize(4, Math.max(4, height - 24), promptWidth, 18);
        UiPrimitives.paintBox(graphics, bounds, 4, 1, 0xE0181D26, 0xFF526176);
        graphics.text(font, font.plainSubstrByWidth(text, Math.max(1, bounds.width() - 8)),
                bounds.left() + 4, bounds.top() + 5, 0xFFF2F5FA, false);
    }

    private static boolean isGameInputAvailable() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player != null;
    }

    private static void handleCompletion(VoiceMessageController.Completion completion) {
        VoiceRecordingSession.Result result = completion.result();
        switch (result.kind()) {
            case READY -> {
                pending = result;
                pendingDeadlineNanos = System.nanoTime() + CONFIRMATION_WINDOW_NANOS;
                systemMessage(Component.translatable(
                        "chatupgrade.voice.pending",
                        VoiceShortcutKey.label(ChatUpgradeConfig.get().voiceShortcutKey),
                        VoiceShortcutKey.label(ChatUpgradeConfig.get().voiceShortcutKey)).withStyle(ChatFormatting.GRAY));
            }
            case TOO_SHORT -> systemMessage(Component.translatable("chatupgrade.voice.too_short").withStyle(ChatFormatting.RED));
            case SILENT -> systemMessage(Component.translatable(
                    "chatupgrade.voice.silent",
                    result.inputDevice()).withStyle(ChatFormatting.RED));
            case FAILED -> {
                ChatUpgrade.LOGGER.warn("chat-upgrade: shortcut voice recording failed: {}", result.failureReason());
                systemMessage(Component.translatable("chatupgrade.voice.failed").withStyle(ChatFormatting.RED));
            }
            case CANCELLED -> {
            }
        }
    }

    private static void sendPending() {
        VoiceRecordingSession.Result result = pending;
        clearPending();
        if (result == null) {
            return;
        }
        if (result.wavBytes().length > ChatUpgradeConfig.get().maxUploadBytes) {
            systemMessage(Component.translatable(
                    "chatupgrade.upload.too_large",
                    ChatUpgradeConfig.formatBytesHuman(ChatUpgradeConfig.get().maxUploadBytes),
                    ChatUpgradeConfig.formatBytesHuman(result.wavBytes().length)).withStyle(ChatFormatting.RED));
            return;
        }
        String displayName = result.fileName().endsWith(".wav")
                ? result.fileName().substring(0, result.fileName().length() - 4)
                : result.fileName();
        AttachmentDraft draft = AttachmentDraft.fromBytes(
                InlineResourceType.AUDIO,
                result.wavBytes(),
                result.fileName(),
                displayName,
                AttachmentDraft.Source.RECORDING,
                "audio/wav");
        if (!SEND_STATE.addDraft(draft)) {
            systemMessage(Component.translatable("chatupgrade.voice.busy").withStyle(ChatFormatting.RED));
            return;
        }
        AttachmentSendController.SendStartResult start = AttachmentSendController.sendCurrentDraft(
                SEND_STATE,
                "",
                (finish, message) -> {
                    message.ifPresent(VoiceShortcutService::systemMessage);
                    if (finish == AttachmentSendController.SendFinishResult.SENT) {
                        systemMessage(Component.translatable("chatupgrade.upload.audio_sent", displayName)
                                .withStyle(ChatFormatting.GRAY));
                    }
                });
        if (start != AttachmentSendController.SendStartResult.STARTED) {
            SEND_STATE.clearForScreenClose();
            systemMessage(sendStartFailure(start));
            return;
        }
        systemMessage(AttachmentSendController.uploadHint());
    }

    private static Component sendStartFailure(AttachmentSendController.SendStartResult result) {
        return switch (result) {
            case NOT_CONNECTED -> Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED);
            case TOO_LARGE -> Component.translatable("chatupgrade.voice.failed").withStyle(ChatFormatting.RED);
            case UPLOAD_IN_PROGRESS -> Component.translatable("chatupgrade.voice.busy").withStyle(ChatFormatting.GRAY);
            case NO_ATTACHMENT, NOT_SENDABLE -> Component.translatable("chatupgrade.voice.failed").withStyle(ChatFormatting.RED);
            case STARTED -> Component.empty();
        };
    }

    private static void clearPending() {
        pending = null;
        pendingDeadlineNanos = 0L;
    }

    private static void systemMessage(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        ChatComponent chat = MinecraftGuiBridge.chat(minecraft);
        if (chat != null) {
            chat.addClientSystemMessage(message);
        }
    }
}