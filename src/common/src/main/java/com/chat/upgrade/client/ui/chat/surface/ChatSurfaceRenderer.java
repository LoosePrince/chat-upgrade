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
        boolean screenMarginsEnabled = frame.appearance().screenMarginsEnabled();
        int radius = screenMarginsEnabled ? frame.appearance().cornerRadius() : 0;
        UiPrimitives.fillRounded(graphics, panel, radius, tokens.panelBackground());
        UiPrimitives.withRoundedClip(
                graphics,
                panel,
                radius,
                () -> paintPanelContents(graphics, font, frame, tokens, panel, screenMarginsEnabled));
        if (screenMarginsEnabled) {
            UiPrimitives.strokeRounded(
                    graphics,
                    panel,
                    radius,
                    tokens.panelBorderWidth(),
                    tokens.panelBorder());
        } else {
            paintRightBorder(graphics, panel, tokens.panelBorderWidth(), tokens.panelBorder());
        }
    }

    private static void paintPanelContents(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatSurfaceFrame frame,
            ChatAppearanceSnapshot.Surface tokens,
            RichChatBounds panel,
            boolean screenMarginsEnabled) {
        RichChatBounds content = frame.contentBounds();
        RichChatBounds timeline = frame.messageViewportBounds();
        RichChatBounds composer = frame.composerBounds();
        if (tokens.headerBackground() != tokens.panelBackground()) {
            graphics.fill(
                    content.left(),
                    content.top(),
                    content.right(),
                    content.top() + ChatPanelGeometry.HEADER_HEIGHT,
                    tokens.headerBackground());
        }
        if (tokens.timelineBackground() != tokens.panelBackground()) {
            graphics.fill(timeline.left(), timeline.top(), timeline.right(), timeline.bottom(), tokens.timelineBackground());
        }
        if (!frame.appearance().vanillaStyleInput()
                && tokens.composerBackground() != tokens.panelBackground()) {
            graphics.fill(composer.left(), composer.top(), composer.right(), composer.bottom(), tokens.composerBackground());
        }
        graphics.fill(content.left(), timeline.top(), content.right(), timeline.top() + 1, tokens.separator());
        if (!frame.appearance().vanillaStyleInput()) {
            graphics.fill(content.left(), composer.top(), content.right(), composer.top() + 1, tokens.separator());
        }
        ChatMessageGroupSidebar.paint(graphics, font, frame);
        RichChatBounds settingsButton = settingsButtonBounds(frame);
        paintSettingsButton(graphics, frame);
        paintMessageGroupToggleButton(graphics, frame);
        graphics.text(
                font,
                I18n.get("chatupgrade.surface.title"),
                settingsButton.right() + 4,
                content.top() + 5,
                tokens.title(),
                false);
        paintResizeGrip(graphics, panel, tokens.resizeGrip(), screenMarginsEnabled);
    }

    public static void paintSettingsButton(
            GuiGraphicsExtractor graphics,
            ChatSurfaceFrame frame) {
        if (graphics == null || frame == null || !frame.isOpenPanel()) {
            return;
        }
        ChatAppearanceSnapshot appearance = frame.appearance();
        RichChatBounds bounds = settingsButtonBounds(frame);
        int radius = Math.clamp(appearance.cornerRadius(), 0, Math.min(bounds.width(), bounds.height()) / 2);
        UiPrimitives.paintBox(
                graphics,
                bounds,
                radius,
                appearance.contextMenu().borderWidth(),
                appearance.media().controlBackground(),
                appearance.contextMenu().border());
        UiTextureAtlas.drawIcon(
                graphics,
                UiTextureAtlas.Icon.GEAR,
                RichChatBounds.ofSize(bounds.left() + 1, bounds.top() + 1, 12, 12),
                appearance.surface().title());
    }

    public static RichChatBounds settingsButtonBounds(ChatSurfaceFrame frame) {
        if (frame == null) {
            return RichChatBounds.ofSize(0, 0, 0, 0);
        }
        RichChatBounds header = frame.headerBounds();
        return RichChatBounds.ofSize(header.left() + 3, header.top() + 2, 14, 14);
    }

    private static void paintMessageGroupToggleButton(
            GuiGraphicsExtractor graphics,
            ChatSurfaceFrame frame) {
        if (!frame.messageGroupingEnabled()) {
            return;
        }
        ChatAppearanceSnapshot appearance = frame.appearance();
        RichChatBounds bounds = frame.messageGroupToggleButtonBounds();
        int radius = Math.clamp(appearance.cornerRadius(), 0, Math.min(bounds.width(), bounds.height()) / 2);
        UiPrimitives.paintBox(
                graphics,
                bounds,
                radius,
                appearance.contextMenu().borderWidth(),
                frame.messageGroupSidebarExpanded()
                        ? appearance.media().controlActiveBackground()
                        : appearance.media().controlBackground(),
                appearance.contextMenu().border());
        int lineLeft = bounds.left() + 3;
        int lineRight = bounds.right() - 3;
        int color = appearance.surface().title();
        graphics.fill(lineLeft, bounds.top() + 4, lineRight, bounds.top() + 5, color);
        graphics.fill(lineLeft, bounds.top() + 7, lineRight, bounds.top() + 8, color);
        graphics.fill(lineLeft, bounds.top() + 10, lineRight, bounds.top() + 11, color);
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

    private static void paintRightBorder(
            GuiGraphicsExtractor graphics,
            RichChatBounds panel,
            int width,
            int color) {
        int safeWidth = Math.clamp(width, 0, panel.width());
        if (safeWidth > 0 && UiPrimitives.visible(color)) {
            graphics.fill(panel.right() - safeWidth, panel.top(), panel.right(), panel.bottom(), color);
        }
    }

    private static void paintResizeGrip(
            GuiGraphicsExtractor graphics,
            RichChatBounds panel,
            int color,
            boolean screenMarginsEnabled) {
        int right = panel.right() - 3;
        if (!screenMarginsEnabled) {
            int centerY = panel.top() + panel.height() / 2;
            graphics.fill(right - 1, centerY - 5, right, centerY + 6, color);
            return;
        }
        int bottom = panel.bottom() - 3;
        graphics.fill(right - 7, bottom, right, bottom + 1, color);
        graphics.fill(right - 4, bottom - 3, right, bottom - 2, color);
    }
}