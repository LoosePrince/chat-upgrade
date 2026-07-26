package com.chat.upgrade.client.ui.chat;

import com.chat.upgrade.client.media.audio.AudioPlayerService;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceRuntime;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.animation.UiMotion;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.render.UiPrimitives;
import com.chat.upgrade.client.ui.render.UiTextureAtlas;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;

/** Lightweight card-level menu for compact audio actions. */
public final class CompactAudioOptionsMenu {
    private static final int ROW_HEIGHT = 20;
    private static final int PADDING = 3;
    private static final int LABEL_LEFT = 22;
    private static final int RIGHT_PADDING = 6;
    private static final int SCREEN_MARGIN = 3;
    private static final int HEIGHT = ROW_HEIGHT * 2 + PADDING * 2;

    private static String url;
    private static String displayName;
    private static int x;
    private static int y;
    private static int width = 1;
    private static int anchorX;
    private static int anchorY;
    private static boolean visible;

    private CompactAudioOptionsMenu() {
    }

    public static void toggle(
            String targetUrl,
            String targetName,
            int anchorX,
            int anchorY,
            Font font,
            int screenWidth,
            int screenHeight) {
        if (targetUrl == null || targetUrl.isBlank() || font == null) {
            return;
        }
        if (visible && targetUrl.equals(url)) {
            close();
            return;
        }
        url = targetUrl;
        displayName = targetName == null ? "" : targetName;
        CompactAudioOptionsMenu.anchorX = anchorX;
        CompactAudioOptionsMenu.anchorY = anchorY;
        visible = true;
        UiMotion.begin(UiMotion.AUDIO_OPTIONS);
        updateGeometry(font, screenWidth, screenHeight);
    }

    public static boolean isVisible() {
        return visible && url != null && !url.isBlank();
    }

    public static void close() {
        visible = false;
        UiMotion.end(UiMotion.AUDIO_OPTIONS);
        url = null;
        displayName = null;
    }

    public static boolean mouseClicked(MouseButtonEvent event, Font font, int screenWidth, int screenHeight) {
        if (!isVisible() || font == null) {
            return false;
        }
        if (UiMotion.isEntering(UiMotion.AUDIO_OPTIONS)) {
            return true;
        }
        updateGeometry(font, screenWidth, screenHeight);
        if (event.button() != 0) {
            close();
            return true;
        }
        RichChatBounds panel = panelBounds();
        if (!panel.contains(round(event.x()), round(event.y()))) {
            close();
            return true;
        }
        int row = (round(event.y()) - panel.top() - PADDING) / ROW_HEIGHT;
        if (row == 0) {
            AudioPlayerService.toggleLoop(url);
        } else if (row == 1) {
            AudioFloatingWindow.toggleFor(url, displayName);
        }
        close();
        return true;
    }

    public static void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int screenWidth,
            int screenHeight) {
        if (!isVisible() || graphics == null || font == null) {
            return;
        }
        updateGeometry(font, screenWidth, screenHeight);
        ChatAppearanceSnapshot appearance = ChatAppearanceRuntime.current();
        ChatAppearanceSnapshot.ContextMenu style = appearance.contextMenu();
        RichChatBounds panel = panelBounds();
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(0, UiMotion.enterFromBottom(UiMotion.AUDIO_OPTIONS, 10));
        UiPrimitives.paintBox(
                graphics,
                panel,
                style.cornerRadius(),
                style.borderWidth(),
                style.background(),
                style.border());
        paintRow(
                graphics,
                font,
                appearance,
                rowBounds(panel, 0),
                UiTextureAtlas.Icon.LOOP,
                AudioPlayerService.isLoopEnabled(url),
                I18n.get(AudioPlayerService.isLoopEnabled(url)
                        ? "chatupgrade.audio.options.loop.disable"
                        : "chatupgrade.audio.options.loop.enable"),
                mouseX,
                mouseY);
        boolean floating = AudioFloatingWindow.isVisibleFor(url);
        paintRow(
                graphics,
                font,
                appearance,
                rowBounds(panel, 1),
                UiTextureAtlas.Icon.POPOUT,
                floating,
                I18n.get(floating
                        ? "chatupgrade.audio.options.floating.disable"
                        : "chatupgrade.audio.options.floating.enable"),
                mouseX,
                mouseY);
        pose.popMatrix();
    }

    private static void paintRow(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatAppearanceSnapshot appearance,
            RichChatBounds bounds,
            UiTextureAtlas.Icon iconType,
            boolean active,
            String label,
            int mouseX,
            int mouseY) {
        if (bounds.contains(mouseX, mouseY)) {
            UiPrimitives.fillRounded(
                    graphics,
                    bounds,
                    Math.min(3, bounds.height() / 2),
                    appearance.media().controlHoverBackground());
        }
        RichChatBounds icon = RichChatBounds.ofSize(bounds.left() + 5, bounds.top() + 4, 12, 12);
        UiTextureAtlas.drawIcon(
                graphics,
                iconType,
                icon,
                active ? appearance.media().progressFill() : appearance.surface().muted());
        String visibleLabel = font.plainSubstrByWidth(
                label,
                Math.max(1, bounds.width() - LABEL_LEFT - RIGHT_PADDING));
        graphics.text(
                font,
                visibleLabel,
                bounds.left() + LABEL_LEFT,
                bounds.top() + Math.max(1, (ROW_HEIGHT - font.lineHeight) / 2),
                appearance.surface().title(),
                false);
    }

    private static void updateGeometry(Font font, int screenWidth, int screenHeight) {
        String loopLabel = I18n.get(AudioPlayerService.isLoopEnabled(url)
                ? "chatupgrade.audio.options.loop.disable"
                : "chatupgrade.audio.options.loop.enable");
        String floatingLabel = I18n.get(AudioFloatingWindow.isVisibleFor(url)
                ? "chatupgrade.audio.options.floating.disable"
                : "chatupgrade.audio.options.floating.enable");
        int labelWidth = Math.max(font.width(loopLabel), font.width(floatingLabel));
        int availableWidth = Math.max(1, screenWidth - SCREEN_MARGIN * 2);
        width = Math.min(
                availableWidth,
                Math.max(1, PADDING * 2 + LABEL_LEFT + labelWidth + RIGHT_PADDING));
        x = Math.clamp(
                anchorX - width,
                SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN, screenWidth - width - SCREEN_MARGIN));
        y = anchorY + 2;
        if (y + HEIGHT > screenHeight - SCREEN_MARGIN) {
            y = anchorY - HEIGHT - 4;
        }
        y = Math.clamp(
                y,
                SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN, screenHeight - HEIGHT - SCREEN_MARGIN));
    }

    private static RichChatBounds panelBounds() {
        return RichChatBounds.ofSize(x, y, width, HEIGHT);
    }

    private static RichChatBounds rowBounds(RichChatBounds panel, int index) {
        return RichChatBounds.ofSize(
                panel.left() + PADDING,
                panel.top() + PADDING + index * ROW_HEIGHT,
                panel.width() - PADDING * 2,
                ROW_HEIGHT);
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }
}