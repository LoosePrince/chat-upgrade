package com.chat.upgrade.client.ui.chat.notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ChatClientConfigRuntime;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.MinecraftGuiBridge;
import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageStatus;
import com.chat.upgrade.client.ui.chat.state.RichChatStateStore;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.render.UiPrimitives;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

/** Detects local-player mentions and owns their short-lived presentation state. */
public final class MentionNotificationService {
    private static final long TITLE_DURATION_MS = 3_000L;
    private static final int PREVIEW_DURATION_TICKS = 100;
    private static final int MAX_PREVIEW_MESSAGES = 4;
    private static final int PREVIEW_MAX_WIDTH = 320;
    private static final int PREVIEW_ROW_HEIGHT = 13;
    private static volatile @Nullable TitleNotification activeTitle;

    private MentionNotificationService() {
    }

    public static void onMessage(RichChatMessage message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isMentionForLocalPlayer(minecraft, message)) {
            return;
        }
        String senderName = senderName(message);
        ChatUpgradeConfig.MentionNotificationMode mode = ChatClientConfigRuntime.uiPreferences()
                .mentionNotificationMode();
        switch (mode) {
            case NONE -> {
            }
            case SOUND -> playSound(minecraft);
            case TITLE -> activeTitle = new TitleNotification(senderName, Util.getMillis() + TITLE_DURATION_MS);
        }
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || MinecraftGuiBridge.currentScreen(minecraft) != null) {
            return;
        }
        renderTitle(graphics, font, width, height);
    }

    public static void renderChatScreen(GuiGraphicsExtractor graphics, Font font, int width, int height) {
        renderTitle(graphics, font, width, height);
    }

    public static void renderPassthrough(GuiGraphicsExtractor graphics, Font font, int width, int height) {
        if (!ChatClientConfigRuntime.uiPreferences().messagePassthroughEnabled()) {
            return;
        }
        renderMessagePreview(graphics, font, width, height);
        renderTitle(graphics, font, width, height);
    }

    public static void clear() {
        activeTitle = null;
    }

    private static boolean isMentionForLocalPlayer(Minecraft minecraft, RichChatMessage message) {
        if (minecraft == null || minecraft.player == null || message == null
                || message.status() != RichChatMessageStatus.VISIBLE
                || !message.kind().playerAuthored()
                || message.authoredByLocalPlayer()) {
            return false;
        }
        String localName = minecraft.player.getScoreboardName();
        String text = message.plainText().isBlank() ? message.component().getString() : message.plainText();
        return containsMention(text, localName);
    }

    private static boolean containsMention(String text, String playerName) {
        if (text == null || playerName == null || playerName.isBlank()) {
            return false;
        }
        String normalizedText = text.toLowerCase(Locale.ROOT);
        String token = "@" + playerName.toLowerCase(Locale.ROOT);
        int fromIndex = 0;
        while (fromIndex < normalizedText.length()) {
            int match = normalizedText.indexOf(token, fromIndex);
            if (match < 0) {
                return false;
            }
            int end = match + token.length();
            if (end == normalizedText.length() || !isPlayerIdCharacter(normalizedText.charAt(end))) {
                return true;
            }
            fromIndex = match + 1;
        }
        return false;
    }

    private static boolean isPlayerIdCharacter(char character) {
        return character == '_' || Character.isLetterOrDigit(character);
    }

    private static void playSound(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7F, 1.0F);
            }
        });
    }

    private static void renderTitle(GuiGraphicsExtractor graphics, Font font, int width, int height) {
        TitleNotification title = activeTitle;
        if (graphics == null || font == null || title == null) {
            return;
        }
        long remaining = title.expiresAtMs() - Util.getMillis();
        if (remaining <= 0L) {
            activeTitle = null;
            return;
        }
        int alpha = fadeAlpha(remaining, TITLE_DURATION_MS);
        String text = I18n.get("chatupgrade.mention.title", title.senderName());
        int textWidth = font.width(text);
        int centerX = width / 2;
        int top = Math.clamp(height / 2 + 14, 3, Math.max(3, height - font.lineHeight - 8));
        RichChatBounds bounds = RichChatBounds.ofSize(
                Math.max(3, centerX - textWidth / 2 - 5),
                top,
                textWidth + 10,
                font.lineHeight + 6);
        UiPrimitives.fillRounded(graphics, bounds, 4, color(alpha * 2 / 3, 0x101820));
        graphics.centeredText(font, text, centerX, top + 3, color(alpha, 0xF4F7FB));
    }

    private static void renderMessagePreview(
            GuiGraphicsExtractor graphics,
            Font font,
            int width,
            int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (graphics == null || font == null || minecraft == null) {
            return;
        }
        int ticks = MinecraftGuiBridge.guiTicks(minecraft);
        List<RichChatMessage> visible = RichChatStateStore.snapshotNewestFirst().stream()
                .filter(message -> message.status() == RichChatMessageStatus.VISIBLE)
                .filter(message -> ticks - message.addedTime() >= 0 && ticks - message.addedTime() <= PREVIEW_DURATION_TICKS)
                .limit(MAX_PREVIEW_MESSAGES)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.reverse(visible);
        int maxWidth = Math.max(80, Math.min(PREVIEW_MAX_WIDTH, width - 12));
        int bottom = Math.max(20, height - 34);
        for (int index = visible.size() - 1; index >= 0; index--) {
            RichChatMessage message = visible.get(index);
            int age = Math.max(0, ticks - message.addedTime());
            int alpha = Math.clamp(255 - Math.max(0, age - 70) * 255 / 30, 0, 255);
            String text = previewText(message);
            String visibleText = font.plainSubstrByWidth(text, maxWidth - 8);
            int rowWidth = Math.min(maxWidth, font.width(visibleText) + 8);
            int top = bottom - PREVIEW_ROW_HEIGHT;
            RichChatBounds row = RichChatBounds.ofSize(4, top, rowWidth, PREVIEW_ROW_HEIGHT);
            UiPrimitives.fillRounded(graphics, row, 3, color(alpha * 2 / 3, 0x101318));
            graphics.text(font, visibleText, row.left() + 4, row.top() + 2, color(alpha, 0xF2F5FA), false);
            bottom = top - 2;
        }
    }

    private static String previewText(RichChatMessage message) {
        String body = message.plainText().isBlank() ? message.component().getString() : message.plainText();
        String author = senderName(message);
        return message.kind().playerAuthored() && !author.isBlank() ? author + ": " + body : body;
    }

    private static String senderName(RichChatMessage message) {
        String name = message == null ? "" : message.author().searchableName();
        return name.isBlank() || "?".equals(name) ? I18n.get("chatupgrade.mention.unknown_player") : name;
    }

    private static int fadeAlpha(long remainingMs, long durationMs) {
        long elapsed = durationMs - remainingMs;
        if (elapsed < 150L) {
            return Math.clamp((int) (elapsed * 255L / 150L), 0, 255);
        }
        if (remainingMs < 400L) {
            return Math.clamp((int) (remainingMs * 255L / 400L), 0, 255);
        }
        return 255;
    }

    private static int color(int alpha, int rgb) {
        return Math.clamp(alpha, 0, 255) << 24 | rgb & 0x00FFFFFF;
    }

    private record TitleNotification(String senderName, long expiresAtMs) {
    }
}