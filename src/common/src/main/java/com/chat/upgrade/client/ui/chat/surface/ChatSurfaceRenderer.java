package com.chat.upgrade.client.ui.chat.surface;

import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

public final class ChatSurfaceRenderer {
    private static final int PANEL_BACKGROUND = 0xE612141A;
    private static final int PANEL_BORDER = 0xFF526176;
    private static final int HEADER_BACKGROUND = 0xF01A1E27;
    private static final int TIMELINE_BACKGROUND = 0xC80B0D12;
    private static final int COMPOSER_BACKGROUND = 0xED171B23;
    private static final int SEPARATOR = 0xFF343D4D;
    private static final int TITLE_COLOR = 0xFFF0F3F8;
    private static final int MUTED_COLOR = 0xFF9AA6B7;
    private static final int RESTRICTED_COLOR = 0xFFFFC76B;

    private ChatSurfaceRenderer() {
    }

    public static void paintPanel(GuiGraphicsExtractor graphics, Font font, ChatSurfaceFrame frame) {
        if (graphics == null || font == null || frame == null || !frame.isOpenPanel()) {
            return;
        }
        RichChatBounds panel = frame.panelBounds();
        RichChatBounds timeline = frame.messageViewportBounds();
        RichChatBounds composer = frame.composerBounds();
        graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), PANEL_BACKGROUND);
        graphics.fill(panel.left(), panel.top(), panel.right(), panel.top() + ChatPanelGeometry.HEADER_HEIGHT,
                HEADER_BACKGROUND);
        graphics.fill(timeline.left(), timeline.top(), timeline.right(), timeline.bottom(), TIMELINE_BACKGROUND);
        graphics.fill(composer.left(), composer.top(), composer.right(), composer.bottom(), COMPOSER_BACKGROUND);
        graphics.fill(panel.left(), timeline.top(), panel.right(), timeline.top() + 1, SEPARATOR);
        graphics.fill(panel.left(), composer.top(), panel.right(), composer.top() + 1, SEPARATOR);
        graphics.outline(panel.left(), panel.top(), panel.width(), panel.height(), PANEL_BORDER);
        graphics.text(font, I18n.get("chatupgrade.surface.title"), panel.left() + 7, panel.top() + 5,
                TITLE_COLOR, false);
        paintResizeGrip(graphics, panel);
    }

    public static void paintTimelineState(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatSurfaceFrame frame,
            boolean empty) {
        if (graphics == null || font == null || frame == null) {
            return;
        }
        if (!frame.isOpenPanel()) {
            if (frame.restricted()) {
                paintClosedRestrictedHud(graphics, font, frame.panelGeometry());
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
        int color = frame.restricted() ? RESTRICTED_COLOR : MUTED_COLOR;
        int textX = viewport.left() + Math.max(6, (viewport.width() - font.width(text)) / 2);
        int textY = viewport.top() + Math.max(6, (viewport.height() - font.lineHeight) / 2);
        graphics.text(font, text, textX, textY, color, false);
    }

    private static void paintClosedRestrictedHud(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatPanelGeometry panelGeometry) {
        String text = I18n.get("chatupgrade.surface.restricted");
        int width = font.width(text) + 12;
        int x = panelGeometry.x();
        int y = Math.max(ChatPanelGeometry.SCREEN_MARGIN, panelGeometry.bottom() - 20);
        graphics.fill(x, y, x + width, y + 16, 0xD8181510);
        graphics.outline(x, y, width, 16, 0xFF8F6A2C);
        graphics.text(font, text, x + 6, y + 4, RESTRICTED_COLOR, false);
    }

    private static void paintResizeGrip(GuiGraphicsExtractor graphics, RichChatBounds panel) {
        int right = panel.right() - 3;
        int bottom = panel.bottom() - 3;
        graphics.fill(right - 7, bottom, right, bottom + 1, 0xFF718097);
        graphics.fill(right - 4, bottom - 3, right, bottom - 2, 0xFF718097);
    }
}