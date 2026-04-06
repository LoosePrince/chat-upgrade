package com.chat.upgrade.client;

import com.chat.upgrade.client.mixin.ChatUpgradeClickableTextOnlyGraphicsAccessor;
import com.chat.upgrade.client.mixin.ChatUpgradeDrawingBackgroundAccessor;
import com.chat.upgrade.client.mixin.ChatUpgradeDrawingFocusedAccessor;
import com.chat.upgrade.client.mixininterface.ImageAttachable;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
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
            drawW = 220;
            drawH = ImageLoader.PREVIEW_HEIGHT;
            tryAudioTooltipOnFocused(graphics, textTop, drawW, drawH, url, parentFrom(line), entry, textOpacity);
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
        Component tip = ChatUpgradeImageTooltips.build(parent, url, entry);
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
            }
            URI uri;
            try {
                uri = URI.create(p.url);
            } catch (Exception e) {
                continue;
            }
            ImageEntry entry = ImageLoader.getIfPresent(p.url);
            Component hover = ChatUpgradeImageTooltips.build(p.parent, p.url, entry);
            return Style.EMPTY
                    .withClickEvent(new ClickEvent.OpenUrl(uri))
                    .withHoverEvent(new HoverEvent.ShowText(hover));
        }
        return null;
    }

    private static @Nullable Style styleForAudioClick(Plane p, float localX, float localY) {
        int x0 = p.localLeft;
        int y0 = p.localTop;
        int h = p.localBottom - p.localTop;
        int btn = h - 12;
        int bx0 = x0 + 6;
        int by0 = y0 + 6;
        if (ActiveTextCollector.isPointInRectangle(localX, localY, bx0, by0, bx0 + btn, by0 + btn)) {
            return Style.EMPTY.withClickEvent(AudioControlClickEvent.forToggle(p.url));
        }
        int barX0 = bx0 + btn + 8;
        int barX1 = p.localRight - 8;
        int barY0 = y0 + h - 14;
        int barY1 = barY0 + 6;
        if (ActiveTextCollector.isPointInRectangle(localX, localY, barX0, barY0, barX1, barY1)) {
            double ratio = (localX - barX0) / Math.max(1.0, barX1 - barX0);
            return Style.EMPTY.withClickEvent(AudioControlClickEvent.forSeek(p.url, ratio));
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
        String state = switch (entry.getState()) {
            case LOADING -> "加载中";
            case LOADED -> AudioPlayerService.isPlaying(url) ? "播放中" : "暂停";
            case FAILED -> "失败";
        };
        Component tip = Component.literal("音频\n状态: " + state + "\n时长: " + formatMs(entry.getDurationMs()) + "\n左键按钮: 播放/暂停\n左键进度条: 跳转\n其它区域: 打开链接");
        gfx.setTooltipForNextFrame(font, font.split(tip, 210), acc.chatupgrade$globalMouseX(), acc.chatupgrade$globalMouseY());
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
