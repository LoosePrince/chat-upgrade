package com.chat.upgrade.client.ui.chat.interaction;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.animation.UiMotion;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceRuntime;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.render.UiPrimitives;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ChatContextMenu {
    private static final int SCREEN_MARGIN = 3;

    public record Selection(RichChatMessage message, ChatAction action, @Nullable RichAttachment attachment) {
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
    private @Nullable RichAttachment attachment;
    private List<ChatMessageActionCatalog.Item> items = List.of();
    private RichChatBounds bounds;
    private Density density = Density.at(100);

    public boolean isOpen() {
        return message != null && bounds != null && !items.isEmpty();
    }

    public boolean open(
            ChatGestureTarget target,
            int pointerX,
            int pointerY,
            int screenWidth,
            int screenHeight,
            Font font) {
        if (target == null) {
            close();
            return false;
        }
        RichAttachment targetAttachment = target.hitBox() == null ? null : target.hitBox().attachment();
        return open(target.message(), targetAttachment, pointerX, pointerY, screenWidth, screenHeight, font);
    }

    public boolean open(
            RichChatMessage nextMessage,
            int pointerX,
            int pointerY,
            int screenWidth,
            int screenHeight,
            Font font) {
        return open(nextMessage, null, pointerX, pointerY, screenWidth, screenHeight, font);
    }

    private boolean open(
            RichChatMessage nextMessage,
            @Nullable RichAttachment nextAttachment,
            int pointerX,
            int pointerY,
            int screenWidth,
            int screenHeight,
            Font font) {
        List<ChatMessageActionCatalog.Item> nextItems = ChatMessageActionCatalog.actionsFor(
                nextMessage,
                nextAttachment);
        if (nextItems.isEmpty() || font == null) {
            close();
            return false;
        }
        density = Density.at(ChatAppearanceRuntime.current().contextMenu().scalePercent());
        int labelWidth = nextItems.stream()
                .mapToInt(item -> font.width(item.label()))
                .max()
                .orElse(1);
        int availableWidth = Math.max(1, screenWidth - SCREEN_MARGIN * 2);
        int width = Math.min(
                availableWidth,
                Math.max(1, labelWidth + density.horizontalPadding() * 2));
        int height = density.verticalPadding() * 2 + nextItems.size() * density.rowHeight();
        int maxX = Math.max(SCREEN_MARGIN, screenWidth - SCREEN_MARGIN - width);
        int maxY = Math.max(SCREEN_MARGIN, screenHeight - SCREEN_MARGIN - height);
        int x = Math.clamp(pointerX, SCREEN_MARGIN, maxX);
        int y = Math.clamp(pointerY, SCREEN_MARGIN, maxY);
        message = nextMessage;
        attachment = nextAttachment;
        items = nextItems;
        bounds = RichChatBounds.ofSize(x, y, width, height);
        UiMotion.begin(UiMotion.CONTEXT_MENU);
        return true;
    }

    public void close() {
        message = null;
        UiMotion.end(UiMotion.CONTEXT_MENU);
        attachment = null;
        items = List.of();
        bounds = null;
        density = Density.at(100);
    }

    public ClickResult mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen() || button != 0) {
            return ClickResult.ignored();
        }
        if (UiMotion.isEntering(UiMotion.CONTEXT_MENU)) {
            return ClickResult.consumed();
        }
        int x = (int) Math.round(mouseX);
        int y = (int) Math.round(mouseY);
        if (!bounds.contains(x, y)) {
            close();
            return ClickResult.consumed();
        }
        int index = (y - bounds.top() - density.verticalPadding()) / density.rowHeight();
        if (index < 0 || index >= items.size()) {
            close();
            return ClickResult.consumed();
        }
        Selection selection = new Selection(message, items.get(index).action(), attachment);
        close();
        return new ClickResult(true, selection);
    }

    public void render(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatAppearanceSnapshot appearance,
            int mouseX,
            int mouseY) {
        if (!isOpen() || graphics == null || font == null || appearance == null) {
            return;
        }
        ChatAppearanceSnapshot.ContextMenu style = appearance.contextMenu();
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(0, UiMotion.enterFromBottom(UiMotion.CONTEXT_MENU, 10));
        UiPrimitives.paintBox(
                graphics,
                bounds,
                style.cornerRadius(),
                style.borderWidth(),
                style.background(),
                style.border());
        for (int index = 0; index < items.size(); index++) {
            ChatMessageActionCatalog.Item item = items.get(index);
            int rowTop = bounds.top() + density.verticalPadding() + index * density.rowHeight();
            RichChatBounds row = RichChatBounds.ofSize(
                    bounds.left() + style.borderWidth(),
                    rowTop,
                    Math.max(0, bounds.width() - style.borderWidth() * 2),
                    density.rowHeight());
            if (row.contains(mouseX, mouseY)) {
                graphics.fill(
                        row.left(),
                        row.top(),
                        row.right(),
                        row.bottom(),
                        appearance.media().controlActiveBackground());
            }
            int color = item.destructive()
                    ? appearance.message().errorBorder()
                    : appearance.surface().title();
            String label = font.plainSubstrByWidth(
                    item.label().getString(),
                    bounds.width() - density.horizontalPadding() * 2);
            graphics.text(
                    font,
                    label,
                    bounds.left() + density.horizontalPadding(),
                    rowTop + Math.max(1, (density.rowHeight() - font.lineHeight) / 2),
                    color,
                    false);
        }
        pose.popMatrix();
    }

    private record Density(
            int horizontalPadding,
            int verticalPadding,
            int rowHeight) {
        private static Density at(int scalePercent) {
            int safeScale = Math.clamp(scalePercent, 75, 150);
            return new Density(
                    scale(8, safeScale),
                    scale(2, safeScale),
                    scale(18, safeScale));
        }

        private static int scale(int value, int percent) {
            return Math.max(1, Math.round(value * percent / 100.0F));
        }
    }
}