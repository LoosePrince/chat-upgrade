package com.chat.upgrade.client.ui.screen;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ChatUpgradeFormatters;
import com.chat.upgrade.client.MinecraftGuiBridge;
import com.chat.upgrade.client.media.image.ImageEntry;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceRuntime;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.animation.UiMotion;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.chat.viewport.RichChatMediaLayout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

public final class ImagePreviewScreen extends Screen {
    private static final float ZOOM_STEP = 1.15f;
    private static final float MIN_ZOOM = 0.1f;
    private static final float MAX_ZOOM = 20.0f;

    private final String url;
    private final @Nullable String nameHint;
    private final @Nullable Screen parent;
    private float scale = 1.0f;
    private float rotationDeg = 0.0f;
    private double panX = 0.0;
    private double panY = 0.0;

    public ImagePreviewScreen(String url, @Nullable String nameHint) {
        super(Component.translatable("chatupgrade.screen.image_preview.title"));
        this.url = url;
        this.nameHint = nameHint;
        this.parent = MinecraftGuiBridge.currentScreen(Minecraft.getInstance());
        UiMotion.begin(UiMotion.IMAGE_PREVIEW);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        ImageEntry entry = ImageLoader.getOrLoad(url);
        ChatAppearanceSnapshot appearance = ChatAppearanceRuntime.current();
        ChatAppearanceSnapshot.Media tokens = appearance.media();
        int cornerRadius = Math.max(2, appearance.cornerRadius());
        MediaPreviewLayout.Frame frame = MediaPreviewLayout.frame(width, height);
        String name = RichChatMediaLayout.displayName(nameHint, url);
        var motionPose = guiGraphics.pose();
        motionPose.pushMatrix();
        motionPose.translate(0, UiMotion.enterFromBottom(UiMotion.IMAGE_PREVIEW, 16));
        paintChrome(guiGraphics, frame, name, entry, tokens, cornerRadius);

        if (entry.getState() != ImageEntry.State.LOADED) {
            MediaPreviewChrome.paintCenteredState(
                    guiGraphics,
                    font,
                    frame.media(),
                    I18n.get("chatupgrade.screen.image_preview.loading"),
                    tokens.muted());
            renderControls(guiGraphics, frame, tokens, cornerRadius);
            motionPose.popMatrix();
            return;
        }

        @Nullable Identifier textureId = entry.isAnimated()
                ? entry.textureIdAtMillis(Util.getMillis())
                : entry.getFullTextureId();
        if (!entry.isAnimated() && textureId == null) {
            textureId = entry.getTextureId();
        }
        if (textureId == null) {
            MediaPreviewChrome.paintCenteredState(
                    guiGraphics,
                    font,
                    frame.media(),
                    I18n.get("chatupgrade.screen.image_preview.texture_unavailable"),
                    tokens.failureText());
            renderControls(guiGraphics, frame, tokens, cornerRadius);
            motionPose.popMatrix();
            return;
        }

        int texW = entry.isAnimated() ? entry.getTextureWidth() : entry.getFullTextureWidth();
        int texH = entry.isAnimated() ? entry.getTextureHeight() : entry.getFullTextureHeight();
        if (!entry.isAnimated() && (texW <= 0 || texH <= 0)) {
            texW = entry.getTextureWidth();
            texH = entry.getTextureHeight();
        }
        if (texW <= 0 || texH <= 0) {
            MediaPreviewChrome.paintCenteredState(
                    guiGraphics,
                    font,
                    frame.media(),
                    I18n.get("chatupgrade.screen.image_preview.invalid_size"),
                    tokens.failureText());
            renderControls(guiGraphics, frame, tokens, cornerRadius);
            motionPose.popMatrix();
            return;
        }

        float baseScale = fitScale(
                entry.getRawPixelWidth(),
                entry.getRawPixelHeight(),
                frame.media().width() - 12.0f,
                frame.media().height() - 12.0f);
        float renderScale = baseScale * scale;
        float centerX = frame.media().left() + frame.media().width() / 2.0f + (float) panX;
        float centerY = frame.media().top() + frame.media().height() / 2.0f + (float) panY;
        guiGraphics.enableScissor(
                frame.media().left(),
                frame.media().top(),
                frame.media().right(),
                frame.media().bottom());
        try {
            var pose = guiGraphics.pose();
            pose.pushMatrix();
            pose.translate(centerX, centerY);
            pose.rotate((float) Math.toRadians(rotationDeg));
            pose.scale(renderScale, renderScale);
            guiGraphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    textureId,
                    -texW / 2,
                    -texH / 2,
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
        } finally {
            guiGraphics.disableScissor();
        }
        renderControls(guiGraphics, frame, tokens, cornerRadius);
        motionPose.popMatrix();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, ChatAppearanceRuntime.current().media().scrim());
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
        MediaPreviewLayout.Frame frame = MediaPreviewLayout.frame(width, height);
        MediaPreviewLayout.ImageControls controls = MediaPreviewLayout.imageControls(frame);
        if (contains(frame.close(), event.x(), event.y())) {
            onClose();
            return true;
        }
        if (contains(controls.zoomIn(), event.x(), event.y())) {
            zoomBy(ZOOM_STEP);
            return true;
        }
        if (contains(controls.zoomOut(), event.x(), event.y())) {
            zoomBy(1.0f / ZOOM_STEP);
            return true;
        }
        if (contains(controls.rotate(), event.x(), event.y())) {
            rotationDeg += 90.0f;
            return true;
        }
        if (contains(controls.reset(), event.x(), event.y())) {
            resetTransform();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        UiMotion.end(UiMotion.IMAGE_PREVIEW);
        MinecraftGuiBridge.setScreen(Minecraft.getInstance(), parent);
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
        MinecraftGuiBridge.setScreen(mc, new ImagePreviewScreen(url, nameHint));
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

    private void paintChrome(
            GuiGraphicsExtractor guiGraphics,
            MediaPreviewLayout.Frame frame,
            String name,
            ImageEntry entry,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        String metadata = I18n.get(
                "chatupgrade.screen.preview.metadata",
                ChatUpgradeFormatters.formatBytes(entry.getFetchedByteLength()),
                url);
        MediaPreviewChrome.paintFrame(guiGraphics, font, frame, name, metadata, tokens, cornerRadius);
    }

    private void renderControls(
            GuiGraphicsExtractor guiGraphics,
            MediaPreviewLayout.Frame frame,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        MediaPreviewLayout.ImageControls controls = MediaPreviewLayout.imageControls(frame);
        MediaPreviewChrome.paintActionButton(guiGraphics, font, controls.zoomIn(),
                I18n.get("chatupgrade.screen.image_preview.button.zoom_in"), tokens, cornerRadius);
        MediaPreviewChrome.paintActionButton(guiGraphics, font, controls.zoomOut(),
                I18n.get("chatupgrade.screen.image_preview.button.zoom_out"), tokens, cornerRadius);
        MediaPreviewChrome.paintActionButton(guiGraphics, font, controls.rotate(),
                I18n.get("chatupgrade.screen.image_preview.button.rotate"), tokens, cornerRadius);
        MediaPreviewChrome.paintActionButton(guiGraphics, font, controls.reset(),
                I18n.get("chatupgrade.screen.image_preview.button.reset"), tokens, cornerRadius);
        String hint = I18n.get("chatupgrade.screen.image_preview.control_hint", Math.round(scale * 100.0f));
        String visibleHint = font.plainSubstrByWidth(hint, Math.max(1, controls.hint().width()));
        guiGraphics.text(
                font,
                visibleHint,
                controls.hint().left(),
                controls.hint().top() + 8,
                tokens.muted(),
                false);
    }

    private static float fitScale(int rawW, int rawH, float maxW, float maxH) {
        int w = Math.max(1, rawW);
        int h = Math.max(1, rawH);
        float sw = maxW / (float) w;
        float sh = maxH / (float) h;
        float fit = Math.min(sw, sh);
        return Math.max(0.01f, fit);
    }

    private static boolean contains(RichChatBounds bounds, double x, double y) {
        return bounds != null
                && x >= bounds.left()
                && x < bounds.right()
                && y >= bounds.top()
                && y < bounds.bottom();
    }
}
