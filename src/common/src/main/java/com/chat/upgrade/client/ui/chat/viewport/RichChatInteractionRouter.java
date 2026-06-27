package com.chat.upgrade.client.ui.chat.viewport;

import java.net.URI;
import java.util.ArrayList;
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
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.media.video.VideoEntry;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.media.video.VideoPlayerService;
import com.chat.upgrade.client.ui.chat.AudioControlClickEvent;
import com.chat.upgrade.client.ui.chat.AudioFloatingWindowClickEvent;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatRenderState;
import com.chat.upgrade.client.ui.chat.ImagePreviewClickEvent;
import com.chat.upgrade.client.ui.chat.UpgradeHudInlinePaint;
import com.chat.upgrade.client.ui.chat.VideoControlClickEvent;
import com.chat.upgrade.client.ui.chat.VideoPreviewClickEvent;
import com.chat.upgrade.client.ui.layout.AudioUiLayout;
import com.chat.upgrade.client.ui.layout.VideoUiLayout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

public final class RichChatInteractionRouter {
    private static final List<ActiveHitBox> ACTIVE_HIT_BOXES = new ArrayList<>();
    private static final String EMOJI_ACTION_PREFIX = "emoji:";
    private static final int EMOJI_PREVIEW_MIN_SIZE = 48;
    private static final int EMOJI_PREVIEW_MAX_SIZE = 96;
    private static final int EMOJI_PREVIEW_GAP = 8;
    private static @Nullable Matrix3x2fc activePose;
    private static @Nullable RichChatBounds activeViewportBounds;

    private RichChatInteractionRouter() {
    }

    public static void clear() {
        ACTIVE_HIT_BOXES.clear();
        activePose = null;
        activeViewportBounds = null;
    }

    public static void setActiveLayout(
            RichChatLayout layout,
            RichChatViewportState state,
            Matrix3x2fc pose,
            int contentToLocalY,
            RichChatBounds localViewportBounds) {
        ACTIVE_HIT_BOXES.clear();
        activePose = pose;
        activeViewportBounds = localViewportBounds;
        if (layout == null) {
            return;
        }
        List<RichChatHitBox> source = state == null ? layout.hitBoxes() : layout.visibleHitBoxes(state);
        for (RichChatHitBox hitBox : source) {
            ACTIVE_HIT_BOXES.add(new ActiveHitBox(hitBox, hitBox.bounds().translateY(contentToLocalY)));
        }
    }

    public static @Nullable RichChatHitBox hitBoxAtLocal(float localX, float localY) {
        if (!isInsideActiveViewport(localX, localY)) {
            return null;
        }
        for (int i = ACTIVE_HIT_BOXES.size() - 1; i >= 0; i--) {
            ActiveHitBox active = ACTIVE_HIT_BOXES.get(i);
            if (active.localBounds().contains(Math.round(localX), Math.round(localY))) {
                return active.hitBox();
            }
        }
        return null;
    }

    public static boolean hasActionAtLocal(float localX, float localY) {
        return styleForLocalClick(localX, localY) != null;
    }

    public static @Nullable Style styleForLocalClick(float localX, float localY) {
        if (!isInsideActiveViewport(localX, localY)) {
            return null;
        }
        for (int i = ACTIVE_HIT_BOXES.size() - 1; i >= 0; i--) {
            ActiveHitBox active = ACTIVE_HIT_BOXES.get(i);
            if (!active.localBounds().contains(Math.round(localX), Math.round(localY))) {
                continue;
            }
            Style style = styleForHitBox(active, localX, localY);
            if (style != null) {
                return style;
            }
        }
        return null;
    }

    public static @Nullable Style styleForScreenClick(int screenX, int screenY) {
        if (!ChatUpgradeChatRenderState.isInClipBounds(screenX, screenY) || activePose == null) {
            return null;
        }
        Matrix3x2f inv = new Matrix3x2f(activePose);
        inv.invert();
        Vector2f local = inv.transformPosition(new Vector2f(screenX, screenY));
        return styleForLocalClick(local.x, local.y);
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
            if (!active.localBounds().contains(Math.round(localX), Math.round(localY))) {
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
        return activeViewportBounds.contains(Math.round(localX), Math.round(localY));
    }

    private static boolean isEmojiHitBox(RichChatHitBox hitBox) {
        return hitBox != null && hitBox.actionKey().startsWith(EMOJI_ACTION_PREFIX);
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

    private static @Nullable Style styleForHitBox(ActiveHitBox active, float localX, float localY) {
        Style textStyle = active.hitBox().style();
        if (textStyle != null && textStyle.getClickEvent() != null) {
            return textStyle;
        }
        RichAttachment attachment = active.hitBox().attachment();
        if (attachment == null || !attachment.hasRenderableUrl()) {
            return null;
        }
        String url = attachment.requireRenderableUrl();
        String name = attachment.displayName();
        return switch (attachment.type()) {
            case IMAGE -> Style.EMPTY.withClickEvent(ImagePreviewClickEvent.forUrlAndName(url, name));
            case AUDIO -> styleForAudioClick(active.localBounds(), url, name, localX, localY);
            case VIDEO -> styleForVideoClick(active.localBounds(), url, name, localX, localY);
        };
    }

    private static @Nullable Style styleForAudioClick(
            RichChatBounds bounds,
            String url,
            String resourceName,
            float localX,
            float localY) {
        AudioAction action = resolveAudioAction(localX, localY, bounds);
        return switch (action.kind()) {
            case TOGGLE -> Style.EMPTY.withClickEvent(AudioControlClickEvent.forToggle(url));
            case TOGGLE_LOOP -> Style.EMPTY.withClickEvent(AudioControlClickEvent.forToggleLoop(url));
            case OPEN_URL -> {
                try {
                    yield Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)));
                } catch (Exception e) {
                    yield null;
                }
            }
            case TOGGLE_FLOATING -> Style.EMPTY.withClickEvent(AudioFloatingWindowClickEvent.forToggle(url, resourceName));
            case SEEK -> Style.EMPTY.withClickEvent(AudioControlClickEvent.forSeek(url, action.ratio()));
            case NONE -> null;
        };
    }

    private static @Nullable Style styleForVideoClick(
            RichChatBounds bounds,
            String url,
            String resourceName,
            float localX,
            float localY) {
        VideoAction action = resolveVideoAction(bounds, url, localX, localY);
        return switch (action.kind()) {
            case TOGGLE -> Style.EMPTY.withClickEvent(VideoControlClickEvent.forToggle(url));
            case SEEK -> Style.EMPTY.withClickEvent(VideoControlClickEvent.forSeek(url, action.ratio()));
            case OPEN_PREVIEW -> Style.EMPTY.withClickEvent(VideoPreviewClickEvent.forUrlAndName(url, resourceName));
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
        AudioUiLayout.ButtonRects rects = AudioUiLayout.buttonRects(bounds.left(), bounds.top());
        if (inside(localX, localY, rects.playLeft(), rects.top(), rects.playRight(), rects.bottom())) {
            return new AudioAction(AudioActionKind.TOGGLE, 0.0);
        }
        if (inside(localX, localY, rects.loopLeft(), rects.top(), rects.loopRight(), rects.bottom())) {
            return new AudioAction(AudioActionKind.TOGGLE_LOOP, 0.0);
        }
        if (inside(localX, localY, rects.openLeft(), rects.top(), rects.openRight(), rects.bottom())) {
            return new AudioAction(AudioActionKind.OPEN_URL, 0.0);
        }
        if (inside(localX, localY, rects.popLeft(), rects.top(), rects.popRight(), rects.bottom())) {
            return new AudioAction(AudioActionKind.TOGGLE_FLOATING, 0.0);
        }
        int barX0 = bounds.left() + UpgradeHudInlinePaint.AUDIO_PAD_X;
        int barX1 = bounds.right() - UpgradeHudInlinePaint.AUDIO_PAD_X;
        int barY0 = bounds.top() + UpgradeHudInlinePaint.AUDIO_PROGRESS_Y;
        int barY1 = barY0 + UpgradeHudInlinePaint.AUDIO_PROGRESS_H;
        if (inside(localX, localY, barX0, barY0, barX1, barY1)) {
            double ratio = Math.clamp((localX - barX0) / Math.max(1.0, barX1 - barX0), 0.0, 1.0);
            return new AudioAction(AudioActionKind.SEEK, ratio);
        }
        return new AudioAction(AudioActionKind.NONE, 0.0);
    }

    private static VideoAction resolveVideoAction(RichChatBounds bounds, String url, float localX, float localY) {
        int controlY = bounds.top() + VideoUiLayout.CONTROL_TOP;
        int btnX0 = bounds.left() + VideoUiLayout.PAD_X;
        int btnX1 = btnX0 + VideoUiLayout.BTN_W;
        if (inside(localX, localY, btnX0, controlY, btnX1, controlY + VideoUiLayout.BTN_H)) {
            return new VideoAction(VideoActionKind.TOGGLE, 0.0);
        }

        Font font = Minecraft.getInstance().font;
        long pos = Math.max(0L, VideoPlayerService.positionMs(url));
        long total = Math.max(0L, VideoPlayerService.durationMs(url));
        String left = ChatUpgradeFormatters.formatMs(pos);
        String right = ChatUpgradeFormatters.formatMs(total);
        int leftX = btnX1 + 4;
        int rightX = bounds.right() - VideoUiLayout.PAD_X - font.width(right);
        int barX0 = leftX + font.width(left) + 4;
        int barX1 = rightX - 4;
        int barY0 = bounds.top() + VideoUiLayout.PROGRESS_TOP;
        int barY1 = barY0 + VideoUiLayout.PROGRESS_H;
        if (barX1 > barX0 && inside(localX, localY, barX0, barY0, barX1, barY1)) {
            double ratio = Math.clamp((localX - barX0) / Math.max(1.0, barX1 - barX0), 0.0, 1.0);
            return new VideoAction(VideoActionKind.SEEK, ratio);
        }

        VideoEntry entry = VideoLoader.getIfPresent(url);
        int rawW = entry == null ? 0 : entry.getRawWidth();
        int rawH = entry == null ? 0 : entry.getRawHeight();
        if (rawW <= 0 || rawH <= 0) {
            return new VideoAction(VideoActionKind.NONE, 0.0);
        }
        VideoUiLayout.Rect rect = VideoUiLayout.fitVideoRect(bounds.left(), bounds.top(), bounds.width(), rawW, rawH);
        if (inside(localX, localY, rect.left(), rect.top(), rect.right(), rect.bottom())) {
            return new VideoAction(VideoActionKind.OPEN_PREVIEW, 0.0);
        }
        return new VideoAction(VideoActionKind.NONE, 0.0);
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

    private record ActiveHitBox(RichChatHitBox hitBox, RichChatBounds localBounds) {
    }
}