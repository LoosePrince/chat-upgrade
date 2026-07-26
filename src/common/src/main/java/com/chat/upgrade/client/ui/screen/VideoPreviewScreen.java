package com.chat.upgrade.client.ui.screen;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ChatUpgradeFormatters;
import com.chat.upgrade.client.MinecraftGuiBridge;
import com.chat.upgrade.client.media.video.VideoEntry;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.media.video.VideoPlayerService;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceRuntime;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.animation.UiMotion;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.chat.viewport.RichChatMediaLayout;
import com.chat.upgrade.client.ui.render.UiTextureAtlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

public final class VideoPreviewScreen extends Screen {

    private final String url;
    private final @Nullable String nameHint;
    private final @Nullable Screen parent;

    public VideoPreviewScreen(String url, @Nullable String nameHint) {
        super(Component.translatable("chatupgrade.screen.video_preview.title"));
        this.url = url;
        this.nameHint = nameHint;
        this.parent = MinecraftGuiBridge.currentScreen(Minecraft.getInstance());
        UiMotion.begin(UiMotion.VIDEO_PREVIEW);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, width, height, ChatAppearanceRuntime.current().media().scrim());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        VideoEntry entry = VideoLoader.getOrLoad(url);
        ChatAppearanceSnapshot appearance = ChatAppearanceRuntime.current();
        ChatAppearanceSnapshot.Media tokens = appearance.media();
        int cornerRadius = Math.max(2, appearance.cornerRadius());
        MediaPreviewLayout.Frame frame = MediaPreviewLayout.frame(width, height);
        String name = RichChatMediaLayout.displayName(nameHint, url);
        String metadata = I18n.get(
                "chatupgrade.screen.preview.metadata",
                ChatUpgradeFormatters.formatBytes(entry.getFetchedByteLength()),
                url);
        var motionPose = guiGraphics.pose();
        motionPose.pushMatrix();
        motionPose.translate(0, UiMotion.enterFromBottom(UiMotion.VIDEO_PREVIEW, 16));
        MediaPreviewChrome.paintFrame(guiGraphics, font, frame, name, metadata, tokens, cornerRadius);

        if (entry.getState() == VideoEntry.State.LOADING) {
            MediaPreviewChrome.paintCenteredState(
                    guiGraphics,
                    font,
                    frame.media(),
                    I18n.get("chatupgrade.screen.video_preview.loading"),
                    tokens.muted());
            renderControls(guiGraphics, frame, tokens, cornerRadius, entry);
            motionPose.popMatrix();
            return;
        }
        if (entry.getState() == VideoEntry.State.FAILED) {
            MediaPreviewChrome.paintCenteredState(
                    guiGraphics,
                    font,
                    frame.media(),
                    I18n.get("chatupgrade.screen.video_preview.failed"),
                    tokens.failureText());
            renderControls(guiGraphics, frame, tokens, cornerRadius, entry);
            motionPose.popMatrix();
            return;
        }

        @Nullable Identifier textureId = VideoPlayerService.textureIdAtMillis(url, Util.getMillis());
        if (textureId != null) {
            RichChatBounds videoBounds = MediaPreviewLayout.fitMedia(
                    frame.media(),
                    entry.getRawWidth(),
                    entry.getRawHeight());
            guiGraphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    textureId,
                    videoBounds.left(),
                    videoBounds.top(),
                    0.0f,
                    0.0f,
                    videoBounds.width(),
                    videoBounds.height(),
                    entry.getRawWidth() > 0 ? entry.getRawWidth() : videoBounds.width(),
                    entry.getRawHeight() > 0 ? entry.getRawHeight() : videoBounds.height(),
                    entry.getRawWidth() > 0 ? entry.getRawWidth() : videoBounds.width(),
                    entry.getRawHeight() > 0 ? entry.getRawHeight() : videoBounds.height(),
                    ARGB.white(1.0f));
        } else {
            MediaPreviewChrome.paintCenteredState(
                    guiGraphics,
                    font,
                    frame.media(),
                    I18n.get("chatupgrade.screen.video_preview.no_frame"),
                    tokens.failureText());
        }

        renderControls(guiGraphics, frame, tokens, cornerRadius, entry);
        motionPose.popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        VideoEntry entry = VideoLoader.getOrLoad(url);
        MediaPreviewLayout.Frame frame = MediaPreviewLayout.frame(width, height);
        MediaPreviewLayout.VideoControls controls = controls(frame, entry);
        if (contains(frame.close(), event.x(), event.y())) {
            onClose();
            return true;
        }
        if (contains(controls.play(), event.x(), event.y())) {
            VideoPlayerService.toggle(url);
            return true;
        }
        RichChatBounds progressHitBounds = new RichChatBounds(
                controls.progress().left(),
                controls.progress().top() - 4,
                controls.progress().right(),
                controls.progress().bottom() + 4);
        if (contains(progressHitBounds, event.x(), event.y())) {
            double ratio = Math.clamp(
                    (event.x() - controls.progress().left()) / Math.max(1.0D, controls.progress().width()),
                    0.0D,
                    1.0D);
            VideoPlayerService.seek(url, ratio);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        UiMotion.end(UiMotion.VIDEO_PREVIEW);
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
        VideoLoader.getOrLoad(url);
        MinecraftGuiBridge.setScreen(mc, new VideoPreviewScreen(url, nameHint));
    }

    private void renderControls(
            GuiGraphicsExtractor guiGraphics,
            MediaPreviewLayout.Frame frame,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius,
            VideoEntry entry) {
        long positionMs = Math.max(0L, VideoPlayerService.positionMs(url));
        long durationMs = durationMs(entry);
        MediaPreviewLayout.VideoControls controls = MediaPreviewLayout.videoControls(
                frame,
                font,
                positionMs,
                durationMs);
        MediaPreviewChrome.paintIconButton(
                guiGraphics,
                controls.play(),
                VideoPlayerService.isPlaying(url) ? UiTextureAtlas.Icon.PAUSE : UiTextureAtlas.Icon.PLAY,
                tokens,
                cornerRadius);
        guiGraphics.text(
                font,
                ChatUpgradeFormatters.formatMs(positionMs),
                controls.leftTime().left(),
                controls.leftTime().top(),
                tokens.muted(),
                false);
        guiGraphics.text(
                font,
                ChatUpgradeFormatters.formatMs(durationMs),
                controls.rightTime().left(),
                controls.rightTime().top(),
                tokens.muted(),
                false);
        MediaPreviewChrome.paintProgress(guiGraphics, controls.progress(), positionMs, durationMs, tokens);
    }

    private MediaPreviewLayout.VideoControls controls(MediaPreviewLayout.Frame frame, VideoEntry entry) {
        return MediaPreviewLayout.videoControls(
                frame,
                font,
                Math.max(0L, VideoPlayerService.positionMs(url)),
                durationMs(entry));
    }

    private long durationMs(VideoEntry entry) {
        long durationMs = VideoPlayerService.durationMs(url);
        if (durationMs <= 0L && entry != null) {
            durationMs = entry.getDurationMs();
        }
        return Math.max(0L, durationMs);
    }

    private static boolean contains(RichChatBounds bounds, double x, double y) {
        return bounds != null
                && x >= bounds.left()
                && x < bounds.right()
                && y >= bounds.top()
                && y < bounds.bottom();
    }
}
