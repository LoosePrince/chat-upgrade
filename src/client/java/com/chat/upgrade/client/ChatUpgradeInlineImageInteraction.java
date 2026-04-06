package com.chat.upgrade.client;

import com.chat.upgrade.client.mixin.ChatUpgradeClickableTextOnlyGraphicsAccessor;
import com.chat.upgrade.client.mixin.ChatUpgradeDrawingBackgroundAccessor;
import com.chat.upgrade.client.mixin.ChatUpgradeDrawingFocusedAccessor;
import com.chat.upgrade.client.mixininterface.ImageAttachable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers screen-space hit parallelograms for inline chat images (same pose stack as
 * {@link ChatComponent} text) and resolves click / hover.
 */
public final class ChatUpgradeInlineImageInteraction {
    private static final List<Plane> PLANES = new ArrayList<>();

    private ChatUpgradeInlineImageInteraction() {}

    public static void clearForExtractPass() {
        PLANES.clear();
    }

    public static void afterChatLinePaint(ChatComponent.ChatGraphicsAccess graphics, GuiMessage.Line line, int textTop, float textOpacity) {
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
            float textOpacity
    ) {
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
        gfx.setTooltipForNextFrame(font, font.split(tip, 210), acc.chatupgrade$globalMouseX(), acc.chatupgrade$globalMouseY());
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
            continue;
        }
        return null;
    }

    private static @Nullable Style styleForAudioClick(Plane p, float localX, float localY) {
        int x0 = p.localLeft;
        int y0 = p.localTop;
        AudioUiLayout.ButtonRects rects = AudioUiLayout.buttonRects(x0, y0);
        if (ActiveTextCollector.isPointInRectangle(localX, localY, rects.playLeft(), rects.top(), rects.playRight(), rects.bottom())) {
            return Style.EMPTY.withClickEvent(AudioControlClickEvent.forToggle(p.url));
        }
        if (ActiveTextCollector.isPointInRectangle(localX, localY, rects.loopLeft(), rects.top(), rects.loopRight(), rects.bottom())) {
            return Style.EMPTY.withClickEvent(AudioControlClickEvent.forToggleLoop(p.url));
        }
        if (ActiveTextCollector.isPointInRectangle(localX, localY, rects.openLeft(), rects.top(), rects.openRight(), rects.bottom())) {
            URI uri;
            try {
                uri = URI.create(p.url);
            } catch (Exception e) {
                return null;
            }
            return Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(uri));
        }
        int barX0 = x0 + UpgradeHudInlinePaint.AUDIO_PAD_X;
        int barX1 = p.localRight - UpgradeHudInlinePaint.AUDIO_PAD_X;
        int barY0 = y0 + UpgradeHudInlinePaint.AUDIO_PROGRESS_Y;
        int barY1 = barY0 + UpgradeHudInlinePaint.AUDIO_PROGRESS_H;
        if (ActiveTextCollector.isPointInRectangle(localX, localY, barX0, barY0, barX1, barY1)) {
            double ratio = (localX - barX0) / Math.max(1.0, barX1 - barX0);
            return Style.EMPTY.withClickEvent(AudioControlClickEvent.forSeek(p.url, ratio));
        }
        return null;
    }

    private static @Nullable Style styleForVideoClick(Plane p, float localX, float localY) {
        int x0 = p.localLeft;
        int y0 = p.localTop;
        int x1 = p.localRight;
        VideoEntry entry = VideoLoader.getIfPresent(p.url);
        int rawW = entry != null ? entry.getRawWidth() : 0;
        int rawH = entry != null ? entry.getRawHeight() : 0;
        VideoUiLayout.Rect rect = VideoUiLayout.fitVideoRect(x0, y0, x1 - x0, rawW, rawH);
        if (ActiveTextCollector.isPointInRectangle(localX, localY, rect.left(), rect.top(), rect.right(), rect.bottom())) {
            return Style.EMPTY.withClickEvent(VideoControlClickEvent.forToggle(p.url));
        }
        int btnX0 = x0 + VideoUiLayout.PAD_X;
        int btnX1 = btnX0 + VideoUiLayout.BTN_W;
        int btnY0 = y0 + VideoUiLayout.CONTROL_TOP;
        int btnY1 = btnY0 + VideoUiLayout.BTN_H;
        if (ActiveTextCollector.isPointInRectangle(localX, localY, btnX0, btnY0, btnX1, btnY1)) {
            return Style.EMPTY.withClickEvent(VideoControlClickEvent.forToggle(p.url));
        }
        long total = VideoPlayerService.durationMs(p.url);
        if (entry != null && total <= 0) {
            total = entry.getDurationMs();
        }
        long pos = VideoPlayerService.positionMs(p.url);
        String left = formatMs(pos);
        String right = formatMs(total);
        int leftX = btnX1 + 4;
        int rightX = x1 - VideoUiLayout.PAD_X - Minecraft.getInstance().font.width(right);
        int barX0 = leftX + Minecraft.getInstance().font.width(left) + 4;
        int barX1 = rightX - 4;
        int barY0 = y0 + VideoUiLayout.PROGRESS_TOP;
        int barY1 = barY0 + VideoUiLayout.PROGRESS_H;
        if (barX1 > barX0 && ActiveTextCollector.isPointInRectangle(localX, localY, barX0, barY0, barX1, barY1)) {
            double ratio = (localX - barX0) / Math.max(1.0, barX1 - barX0);
            return Style.EMPTY.withClickEvent(VideoControlClickEvent.forSeek(p.url, ratio));
        }
        return null;
    }

    private static void tryAudioTooltipOnFocused(
            ChatComponent.ChatGraphicsAccess graphics,
            int textTop,
            int drawW,
            int drawH,
            String url,
            GuiMessage parent,
            AudioEntry entry,
            float textOpacity
    ) {
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
        gfx.setTooltipForNextFrame(font, font.split(tip, 210), acc.chatupgrade$globalMouseX(), acc.chatupgrade$globalMouseY());
    }

    private static void tryVideoTooltipOnFocused(
            ChatComponent.ChatGraphicsAccess graphics,
            int textTop,
            int drawW,
            int drawH,
            String url,
            GuiMessage parent,
            VideoEntry entry,
            float textOpacity
    ) {
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
        gfx.setTooltipForNextFrame(font, font.split(tip, 210), acc.chatupgrade$globalMouseX(), acc.chatupgrade$globalMouseY());
    }

    private static String describeAudioHoverAction(float localX, float localY, int textTop, int drawW, String url, AudioEntry entry) {
        int x0 = 0;
        int y0 = textTop;
        AudioUiLayout.ButtonRects rects = AudioUiLayout.buttonRects(x0, y0);
        long total = AudioPlayerService.durationMs(url);
        if (total <= 0L) {
            total = entry.getDurationMs();
        }
        long pos = AudioPlayerService.positionMs(url);
        if (ActiveTextCollector.isPointInRectangle(localX, localY, rects.playLeft(), rects.top(), rects.playRight(), rects.bottom())) {
            return AudioPlayerService.isPlaying(url) ? "按钮：暂停播放" : "按钮：开始播放";
        }
        if (ActiveTextCollector.isPointInRectangle(localX, localY, rects.loopLeft(), rects.top(), rects.loopRight(), rects.bottom())) {
            return AudioPlayerService.isLoopEnabled(url) ? "按钮：关闭循环播放" : "按钮：开启循环播放";
        }
        if (ActiveTextCollector.isPointInRectangle(localX, localY, rects.openLeft(), rects.top(), rects.openRight(), rects.bottom())) {
            return "按钮：打开链接";
        }
        int barX0 = x0 + UpgradeHudInlinePaint.AUDIO_PAD_X;
        int barX1 = x0 + drawW - UpgradeHudInlinePaint.AUDIO_PAD_X;
        int barY0 = y0 + UpgradeHudInlinePaint.AUDIO_PROGRESS_Y;
        int barY1 = barY0 + UpgradeHudInlinePaint.AUDIO_PROGRESS_H;
        if (ActiveTextCollector.isPointInRectangle(localX, localY, barX0, barY0, barX1, barY1)) {
            double ratio = (localX - barX0) / Math.max(1.0, barX1 - barX0);
            long target = (long) (Math.clamp(ratio, 0.0, 1.0) * Math.max(0L, total));
            return "进度条：点击将跳转到 " + formatMs(target);
        }
        return "音频播放器区域\n当前: " + formatMs(pos) + " / " + formatMs(total);
    }

    private static String describeVideoHoverAction(float localX, float localY, int textTop, int drawW, String url, VideoEntry entry) {
        int x0 = 0;
        int y0 = textTop;
        int x1 = x0 + drawW;
        VideoUiLayout.Rect rect = VideoUiLayout.fitVideoRect(x0, y0, drawW, entry.getRawWidth(), entry.getRawHeight());
        if (ActiveTextCollector.isPointInRectangle(localX, localY, rect.left(), rect.top(), rect.right(), rect.bottom())) {
            return VideoPlayerService.isPlaying(url) ? "视频画面：点击暂停" : "视频画面：点击播放";
        }
        int btnX0 = x0 + VideoUiLayout.PAD_X;
        int btnX1 = btnX0 + VideoUiLayout.BTN_W;
        int btnY0 = y0 + VideoUiLayout.CONTROL_TOP;
        int btnY1 = btnY0 + VideoUiLayout.BTN_H;
        if (ActiveTextCollector.isPointInRectangle(localX, localY, btnX0, btnY0, btnX1, btnY1)) {
            return VideoPlayerService.isPlaying(url) ? "按钮：暂停播放" : "按钮：开始播放";
        }
        long total = VideoPlayerService.durationMs(url);
        if (total <= 0L) {
            total = entry.getDurationMs();
        }
        long pos = VideoPlayerService.positionMs(url);
        String left = formatMs(pos);
        String right = formatMs(total);
        int leftX = btnX1 + 4;
        int rightX = x1 - VideoUiLayout.PAD_X - Minecraft.getInstance().font.width(right);
        int barX0 = leftX + Minecraft.getInstance().font.width(left) + 4;
        int barX1 = rightX - 4;
        int barY0 = y0 + VideoUiLayout.PROGRESS_TOP;
        int barY1 = barY0 + VideoUiLayout.PROGRESS_H;
        if (barX1 > barX0 && ActiveTextCollector.isPointInRectangle(localX, localY, barX0, barY0, barX1, barY1)) {
            double ratio = (localX - barX0) / Math.max(1.0, barX1 - barX0);
            long target = (long) (Math.clamp(ratio, 0.0, 1.0) * Math.max(0L, total));
            return "进度条：点击将跳转到 " + formatMs(target);
        }
        return "视频播放器区域\n当前: " + formatMs(pos) + " / " + formatMs(total);
    }

    private static String formatMs(long ms) {
        long s = Math.max(0L, ms / 1000L);
        long m = s / 60L;
        long r = s % 60L;
        return String.format("%d:%02d", m, r);
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
            InlineResourceType resourceType
    ) {}
}
