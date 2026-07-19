package com.chat.upgrade.client.ui.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Supersampled atlas for rounded corners, controls, and compact toolbar icons. */
public final class UiTextureAtlas {
    public enum Icon {
        GEAR,
        ATTACHMENT,
        EMOJI,
        SEND,
        CLOSE,
        PLAY,
        PAUSE,
        LOOP,
        OPEN,
        POPOUT,
        CHECK,
        SLIDER_KNOB
    }

    private static final int TILE_LOGICAL_SIZE = 32;
    private static final int ICON_LOGICAL_SIZE = 16;
    private static final int COLUMN_COUNT = 8;
    private static final int MAX_PIXEL_SCALE = 8;
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private static Atlas active;

    private UiTextureAtlas() {
    }

    public static synchronized void invalidate() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && active != null) {
            minecraft.getTextureManager().release(active.texture());
        }
        active = null;
    }

    public static boolean paintRounded(
            GuiGraphicsExtractor graphics,
            RichChatBounds bounds,
            int radius,
            int color) {
        if (graphics == null || bounds == null || bounds.width() <= 0 || bounds.height() <= 0
                || (color >>> 24) == 0) {
            return true;
        }
        int safeRadius = Math.clamp(radius, 0, Math.min(bounds.width(), bounds.height()) / 2);
        if (safeRadius <= 0) {
            return false;
        }
        Atlas atlas = ensure();
        if (atlas == null) {
            return false;
        }
        int radiusIndex = safeRadius - 1;
        int sourceX = tileX(radiusIndex, atlas.tileSize());
        int sourceY = tileY(radiusIndex, atlas.tileSize());
        int sourceRadius = safeRadius * atlas.pixelScale();
        int sourceDiameter = sourceRadius * 2;

        int left = bounds.left();
        int top = bounds.top();
        int right = bounds.right();
        int bottom = bounds.bottom();
        graphics.fill(left + safeRadius, top, right - safeRadius, bottom, color);
        graphics.fill(left, top + safeRadius, left + safeRadius, bottom - safeRadius, color);
        graphics.fill(right - safeRadius, top + safeRadius, right, bottom - safeRadius, color);

        blitMask(graphics, atlas, left, top, safeRadius, sourceX, sourceY, sourceRadius, color);
        blitMask(
                graphics,
                atlas,
                right - safeRadius,
                top,
                safeRadius,
                sourceX + sourceRadius,
                sourceY,
                sourceRadius,
                color);
        blitMask(
                graphics,
                atlas,
                left,
                bottom - safeRadius,
                safeRadius,
                sourceX,
                sourceY + sourceRadius,
                sourceRadius,
                color);
        blitMask(
                graphics,
                atlas,
                right - safeRadius,
                bottom - safeRadius,
                safeRadius,
                sourceX + sourceRadius,
                sourceY + sourceRadius,
                sourceRadius,
                color);
        return sourceDiameter > 0;
    }

    public static void drawIcon(
            GuiGraphicsExtractor graphics,
            Icon icon,
            RichChatBounds bounds,
            int color) {
        if (graphics == null || icon == null || bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        Atlas atlas = ensure();
        if (atlas == null) {
            return;
        }
        int entry = 16 + icon.ordinal();
        int sourceSize = ICON_LOGICAL_SIZE * atlas.pixelScale();
        int sourceOffset = (TILE_LOGICAL_SIZE - ICON_LOGICAL_SIZE) * atlas.pixelScale() / 2;
        int sourceX = tileX(entry, atlas.tileSize()) + sourceOffset;
        int sourceY = tileY(entry, atlas.tileSize()) + sourceOffset;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                atlas.texture(),
                bounds.left(),
                bounds.top(),
                sourceX,
                sourceY,
                bounds.width(),
                bounds.height(),
                sourceSize,
                sourceSize,
                atlas.width(),
                atlas.height(),
                color);
    }

    private static synchronized Atlas ensure() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return null;
        }
        int pixelScale = pixelScale(minecraft);
        if (active != null && active.pixelScale() == pixelScale) {
            return active;
        }
        invalidate();
        try {
            BufferedImage image = buildAtlas(pixelScale);
            NativeImage nativeImage = toNativeImage(image);
            int generation = GENERATION.incrementAndGet();
            Identifier textureId = Identifier.fromNamespaceAndPath(
                    ChatUpgrade.MOD_ID,
                    "ui/atlas_" + generation);
            DynamicTexture texture = new DynamicTexture(() -> "chat-upgrade ui atlas " + generation, nativeImage);
            minecraft.getTextureManager().register(textureId, texture);
            active = new Atlas(
                    textureId,
                    pixelScale,
                    TILE_LOGICAL_SIZE * pixelScale,
                    image.getWidth(),
                    image.getHeight());
            return active;
        } catch (RuntimeException exception) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to build UI texture atlas: {}", exception.getMessage());
            return null;
        }
    }

    private static int pixelScale(Minecraft minecraft) {
        double densityX = ImageLoader.previewTexelsPerGuiPixelX(minecraft.getWindow());
        double densityY = ImageLoader.previewTexelsPerGuiPixelY(minecraft.getWindow());
        return Math.clamp((int) Math.ceil(Math.max(densityX, densityY) * 2.0D), 2, MAX_PIXEL_SCALE);
    }

    private static BufferedImage buildAtlas(int pixelScale) {
        int entryCount = 16 + Icon.values().length;
        int rows = (entryCount + COLUMN_COUNT - 1) / COLUMN_COUNT;
        int tileSize = TILE_LOGICAL_SIZE * pixelScale;
        BufferedImage atlas = new BufferedImage(
                COLUMN_COUNT * tileSize,
                rows * tileSize,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.setColor(Color.WHITE);
            for (int radius = 1; radius <= 16; radius++) {
                int entry = radius - 1;
                int x = tileX(entry, tileSize);
                int y = tileY(entry, tileSize);
                int diameter = radius * 2 * pixelScale;
                graphics.fill(new Ellipse2D.Float(x, y, diameter, diameter));
            }
            for (Icon icon : Icon.values()) {
                int entry = 16 + icon.ordinal();
                int x = tileX(entry, tileSize) + (TILE_LOGICAL_SIZE - ICON_LOGICAL_SIZE) * pixelScale / 2;
                int y = tileY(entry, tileSize) + (TILE_LOGICAL_SIZE - ICON_LOGICAL_SIZE) * pixelScale / 2;
                paintIcon(graphics, icon, x, y, pixelScale);
            }
        } finally {
            graphics.dispose();
        }
        return atlas;
    }

    private static void paintIcon(Graphics2D graphics, Icon icon, int x, int y, int scale) {
        Graphics2D iconGraphics = (Graphics2D) graphics.create();
        try {
            iconGraphics.translate(x, y);
            iconGraphics.scale(scale, scale);
            iconGraphics.setColor(Color.WHITE);
            iconGraphics.setStroke(new BasicStroke(1.5F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (icon) {
                case GEAR -> paintGear(iconGraphics);
                case ATTACHMENT -> paintAttachment(iconGraphics);
                case EMOJI -> paintEmoji(iconGraphics);
                case SEND -> paintSend(iconGraphics);
                case CLOSE -> paintClose(iconGraphics);
                case PLAY -> paintPlay(iconGraphics);
                case PAUSE -> paintPause(iconGraphics);
                case LOOP -> paintLoop(iconGraphics);
                case OPEN -> paintOpen(iconGraphics);
                case POPOUT -> paintPopout(iconGraphics);
                case CHECK -> paintCheck(iconGraphics);
                case SLIDER_KNOB -> iconGraphics.fill(new Ellipse2D.Float(4.0F, 4.0F, 8.0F, 8.0F));
            }
        } finally {
            iconGraphics.dispose();
        }
    }

    private static void paintGear(Graphics2D graphics) {
        graphics.draw(new Ellipse2D.Float(4.0F, 4.0F, 8.0F, 8.0F));
        graphics.draw(new Ellipse2D.Float(6.5F, 6.5F, 3.0F, 3.0F));
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.PI / 4.0D;
            double innerX = 8.0D + Math.cos(angle) * 5.0D;
            double innerY = 8.0D + Math.sin(angle) * 5.0D;
            double outerX = 8.0D + Math.cos(angle) * 7.0D;
            double outerY = 8.0D + Math.sin(angle) * 7.0D;
            graphics.draw(new Line2D.Double(innerX, innerY, outerX, outerY));
        }
    }

    private static void paintAttachment(Graphics2D graphics) {
        Path2D path = new Path2D.Float();
        path.moveTo(5.0D, 8.0D);
        path.lineTo(10.5D, 2.5D);
        path.curveTo(14.5D, -1.0D, 18.0D, 3.0D, 14.0D, 7.0D);
        path.lineTo(7.0D, 14.0D);
        path.curveTo(3.0D, 18.0D, -1.0D, 13.0D, 3.0D, 9.0D);
        path.lineTo(9.5D, 2.5D);
        graphics.draw(path);
    }

    private static void paintEmoji(Graphics2D graphics) {
        graphics.draw(new Ellipse2D.Float(1.5F, 1.5F, 13.0F, 13.0F));
        graphics.fill(new Ellipse2D.Float(5.0F, 5.0F, 1.5F, 1.5F));
        graphics.fill(new Ellipse2D.Float(9.5F, 5.0F, 1.5F, 1.5F));
        graphics.draw(new Arc2D.Float(4.0F, 5.5F, 8.0F, 6.0F, 200.0F, 140.0F, Arc2D.OPEN));
    }

    private static void paintSend(Graphics2D graphics) {
        Path2D path = new Path2D.Float();
        path.moveTo(1.5D, 2.0D);
        path.lineTo(15.0D, 8.0D);
        path.lineTo(1.5D, 14.0D);
        path.lineTo(4.5D, 8.0D);
        path.closePath();
        graphics.draw(path);
        graphics.draw(new Line2D.Float(4.5F, 8.0F, 14.0F, 8.0F));
    }

    private static void paintClose(Graphics2D graphics) {
        graphics.draw(new Line2D.Float(3.0F, 3.0F, 13.0F, 13.0F));
        graphics.draw(new Line2D.Float(13.0F, 3.0F, 3.0F, 13.0F));
    }

    private static void paintPlay(Graphics2D graphics) {
        Path2D path = new Path2D.Float();
        path.moveTo(4.0D, 2.0D);
        path.lineTo(13.0D, 8.0D);
        path.lineTo(4.0D, 14.0D);
        path.closePath();
        graphics.fill(path);
    }

    private static void paintPause(Graphics2D graphics) {
        graphics.fillRect(4, 2, 3, 12);
        graphics.fillRect(10, 2, 3, 12);
    }

    private static void paintLoop(Graphics2D graphics) {
        graphics.draw(new Arc2D.Float(2.0F, 3.0F, 12.0F, 9.0F, 35.0F, 260.0F, Arc2D.OPEN));
        Path2D arrow = new Path2D.Float();
        arrow.moveTo(12.0D, 1.5D);
        arrow.lineTo(15.0D, 4.0D);
        arrow.lineTo(11.0D, 5.0D);
        arrow.closePath();
        graphics.fill(arrow);
    }

    private static void paintOpen(Graphics2D graphics) {
        graphics.drawRect(2, 4, 10, 10);
        graphics.draw(new Line2D.Float(7.0F, 9.0F, 14.0F, 2.0F));
        graphics.draw(new Line2D.Float(9.0F, 2.0F, 14.0F, 2.0F));
        graphics.draw(new Line2D.Float(14.0F, 2.0F, 14.0F, 7.0F));
    }

    private static void paintPopout(Graphics2D graphics) {
        graphics.drawRect(2, 5, 9, 8);
        graphics.drawRect(6, 2, 8, 8);
    }

    private static void paintCheck(Graphics2D graphics) {
        Path2D path = new Path2D.Float();
        path.moveTo(2.5D, 8.5D);
        path.lineTo(6.5D, 12.0D);
        path.lineTo(13.5D, 3.5D);
        graphics.draw(path);
    }

    private static void blitMask(
            GuiGraphicsExtractor graphics,
            Atlas atlas,
            int targetX,
            int targetY,
            int targetSize,
            int sourceX,
            int sourceY,
            int sourceSize,
            int color) {
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                atlas.texture(),
                targetX,
                targetY,
                sourceX,
                sourceY,
                targetSize,
                targetSize,
                sourceSize,
                sourceSize,
                atlas.width(),
                atlas.height(),
                color);
    }

    private static int tileX(int entry, int tileSize) {
        return entry % COLUMN_COUNT * tileSize;
    }

    private static int tileY(int entry, int tileSize) {
        return entry / COLUMN_COUNT * tileSize;
    }

    private static NativeImage toNativeImage(BufferedImage source) {
        NativeImage target = new NativeImage(
                NativeImage.Format.RGBA,
                source.getWidth(),
                source.getHeight(),
                false);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = argb >>> 24;
                int red = argb >>> 16 & 0xFF;
                int green = argb >>> 8 & 0xFF;
                int blue = argb & 0xFF;
                target.setPixelABGR(x, y, alpha << 24 | blue << 16 | green << 8 | red);
            }
        }
        return target;
    }

    private record Atlas(
            Identifier texture,
            int pixelScale,
            int tileSize,
            int width,
            int height) {
    }
}