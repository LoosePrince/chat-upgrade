package com.chat.upgrade.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import com.chat.upgrade.client.mixin.ChatUpgradeClickableTextOnlyGraphicsAccessor;
import com.chat.upgrade.client.mixin.ChatUpgradeDrawingBackgroundAccessor;
import com.chat.upgrade.client.mixin.ChatUpgradeDrawingFocusedAccessor;
import com.chat.upgrade.client.mixininterface.ImageAttachable;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Registers screen-space hit parallelograms for inline chat images (same pose
 * stack as
 * {@link ChatComponent} text) and resolves click / hover.
 */
public final class ChatUpgradeInlineImageInteraction {
    private static final List<Plane> PLANES = new ArrayList<>();

    private ChatUpgradeInlineImageInteraction() {
    }

    public static void clearForExtractPass() {
        PLANES.clear();
    }

    public static void afterChatLinePaint(ChatComponent.ChatGraphicsAccess graphics, GuiMessage.Line line, int textTop,
            float textOpacity) {
        if (!(((Object) line) instanceof ImageAttachable attachable)) {
            return;
        }
        String url = attachable.chatupgrade$getImageUrl();
        if (url == null) {
            return;
        }
        int drawW;
        int drawH;
        if (attachable.chatupgrade$getResourceType() == InlineResourceType.AUDIO) {
            AudioEntry entry = AudioLoader.getIfPresent(url);
            if (entry == null || entry.getState() == AudioEntry.State.FAILED) {
                return;
            }
            drawW = UpgradeHudInlinePaint.AUDIO_WIDTH;
            drawH = UpgradeHudInlinePaint.AUDIO_HEIGHT;
            tryAudioTooltipOnFocused(graphics, textTop, drawW, drawH, url, parentFrom(line), entry, textOpacity);
        } else if (attachable.chatupgrade$getResourceType() == InlineResourceType.VIDEO) {
            VideoEntry entry = VideoLoader.getIfPresent(url);
            if (entry == null || entry.getState() == VideoEntry.State.FAILED) {
                return;
            }
            drawW = VideoUiLayout.WIDTH;
            drawH = VideoUiLayout.HEIGHT;
            tryVideoTooltipOnFocused(graphics, textTop, drawW, drawH, url, parentFrom(line), entry, textOpacity);
        } else {
            ImageEntry entry = ImageLoader.getIfPresent(url);
            if (entry == null || entry.getState() == ImageEntry.State.FAILED) {
                return;
            }
            switch (entry.getState()) {
                case LOADING -> {
                    drawW = ImageLoader.PREVIEW_HEIGHT;
                    drawH = ImageLoader.PREVIEW_HEIGHT;
                }
                case LOADED -> {
                    drawW = entry.getWidth();
                    drawH = entry.getHeight();
                    if (drawH > ImageLoader.PREVIEW_HEIGHT) {
                        drawH = ImageLoader.PREVIEW_HEIGHT;
                    }
                }
                default -> {
                    return;
                }
            }
            tryTooltipOnFocused(graphics, textTop, drawW, drawH, url, parentFrom(line), entry, textOpacity);
        }

        if (drawW <= 0 || drawH <= 0) {
            return;
        }

        Matrix3x2fc pose = poseFromGraphics(graphics);
        if (pose == null) {
            return;
        }

        GuiMessage parent = line.parent();
        PLANES.add(new Plane(
                pose,
                0,
                textTop,
                drawW,
                textTop + drawH,
                url,
                parent,
                attachable.chatupgrade$getResourceType()));
    }

    private static GuiMessage parentFrom(GuiMessage.Line line) {
        return line.parent();
    }

    private static void tryTooltipOnFocused(
            ChatComponent.ChatGraphicsAccess graphics,
            int textTop,
            int drawW,
            int drawH,
            String url,
            GuiMessage parent,
            ImageEntry entry,
            float textOpacity) {
        if (!(graphics instanceof ChatUpgradeDrawingFocusedAccessor acc)) {
            return;
        }
        if (textOpacity <= 1.0e-5F) {
            return;
        }
        Vector2f local = acc.chatupgrade$localMousePos();
        float lx = local.x;
        float ly = local.y;
        if (!ActiveTextCollector.isPointInRectangle(lx, ly, 0, textTop, drawW, textTop + drawH)) {
            return;
        }
        Font font = acc.chatupgrade$font();
        GuiGraphicsExtractor gfx = acc.chatupgrade$graphics();
        String state = switch (entry.getState()) {
            case LOADING -> "图片加载中";
            case LOADED -> "图片已加载";
            case FAILED -> "图片加载失败";
        };
        Component tip = Component.literal(state + "\n预览区域：仅显示内容\n链接与详情：请悬停 [类型:名称] 或点击 [url]");
        gfx.setTooltipForNextFrame(font, font.split(tip, 210), acc.chatupgrade$globalMouseX(),
                acc.chatupgrade$globalMouseY());
    }

    public static @Nullable Style styleForScreenClick(int screenX, int screenY) {
        for (int i = PLANES.size() - 1; i >= 0; i--) {
            Plane p = PLANES.get(i);
            if (!containsScreenPoint(p, screenX, screenY)) {
                continue;
            }
            Matrix3x2f inv = new Matrix3x2f(p.pose);
            inv.invert();
            Vector2f local = inv.transformPosition(new Vector2f(screenX, screenY));
            if (p.resourceType == InlineResourceType.AUDIO) {
                Style actionStyle = styleForAudioClick(p, local.x, local.y);
                if (actionStyle != null) {
                    return actionStyle;
                }
                continue;
            }
            if (p.resourceType == InlineResourceType.VIDEO) {
                Style actionStyle = styleForVideoClick(p, local.x, local.y);
                if (actionStyle != null) {
                    return actionStyle;
                }
                continue;
            }
            if (p.resourceType == InlineResourceType.IMAGE) {
                return Style.EMPTY.withClickEvent(ImagePreviewClickEvent.forUrl(p.url));
            }
            continue;
        }
        return null;
    }

    private static @Nullable Style styleForAudioClick(Plane p, float localX, float localY) {
        AudioAction action = resolveAudioAction(localX, localY, p.localLeft, p.localTop, p.localRight);
        return switch (action.kind()) {
            case TOGGLE -> Style.EMPTY.withClickEvent(AudioControlClickEvent.forToggle(p.url));
            case TOGGLE_LOOP -> Style.EMPTY.withClickEvent(AudioControlClickEvent.forToggleLoop(p.url));
            case OPEN_URL -> {
                try {
                    yield Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create(p.url)));
                } catch (Exception e) {
                    yield null;
                }
            }
            case TOGGLE_FLOATING -> Style.EMPTY.withClickEvent(AudioFloatingWindowClickEvent.forToggle(p.url));
            case SEEK -> Style.EMPTY.withClickEvent(AudioControlClickEvent.forSeek(p.url, action.ratio()));
            case NONE -> null;
        };
    }

    private static @Nullable Style styleForVideoClick(Plane p, float localX, float localY) {
        return Style.EMPTY.withClickEvent(VideoPreviewClickEvent.forUrl(p.url));
    }

    private static void tryAudioTooltipOnFocused(
            ChatComponent.ChatGraphicsAccess graphics,
            int textTop,
            int drawW,
            int drawH,
            String url,
            GuiMessage parent,
            AudioEntry entry,
            float textOpacity) {
        if (!(graphics instanceof ChatUpgradeDrawingFocusedAccessor acc)) {
            return;
        }
        if (textOpacity <= 1.0e-5F) {
            return;
        }
        Vector2f local = acc.chatupgrade$localMousePos();
        if (!ActiveTextCollector.isPointInRectangle(local.x, local.y, 0, textTop, drawW, textTop + drawH)) {
            return;
        }
        Font font = acc.chatupgrade$font();
        GuiGraphicsExtractor gfx = acc.chatupgrade$graphics();
        String tipText = describeAudioHoverAction(local.x, local.y, textTop, drawW, url, entry);
        Component tip = Component.literal(tipText);
        gfx.setTooltipForNextFrame(font, font.split(tip, 210), acc.chatupgrade$globalMouseX(),
                acc.chatupgrade$globalMouseY());
    }

    private static void tryVideoTooltipOnFocused(
            ChatComponent.ChatGraphicsAccess graphics,
            int textTop,
            int drawW,
            int drawH,
            String url,
            GuiMessage parent,
            VideoEntry entry,
            float textOpacity) {
        if (!(graphics instanceof ChatUpgradeDrawingFocusedAccessor acc)) {
            return;
        }
        if (textOpacity <= 1.0e-5F) {
            return;
        }
        Vector2f local = acc.chatupgrade$localMousePos();
        if (!ActiveTextCollector.isPointInRectangle(local.x, local.y, 0, textTop, drawW, textTop + drawH)) {
            return;
        }
        Font font = acc.chatupgrade$font();
        GuiGraphicsExtractor gfx = acc.chatupgrade$graphics();
        String tipText = describeVideoHoverAction(local.x, local.y, textTop, drawW, url, entry);
        Component tip = Component.literal(tipText);
        gfx.setTooltipForNextFrame(font, font.split(tip, 210), acc.chatupgrade$globalMouseX(),
                acc.chatupgrade$globalMouseY());
    }

    private static String describeAudioHoverAction(float localX, float localY, int textTop, int drawW, String url,
            AudioEntry entry) {
        AudioAction action = resolveAudioAction(localX, localY, 0, textTop, drawW);
        long total = AudioPlayerService.durationMs(url);
        if (total <= 0L) {
            total = entry.getDurationMs();
        }
        long pos = AudioPlayerService.positionMs(url);
        return switch (action.kind()) {
            case TOGGLE -> AudioPlayerService.isPlaying(url) ? "按钮：暂停播放" : "按钮：开始播放";
            case TOGGLE_LOOP -> AudioPlayerService.isLoopEnabled(url) ? "按钮：关闭循环播放" : "按钮：开启循环播放";
            case OPEN_URL -> "按钮：打开链接";
            case TOGGLE_FLOATING -> "按钮：创建/移除小窗";
            case SEEK -> "进度条：点击将跳转到 " + ChatUpgradeFormatters.formatMs((long) (action.ratio() * Math.max(0L, total)));
            case NONE ->
                "音频播放器区域\n当前: " + ChatUpgradeFormatters.formatMs(pos) + " / " + ChatUpgradeFormatters.formatMs(total);
        };
    }

    private static String describeVideoHoverAction(float localX, float localY, int textTop, int drawW, String url,
            VideoEntry entry) {
        long total = VideoPlayerService.durationMs(url);
        if (total <= 0L) {
            total = entry.getDurationMs();
        }
        long pos = VideoPlayerService.positionMs(url);
        return "视频预览区域：点击打开预览窗口\n当前: " + ChatUpgradeFormatters.formatMs(pos) + " / "
                + ChatUpgradeFormatters.formatMs(total);
    }

    private static AudioAction resolveAudioAction(float localX, float localY, int x0, int y0, int x1) {
        AudioUiLayout.ButtonRects rects = AudioUiLayout.buttonRects(x0, y0);
        if (ActiveTextCollector.isPointInRectangle(localX, localY, rects.playLeft(), rects.top(), rects.playRight(),
                rects.bottom())) {
            return new AudioAction(AudioActionKind.TOGGLE, 0.0);
        }
        if (ActiveTextCollector.isPointInRectangle(localX, localY, rects.loopLeft(), rects.top(), rects.loopRight(),
                rects.bottom())) {
            return new AudioAction(AudioActionKind.TOGGLE_LOOP, 0.0);
        }
        if (ActiveTextCollector.isPointInRectangle(localX, localY, rects.openLeft(), rects.top(), rects.openRight(),
                rects.bottom())) {
            return new AudioAction(AudioActionKind.OPEN_URL, 0.0);
        }
        if (ActiveTextCollector.isPointInRectangle(localX, localY, rects.popLeft(), rects.top(), rects.popRight(),
                rects.bottom())) {
            return new AudioAction(AudioActionKind.TOGGLE_FLOATING, 0.0);
        }
        int barX0 = x0 + UpgradeHudInlinePaint.AUDIO_PAD_X;
        int barX1 = x1 - UpgradeHudInlinePaint.AUDIO_PAD_X;
        int barY0 = y0 + UpgradeHudInlinePaint.AUDIO_PROGRESS_Y;
        int barY1 = barY0 + UpgradeHudInlinePaint.AUDIO_PROGRESS_H;
        if (ActiveTextCollector.isPointInRectangle(localX, localY, barX0, barY0, barX1, barY1)) {
            double ratio = Math.clamp((localX - barX0) / Math.max(1.0, barX1 - barX0), 0.0, 1.0);
            return new AudioAction(AudioActionKind.SEEK, ratio);
        }
        return new AudioAction(AudioActionKind.NONE, 0.0);
    }

    private enum AudioActionKind {
        TOGGLE, TOGGLE_LOOP, OPEN_URL, TOGGLE_FLOATING, SEEK, NONE
    }

    private record AudioAction(AudioActionKind kind, double ratio) {
    }

    private static boolean containsScreenPoint(Plane p, int screenX, int screenY) {
        Matrix3x2f inv = new Matrix3x2f(p.pose);
        inv.invert();
        Vector2f local = inv.transformPosition(new Vector2f(screenX, screenY));
        return ActiveTextCollector.isPointInRectangle(
                local.x, local.y, p.localLeft, p.localTop, p.localRight, p.localBottom);
    }

    private static @Nullable Matrix3x2fc poseFromGraphics(ChatComponent.ChatGraphicsAccess graphics) {
        if (graphics instanceof ChatUpgradeClickableTextOnlyGraphicsAccessor clickable) {
            var out = clickable.chatupgrade$output();
            if (out instanceof ActiveTextCollector.ClickableStyleFinder finder) {
                return finder.defaultParameters().pose();
            }
        }
        if (graphics instanceof ChatUpgradeDrawingFocusedAccessor focused) {
            return focused.chatupgrade$graphics().pose();
        }
        if (graphics instanceof ChatUpgradeDrawingBackgroundAccessor background) {
            return background.chatupgrade$graphics().pose();
        }
        return null;
    }

    private record Plane(
            Matrix3x2fc pose,
            int localLeft,
            int localTop,
            int localRight,
            int localBottom,
            String url,
            GuiMessage parent,
            InlineResourceType resourceType) {
    }
}
