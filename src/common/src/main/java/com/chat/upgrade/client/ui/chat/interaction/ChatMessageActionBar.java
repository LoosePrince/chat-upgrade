package com.chat.upgrade.client.ui.chat.interaction;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.surface.ChatTheme;
import com.chat.upgrade.client.ui.chat.surface.ChatThemePainter;
import com.chat.upgrade.client.ui.chat.surface.ChatThemeTokens;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Compact message-local action strip. The action catalog remains the only source of enablement;
 * this class only owns the hover presentation and hit geometry.
 */
public final class ChatMessageActionBar {
    private static final int BUTTON_WIDTH = 28;
    private static final int BUTTON_HEIGHT = 17;
    private static final int PADDING = 2;

    private ChatMessageActionBar() {
    }

    public static Optional<ChatContextMenu.Selection> actionAt(
            RichChatMessage message,
            RichChatBounds messageBounds,
            Font font,
            float mouseX,
            float mouseY) {
        Layout layout = layout(message, messageBounds, font);
        if (layout == null || !layout.bounds().contains(Math.round(mouseX), Math.round(mouseY))) {
            return Optional.empty();
        }
        int index = layout.indexAt(Math.round(mouseX), Math.round(mouseY));
        if (index < 0) {
            return Optional.empty();
        }
        return Optional.of(new ChatContextMenu.Selection(message, layout.items().get(index).action()));
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatTheme theme,
            RichChatMessage message,
            RichChatBounds messageBounds,
            float mouseX,
            float mouseY) {
        Layout layout = layout(message, messageBounds, font);
        if (layout == null) {
            return;
        }
        ChatThemeTokens tokens = theme.tokens();
        graphics.fill(
                layout.bounds().left(),
                layout.bounds().top(),
                layout.bounds().right(),
                layout.bounds().bottom(),
                tokens.surface().panelBackground());
        graphics.outline(
                layout.bounds().left(),
                layout.bounds().top(),
                layout.bounds().width(),
                layout.bounds().height(),
                tokens.surface().panelBorder());
        for (int index = 0; index < layout.items().size(); index++) {
            ChatMessageActionCatalog.Item item = layout.items().get(index);
            RichChatBounds button = layout.buttonAt(index);
            if (button.contains(Math.round(mouseX), Math.round(mouseY))) {
                graphics.fill(
                        button.left(),
                        button.top(),
                        button.right(),
                        button.bottom(),
                        tokens.message().playerBackground());
            }
            int color = item.destructive()
                    ? tokens.message().errorBorder()
                    : tokens.surface().title();
            String label = font.plainSubstrByWidth(item.label().getString(), Math.max(1, button.width() - 4));
            int labelX = button.left() + Math.max(2, (button.width() - font.width(label)) / 2);
            int labelY = button.top() + Math.max(1, (button.height() - font.lineHeight) / 2);
            graphics.text(font, label, labelX, labelY, color, false);
        }
    }

    public static @Nullable String tooltipAt(
            RichChatMessage message,
            RichChatBounds messageBounds,
            Font font,
            float mouseX,
            float mouseY) {
        Layout layout = layout(message, messageBounds, font);
        if (layout == null) {
            return null;
        }
        int index = layout.indexAt(Math.round(mouseX), Math.round(mouseY));
        return index < 0 ? null : layout.items().get(index).label().getString();
    }

    private static @Nullable Layout layout(RichChatMessage message, RichChatBounds messageBounds, Font font) {
        if (message == null || messageBounds == null || font == null) {
            return null;
        }
        List<ChatMessageActionCatalog.Item> items = ChatMessageActionCatalog.actionsFor(message);
        if (items.isEmpty()) {
            return null;
        }
        int width = Math.min(messageBounds.width(), PADDING * 2 + items.size() * BUTTON_WIDTH);
        int height = PADDING * 2 + BUTTON_HEIGHT;
        int left = messageBounds.right() - width;
        int top = messageBounds.top();
        return new Layout(items, RichChatBounds.ofSize(left, top, width, height));
    }

    private record Layout(List<ChatMessageActionCatalog.Item> items, RichChatBounds bounds) {
        private RichChatBounds buttonAt(int index) {
            int contentWidth = Math.max(0, bounds.width() - PADDING * 2);
            int itemCount = Math.max(1, items.size());
            int left = bounds.left() + PADDING + contentWidth * index / itemCount;
            int right = bounds.left() + PADDING + contentWidth * (index + 1) / itemCount;
            return RichChatBounds.ofSize(left, bounds.top() + PADDING, Math.max(1, right - left), BUTTON_HEIGHT);
        }

        private int indexAt(int x, int y) {
            if (!bounds.contains(x, y) || y < bounds.top() + PADDING || y >= bounds.bottom() - PADDING) {
                return -1;
            }
            for (int index = 0; index < items.size(); index++) {
                if (buttonAt(index).contains(x, y)) {
                    return index;
                }
            }
            return -1;
        }
    }
}