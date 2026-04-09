package com.chat.upgrade.client.ui.chat;

import java.util.List;

import com.chat.upgrade.client.media.image.ImageEntry;
import com.chat.upgrade.client.media.image.ImageLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

public final class InlineEmojiHudPaint {
    private static final int EMOJI_SIDE_GAP_PX = 1;

    private InlineEmojiHudPaint() {
    }

    public static void paintLineEmoji(GuiMessage.Line line, int messageY, float opacity, int lineHeight) {
        if (!(((Object) line) instanceof com.chat.upgrade.client.mixininterface.ImageAttachable attachable)) {
            return;
        }
        List<InlineEmojiSlot> slots = attachable.chatupgrade$getInlineEmojiSlots();
        if (slots == null || slots.isEmpty()) {
            return;
        }
        GuiGraphicsExtractor gfx = ChatUpgradeRenderScope.current();
        if (gfx == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        String plain = extractPlain(line.content());
        int size = Math.max(1, lineHeight + EMOJI_SIDE_GAP_PX * 2);
        for (InlineEmojiSlot slot : slots) {
            int charIndex = Math.clamp(slot.charIndex(), 0, plain.length());
            int x = font.width(plain.substring(0, charIndex)) + EMOJI_SIDE_GAP_PX;
            paintSingle(gfx, slot.iconUrl(), x, messageY, size, opacity);
        }
    }

    private static void paintSingle(GuiGraphicsExtractor gfx, String url, int x, int y, int size, float opacity) {
        ImageEntry entry = ImageLoader.getOrLoad(url);
        switch (entry.getState()) {
            case FAILED -> {
                return;
            }
            case LOADING -> gfx.fill(x, y, x + size, y + size, argb(opacity * 0.85f, 28, 28, 32));
            case LOADED -> {
                Identifier textureId = entry.isAnimated() ? entry.textureIdAtMillis(Util.getMillis())
                        : entry.getTextureId();
                if (textureId == null) {
                    return;
                }
                gfx.blit(
                        RenderPipelines.GUI_TEXTURED,
                        textureId,
                        x, y,
                        0.0f, 0.0f,
                        size, size,
                        entry.getTextureWidth(), entry.getTextureHeight(),
                        entry.getTextureWidth(), entry.getTextureHeight(),
                        ARGB.white(opacity));
            }
        }
    }

    private static int argb(float opacity, int r, int g, int b) {
        int a = Math.clamp(Math.round(opacity * 255.0f), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String extractPlain(net.minecraft.util.FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }
}
