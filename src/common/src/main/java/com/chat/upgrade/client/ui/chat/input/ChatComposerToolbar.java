package com.chat.upgrade.client.ui.chat.input;

import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.render.UiPrimitives;
import com.chat.upgrade.client.ui.render.UiTextureAtlas;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

/** Pure compact-toolbar layout shared by rendering and hit testing. */
public final class ChatComposerToolbar {
    public enum Action {
        ATTACHMENT,
        EMOJI,
        CLEAR,
        SEND
    }

    public record State(
            boolean attachmentEnabled,
            boolean emojiOpen,
            boolean clearVisible,
            boolean clearEnabled,
            boolean sendVisible,
            boolean sendEnabled) {
        public static State idle() {
            return new State(true, false, false, false, false, false);
        }
    }

    public record Layout(
            RichChatBounds attachment,
            RichChatBounds emoji,
            RichChatBounds clear,
            RichChatBounds send,
            RichChatBounds attachmentTray,
            boolean clearVisible,
            boolean sendVisible) {
        public RichChatBounds bounds(Action action) {
            return switch (action) {
                case ATTACHMENT -> attachment;
                case EMOJI -> emoji;
                case CLEAR -> clear;
                case SEND -> send;
            };
        }

        public boolean visible(Action action) {
            return switch (action) {
                case ATTACHMENT, EMOJI -> true;
                case CLEAR -> clearVisible;
                case SEND -> sendVisible;
            };
        }
    }

    private static final int BUTTON_SIZE = 18;
    private static final int GAP = 4;

    private ChatComposerToolbar() {
    }

    public static Layout layout(int left, int right, int top, State state) {
        int safeRight = Math.max(left + BUTTON_SIZE * 2 + GAP, right);
        RichChatBounds attachment = RichChatBounds.ofSize(left, top, BUTTON_SIZE, BUTTON_SIZE);
        RichChatBounds emoji = RichChatBounds.ofSize(attachment.right() + GAP, top, BUTTON_SIZE, BUTTON_SIZE);

        int cursor = safeRight;
        RichChatBounds send = RichChatBounds.ofSize(cursor, top, 0, 0);
        if (state.sendVisible()) {
            send = RichChatBounds.ofSize(cursor - BUTTON_SIZE, top, BUTTON_SIZE, BUTTON_SIZE);
            cursor = send.left() - GAP;
        }
        RichChatBounds clear = RichChatBounds.ofSize(cursor, top, 0, 0);
        if (state.clearVisible()) {
            clear = RichChatBounds.ofSize(cursor - BUTTON_SIZE, top, BUTTON_SIZE, BUTTON_SIZE);
            cursor = clear.left() - GAP;
        }
        RichChatBounds tray = new RichChatBounds(
                Math.min(cursor, emoji.right() + GAP),
                top,
                Math.max(emoji.right() + GAP, cursor),
                top + BUTTON_SIZE);
        return new Layout(attachment, emoji, clear, send, tray, state.clearVisible(), state.sendVisible());
    }

    public static Action actionAt(Layout layout, State state, double mouseX, double mouseY) {
        int x = (int) Math.round(mouseX);
        int y = (int) Math.round(mouseY);
        for (Action action : Action.values()) {
            if (layout.visible(action) && layout.bounds(action).contains(x, y)) {
                return action;
            }
        }
        return null;
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatAppearanceSnapshot appearance,
            Layout layout,
            State state,
            int mouseX,
            int mouseY) {
        if (graphics == null || font == null || appearance == null || layout == null || state == null) {
            return;
        }
        paintButton(
                graphics,
                font,
                appearance,
                layout.attachment(),
                UiTextureAtlas.Icon.ATTACHMENT,
                "chatupgrade.input.button.attachment.tooltip",
                state.attachmentEnabled(),
                false,
                mouseX,
                mouseY);
        paintButton(
                graphics,
                font,
                appearance,
                layout.emoji(),
                UiTextureAtlas.Icon.EMOJI,
                "chatupgrade.input.button.emoji.tooltip",
                true,
                state.emojiOpen(),
                mouseX,
                mouseY);
        if (state.clearVisible()) {
            paintButton(
                    graphics,
                    font,
                    appearance,
                    layout.clear(),
                    UiTextureAtlas.Icon.CLOSE,
                    "chatupgrade.input.button.clear.tooltip",
                    state.clearEnabled(),
                    false,
                    mouseX,
                    mouseY);
        }
        if (state.sendVisible()) {
            paintButton(
                    graphics,
                    font,
                    appearance,
                    layout.send(),
                    UiTextureAtlas.Icon.SEND,
                    "chatupgrade.input.button.send.tooltip",
                    state.sendEnabled(),
                    false,
                    mouseX,
                    mouseY);
        }
    }

    private static void paintButton(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatAppearanceSnapshot appearance,
            RichChatBounds bounds,
            UiTextureAtlas.Icon icon,
            String tooltipKey,
            boolean enabled,
            boolean active,
            int mouseX,
            int mouseY) {
        boolean hover = bounds.contains(mouseX, mouseY);
        int background = active
                ? 0xFF4C6284
                : hover && enabled
                        ? 0xD03B4A60
                        : 0xB0202732;
        int border = enabled ? appearance.surface().panelBorder() : 0x80616A78;
        UiPrimitives.paintBox(graphics, bounds, 4, 1, background, border);
        UiTextureAtlas.drawIcon(
                graphics,
                icon,
                RichChatBounds.ofSize(bounds.left() + 3, bounds.top() + 3, 12, 12),
                enabled ? appearance.surface().title() : 0xFF737B88);
        if (hover) {
            paintTooltip(graphics, font, bounds, I18n.get(tooltipKey));
        }
    }

    private static void paintTooltip(
            GuiGraphicsExtractor graphics,
            Font font,
            RichChatBounds anchor,
            String text) {
        int width = font.width(text) + 8;
        int left = Math.max(2, anchor.left() + anchor.width() / 2 - width / 2);
        int top = Math.max(2, anchor.top() - 17);
        RichChatBounds bounds = RichChatBounds.ofSize(left, top, width, 14);
        UiPrimitives.paintBox(graphics, bounds, 3, 1, 0xF0181D26, 0xFF526176);
        graphics.text(font, text, bounds.left() + 4, bounds.top() + 4, 0xFFF2F5FA, false);
    }
}