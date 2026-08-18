package com.chat.upgrade.client.ui.settings;

import java.util.function.IntConsumer;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.MinecraftGuiBridge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A lightweight Minecraft-native HSV picker that works without AWT or Swing. */
public final class StandardColorPicker {
    private StandardColorPicker() {
    }

    public static void open(String title, int initialRgb, IntConsumer onSelected) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || onSelected == null) {
            return;
        }
        Screen parent = MinecraftGuiBridge.currentScreen(minecraft);
        MinecraftGuiBridge.setScreen(
                minecraft,
                new PickerScreen(parent, title, initialRgb, onSelected));
    }

    private static final class PickerScreen extends Screen {
        private static final int PANEL_WIDTH = 310;
        private static final int PANEL_HEIGHT = 260;
        private static final int SQUARE_SIZE = 160;
        private static final int HUE_WIDTH = 18;
        private static final int TEXT = 0xFFF2F2F2;
        private static final int MUTED = 0xFFB8B8B8;
        private static final int CONTROL = 0xFF505050;
        private static final int ACTIVE = 0xFF707070;

        private final @Nullable Screen parent;
        private final String title;
        private final IntConsumer onSelected;
        private float hue;
        private float saturation;
        private float value;

        private PickerScreen(
                @Nullable Screen parent,
                @Nullable String title,
                int initialRgb,
                IntConsumer onSelected) {
            super(Component.literal(title == null || title.isBlank() ? "颜色选择器" : title));
            this.parent = parent;
            this.title = title == null || title.isBlank() ? "颜色选择器" : title;
            this.onSelected = onSelected;
            float[] hsv = rgbToHsv(initialRgb & 0x00FFFFFF);
            hue = hsv[0];
            saturation = hsv[1];
            value = hsv[2];
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0x99000000);
        }

        @Override
        public void extractRenderState(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float partialTick) {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            int left = Math.max(8, (width - PANEL_WIDTH) / 2);
            int top = Math.max(8, (height - PANEL_HEIGHT) / 2);
            int squareLeft = left + 18;
            int squareTop = top + 48;
            int hueLeft = squareLeft + SQUARE_SIZE + 12;
            int[] selected = new int[] { hsvToRgb(hue, saturation, value) };

            graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF202020);
            graphics.fill(left, top, left + PANEL_WIDTH, top + 28, 0xFF303030);
            graphics.text(font, title, left + 12, top + 9, TEXT, false);
            graphics.text(font, String.format("#%06X", selected[0]), hueLeft + HUE_WIDTH + 14, squareTop + 12, MUTED, false);

            graphics.fill(squareLeft - 2, squareTop - 2, squareLeft + SQUARE_SIZE + 2, squareTop + SQUARE_SIZE + 2, 0xFF808080);
            paintColorSquare(graphics, squareLeft, squareTop);
            paintHueBar(graphics, hueLeft, squareTop);
            paintMarker(graphics, squareLeft, squareTop, saturation * SQUARE_SIZE, (1.0f - value) * SQUARE_SIZE);
            int hueMarkerY = squareTop + Math.round(hue * SQUARE_SIZE);
            graphics.fill(hueLeft - 3, hueMarkerY - 2, hueLeft + HUE_WIDTH + 3, hueMarkerY + 2, TEXT);

            int previewLeft = left + 18;
            int previewTop = squareTop + SQUARE_SIZE + 16;
            graphics.fill(previewLeft, previewTop, previewLeft + 48, previewTop + 24, 0xFF000000 | selected[0]);
            graphics.text(font, "点击色盘选择颜色", previewLeft + 60, previewTop + 8, MUTED, false);

            int cancelLeft = left + PANEL_WIDTH - 142;
            int buttonTop = top + PANEL_HEIGHT - 32;
            paintButton(graphics, "取消", cancelLeft, buttonTop, 60, 20, mouseX, mouseY, false);
            paintButton(graphics, "确定", cancelLeft + 70, buttonTop, 60, 20, mouseX, mouseY, true);
        }

        private void paintColorSquare(GuiGraphicsExtractor graphics, int left, int top) {
            for (int y = 0; y < SQUARE_SIZE; y += 2) {
                float currentValue = 1.0f - y / (float) (SQUARE_SIZE - 1);
                for (int x = 0; x < SQUARE_SIZE; x += 2) {
                    float currentSaturation = x / (float) (SQUARE_SIZE - 1);
                    int color = hsvToRgb(hue, currentSaturation, currentValue);
                    graphics.fill(left + x, top + y, left + Math.min(SQUARE_SIZE, x + 2),
                            top + Math.min(SQUARE_SIZE, y + 2), 0xFF000000 | color);
                }
            }
        }

        private void paintHueBar(GuiGraphicsExtractor graphics, int left, int top) {
            for (int y = 0; y < SQUARE_SIZE; y += 2) {
                int color = hsvToRgb(y / (float) (SQUARE_SIZE - 1), 1.0f, 1.0f);
                graphics.fill(left, top + y, left + HUE_WIDTH, top + Math.min(SQUARE_SIZE, y + 2),
                        0xFF000000 | color);
            }
        }

        private void paintMarker(GuiGraphicsExtractor graphics, int left, int top, float x, float y) {
            int markerX = left + Math.round(x);
            int markerY = top + Math.round(y);
            graphics.fill(markerX - 4, markerY - 1, markerX + 5, markerY + 1, TEXT);
            graphics.fill(markerX - 1, markerY - 4, markerX + 1, markerY + 5, TEXT);
        }

        private void paintButton(
                GuiGraphicsExtractor graphics,
                String label,
                int left,
                int top,
                int buttonWidth,
                int buttonHeight,
                int mouseX,
                int mouseY,
                boolean primary) {
            boolean hover = mouseX >= left && mouseX < left + buttonWidth
                    && mouseY >= top && mouseY < top + buttonHeight;
            graphics.fill(left, top, left + buttonWidth, top + buttonHeight, hover || primary ? ACTIVE : CONTROL);
            graphics.text(font, label, left + 18, top + 6, TEXT, false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) {
                return super.mouseClicked(event, doubleClick);
            }
            int left = Math.max(8, (width - PANEL_WIDTH) / 2);
            int top = Math.max(8, (height - PANEL_HEIGHT) / 2);
            int squareLeft = left + 18;
            int squareTop = top + 48;
            int hueLeft = squareLeft + SQUARE_SIZE + 12;
            int buttonTop = top + PANEL_HEIGHT - 32;
            int cancelLeft = left + PANEL_WIDTH - 142;
            if (contains(squareLeft, squareTop, SQUARE_SIZE, SQUARE_SIZE, event.x(), event.y())) {
                updateSquare(event.x() - squareLeft, event.y() - squareTop);
                return true;
            }
            if (contains(hueLeft, squareTop, HUE_WIDTH, SQUARE_SIZE, event.x(), event.y())) {
                hue = clamp((float) (event.y() - squareTop) / (SQUARE_SIZE - 1));
                return true;
            }
            if (contains(cancelLeft, buttonTop, 60, 20, event.x(), event.y())) {
                closePicker();
                return true;
            }
            if (contains(cancelLeft + 70, buttonTop, 60, 20, event.x(), event.y())) {
                onSelected.accept(hsvToRgb(hue, saturation, value));
                closePicker();
                return true;
            }
            return true;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.key() == 256) {
                closePicker();
                return true;
            }
            if (event.key() == 257) {
                onSelected.accept(hsvToRgb(hue, saturation, value));
                closePicker();
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        public void onClose() {
            closePicker();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        private void updateSquare(double x, double y) {
            saturation = clamp((float) (x / (SQUARE_SIZE - 1)));
            value = clamp(1.0f - (float) (y / (SQUARE_SIZE - 1)));
        }

        private void closePicker() {
            MinecraftGuiBridge.setScreen(Minecraft.getInstance(), parent);
        }

        private static boolean contains(int left, int top, int width, int height, double x, double y) {
            return x >= left && x < left + width && y >= top && y < top + height;
        }

        private static float clamp(float value) {
            return Math.max(0.0f, Math.min(1.0f, value));
        }

        private static int hsvToRgb(float hue, float saturation, float value) {
            float h = (hue - (float) Math.floor(hue)) * 6.0f;
            int sector = (int) Math.floor(h);
            float fraction = h - sector;
            float p = value * (1.0f - saturation);
            float q = value * (1.0f - fraction * saturation);
            float t = value * (1.0f - (1.0f - fraction) * saturation);
            float red;
            float green;
            float blue;
            switch (sector) {
                case 0 -> { red = value; green = t; blue = p; }
                case 1 -> { red = q; green = value; blue = p; }
                case 2 -> { red = p; green = value; blue = t; }
                case 3 -> { red = p; green = q; blue = value; }
                case 4 -> { red = t; green = p; blue = value; }
                default -> { red = value; green = p; blue = q; }
            }
            return (Math.round(red * 255.0f) << 16)
                    | (Math.round(green * 255.0f) << 8)
                    | Math.round(blue * 255.0f);
        }

        private static float[] rgbToHsv(int rgb) {
            float red = ((rgb >>> 16) & 0xFF) / 255.0f;
            float green = ((rgb >>> 8) & 0xFF) / 255.0f;
            float blue = (rgb & 0xFF) / 255.0f;
            float max = Math.max(red, Math.max(green, blue));
            float min = Math.min(red, Math.min(green, blue));
            float delta = max - min;
            float hue = 0.0f;
            if (delta > 0.0f) {
                if (max == red) {
                    hue = ((green - blue) / delta) % 6.0f;
                } else if (max == green) {
                    hue = (blue - red) / delta + 2.0f;
                } else {
                    hue = (red - green) / delta + 4.0f;
                }
                hue /= 6.0f;
                if (hue < 0.0f) {
                    hue += 1.0f;
                }
            }
            float saturation = max == 0.0f ? 0.0f : delta / max;
            return new float[] { hue, saturation, max };
        }
    }
}
