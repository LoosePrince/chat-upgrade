package com.chat.upgrade.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

public final class ImagePreviewScreen extends Screen {
    private static final float ZOOM_STEP = 1.15f;
    private static final float MIN_ZOOM = 0.1f;
    private static final float MAX_ZOOM = 20.0f;
    private static final int GAP = 6;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_W = 56;

    private final String url;
    private float scale = 1.0f;
    private float rotationDeg = 0.0f;
    private double panX = 0.0;
    private double panY = 0.0;

    public ImagePreviewScreen(String url) {
        super(Component.literal("图片预览"));
        this.url = url;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("放大"), b -> zoomBy(ZOOM_STEP))
                .bounds(GAP, GAP, BUTTON_W, BUTTON_H)
                .build());
        addRenderableWidget(Button.builder(Component.literal("缩小"), b -> zoomBy(1.0f / ZOOM_STEP))
                .bounds(GAP + (BUTTON_W + GAP), GAP, BUTTON_W, BUTTON_H)
                .build());
        addRenderableWidget(Button.builder(Component.literal("旋转"), b -> rotationDeg += 90.0f)
                .bounds(GAP + (BUTTON_W + GAP) * 2, GAP, BUTTON_W, BUTTON_H)
                .build());
        addRenderableWidget(Button.builder(Component.literal("重置"), b -> resetTransform())
                .bounds(GAP + (BUTTON_W + GAP) * 3, GAP, BUTTON_W, BUTTON_H)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        ImageEntry entry = ImageLoader.getOrLoad(url);
        if (entry.getState() != ImageEntry.State.LOADED) {
            guiGraphics.centeredText(font, "图片加载中...", width / 2, height / 2, 0xFFE6E6E6);
            return;
        }

        @Nullable
        Identifier textureId = entry.isAnimated() ? entry.textureIdAtMillis(Util.getMillis()) : entry.getTextureId();
        if (textureId == null) {
            guiGraphics.centeredText(font, "图片纹理不可用", width / 2, height / 2, 0xFFFF8080);
            return;
        }

        int texW = entry.getTextureWidth();
        int texH = entry.getTextureHeight();
        if (texW <= 0 || texH <= 0) {
            guiGraphics.centeredText(font, "图片尺寸异常", width / 2, height / 2, 0xFFFF8080);
            return;
        }

        float baseScale = fitScale(entry.getRawPixelWidth(), entry.getRawPixelHeight(), width - 24.0f, height - 72.0f);
        float sx = baseScale * scale;
        float sy = baseScale * scale;
        float drawW = texW;
        float drawH = texH;

        float cx = (float) width / 2.0f + (float) panX;
        float cy = (float) height / 2.0f + (float) panY + 10.0f;
        var pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(rotationDeg));
        pose.scale(sx, sy);

        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                textureId,
                (int) (-drawW / 2.0f),
                (int) (-drawH / 2.0f),
                0.0f,
                0.0f,
                texW,
                texH,
                texW,
                texH,
                texW,
                texH,
                ARGB.white(1.0f));
        pose.popMatrix();

        String info = "滚轮缩放 | 按住左键拖动 | ESC关闭";
        guiGraphics.text(font, info, GAP, height - 12 - GAP, 0xFFC8CDD4, false);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, 0xE0101010);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0) {
            panX += dragX;
            panY += dragY;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0.0) {
            zoomBy(ZOOM_STEP);
            return true;
        }
        if (scrollY < 0.0) {
            zoomBy(1.0f / ZOOM_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void open(String url) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        ImageLoader.getOrLoad(url);
        mc.setScreen(new ImagePreviewScreen(url));
    }

    private void resetTransform() {
        scale = 1.0f;
        rotationDeg = 0.0f;
        panX = 0.0;
        panY = 0.0;
    }

    private void zoomBy(float factor) {
        scale = Math.clamp(scale * factor, MIN_ZOOM, MAX_ZOOM);
    }

    private static float fitScale(int rawW, int rawH, float maxW, float maxH) {
        int w = Math.max(1, rawW);
        int h = Math.max(1, rawH);
        float sw = maxW / (float) w;
        float sh = maxH / (float) h;
        float fit = Math.min(sw, sh);
        return Math.max(0.01f, fit);
    }
}
