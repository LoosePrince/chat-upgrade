package com.chat.upgrade.client.ui.chat.viewport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

import com.chat.upgrade.client.ChatUpgradeFormatters;
import com.chat.upgrade.client.media.audio.AudioEntry;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.audio.AudioPlayerService;
import com.chat.upgrade.client.media.image.ImageEntry;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.media.video.VideoEntry;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.media.video.VideoPlayerService;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatRenderState;
import com.chat.upgrade.client.ui.chat.interaction.ChatAction;
import com.chat.upgrade.client.ui.chat.interaction.ChatActionStyleAdapter;
import com.chat.upgrade.client.ui.chat.interaction.ChatGesture;
import com.chat.upgrade.client.ui.chat.interaction.ChatGestureTarget;
import com.chat.upgrade.client.ui.chat.interaction.ChatHitTarget;
import com.chat.upgrade.client.ui.chat.interaction.ChatTextSelectionState;
import com.chat.upgrade.client.ui.chat.interaction.ChatGestureArena;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceController;
import com.chat.upgrade.client.ui.render.UiPrimitives;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

public final class RichChatInteractionRouter {
    private static final List<ActiveHitBox> ACTIVE_HIT_BOXES = new ArrayList<>();
    private static final List<ActiveMessage> ACTIVE_MESSAGES = new ArrayList<>();
    private static final int EMOJI_PREVIEW_MIN_SIZE = 48;
    private static final int EMOJI_PREVIEW_MAX_SIZE = 96;
    private static final int EMOJI_PREVIEW_GAP = 8;
    private static @Nullable Matrix3x2fc activePose;
    private static @Nullable RichChatBounds activeViewportBounds;
    private static int activeCornerRadius;
    private static int activeAvatarCornerRadius;
    private static boolean activeMessageBubbles;
    private static boolean activeRoundedViewportBottom;
    private static @Nullable MediaCapture mediaCapture;

    private RichChatInteractionRouter() {
    }

    public static void clear() {
        clearActiveLayout();
        ChatGestureArena.cancel();
    }

    public static void clearActiveLayout() {
        ACTIVE_HIT_BOXES.clear();
        ACTIVE_MESSAGES.clear();
        activePose = null;
        activeViewportBounds = null;
        activeCornerRadius = 0;
        activeAvatarCornerRadius = 0;
        activeMessageBubbles = false;
        activeRoundedViewportBottom = false;
        cancelLayoutBoundPointerCapture();
    }

    public static void cancelAllPointerCapture() {
        mediaCapture = null;
        ChatGestureArena.cancel();
        ChatTextSelectionState.clear();
    }

    public static void cancelPointerCapture() {
        cancelLayoutBoundPointerCapture();
        ChatGestureArena.cancel(ChatGestureArena.Owner.TIMELINE_SCROLL);
    }

    private static void cancelLayoutBoundPointerCapture() {
        ChatGestureArena.cancel(ChatGestureArena.Owner.MEDIA);
        ChatGestureArena.cancel(ChatGestureArena.Owner.TEXT_SELECTION);
        ChatTextSelectionState.clear();
    }

    public static void setActiveLayout(
            RichChatLayout layout,
            RichChatViewportState state,
            Matrix3x2fc pose,
            int contentToLocalY,
            RichChatBounds localViewportBounds,
            ChatAppearanceSnapshot appearance,
            boolean roundedViewportBottom) {
        ACTIVE_HIT_BOXES.clear();
        ACTIVE_MESSAGES.clear();
        activePose = pose;
        activeViewportBounds = localViewportBounds;
        activeCornerRadius = appearance == null ? 0 : Math.max(0, appearance.cornerRadius());
        activeAvatarCornerRadius = appearance == null || !appearance.doubleLineLayout()
                ? 0
                : activeCornerRadius;
        activeMessageBubbles = appearance != null && appearance.messageBubbles();
        activeRoundedViewportBottom = roundedViewportBottom;
        if (layout == null) {
            return;
        }
        int visibleTop = state == null ? Integer.MIN_VALUE : state.visibleTop();
        int visibleBottom = state == null ? Integer.MAX_VALUE : state.visibleBottom();
        for (RichChatMessageLayout message : layout.messages()) {
            if (message.visibleIn(visibleTop, visibleBottom)) {
                ACTIVE_MESSAGES.add(new ActiveMessage(
                        message,
                        interactionBounds(message).translateY(contentToLocalY)));
            }
        }
        List<RichChatHitBox> source = state == null ? layout.hitBoxes() : layout.visibleHitBoxes(state);
        for (RichChatHitBox hitBox : source) {
            ACTIVE_HIT_BOXES.add(new ActiveHitBox(hitBox, hitBox.bounds().translateY(contentToLocalY)));
        }
    }

    private static RichChatBounds interactionBounds(RichChatMessageLayout message) {
        RichChatBounds visual = message.visualBounds();
        RichChatBounds identity = message.identityBounds();
        if (identity == null) {
            return visual;
        }
        int left = Math.min(visual.left(), identity.left());
        int top = Math.min(visual.top(), identity.top());
        int right = Math.max(visual.right(), identity.right());
        int bottom = Math.max(visual.bottom(), identity.bottom());
        return RichChatBounds.ofSize(left, top, right - left, bottom - top);
    }

    public static @Nullable RichChatHitBox hitBoxAtLocal(float localX, float localY) {
        if (!isInsideActiveViewport(localX, localY)) {
            return null;
        }
        for (int i = ACTIVE_HIT_BOXES.size() - 1; i >= 0; i--) {
            ActiveHitBox active = ACTIVE_HIT_BOXES.get(i);
            if (active.localBounds().contains(Math.round(localX), Math.round(localY))
                    && containsRoundedHitBox(active, localX, localY)) {
                return active.hitBox();
            }
        }
        return null;
    }

    public static @Nullable ChatGestureTarget targetForScreenGesture(
            int screenX,
            int screenY,
            ChatGesture gesture) {
        Vector2f local = localPositionForScreen(screenX, screenY);
        if (local == null || !isInsideActiveViewport(local.x, local.y)) {
            return null;
        }
        ActiveMessage activeMessage = activeMessageAtLocal(local.x, local.y);
        RichChatHitBox hitBox = hitBoxAtLocal(local.x, local.y);
        if (activeMessage == null && hitBox != null) {
            activeMessage = activeMessageById(hitBox.messageId());
        }
        if (activeMessage == null) {
            return null;
        }
        return new ChatGestureTarget(
                gesture == null ? ChatGesture.PRIMARY : gesture,
                activeMessage.layout().message(),
                hitBox,
                local.x,
                local.y);
    }

    public static boolean beginPointerAtScreen(int screenX, int screenY) {
        if (ChatGestureArena.hasCapture()) {
            return true;
        }
        if (RichChatViewport.state().canScroll()
                && ChatSurfaceController.isOverTimelineScrollbar(screenX, screenY)) {
            return false;
        }
        Vector2f local = localPositionForScreen(screenX, screenY);
        if (local == null || !isInsideActiveViewport(local.x, local.y)) {
            return beginBlankScroll(screenX, screenY);
        }
        ActiveMessage activeMessage = activeMessageAtLocal(local.x, local.y);
        RichChatHitBox hitBox = hitBoxAtLocal(local.x, local.y);
        if (hitBox != null && hitBox.style() != null && hitBox.style().getClickEvent() != null) {
            return false;
        }
        if (hitBox != null && hitBox.attachment() != null) {
            ActiveHitBox activeHitBox = activeHitBoxAtLocal(local.x, local.y);
            if (activeHitBox != null && beginMediaCapture(activeHitBox, local.x, local.y)) {
                return true;
            }
            return false;
        }
        if (activeMessage == null) {
            return beginBlankScroll(screenX, screenY);
        }
        TextSelectionPoint selectionPoint = selectionPointAt(activeMessage, local.x, local.y, false);
        if (selectionPoint == null) {
            return beginBlankScroll(screenX, screenY);
        }
        if (!ChatGestureArena.tryCapture(
                ChatGestureArena.Owner.TEXT_SELECTION,
                ChatTextSelectionState::cancel)) {
            return false;
        }
        ChatTextSelectionState.begin(
                activeMessage.layout().message(),
                selectableLines(activeMessage),
                selectionPoint.lineIndex(),
                selectionPoint.charIndex());
        return true;
    }

    public static boolean updatePointerAtScreen(int screenX, int screenY) {
        if (ChatGestureArena.isCapturedBy(ChatGestureArena.Owner.MEDIA)) {
            if (mediaCapture != null) {
                seekMediaCapture(screenX, screenY);
            }
            return true;
        }
        if (ChatGestureArena.isCapturedBy(ChatGestureArena.Owner.TEXT_SELECTION)) {
            if (ChatTextSelectionState.isSelecting()) {
                ActiveMessage activeMessage = activeMessageById(ChatTextSelectionState.messageId());
                Vector2f local = localPositionForCapturedScreen(screenX, screenY);
                if (activeMessage != null && local != null) {
                    TextSelectionPoint selectionPoint = selectionPointAt(activeMessage, local.x, local.y, true);
                    if (selectionPoint != null) {
                        ChatTextSelectionState.update(selectionPoint.lineIndex(), selectionPoint.charIndex());
                    }
                }
            }
            return true;
        }
        return ChatGestureArena.update(screenX, screenY);
    }

    public static boolean finishPointerAtScreen() {
        if (ChatGestureArena.isCapturedBy(ChatGestureArena.Owner.MEDIA)) {
            mediaCapture = null;
            ChatGestureArena.release(ChatGestureArena.Owner.MEDIA);
            return true;
        }
        if (ChatGestureArena.isCapturedBy(ChatGestureArena.Owner.TEXT_SELECTION)) {
            ChatTextSelectionState.finish();
            ChatGestureArena.release(ChatGestureArena.Owner.TEXT_SELECTION);
            return true;
        }
        return ChatGestureArena.finish();
    }

    public static boolean hasPointerCapture() {
        return ChatGestureArena.isCapturedBy(ChatGestureArena.Owner.MEDIA)
                || ChatGestureArena.isCapturedBy(ChatGestureArena.Owner.TEXT_SELECTION)
                || ChatGestureArena.isCapturedBy(ChatGestureArena.Owner.TIMELINE_SCROLL);
    }

    private static boolean beginBlankScroll(int screenX, int screenY) {
        if (ChatSurfaceController.state().presentationMode()
                != com.chat.upgrade.client.ui.chat.surface.ChatPresentationMode.OPEN_PANEL
                || !ChatSurfaceController.messageViewportBounds().contains(screenX, screenY)) {
            return false;
        }
        ChatTextSelectionState.clear();
        return ChatGestureArena.beginBlankScroll(screenX, screenY);
    }

    private static void seekMediaCapture(int screenX, int screenY) {
        Vector2f local = localPositionForScreen(screenX, screenY);
        MediaCapture capture = mediaCapture;
        if (local == null || capture == null) {
            return;
        }
        double ratio = capture.ratioAt(local.x, local.y);
        if (ratio < 0.0D) {
            return;
        }
        if (capture.type() == InlineResourceType.AUDIO) {
            AudioPlayerService.seek(capture.url(), ratio);
        } else if (capture.type() == InlineResourceType.VIDEO) {
            VideoPlayerService.seek(capture.url(), ratio);
        }
    }

    private static boolean beginMediaCapture(ActiveHitBox activeHitBox, float localX, float localY) {
        RichChatHitBox hitBox = activeHitBox.hitBox();
        RichAttachment attachment = hitBox.attachment();
        if (attachment == null || !attachment.hasRenderableUrl()) {
            return false;
        }
        if (attachment.type() == InlineResourceType.IMAGE) {
            return false;
        }
        String url = attachment.requireRenderableUrl();
        RichChatBounds bounds = activeHitBox.localBounds();
        double ratio = progressRatio(bounds, attachment.type(), url, localX, localY);
        if (ratio < 0.0D) {
            return false;
        }
        if (!ChatGestureArena.tryCapture(
                ChatGestureArena.Owner.MEDIA,
                () -> mediaCapture = null)) {
            return false;
        }
        ChatTextSelectionState.clear();
        mediaCapture = new MediaCapture(attachment.type(), url, bounds);
        seekMediaCaptureAtRatio(ratio);
        return true;
    }

    private static void seekMediaCaptureAtRatio(double ratio) {
        MediaCapture capture = mediaCapture;
        if (capture == null) {
            return;
        }
        if (capture.type() == InlineResourceType.AUDIO) {
            AudioPlayerService.seek(capture.url(), ratio);
        } else if (capture.type() == InlineResourceType.VIDEO) {
            VideoPlayerService.seek(capture.url(), ratio);
        }
    }

    private static double progressRatio(
            RichChatBounds bounds,
            InlineResourceType type,
            String url,
            float localX,
            float localY) {
        if (type == InlineResourceType.AUDIO) {
            RichChatBounds progress = RichChatMediaLayout.audio(bounds).progress();
            if (inside(
                    localX,
                    localY,
                    progress.left(),
                    progress.top() - 4,
                    progress.right(),
                    progress.bottom() + 4)) {
                return Math.clamp(
                        (localX - progress.left()) / Math.max(1.0D, progress.width()),
                        0.0D,
                        1.0D);
            }
            return -1.0D;
        }
        if (type == InlineResourceType.VIDEO) {
            RichChatBounds progress = videoGeometry(bounds, url).progress();
            if (inside(
                    localX,
                    localY,
                    progress.left(),
                    progress.top() - 4,
                    progress.right(),
                    progress.bottom() + 4)) {
                return Math.clamp(
                        (localX - progress.left()) / Math.max(1.0D, progress.width()),
                        0.0D,
                        1.0D);
            }
        }
        return -1.0D;
    }

    private static @Nullable ActiveHitBox activeHitBoxAtLocal(float localX, float localY) {
        for (int i = ACTIVE_HIT_BOXES.size() - 1; i >= 0; i--) {
            ActiveHitBox active = ACTIVE_HIT_BOXES.get(i);
            if (active.localBounds().contains(Math.round(localX), Math.round(localY))
                    && containsRoundedHitBox(active, localX, localY)) {
                return active;
            }
        }
        return null;
    }

    private static @Nullable ActiveMessage activeMessageAtLocal(float localX, float localY) {
        for (int i = ACTIVE_MESSAGES.size() - 1; i >= 0; i--) {
            ActiveMessage active = ACTIVE_MESSAGES.get(i);
            if (active.localBounds().contains(Math.round(localX), Math.round(localY))
                    && containsRoundedMessage(active, localX, localY)) {
                return active;
            }
        }
        return null;
    }

    private static @Nullable ActiveMessage activeMessageById(String messageId) {
        for (int i = ACTIVE_MESSAGES.size() - 1; i >= 0; i--) {
            ActiveMessage active = ACTIVE_MESSAGES.get(i);
            if (active.layout().message().messageId().equals(messageId)) {
                return active;
            }
        }
        return null;
    }

    private static List<ChatTextSelectionState.SelectableLine> selectableLines(ActiveMessage activeMessage) {
        int contentToLocalY = activeMessage.localBounds().top() - activeMessage.layout().bounds().top();
        List<ChatTextSelectionState.SelectableLine> lines = new ArrayList<>();
        for (RichChatRenderNode node : activeMessage.layout().nodes()) {
            if (node.text() == null
                    || (node.kind() != RichChatRenderNodeKind.REPLY
                            && node.kind() != RichChatRenderNodeKind.TEXT
                            && node.kind() != RichChatRenderNodeKind.SYSTEM
                            && node.kind() != RichChatRenderNodeKind.DELETED)) {
                continue;
            }
            lines.add(ChatTextSelectionState.SelectableLine.fromRendered(
                    node.order(),
                    node.bounds().translateY(contentToLocalY),
                    node.text(),
                    Minecraft.getInstance().font));
        }
        lines.sort(Comparator.comparingInt(ChatTextSelectionState.SelectableLine::order));
        return List.copyOf(lines);
    }

    private static @Nullable TextSelectionPoint selectionPointAt(
            ActiveMessage activeMessage,
            float localX,
            float localY,
            boolean clampOutside) {
        List<ChatTextSelectionState.SelectableLine> lines = selectableLines(activeMessage);
        if (lines.isEmpty()) {
            return null;
        }
        int lineIndex = lineIndexAt(lines, localY, clampOutside);
        if (lineIndex < 0) {
            return null;
        }
        ChatTextSelectionState.SelectableLine line = lines.get(lineIndex);
        if (!clampOutside
                && (localX < line.bounds().left()
                        || localX > line.bounds().left() + line.renderedWidth())) {
            return null;
        }
        return new TextSelectionPoint(
                lineIndex,
                line.charIndexAt(localX - line.bounds().left()));
    }

    private static int lineIndexAt(
            List<ChatTextSelectionState.SelectableLine> lines,
            float localY,
            boolean clampOutside) {
        for (int index = 0; index < lines.size(); index++) {
            RichChatBounds bounds = lines.get(index).bounds();
            if (localY >= bounds.top() && localY < bounds.bottom()) {
                return index;
            }
            if (clampOutside && localY < bounds.top()) {
                return index;
            }
        }
        return clampOutside ? lines.size() - 1 : -1;
    }

    private static @Nullable Vector2f localPositionForScreen(int screenX, int screenY) {
        if (!ChatUpgradeChatRenderState.isInClipBounds(screenX, screenY)) {
            return null;
        }
        return localPositionForCapturedScreen(screenX, screenY);
    }

    private static @Nullable Vector2f localPositionForCapturedScreen(int screenX, int screenY) {
        if (activePose == null) {
            return null;
        }
        Matrix3x2f inv = new Matrix3x2f(activePose);
        inv.invert();
        return inv.transformPosition(new Vector2f(screenX, screenY));
    }

    public static @Nullable Style styleForLocalClick(float localX, float localY) {
        if (!isInsideActiveViewport(localX, localY)) {
            return null;
        }
        for (int i = ACTIVE_HIT_BOXES.size() - 1; i >= 0; i--) {
            ActiveHitBox active = ACTIVE_HIT_BOXES.get(i);
            if (!active.localBounds().contains(Math.round(localX), Math.round(localY))
                    || !containsRoundedHitBox(active, localX, localY)) {
                continue;
            }
            Style style = ChatActionStyleAdapter.toStyle(actionForHitBox(active, localX, localY));
            if (style != null) {
                return style;
            }
        }
        return null;
    }

    public static @Nullable Style styleForScreenClick(int screenX, int screenY) {
        Vector2f local = localPositionForScreen(screenX, screenY);
        return local == null ? null : styleForLocalClick(local.x, local.y);
    }

    public static boolean showTooltipForLocalHover(
            GuiGraphicsExtractor gfx,
            Font font,
            float localX,
            float localY,
            int screenX,
            int screenY) {
        if (gfx == null || font == null || !isInsideActiveViewport(localX, localY)) {
            return false;
        }
        for (int i = ACTIVE_HIT_BOXES.size() - 1; i >= 0; i--) {
            ActiveHitBox active = ACTIVE_HIT_BOXES.get(i);
            if (!active.localBounds().contains(Math.round(localX), Math.round(localY))
                    || !containsRoundedHitBox(active, localX, localY)) {
                continue;
            }
            if (isEmojiHitBox(active.hitBox())) {
                return showEmojiPreviewForLocalHover(gfx, font, active, localX, localY);
            }
            String text = tooltipForHitBox(active, localX, localY);
            if (text == null || text.isBlank()) {
                return false;
            }
            Component tip = Component.literal(text);
            gfx.setTooltipForNextFrame(font, font.split(tip, 210), screenX, screenY);
            return true;
        }
        return false;
    }

    private static boolean isInsideActiveViewport(float localX, float localY) {
        if (activeViewportBounds == null) {
            return true;
        }
        int x = Math.round(localX);
        int y = Math.round(localY);
        if (!activeViewportBounds.contains(x, y)) {
            return false;
        }
        return !activeRoundedViewportBottom
                || UiPrimitives.containsBottomRounded(activeViewportBounds, x, y, activeCornerRadius);
    }

    private static boolean containsRoundedHitBox(ActiveHitBox active, float localX, float localY) {
        if (active == null) {
            return false;
        }
        ActiveMessage message = activeMessageById(active.hitBox().messageId());
        if (message != null && !containsRoundedMessage(message, localX, localY)) {
            return false;
        }
        if (active.hitBox().attachment() == null) {
            return true;
        }
        return UiPrimitives.containsRounded(
                active.localBounds(),
                Math.round(localX),
                Math.round(localY),
                activeCornerRadius);
    }

    private static boolean containsRoundedMessage(ActiveMessage active, float localX, float localY) {
        if (active == null) {
            return false;
        }
        int x = Math.round(localX);
        int y = Math.round(localY);
        int contentToLocalY = active.localBounds().top() - interactionBounds(active.layout()).top();
        RichChatBounds visual = active.layout().visualBounds().translateY(contentToLocalY);
        boolean insideVisual = activeMessageBubbles
                ? UiPrimitives.containsRounded(visual, x, y, activeCornerRadius)
                : visual.contains(x, y);
        if (insideVisual) {
            return true;
        }
        RichChatBounds identity = active.layout().identityBounds();
        if (identity == null) {
            return false;
        }
        RichChatBounds localIdentity = identity.translateY(contentToLocalY);
        return UiPrimitives.containsRounded(localIdentity, x, y, activeAvatarCornerRadius);
    }

    private static boolean isEmojiHitBox(RichChatHitBox hitBox) {
        return hitBox != null && hitBox.target() instanceof ChatHitTarget.Emoji;
    }

    private static boolean showEmojiPreviewForLocalHover(
            GuiGraphicsExtractor gfx,
            Font font,
            ActiveHitBox active,
            float localX,
            float localY) {
        RichAttachment attachment = active.hitBox().attachment();
        if (attachment == null || !attachment.hasRenderableUrl()) {
            return false;
        }
        String url = attachment.requireRenderableUrl();
        RichChatBounds previewBounds = emojiPreviewBounds(active.localBounds(), localX, localY);
        ChatUpgradeChatRenderState.withClipSuspended(gfx, () -> paintEmojiPreview(gfx, font, url, previewBounds));
        return true;
    }

    private static RichChatBounds emojiPreviewBounds(RichChatBounds sourceBounds, float localX, float localY) {
        int previewSize = Math.clamp(sourceBounds.height() * 6, EMOJI_PREVIEW_MIN_SIZE, EMOJI_PREVIEW_MAX_SIZE);
        int x0 = Math.round(localX) + EMOJI_PREVIEW_GAP;
        int y0 = Math.round(localY) - previewSize - EMOJI_PREVIEW_GAP;
        RichChatBounds viewport = activeViewportBounds;
        if (viewport != null) {
            if (x0 + previewSize > viewport.right()) {
                x0 = Math.round(localX) - EMOJI_PREVIEW_GAP - previewSize;
            }
            if (x0 < viewport.left()) {
                x0 = viewport.left();
            }
            if (y0 < viewport.top()) {
                y0 = Math.round(localY) + EMOJI_PREVIEW_GAP;
            }
            if (y0 + previewSize > viewport.bottom()) {
                y0 = Math.max(viewport.top(), viewport.bottom() - previewSize);
            }
        }
        return RichChatBounds.ofSize(x0, y0, previewSize, previewSize);
    }

    private static void paintEmojiPreview(
            GuiGraphicsExtractor gfx,
            Font font,
            String url,
            RichChatBounds bounds) {
        gfx.fill(bounds.left() - 1, bounds.top() - 1, bounds.right() + 1, bounds.bottom() + 1, 0xDD0A0C10);
        gfx.outline(bounds.left() - 1, bounds.top() - 1, bounds.width() + 2, bounds.height() + 2, 0xFF5A6B84);
        ImageEntry entry = ImageLoader.getOrLoad(url);
        switch (entry.getState()) {
            case LOADED -> paintLoadedEmojiPreview(gfx, entry, bounds);
            case LOADING -> {
                gfx.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xDD23262E);
                gfx.centeredText(font, "...", bounds.left() + bounds.width() / 2,
                        bounds.top() + bounds.height() / 2 - font.lineHeight / 2, 0xFFE6EAF2);
            }
            case FAILED -> {
                gfx.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xDD3A1D1D);
                gfx.centeredText(font, "x", bounds.left() + bounds.width() / 2,
                        bounds.top() + bounds.height() / 2 - font.lineHeight / 2, 0xFFFFB0B0);
            }
        }
    }

    private static void paintLoadedEmojiPreview(GuiGraphicsExtractor gfx, ImageEntry entry, RichChatBounds bounds) {
        Identifier textureId = entry.isAnimated() ? entry.textureIdAtMillis(Util.getMillis()) : entry.getTextureId();
        if (textureId == null) {
            return;
        }
        gfx.blit(
                RenderPipelines.GUI_TEXTURED,
                textureId,
                bounds.left(), bounds.top(),
                0.0F, 0.0F,
                bounds.width(), bounds.height(),
                entry.getTextureWidth(), entry.getTextureHeight(),
                entry.getTextureWidth(), entry.getTextureHeight(),
                ARGB.white(1.0F));
    }

    private static @Nullable ChatAction actionForHitBox(ActiveHitBox active, float localX, float localY) {
        Style textStyle = active.hitBox().style();
        if (textStyle != null && textStyle.getClickEvent() != null) {
            return new ChatAction.StyledText(textStyle);
        }
        RichAttachment attachment = active.hitBox().attachment();
        if (attachment == null || !attachment.hasRenderableUrl()) {
            return null;
        }
        String url = attachment.requireRenderableUrl();
        String name = attachment.displayName();
        return switch (attachment.type()) {
            case IMAGE -> new ChatAction.PreviewImage(url, name);
            case AUDIO -> actionForAudioClick(active.localBounds(), url, name, localX, localY);
            case VIDEO -> actionForVideoClick(active.localBounds(), url, name, localX, localY);
        };
    }

    private static @Nullable ChatAction actionForAudioClick(
            RichChatBounds bounds,
            String url,
            String resourceName,
            float localX,
            float localY) {
        AudioAction action = resolveAudioAction(localX, localY, bounds);
        return switch (action.kind()) {
            case TOGGLE -> new ChatAction.ToggleAudio(url);
            case TOGGLE_LOOP -> new ChatAction.ToggleAudioLoop(url);
            case OPEN_URL -> new ChatAction.OpenUrl(url);
            case TOGGLE_FLOATING -> new ChatAction.ToggleAudioFloating(url, resourceName);
            case SEEK -> new ChatAction.SeekAudio(url, action.ratio());
            case NONE -> null;
        };
    }

    private static @Nullable ChatAction actionForVideoClick(
            RichChatBounds bounds,
            String url,
            String resourceName,
            float localX,
            float localY) {
        VideoAction action = resolveVideoAction(bounds, url, localX, localY);
        return switch (action.kind()) {
            case TOGGLE -> new ChatAction.ToggleVideo(url);
            case SEEK -> new ChatAction.SeekVideo(url, action.ratio());
            case OPEN_PREVIEW -> new ChatAction.PreviewVideo(url, resourceName);
            case NONE -> null;
        };
    }

    private static @Nullable String tooltipForHitBox(ActiveHitBox active, float localX, float localY) {
        String textTooltip = tooltipForStyle(active.hitBox().style());
        if (textTooltip != null) {
            return textTooltip;
        }
        RichAttachment attachment = active.hitBox().attachment();
        if (attachment == null || !attachment.hasRenderableUrl()) {
            return null;
        }
        String url = attachment.requireRenderableUrl();
        return switch (attachment.type()) {
            case IMAGE -> describeImage(url);
            case AUDIO -> describeAudio(active.localBounds(), localX, localY, url);
            case VIDEO -> describeVideo(active.localBounds(), localX, localY, url);
        };
    }

    private static @Nullable String tooltipForStyle(@Nullable Style style) {
        if (style == null) {
            return null;
        }
        HoverEvent hoverEvent = style.getHoverEvent();
        if (hoverEvent instanceof HoverEvent.ShowText showText) {
            String text = showText.value().getString();
            return text.isBlank() ? null : text;
        }
        return null;
    }

    private static String describeImage(String url) {
        ImageEntry entry = ImageLoader.getOrLoad(url);
        String state = switch (entry.getState()) {
            case LOADING -> I18n.get("chatupgrade.inline.state.image_loading");
            case LOADED -> I18n.get("chatupgrade.inline.state.image_loaded");
            case FAILED -> I18n.get("chatupgrade.inline.state.image_failed");
        };
        return I18n.get("chatupgrade.inline.tip.preview_area", state);
    }

    private static String describeAudio(RichChatBounds bounds, float localX, float localY, String url) {
        AudioEntry entry = AudioLoader.getOrLoad(url);
        AudioAction action = resolveAudioAction(localX, localY, bounds);
        long total = AudioPlayerService.durationMs(url);
        if (total <= 0L) {
            total = entry.getDurationMs();
        }
        long pos = AudioPlayerService.positionMs(url);
        return switch (action.kind()) {
            case TOGGLE -> AudioPlayerService.isPlaying(url)
                    ? I18n.get("chatupgrade.inline.audio.button.pause")
                    : I18n.get("chatupgrade.inline.audio.button.play");
            case TOGGLE_LOOP -> AudioPlayerService.isLoopEnabled(url)
                    ? I18n.get("chatupgrade.inline.audio.button.loop_off")
                    : I18n.get("chatupgrade.inline.audio.button.loop_on");
            case OPEN_URL -> I18n.get("chatupgrade.inline.audio.button.open_url");
            case TOGGLE_FLOATING -> I18n.get("chatupgrade.inline.audio.button.floating");
            case SEEK -> I18n.get("chatupgrade.inline.audio.seek_to",
                    ChatUpgradeFormatters.formatMs((long) (action.ratio() * Math.max(0L, total))));
            case NONE -> I18n.get("chatupgrade.inline.audio.current",
                    ChatUpgradeFormatters.formatMs(pos),
                    ChatUpgradeFormatters.formatMs(total));
        };
    }

    private static String describeVideo(RichChatBounds bounds, float localX, float localY, String url) {
        VideoEntry entry = VideoLoader.getOrLoad(url);
        VideoAction action = resolveVideoAction(bounds, url, localX, localY);
        long total = VideoPlayerService.durationMs(url);
        if (total <= 0L) {
            total = entry.getDurationMs();
        }
        long pos = VideoPlayerService.positionMs(url);
        return switch (action.kind()) {
            case TOGGLE -> VideoPlayerService.isPlaying(url)
                    ? I18n.get("chatupgrade.inline.video.button.pause")
                    : I18n.get("chatupgrade.inline.video.button.play");
            case SEEK -> I18n.get("chatupgrade.inline.video.seek_to",
                    ChatUpgradeFormatters.formatMs((long) (action.ratio() * Math.max(0L, total))));
            case OPEN_PREVIEW -> I18n.get("chatupgrade.inline.video.open_preview");
            case NONE -> I18n.get("chatupgrade.inline.video.current",
                    ChatUpgradeFormatters.formatMs(pos),
                    ChatUpgradeFormatters.formatMs(total));
        };
    }

    private static AudioAction resolveAudioAction(float localX, float localY, RichChatBounds bounds) {
        RichChatMediaLayout.AudioGeometry geometry = RichChatMediaLayout.audio(bounds);
        if (contains(geometry.play(), localX, localY)) {
            return new AudioAction(AudioActionKind.TOGGLE, 0.0D);
        }
        if (contains(geometry.loop(), localX, localY)) {
            return new AudioAction(AudioActionKind.TOGGLE_LOOP, 0.0D);
        }
        if (contains(geometry.open(), localX, localY)) {
            return new AudioAction(AudioActionKind.OPEN_URL, 0.0D);
        }
        if (contains(geometry.popout(), localX, localY)) {
            return new AudioAction(AudioActionKind.TOGGLE_FLOATING, 0.0D);
        }
        if (contains(geometry.progress(), localX, localY)) {
            double ratio = Math.clamp(
                    (localX - geometry.progress().left()) / Math.max(1.0D, geometry.progress().width()),
                    0.0D,
                    1.0D);
            return new AudioAction(AudioActionKind.SEEK, ratio);
        }
        return new AudioAction(AudioActionKind.NONE, 0.0D);
    }

    private static VideoAction resolveVideoAction(RichChatBounds bounds, String url, float localX, float localY) {
        RichChatMediaLayout.VideoGeometry geometry = videoGeometry(bounds, url);
        if (contains(geometry.play(), localX, localY)) {
            return new VideoAction(VideoActionKind.TOGGLE, 0.0D);
        }
        if (contains(geometry.progress(), localX, localY)) {
            double ratio = Math.clamp(
                    (localX - geometry.progress().left()) / Math.max(1.0D, geometry.progress().width()),
                    0.0D,
                    1.0D);
            return new VideoAction(VideoActionKind.SEEK, ratio);
        }
        if (contains(geometry.open(), localX, localY) || contains(geometry.frame(), localX, localY)) {
            return new VideoAction(VideoActionKind.OPEN_PREVIEW, 0.0D);
        }
        return new VideoAction(VideoActionKind.NONE, 0.0D);
    }

    private static RichChatMediaLayout.VideoGeometry videoGeometry(RichChatBounds bounds, String url) {
        VideoEntry entry = VideoLoader.getIfPresent(url);
        long positionMs = Math.max(0L, VideoPlayerService.positionMs(url));
        long durationMs = VideoPlayerService.durationMs(url);
        if (durationMs <= 0L && entry != null) {
            durationMs = entry.getDurationMs();
        }
        return RichChatMediaLayout.video(
                bounds,
                Minecraft.getInstance().font,
                positionMs,
                Math.max(0L, durationMs),
                entry == null ? 0 : entry.getRawWidth(),
                entry == null ? 0 : entry.getRawHeight());
    }

    private static boolean contains(RichChatBounds bounds, float x, float y) {
        return bounds != null && inside(x, y, bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
    }

    private static boolean inside(float x, float y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    private enum AudioActionKind {
        TOGGLE, TOGGLE_LOOP, OPEN_URL, TOGGLE_FLOATING, SEEK, NONE
    }

    private record AudioAction(AudioActionKind kind, double ratio) {
    }

    private enum VideoActionKind {
        TOGGLE, SEEK, OPEN_PREVIEW, NONE
    }

    private record VideoAction(VideoActionKind kind, double ratio) {
    }

    private record MediaCapture(InlineResourceType type, String url, RichChatBounds bounds) {
        double ratioAt(float localX, float localY) {
            return progressRatio(bounds, type, url, localX, localY);
        }
    }

    private record TextSelectionPoint(int lineIndex, int charIndex) {
    }

    private record ActiveMessage(RichChatMessageLayout layout, RichChatBounds localBounds) {
    }

    private record ActiveHitBox(RichChatHitBox hitBox, RichChatBounds localBounds) {
    }
}
