package com.chat.upgrade.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    private static final int GAP = 8;
    private static final int TOP_H = 24;
    private static final int CONTROL_H = 20;
    private static final int BTN_W = 44;

    private final String url;
    private final @Nullable String nameHint;
    private float scale = 1.0f;
    private float rotationDeg = 0.0f;
    private double panX = 0.0;
    private double panY = 0.0;

    public ImagePreviewScreen(String url, @Nullable String nameHint) {
        super(Component.literal("图片预览"));
        this.url = url;
        this.nameHint = nameHint;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        ImageEntry entry = ImageLoader.getOrLoad(url);
        int panelLeft = GAP;
        int panelRight = width - GAP;
        String name = AudioUiLayout.shortName(nameHint, url);
        String size = ChatUpgradeFormatters.formatBytes(entry.getFetchedByteLength());
        String link = url.length() > 56 ? url.substring(0, 53) + "..." : url;
        String header = "名称: " + name + "  |  大小: " + size + "  |  链接: " + link;
        guiGraphics.text(font, header, panelLeft, GAP + 6, 0xFFE7ECF4, false);
        guiGraphics.fill(panelLeft, GAP + TOP_H - 1, panelRight, GAP + TOP_H, 0xFF3A4456);

        int imageTop = GAP + TOP_H;
        int controlTop = height - GAP - CONTROL_H;
        int imageBottom = Math.max(imageTop + 16, controlTop - GAP);
        int boxW = panelRight - panelLeft;
        int boxH = imageBottom - imageTop;
        guiGraphics.fill(panelLeft, imageTop, panelRight, imageBottom, 0xFF141A22);
        guiGraphics.outline(panelLeft, imageTop, boxW, boxH, 0xFF344055);

        if (entry.getState() != ImageEntry.State.LOADED) {
            guiGraphics.centeredText(font, "图片加载中...", width / 2, imageTop + boxH / 2 - 4, 0xFFE6E6E6);
            renderControls(guiGraphics, panelLeft, panelRight, controlTop);
            return;
        }

        @Nullable
        Identifier textureId = entry.isAnimated() ? entry.textureIdAtMillis(Util.getMillis()) : entry.getTextureId();
        if (textureId == null) {
            guiGraphics.centeredText(font, "图片纹理不可用", width / 2, imageTop + boxH / 2 - 4, 0xFFFF8080);
            renderControls(guiGraphics, panelLeft, panelRight, controlTop);
            return;
        }

        int texW = entry.getTextureWidth();
        int texH = entry.getTextureHeight();
        if (texW <= 0 || texH <= 0) {
            guiGraphics.centeredText(font, "图片尺寸异常", width / 2, imageTop + boxH / 2 - 4, 0xFFFF8080);
            renderControls(guiGraphics, panelLeft, panelRight, controlTop);
            return;
        }

        float baseScale = fitScale(entry.getRawPixelWidth(), entry.getRawPixelHeight(), boxW - 8.0f, boxH - 8.0f);
        float sx = baseScale * scale;
        float sy = baseScale * scale;
        float drawW = texW;
        float drawH = texH;

        float cx = (float) width / 2.0f + (float) panX;
        float cy = (float) (imageTop + boxH / 2) + (float) panY;
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
        renderControls(guiGraphics, panelLeft, panelRight, controlTop);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        int panelLeft = GAP;
        int controlTop = height - GAP - CONTROL_H;
        int y1 = controlTop + CONTROL_H;
        int x = panelLeft;
        if (inside(event.x(), event.y(), x, controlTop, x + BTN_W, y1)) {
            zoomBy(ZOOM_STEP);
            return true;
        }
        x += BTN_W + 6;
        if (inside(event.x(), event.y(), x, controlTop, x + BTN_W, y1)) {
            zoomBy(1.0f / ZOOM_STEP);
            return true;
        }
        x += BTN_W + 6;
        if (inside(event.x(), event.y(), x, controlTop, x + BTN_W, y1)) {
            rotationDeg += 90.0f;
            return true;
        }
        x += BTN_W + 6;
        if (inside(event.x(), event.y(), x, controlTop, x + BTN_W, y1)) {
            resetTransform();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void open(String url, @Nullable String nameHint) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        ImageLoader.getOrLoad(url);
        mc.setScreen(new ImagePreviewScreen(url, nameHint));
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

    private void renderControls(GuiGraphicsExtractor guiGraphics, int panelLeft, int panelRight, int controlTop) {
        guiGraphics.fill(panelLeft, controlTop, panelRight, controlTop + CONTROL_H, 0xFF1A212C);
        guiGraphics.outline(panelLeft, controlTop, panelRight - panelLeft, CONTROL_H, 0xFF3A4456);
        int x = panelLeft;
        x = drawActionButton(guiGraphics, x, controlTop, "放大");
        x = drawActionButton(guiGraphics, x, controlTop, "缩小");
        x = drawActionButton(guiGraphics, x, controlTop, "旋转");
        x = drawActionButton(guiGraphics, x, controlTop, "重置");
        String right = "缩放 " + Math.round(scale * 100.0f) + "%  |  拖拽移动  |  滚轮缩放";
        guiGraphics.text(font, right, x + 6, controlTop + 6, 0xFFCAD2DD, false);
    }

    private int drawActionButton(GuiGraphicsExtractor guiGraphics, int x0, int y0, String text) {
        int x1 = x0 + BTN_W;
        guiGraphics.fill(x0, y0, x1, y0 + CONTROL_H, 0xFF2B3646);
        guiGraphics.centeredText(font, text, (x0 + x1) / 2, y0 + 6, 0xFFE7ECF4);
        return x1 + 6;
    }

    private static float fitScale(int rawW, int rawH, float maxW, float maxH) {
        int w = Math.max(1, rawW);
        int h = Math.max(1, rawH);
        float sw = maxW / (float) w;
        float sh = maxH / (float) h;
        float fit = Math.min(sw, sh);
        return Math.max(0.01f, fit);
    }

    private static boolean inside(double x, double y, int x0, int y0, int x1, int y1) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }
}
