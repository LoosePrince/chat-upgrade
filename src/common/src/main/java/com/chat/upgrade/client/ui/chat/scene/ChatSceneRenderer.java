package com.chat.upgrade.client.ui.chat.scene;

import com.chat.upgrade.client.ui.chat.interaction.ChatTextSelectionState;
import com.chat.upgrade.client.ui.chat.state.ChatMessageMetadata;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageStatus;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceRenderer;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.chat.viewport.RichChatLayout;
import com.chat.upgrade.client.ui.chat.viewport.RichChatMediaRenderer;
import com.chat.upgrade.client.ui.chat.viewport.RichChatMessageLayout;
import com.chat.upgrade.client.ui.chat.viewport.RichChatRenderNode;
import com.chat.upgrade.client.ui.chat.viewport.RichChatRenderNodeKind;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewportMetrics;
import com.chat.upgrade.client.ui.chat.viewport.RichChatViewportState;
import com.chat.upgrade.client.ui.render.UiPrimitives;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.renderer.RenderPipelines;
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
                paintMessageDecoration(extractor, message, scene.surface().appearance(), contentToLocalY, alpha,
                        metrics.backgroundOpacity());
                paintTextSelection(extractor, font, message, contentToLocalY, alpha);
                paintIdentity(extractor, font, metrics, message, scene.surface().appearance(), contentToLocalY, alpha);
                paintMetadata(extractor, font, metrics, message, scene.surface().appearance(), contentToLocalY, alpha);
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
                        scene.surface().appearance(),
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
        ChatAppearanceSnapshot.Scrollbar tokens = scene.surface().appearance().scrollbar();
        int thumb = newMessageSinceScroll ? tokens.newMessageThumb() : tokens.thumb();
        int x = metrics.scrollbarX();
        graphics.fill(
                x,
                bottom - barHeight,
                x + 2,
                bottom,
                UiPrimitives.withOpacity(thumb, opacity));
        graphics.fill(
                x + 2,
                bottom - barHeight,
                x + 3,
                bottom,
                UiPrimitives.withOpacity(tokens.track(), opacity));
    }

    private static void paintMessageDecoration(
            GuiGraphicsExtractor graphics,
            RichChatMessageLayout message,
            ChatAppearanceSnapshot appearance,
            int contentToLocalY,
            float alpha,
            float backgroundOpacity) {
        if (!appearance.messageBubbles()) {
            return;
        }
        ChatAppearanceSnapshot.Message tokens = appearance.message();
        int fill;
        int border;
        if (message.message().status() == RichChatMessageStatus.DELETED) {
            fill = tokens.deletedBackground();
            border = tokens.deletedBorder();
        } else {
            fill = tokens.background(message.timeline().kind());
            border = tokens.border(message.timeline().kind());
        }
        float opacity = alpha * backgroundOpacity;
        fill = UiPrimitives.withOpacity(fill, opacity);
        border = UiPrimitives.withOpacity(border, opacity);
        RichChatBounds visual = message.visualBounds().translateY(contentToLocalY);
        UiPrimitives.paintBox(
                graphics,
                visual,
                appearance.cornerRadius(),
                tokens.bubbleBorderWidth(),
                fill,
                border);
        paintReplyCard(graphics, message, appearance, contentToLocalY, opacity);
    }

    private static void paintReplyCard(
            GuiGraphicsExtractor graphics,
            RichChatMessageLayout message,
            ChatAppearanceSnapshot appearance,
            int contentToLocalY,
            float opacity) {
        RichChatRenderNode firstReply = null;
        RichChatRenderNode lastReply = null;
        for (RichChatRenderNode node : message.nodes()) {
            if (node.kind() == RichChatRenderNodeKind.REPLY) {
                if (firstReply == null) {
                    firstReply = node;
                }
                lastReply = node;
            }
        }
        if (firstReply == null || lastReply == null) {
            return;
        }
        RichChatBounds visual = message.visualBounds();
        RichChatBounds reply = new RichChatBounds(
                Math.max(visual.left(), firstReply.bounds().left() - 2),
                firstReply.bounds().top(),
                Math.min(visual.right(), Math.max(firstReply.bounds().right(), lastReply.bounds().right()) + 2),
                lastReply.bounds().bottom()).translateY(contentToLocalY);
        ChatAppearanceSnapshot.Message tokens = appearance.message();
        UiPrimitives.paintBox(
                graphics,
                reply,
                appearance.cornerRadius(),
                tokens.bubbleBorderWidth(),
                UiPrimitives.withOpacity(tokens.replyBackground(), opacity),
                UiPrimitives.withOpacity(tokens.replyBorder(), opacity));
    }

    private static void paintTextSelection(
            GuiGraphicsExtractor graphics,
            Font font,
            RichChatMessageLayout message,
            int contentToLocalY,
            float alpha) {
        int selectionFill = UiPrimitives.withOpacity(0x805A8DFF, alpha);
        for (RichChatRenderNode node : message.nodes()) {
            if (node.text() == null) {
                continue;
            }
            ChatTextSelectionState.LineSelection selection = ChatTextSelectionState.selectionFor(
                    message.message().messageId(),
                    node.order());
            if (selection == null) {
                continue;
            }
            String plain = plainText(node.text());
            int start = Math.clamp(selection.startIndex(), 0, plain.length());
            int end = Math.clamp(selection.endIndex(), start, plain.length());
            if (end <= start) {
                continue;
            }
            RichChatBounds line = node.bounds().translateY(contentToLocalY);
            int left = line.left() + selection.startPixel();
            int right = line.left() + selection.endPixel();
            graphics.fill(left, line.top(), Math.max(left + 1, right), line.bottom(), selectionFill);
        }
    }

    private static String plainText(net.minecraft.util.FormattedCharSequence text) {
        StringBuilder plain = new StringBuilder();
        text.accept((index, style, codePoint) -> {
            plain.appendCodePoint(codePoint);
            return true;
        });
        return plain.toString();
    }

    private static void paintIdentity(
            GuiGraphicsExtractor graphics,
            Font font,
            RichChatViewportMetrics metrics,
            RichChatMessageLayout message,
            ChatAppearanceSnapshot appearance,
            int contentToLocalY,
            float alpha) {
        if (message.identityBounds() == null || !appearance.showIdentity(message.timeline())) {
            return;
        }
        RichChatBounds avatar = message.identityBounds().translateY(contentToLocalY);
        int avatarColor = ARGB.color(
                Math.clamp(Math.round(alpha * 255.0F), 0, 255),
                message.timeline().avatar().backgroundRgb());
        int foreground = ARGB.color(
                Math.clamp(Math.round(alpha * metrics.textOpacity() * 255.0F), 0, 255),
                message.timeline().avatar().foregroundRgb());
        int radius = Math.max(0, avatar.width() / 2);
        UiPrimitives.paintBox(
                graphics,
                avatar,
                radius,
                1,
                avatarColor,
                UiPrimitives.withOpacity(appearance.identity().avatarBorder(), alpha));
        if (message.timeline().avatar().skinTexture() != null) {
            paintSkinAvatar(graphics, avatar, message.timeline().avatar().skinTexture(), alpha);
        } else {
            String glyph = message.timeline().avatar().glyph();
            int glyphX = avatar.left() + Math.max(1, (avatar.width() - font.width(glyph)) / 2);
            int glyphY = avatar.top() + Math.max(1, (avatar.height() - font.lineHeight) / 2);
            graphics.text(font, glyph, glyphX, glyphY, foreground, false);
        }
    }

    private static void paintMetadata(
            GuiGraphicsExtractor graphics,
            Font font,
            RichChatViewportMetrics metrics,
            RichChatMessageLayout message,
            ChatAppearanceSnapshot appearance,
            int contentToLocalY,
            float alpha) {
        if (message.metadataBounds() == null) {
            return;
        }
        String author = ChatMessageMetadata.author(message.timeline());
        String timestamp = ChatMessageMetadata.timestamp(message.timeline());
        if (author.isBlank() && timestamp.isBlank()) {
            return;
        }
        RichChatBounds bounds = message.metadataBounds().translateY(contentToLocalY);
        int textAlpha = Math.clamp(Math.round(alpha * metrics.textOpacity() * 255.0F), 0, 255);
        int nameRgb = message.timeline().author().team().colorRgb() >= 0
                ? message.timeline().author().team().colorRgb()
                : appearance.identity().fallbackName() & 0x00FFFFFF;
        int nameColor = ARGB.color(textAlpha, nameRgb);
        int mutedColor = ARGB.color(textAlpha, appearance.surface().muted() & 0x00FFFFFF);
        int cursorX = bounds.left();
        int remainingWidth = bounds.width();
        boolean fullAuthorVisible = author.isBlank();
        if (!author.isBlank()) {
            String visibleAuthor = font.plainSubstrByWidth(author, remainingWidth);
            graphics.text(font, visibleAuthor, cursorX, bounds.top(), nameColor, false);
            int authorWidth = font.width(visibleAuthor);
            cursorX += authorWidth;
            remainingWidth = Math.max(0, remainingWidth - authorWidth);
            fullAuthorVisible = visibleAuthor.equals(author);
        }
        if (!timestamp.isBlank() && fullAuthorVisible && remainingWidth > 0) {
            String timestampLabel = author.isBlank() ? timestamp : " · " + timestamp;
            String visibleTimestamp = font.plainSubstrByWidth(timestampLabel, remainingWidth);
            graphics.text(font, visibleTimestamp, cursorX, bounds.top(), mutedColor, false);
        }
    }

    private static void paintSkinAvatar(
            GuiGraphicsExtractor graphics,
            RichChatBounds bounds,
            net.minecraft.resources.Identifier texture,
            float alpha) {
        int color = ARGB.color(Math.clamp(Math.round(alpha * 255.0F), 0, 255), 0xFFFFFF);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                bounds.left(),
                bounds.top(),
                8.0F,
                8.0F,
                bounds.width(),
                bounds.height(),
                8,
                8,
                64,
                64,
                color);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                bounds.left(),
                bounds.top(),
                40.0F,
                8.0F,
                bounds.width(),
                bounds.height(),
                8,
                8,
                64,
                64,
                color);
    }

    private static void paintNode(
            ChatComponent.ChatGraphicsAccess graphics,
            GuiGraphicsExtractor extractor,
            Font font,
            RichChatViewportMetrics metrics,
            RichChatRenderNode node,
            ChatAppearanceSnapshot appearance,
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
                    appearance);
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