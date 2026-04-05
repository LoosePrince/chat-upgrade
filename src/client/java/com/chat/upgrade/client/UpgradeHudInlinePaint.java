package com.chat.upgrade.client;

import com.chat.upgrade.client.mixininterface.ImageAttachable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

/** Draws URL preview tiles in the chat HUD using the scoped {@link GuiGraphicsExtractor} from {@link ChatUpgradeRenderScope}. */
public final class UpgradeHudInlinePaint {
    private UpgradeHudInlinePaint() {}

    public static void paintLinePreview(GuiMessage.Line line, int messageY, float opacity) {
        if (!(((Object) line) instanceof ImageAttachable attachable)) return;

        String resourceUrl = attachable.chatupgrade$getImageUrl();
        if (resourceUrl == null) return;

        GuiGraphicsExtractor gfx = ChatUpgradeRenderScope.current();
        if (gfx == null) return;

        ImageEntry entry = ImageLoader.getOrLoad(resourceUrl);

        switch (entry.getState()) {
            case FAILED -> {}
            case LOADING -> paintLoadingStrip(gfx, entry, messageY, opacity);
            case LOADED -> paintDecodedBlit(gfx, entry, messageY, opacity);
        }
    }

    private static void paintDecodedBlit(GuiGraphicsExtractor gfx, ImageEntry entry, int messageY, float opacity) {
        Identifier textureId = entry.getTextureId();
        if (textureId == null) return;

        int drawW = entry.getWidth();
        int drawH = entry.getHeight();
        int texW = entry.getTextureWidth();
        int texH = entry.getTextureHeight();

        if (drawW <= 0 || drawH <= 0 || texW <= 0 || texH <= 0) return;

        if (drawH > ImageLoader.PREVIEW_HEIGHT) drawH = ImageLoader.PREVIEW_HEIGHT;

        int color = ARGB.white(opacity);
        gfx.blit(
                RenderPipelines.GUI_TEXTURED,
                textureId,
                0, messageY,
                0.0f, 0.0f,
                drawW, drawH,
                texW, texH,
                texW, texH,
                color
        );
    }

    private static int argb(float opacity, int r, int g, int b) {
        int a = Math.clamp(Math.round(opacity * 255.0f), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void paintLoadingStrip(GuiGraphicsExtractor gfx, ImageEntry entry, int messageY, float opacity) {
        int s = ImageLoader.PREVIEW_HEIGHT;
        int x0 = 0;
        int y0 = messageY;
        int x1 = x0 + s;
        int y1 = y0 + s;

        gfx.fill(x0, y0, x1, y1, argb(opacity * 0.85f, 28, 28, 32));

        long t = Util.getMillis();
        int sweepW = Math.min(48, s - 16);
        int travel = Math.max(1, s - 16 - sweepW);
        int sweepX = x0 + 8 + (int) ((t / 35L) % travel);

        gfx.fill(sweepX, y1 - 7, sweepX + sweepW, y1 - 3, argb(opacity, 100, 180, 255));

        Font font = Minecraft.getInstance().font;
        String label = entry.getLoadPhase() == ImageEntry.LoadPhase.DECODE ? "处理中…" : "下载中…";
        int textColor = argb(opacity, 200, 200, 210);
        gfx.centeredText(font, label, x0 + s / 2, y0 + s / 2 - font.lineHeight / 2, textColor);
    }
}
