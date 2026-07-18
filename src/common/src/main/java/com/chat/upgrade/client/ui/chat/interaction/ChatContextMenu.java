package com.chat.upgrade.client.ui.chat.interaction;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.surface.ChatTheme;
import com.chat.upgrade.client.ui.chat.surface.ChatThemeTokens;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ChatContextMenu {
    private static final int SCREEN_MARGIN = 3;
    private static final int HORIZONTAL_PADDING = 8;
    private static final int VERTICAL_PADDING = 2;
    private static final int ROW_HEIGHT = 18;
    private static final int MIN_WIDTH = 92;
    private static final int MAX_WIDTH = 190;

    public record Selection(RichChatMessage message, ChatAction action) {
        public Selection {
            if (message == null || action == null) {
                throw new IllegalArgumentException("message and action must not be null");
            }
        }
    }

    public record ClickResult(boolean handled, @Nullable Selection selection) {
        public static ClickResult ignored() {
            return new ClickResult(false, null);
        }

        public static ClickResult consumed() {
            return new ClickResult(true, null);
        }
    }

    private RichChatMessage message;
    private List<ChatMessageActionCatalog.Item> items = List.of();
    private RichChatBounds bounds;

    public boolean isOpen() {
        return message != null && bounds != null && !items.isEmpty();
    }

    public boolean open(
            RichChatMessage nextMessage,
            int pointerX,
            int pointerY,
            int screenWidth,
            int screenHeight,
            Font font) {
        List<ChatMessageActionCatalog.Item> nextItems = ChatMessageActionCatalog.actionsFor(nextMessage);
        if (nextItems.isEmpty() || font == null) {
            close();
            return false;
        }
        int labelWidth = nextItems.stream()
                .mapToInt(item -> font.width(item.label()))
                .max()
                .orElse(MIN_WIDTH - HORIZONTAL_PADDING * 2);
        int width = Math.clamp(labelWidth + HORIZONTAL_PADDING * 2, MIN_WIDTH, MAX_WIDTH);
        int height = VERTICAL_PADDING * 2 + nextItems.size() * ROW_HEIGHT;
        int maxX = Math.max(SCREEN_MARGIN, screenWidth - SCREEN_MARGIN - width);
        int maxY = Math.max(SCREEN_MARGIN, screenHeight - SCREEN_MARGIN - height);
        int x = Math.clamp(pointerX, SCREEN_MARGIN, maxX);
        int y = Math.clamp(pointerY, SCREEN_MARGIN, maxY);
        message = nextMessage;
        items = nextItems;
        bounds = RichChatBounds.ofSize(x, y, width, height);
        return true;
    }

    public void close() {
        message = null;
        items = List.of();
        bounds = null;
    }

    public ClickResult mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen() || button != 0) {
            return ClickResult.ignored();
        }
        int x = (int) Math.round(mouseX);
        int y = (int) Math.round(mouseY);
        if (!bounds.contains(x, y)) {
            close();
            return ClickResult.consumed();
        }
        int index = (y - bounds.top() - VERTICAL_PADDING) / ROW_HEIGHT;
        if (index < 0 || index >= items.size()) {
            close();
            return ClickResult.consumed();
        }
        Selection selection = new Selection(message, items.get(index).action());
        close();
        return new ClickResult(true, selection);
    }

    public void render(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatTheme theme,
            int mouseX,
            int mouseY) {
        if (!isOpen() || graphics == null || font == null || theme == null) {
            return;
        }
        ChatThemeTokens tokens = theme.tokens();
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(),
                tokens.surface().panelBackground());
        graphics.outline(bounds.left(), bounds.top(), bounds.width(), bounds.height(),
                tokens.surface().panelBorder());
        for (int index = 0; index < items.size(); index++) {
            ChatMessageActionCatalog.Item item = items.get(index);
            int rowTop = bounds.top() + VERTICAL_PADDING + index * ROW_HEIGHT;
            RichChatBounds row = RichChatBounds.ofSize(
                    bounds.left() + 1,
                    rowTop,
                    Math.max(0, bounds.width() - 2),
                    ROW_HEIGHT);
            if (row.contains(mouseX, mouseY)) {
                graphics.fill(row.left(), row.top(), row.right(), row.bottom(),
                        tokens.message().playerBackground());
            }
            int color = item.destructive()
                    ? tokens.message().errorBorder()
                    : tokens.surface().title();
            graphics.text(
                    font,
                    font.plainSubstrByWidth(item.label().getString(), bounds.width() - HORIZONTAL_PADDING * 2),
                    bounds.left() + HORIZONTAL_PADDING,
                    rowTop + 5,
                    color,
                    false);
        }
    }
}