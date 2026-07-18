package com.chat.upgrade.client.ui.chat.scene;

import com.chat.upgrade.client.ui.chat.state.RichChatMessageStatus;
import com.chat.upgrade.client.ui.chat.surface.ChatLayoutPolicy;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceRenderer;
import com.chat.upgrade.client.ui.chat.surface.ChatTheme;
import com.chat.upgrade.client.ui.chat.surface.ChatThemePainter;
import com.chat.upgrade.client.ui.chat.surface.ChatThemeTokens;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.chat.viewport.RichChatLayout;
import com.chat.upgrade.client.ui.chat.viewport.RichChatMediaRenderer;
import com.chat.upgrade.client.ui.chat.viewport.RichChatMessageLayout;
import com.chat.upgrade.client.ui.chat.viewport.RichChatRenderNode;
import com.chat.upgrade.client.ui.chat.viewport.RichChatRenderNodeKind;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewportMetrics;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewportState;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public final class ChatSceneRenderer {
    private ChatSceneRenderer() {
    }

    public static void paintSurface(GuiGraphicsExtractor graphics, Font font, ChatScene scene) {
        if (scene == null) {
            return;
        }
        ChatSurfaceRenderer.paintPanel(graphics, font, scene.surface());
        ChatSurfaceRenderer.paintTimelineState(
                graphics,
                font,
                scene.surface(),
                scene.timeline().totalHeight() <= 0);
    }

    public static void paintTimeline(
            ChatComponent.ChatGraphicsAccess graphics,
            GuiGraphicsExtractor extractor,
            Font font,
            ChatScene scene,
            RichChatViewportState state,
            int contentToLocalY,
            int ticks,
            boolean foreground) {
        if (graphics == null || font == null || scene == null || state == null) {
            return;
        }
        RichChatViewportMetrics metrics = scene.viewport();
        int visibleTop = state.visibleTop();
        int visibleBottom = state.visibleBottom();
        for (RichChatMessageLayout message : scene.timeline().messages()) {
            if (!message.visibleIn(visibleTop, visibleBottom)) {
                continue;
            }
            float alpha = messageAlpha(message, ticks, foreground);
            if (alpha <= 1.0e-5F) {
                continue;
            }
            if (extractor != null) {
                paintMessageDecoration(extractor, message, scene.surface().theme(), contentToLocalY, alpha,
                        metrics.backgroundOpacity());
                paintIdentity(extractor, font, metrics, message, scene.surface().theme(), contentToLocalY, alpha);
            }
            for (RichChatRenderNode node : message.nodes()) {
                if (!node.bounds().intersectsVerticalRange(visibleTop, visibleBottom)) {
                    continue;
                }
                paintNode(
                        graphics,
                        extractor,
                        font,
                        metrics,
                        node,
                        scene.surface().theme(),
                        contentToLocalY,
                        alpha);
            }
        }
    }

    public static void paintScrollbar(
            ChatComponent.ChatGraphicsAccess graphics,
            ChatScene scene,
            RichChatViewportState state,
            boolean newMessageSinceScroll) {
        if (graphics == null || scene == null || state == null) {
            return;
        }
        RichChatLayout layout = scene.timeline();
        RichChatViewportMetrics metrics = scene.viewport();
        if (layout.totalHeight() <= metrics.visibleHeight() || metrics.visibleHeight() <= 0) {
            return;
        }
        int barHeight = Math.max(2, metrics.visibleHeight() * metrics.visibleHeight() / layout.totalHeight());
        int scrollOffset = state.visualScrollPx() * metrics.visibleHeight() / layout.totalHeight();
        int bottom = metrics.chatBottom() - scrollOffset;
        float opacity = state.scrollPx() > 0 ? 1.0F : 0.62F;
        ChatThemeTokens.Scrollbar tokens = scene.surface().theme().tokens().scrollbar();
        int thumb = newMessageSinceScroll ? tokens.newMessageThumb() : tokens.thumb();
        int x = metrics.scrollbarX();
        graphics.fill(
                x,
                bottom - barHeight,
                x + 2,
                bottom,
                ChatThemePainter.withOpacity(thumb, opacity));
        graphics.fill(
                x + 2,
                bottom - barHeight,
                x + 3,
                bottom,
                ChatThemePainter.withOpacity(tokens.track(), opacity));
    }

    private static void paintMessageDecoration(
            GuiGraphicsExtractor graphics,
            RichChatMessageLayout message,
            ChatTheme theme,
            int contentToLocalY,
            float alpha,
            float backgroundOpacity) {
        ChatLayoutPolicy policy = theme.layout();
        ChatThemeTokens.Message tokens = theme.tokens().message();
        int fill;
        int border;
        if (message.message().status() == RichChatMessageStatus.DELETED) {
            fill = tokens.deletedBackground();
            border = tokens.deletedBorder();
        } else if (message.message().replyTo() != null) {
            fill = tokens.replyBackground();
            border = tokens.replyBorder();
        } else {
            fill = tokens.background(message.timeline().kind());
            border = tokens.border(message.timeline().kind());
        }
        float opacity = alpha * backgroundOpacity;
        fill = ChatThemePainter.withOpacity(fill, opacity);
        border = ChatThemePainter.withOpacity(border, opacity);
        RichChatBounds visual = message.visualBounds().translateY(contentToLocalY);
        RichChatBounds full = message.bounds().translateY(contentToLocalY);
        switch (policy.messageDecoration()) {
            case BUBBLE -> ChatThemePainter.paintBox(graphics, visual, 4, 1, fill, border);
            case FEED_STRIPE -> {
                ChatThemePainter.paintBox(graphics, full, 0, 0, fill, 0);
                graphics.fill(
                        full.left(),
                        full.top(),
                        Math.min(full.right(), full.left() + 2),
                        full.bottom(),
                        border);
            }
            case NATIVE_CARD -> ChatThemePainter.paintBox(graphics, full, 0, 0, fill, border);
        }
    }

    private static void paintIdentity(
            GuiGraphicsExtractor graphics,
            Font font,
            RichChatViewportMetrics metrics,
            RichChatMessageLayout message,
            ChatTheme theme,
            int contentToLocalY,
            float alpha) {
        if (message.identityBounds() == null || !theme.layout().showIdentity(message.timeline())) {
            return;
        }
        RichChatBounds avatar = message.identityBounds().translateY(contentToLocalY);
        int avatarColor = ARGB.color(
                Math.clamp(Math.round(alpha * 255.0F), 0, 255),
                message.timeline().avatar().backgroundRgb());
        int foreground = ARGB.color(
                Math.clamp(Math.round(alpha * metrics.textOpacity() * 255.0F), 0, 255),
                message.timeline().avatar().foregroundRgb());
        int radius = switch (theme.layout().messageDecoration()) {
            case BUBBLE -> Math.max(0, avatar.width() / 2);
            case FEED_STRIPE -> 2;
            case NATIVE_CARD -> 0;
        };
        ChatThemePainter.paintBox(
                graphics,
                avatar,
                radius,
                1,
                avatarColor,
                ChatThemePainter.withOpacity(theme.tokens().identity().avatarBorder(), alpha));
        String glyph = message.timeline().avatar().glyph();
        int glyphX = avatar.left() + Math.max(1, (avatar.width() - font.width(glyph)) / 2);
        int glyphY = avatar.top() + Math.max(1, (avatar.height() - font.lineHeight) / 2);
        graphics.text(font, glyph, glyphX, glyphY, foreground, false);

        String authorName = message.timeline().author().visibleName();
        int nameRgb = message.timeline().author().team().colorRgb() >= 0
                ? message.timeline().author().team().colorRgb()
                : theme.tokens().identity().fallbackName() & 0x00FFFFFF;
        int nameColor = ARGB.color(
                Math.clamp(Math.round(alpha * metrics.textOpacity() * 255.0F), 0, 255),
                nameRgb);
        int nameX = avatar.left() + theme.layout().identityGutter() + theme.layout().bubblePaddingX();
        graphics.text(font, authorName, nameX, avatar.top(), nameColor, false);
    }

    private static void paintNode(
            ChatComponent.ChatGraphicsAccess graphics,
            GuiGraphicsExtractor extractor,
            Font font,
            RichChatViewportMetrics metrics,
            RichChatRenderNode node,
            ChatTheme theme,
            int contentToLocalY,
            float alpha) {
        if (extractor != null) {
            RichChatMediaRenderer.paintNode(
                    extractor,
                    font,
                    metrics,
                    node,
                    contentToLocalY,
                    alpha,
                    theme);
            return;
        }
        if ((node.kind() == RichChatRenderNodeKind.DELETED
                || node.kind() == RichChatRenderNodeKind.REPLY
                || node.kind() == RichChatRenderNodeKind.TEXT
                || node.kind() == RichChatRenderNodeKind.SYSTEM)
                && node.text() != null) {
            RichChatBounds localBounds = node.bounds().translateY(contentToLocalY);
            int textY = localBounds.bottom() - metrics.entryBottomToMessageY();
            graphics.handleMessage(textY, alpha * metrics.textOpacity(), node.text());
        }
    }

    private static float messageAlpha(RichChatMessageLayout layout, int ticks, boolean foreground) {
        if (foreground) {
            return 1.0F;
        }
        int tickDelta = ticks - layout.message().addedTime();
        double value = 1.0D - tickDelta / 200.0D;
        value = Mth.clamp(value * 10.0D, 0.0D, 1.0D);
        return (float) (value * value);
    }
}