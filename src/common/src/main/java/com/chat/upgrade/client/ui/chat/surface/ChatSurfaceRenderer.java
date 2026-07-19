package com.chat.upgrade.client.ui.chat.surface;

import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.render.UiPrimitives;
import com.chat.upgrade.client.ui.render.UiTextureAtlas;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

public final class ChatSurfaceRenderer {
    private ChatSurfaceRenderer() {
    }

    public static void paintPanel(GuiGraphicsExtractor graphics, Font font, ChatSurfaceFrame frame) {
        if (graphics == null || font == null || frame == null || !frame.isOpenPanel()) {
            return;
        }
        ChatAppearanceSnapshot.Surface tokens = frame.appearance().surface();
        RichChatBounds panel = frame.panelBounds();
        RichChatBounds timeline = frame.messageViewportBounds();
        RichChatBounds composer = frame.composerBounds();
        graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), tokens.panelBackground());
        graphics.fill(
                panel.left(),
                panel.top(),
                panel.right(),
                panel.top() + ChatPanelGeometry.HEADER_HEIGHT,
                tokens.headerBackground());
        graphics.fill(timeline.left(), timeline.top(), timeline.right(), timeline.bottom(), tokens.timelineBackground());
        if (!frame.appearance().vanillaStyleInput()) {
            graphics.fill(composer.left(), composer.top(), composer.right(), composer.bottom(), tokens.composerBackground());
        }
        graphics.fill(panel.left(), timeline.top(), panel.right(), timeline.top() + 1, tokens.separator());
        if (!frame.appearance().vanillaStyleInput()) {
            graphics.fill(panel.left(), composer.top(), panel.right(), composer.top() + 1, tokens.separator());
        }
        paintPanelBorder(graphics, panel, tokens.panelBorderWidth(), tokens.panelBorder());
        RichChatBounds settingsButton = settingsButtonBounds(frame);
        UiPrimitives.fillRounded(graphics, settingsButton, 3, 0x70343D4D);
        UiTextureAtlas.drawIcon(
                graphics,
                UiTextureAtlas.Icon.GEAR,
                RichChatBounds.ofSize(settingsButton.left() + 1, settingsButton.top() + 1, 12, 12),
                tokens.title());
        graphics.text(
                font,
                I18n.get("chatupgrade.surface.title"),
                settingsButton.right() + 4,
                panel.top() + 5,
                tokens.title(),
                false);
        paintResizeGrip(graphics, panel, tokens.resizeGrip());
    }

    public static RichChatBounds settingsButtonBounds(ChatSurfaceFrame frame) {
        if (frame == null) {
            return RichChatBounds.ofSize(0, 0, 0, 0);
        }
        RichChatBounds panel = frame.panelBounds();
        return RichChatBounds.ofSize(panel.left() + 3, panel.top() + 2, 14, 14);
    }

    public static void paintTimelineState(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatSurfaceFrame frame,
            boolean empty) {
        if (graphics == null || font == null || frame == null) {
            return;
        }
        ChatAppearanceSnapshot.Surface tokens = frame.appearance().surface();
        if (!frame.isOpenPanel()) {
            if (frame.restricted()) {
                paintClosedRestrictedHud(graphics, font, frame.panelGeometry(), tokens);
            }
            return;
        }
        if (!frame.restricted() && !empty) {
            return;
        }
        RichChatBounds viewport = frame.messageViewportBounds();
        String text = I18n.get(frame.restricted()
                ? "chatupgrade.surface.restricted"
                : "chatupgrade.surface.empty");
        int color = frame.restricted() ? tokens.restricted() : tokens.muted();
        int textX = viewport.left() + Math.max(6, (viewport.width() - font.width(text)) / 2);
        int textY = viewport.top() + Math.max(6, (viewport.height() - font.lineHeight) / 2);
        graphics.text(font, text, textX, textY, color, false);
    }

    private static void paintClosedRestrictedHud(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatPanelGeometry panelGeometry,
            ChatAppearanceSnapshot.Surface tokens) {
        String text = I18n.get("chatupgrade.surface.restricted");
        int width = font.width(text) + 12;
        int x = panelGeometry.x();
        int y = Math.max(ChatPanelGeometry.SCREEN_MARGIN, panelGeometry.bottom() - 20);
        graphics.fill(x, y, x + width, y + 16, tokens.restrictedHudBackground());
        graphics.outline(x, y, width, 16, tokens.restrictedHudBorder());
        graphics.text(font, text, x + 6, y + 4, tokens.restricted(), false);
    }

    private static void paintPanelBorder(
            GuiGraphicsExtractor graphics,
            RichChatBounds panel,
            int width,
            int color) {
        for (int inset = 0; inset < width; inset++) {
            int outlineWidth = panel.width() - inset * 2;
            int outlineHeight = panel.height() - inset * 2;
            if (outlineWidth <= 0 || outlineHeight <= 0) {
                break;
            }
            graphics.outline(
                    panel.left() + inset,
                    panel.top() + inset,
                    outlineWidth,
                    outlineHeight,
                    color);
        }
    }

    private static void paintResizeGrip(GuiGraphicsExtractor graphics, RichChatBounds panel, int color) {
        int right = panel.right() - 3;
        int bottom = panel.bottom() - 3;
        graphics.fill(right - 7, bottom, right, bottom + 1, color);
        graphics.fill(right - 4, bottom - 3, right, bottom - 2, color);
    }
}