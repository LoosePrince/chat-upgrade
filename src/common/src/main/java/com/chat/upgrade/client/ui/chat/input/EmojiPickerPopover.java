package com.chat.upgrade.client.ui.chat.input;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.emoji.EmojiCatalog;
import com.chat.upgrade.client.emoji.TwikooOwoRegistry;
import com.chat.upgrade.client.media.image.ImageEntry;
import com.chat.upgrade.client.media.image.ImageLoader;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

public final class EmojiPickerPopover {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 172;
    private static final int PAD = 6;
    private static final int TAB_H = 20;
    private static final int CELL = 24;
    private static final int GAP = 4;
    private static final int PREVIEW_W = 76;
    private static final int PREVIEW_H = 84;
    private static final int PREVIEW_IMAGE = 48;

    private boolean visible = false;
    private double gridScrollY = 0.0D;
    private double groupScrollX = 0.0D;
    private @Nullable String selectedGroupId;
    private @Nullable EmojiCatalog.Item hoveredItem;

    public record ClickResult(boolean handled, boolean close, @Nullable String insertionText) {
        public static ClickResult unhandled() {
            return new ClickResult(false, false, null);
        }

        public static ClickResult outside() {
            return new ClickResult(false, true, null);
        }

        public static ClickResult consumed() {
            return new ClickResult(true, false, null);
        }

        public static ClickResult insert(String insertionText) {
            return new ClickResult(true, true, insertionText);
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void toggle() {
        if (visible) {
            close();
            return;
        }
        open();
    }

    public void open() {
        visible = true;
        TwikooOwoRegistry.refreshIfExpired();
    }

    public void close() {
        visible = false;
        hoveredItem = null;
    }

    public void render(
            GuiGraphicsExtractor gfx,
            Font font,
            int mouseX,
            int mouseY,
            int screenWidth,
            int screenHeight,
            int anchorX,
            int anchorY,
            int anchorWidth) {
        if (!visible) {
            return;
        }
        EmojiCatalog catalog = TwikooOwoRegistry.catalog();
        Layout layout = layout(screenWidth, screenHeight, anchorX, anchorY, anchorWidth);
        hoveredItem = null;

        gfx.fill(layout.x0, layout.y0, layout.x1, layout.y1, 0xF018202B);
        gfx.outline(layout.x0, layout.y0, layout.w, layout.h, 0xFF425066);

        if (catalog.isEmpty()) {
            gfx.centeredText(
                    font,
                    I18n.get("chatupgrade.emoji.picker.loading_or_empty"),
                    layout.x0 + layout.w / 2,
                    layout.y0 + layout.h / 2 - 4,
                    0xFFCAD2DD);
            return;
        }

        EmojiCatalog.Group group = selectedGroup(catalog);
        renderGroups(gfx, font, catalog.groups(), group, mouseX, mouseY, layout);
        renderGrid(gfx, font, group, mouseX, mouseY, layout);
        if (hoveredItem != null) {
            renderPreview(gfx, font, hoveredItem, screenWidth, layout);
        }
    }

    public ClickResult mouseClicked(
            MouseButtonEvent event,
            int screenWidth,
            int screenHeight,
            int anchorX,
            int anchorY,
            int anchorWidth) {
        if (!visible || event.button() != 0) {
            return ClickResult.unhandled();
        }
        EmojiCatalog catalog = TwikooOwoRegistry.catalog();
        Layout layout = layout(screenWidth, screenHeight, anchorX, anchorY, anchorWidth);
        if (!inside(event.x(), event.y(), layout.x0, layout.y0, layout.x1, layout.y1)) {
            return ClickResult.outside();
        }
        if (catalog.isEmpty()) {
            return ClickResult.consumed();
        }
        EmojiCatalog.Group group = selectedGroup(catalog);
        @Nullable String groupId = hitGroup(catalog.groups(), event.x(), event.y(), layout);
        if (groupId != null) {
            selectedGroupId = groupId;
            gridScrollY = 0.0D;
            return ClickResult.consumed();
        }
        @Nullable EmojiCatalog.Item item = hitItem(group, event.x(), event.y(), layout);
        if (item != null) {
            return ClickResult.insert("[:" + item.token() + "]");
        }
        return ClickResult.consumed();
    }

    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollY,
            int screenWidth,
            int screenHeight,
            int anchorX,
            int anchorY,
            int anchorWidth) {
        if (!visible) {
            return false;
        }
        EmojiCatalog catalog = TwikooOwoRegistry.catalog();
        Layout layout = layout(screenWidth, screenHeight, anchorX, anchorY, anchorWidth);
        if (!inside(mouseX, mouseY, layout.x0, layout.y0, layout.x1, layout.y1)) {
            return false;
        }
        if (inside(mouseX, mouseY, layout.tabX0, layout.tabY0, layout.tabX1, layout.tabY1)) {
            groupScrollX = clamp(groupScrollX - scrollY * 28.0D, 0.0D, maxGroupScroll(catalog.groups(), layout));
            return true;
        }
        EmojiCatalog.Group group = selectedGroup(catalog);
        gridScrollY = clamp(gridScrollY - scrollY * 18.0D, 0.0D, maxGridScroll(group, layout));
        return true;
    }

    private void renderGroups(
            GuiGraphicsExtractor gfx,
            Font font,
            List<EmojiCatalog.Group> groups,
            EmojiCatalog.Group selected,
            int mouseX,
            int mouseY,
            Layout layout) {
        gfx.fill(layout.tabX0, layout.tabY0, layout.tabX1, layout.tabY1, 0xFF1F2A38);
        int cursor = layout.tabX0 - (int) Math.round(groupScrollX);
        for (EmojiCatalog.Group group : groups) {
            int tabW = tabWidth(font, group);
            int x0 = cursor;
            int x1 = cursor + tabW;
            cursor = x1 + GAP;
            if (x1 <= layout.tabX0 || x0 >= layout.tabX1) {
                continue;
            }
            boolean active = group.id().equals(selected.id());
            int visibleX0 = Math.max(x0, layout.tabX0);
            int visibleX1 = Math.min(x1, layout.tabX1);
            boolean hover = inside(mouseX, mouseY, visibleX0, layout.tabY0, visibleX1, layout.tabY1);
            int bg = active ? 0xFF4C6284 : hover ? 0xFF334155 : 0xFF2A3545;
            gfx.fill(visibleX0, layout.tabY0, visibleX1, layout.tabY1, bg);
            int textX = visibleX0 + 5;
            int textMaxWidth = visibleX1 - textX - 5;
            if (textMaxWidth > 0) {
                String name = trim(font, group.name(), textMaxWidth);
                gfx.text(font, name, textX, layout.tabY0 + 6, active ? 0xFFFFFFFF : 0xFFCAD2DD, false);
            }
        }
    }

    private void renderGrid(
            GuiGraphicsExtractor gfx,
            Font font,
            EmojiCatalog.Group group,
            int mouseX,
            int mouseY,
            Layout layout) {
        List<EmojiCatalog.Item> items = group.items();
        if (items.isEmpty()) {
            gfx.centeredText(
                    font,
                    I18n.get("chatupgrade.emoji.picker.empty_group"),
                    layout.gridX0 + layout.gridW / 2,
                    layout.gridY0 + layout.gridH / 2 - 4,
                    0xFFCAD2DD);
            return;
        }
        gridScrollY = clamp(gridScrollY, 0.0D, maxGridScroll(group, layout));
        int columns = columns(layout);
        int rowStep = CELL + GAP;
        int firstRow = Math.max(0, (int) Math.floor(gridScrollY / rowStep));
        int lastRow = Math.min((items.size() + columns - 1) / columns, firstRow + layout.gridH / rowStep + 3);
        int offsetY = (int) Math.round(gridScrollY - firstRow * rowStep);

        gfx.fill(layout.gridX0, layout.gridY0, layout.gridX1, layout.gridY1, 0xFF141A22);
        for (int row = firstRow; row < lastRow; row++) {
            int y0 = layout.gridY0 + (row - firstRow) * rowStep - offsetY;
            if (y0 < layout.gridY0 || y0 + CELL > layout.gridY1) {
                continue;
            }
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                if (index >= items.size()) {
                    return;
                }
                int x0 = layout.gridX0 + col * rowStep;
                EmojiCatalog.Item item = items.get(index);
                boolean hover = inside(mouseX, mouseY, x0, y0, x0 + CELL, y0 + CELL);
                if (hover) {
                    hoveredItem = item;
                }
                renderItem(gfx, font, item, x0, y0, CELL, hover);
            }
        }
        renderScrollBar(gfx, group, layout);
    }

    private void renderItem(
            GuiGraphicsExtractor gfx,
            Font font,
            EmojiCatalog.Item item,
            int x,
            int y,
            int size,
            boolean hover) {
        gfx.fill(x, y, x + size, y + size, hover ? 0xFF334155 : 0xFF202A36);
        gfx.outline(x, y, size, size, hover ? 0xFF7DB5FF : 0xFF3A4456);
        ImageEntry entry = ImageLoader.getOrLoad(item.loaderUrl());
        int imageSize = size - 6;
        int imageX = x + 3;
        int imageY = y + 3;
        switch (entry.getState()) {
            case FAILED -> gfx.centeredText(font, "?", x + size / 2, y + 8, 0xFFFF9090);
            case LOADING -> gfx.fill(imageX, imageY, imageX + imageSize, imageY + imageSize, 0xFF2F3846);
            case LOADED -> blitImage(gfx, entry, imageX, imageY, imageSize, 1.0F);
        }
    }

    private void renderPreview(
            GuiGraphicsExtractor gfx,
            Font font,
            EmojiCatalog.Item item,
            int screenWidth,
            Layout layout) {
        int x0 = layout.x1 + GAP;
        if (x0 + PREVIEW_W > screenWidth - 2) {
            x0 = Math.max(2, layout.x0 - PREVIEW_W - GAP);
        }
        int y0 = layout.y0;
        int x1 = x0 + PREVIEW_W;
        int y1 = y0 + PREVIEW_H;
        gfx.fill(x0, y0, x1, y1, 0xF01A212C);
        gfx.outline(x0, y0, PREVIEW_W, PREVIEW_H, 0xFF5A6B84);
        ImageEntry entry = ImageLoader.getOrLoad(item.loaderUrl());
        int imageX = x0 + (PREVIEW_W - PREVIEW_IMAGE) / 2;
        int imageY = y0 + 8;
        switch (entry.getState()) {
            case FAILED -> gfx.centeredText(font, I18n.get("chatupgrade.emoji.picker.failed"), x0 + PREVIEW_W / 2, imageY + 18, 0xFFFF9090);
            case LOADING -> {
                gfx.fill(imageX, imageY, imageX + PREVIEW_IMAGE, imageY + PREVIEW_IMAGE, 0xFF2F3846);
                gfx.centeredText(font, "...", x0 + PREVIEW_W / 2, imageY + 20, 0xFFCAD2DD);
            }
            case LOADED -> blitImage(gfx, entry, imageX, imageY, PREVIEW_IMAGE, 1.0F);
        }
        gfx.centeredText(font, trim(font, item.token(), PREVIEW_W - 8), x0 + PREVIEW_W / 2, y0 + 62, 0xFFE7ECF4);
    }

    private void renderScrollBar(GuiGraphicsExtractor gfx, EmojiCatalog.Group group, Layout layout) {
        double max = maxGridScroll(group, layout);
        if (max <= 0.0D) {
            return;
        }
        int barX = layout.gridX1 - 3;
        int trackY0 = layout.gridY0 + 2;
        int trackH = Math.max(1, layout.gridH - 4);
        int contentH = rows(group, layout) * (CELL + GAP);
        int thumbH = Math.clamp((int) Math.round(trackH * (layout.gridH / (double) Math.max(layout.gridH, contentH))), 14, trackH);
        int thumbY = trackY0 + (int) Math.round((trackH - thumbH) * (gridScrollY / max));
        gfx.fill(barX, trackY0, barX + 2, trackY0 + trackH, 0xFF263241);
        gfx.fill(barX, thumbY, barX + 2, thumbY + thumbH, 0xFF7D8EA6);
    }

    private @Nullable String hitGroup(List<EmojiCatalog.Group> groups, double mouseX, double mouseY, Layout layout) {
        if (!inside(mouseX, mouseY, layout.tabX0, layout.tabY0, layout.tabX1, layout.tabY1)) {
            return null;
        }
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int cursor = layout.tabX0 - (int) Math.round(groupScrollX);
        for (EmojiCatalog.Group group : groups) {
            int tabW = tabWidth(font, group);
            int x0 = cursor;
            int x1 = cursor + tabW;
            cursor = x1 + GAP;
            if (inside(mouseX, mouseY, x0, layout.tabY0, x1, layout.tabY1)) {
                return group.id();
            }
        }
        return null;
    }

    private @Nullable EmojiCatalog.Item hitItem(EmojiCatalog.Group group, double mouseX, double mouseY, Layout layout) {
        if (!inside(mouseX, mouseY, layout.gridX0, layout.gridY0, layout.gridX1, layout.gridY1)) {
            return null;
        }
        List<EmojiCatalog.Item> items = group.items();
        int columns = columns(layout);
        int rowStep = CELL + GAP;
        int localX = (int) Math.floor(mouseX) - layout.gridX0;
        int localY = (int) Math.floor(mouseY) - layout.gridY0 + (int) Math.round(gridScrollY);
        int col = localX / rowStep;
        int row = localY / rowStep;
        int cellX = localX % rowStep;
        int cellY = localY % rowStep;
        if (col < 0 || col >= columns || cellX >= CELL || cellY >= CELL) {
            return null;
        }
        int index = row * columns + col;
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    private EmojiCatalog.Group selectedGroup(EmojiCatalog catalog) {
        List<EmojiCatalog.Group> groups = catalog.groups();
        if (groups.isEmpty()) {
            return new EmojiCatalog.Group("empty", "empty", List.of());
        }
        if (selectedGroupId != null) {
            for (EmojiCatalog.Group group : groups) {
                if (selectedGroupId.equals(group.id())) {
                    return group;
                }
            }
        }
        EmojiCatalog.Group first = groups.getFirst();
        selectedGroupId = first.id();
        return first;
    }

    private void blitImage(GuiGraphicsExtractor gfx, ImageEntry entry, int x, int y, int size, float opacity) {
        Identifier textureId = entry.isAnimated() ? entry.textureIdAtMillis(Util.getMillis()) : entry.getTextureId();
        if (textureId == null) {
            return;
        }
        gfx.blit(
                RenderPipelines.GUI_TEXTURED,
                textureId,
                x,
                y,
                0.0F,
                0.0F,
                size,
                size,
                entry.getTextureWidth(),
                entry.getTextureHeight(),
                entry.getTextureWidth(),
                entry.getTextureHeight(),
                ARGB.white(opacity));
    }

    private Layout layout(int screenWidth, int screenHeight, int anchorX, int anchorY, int anchorWidth) {
        int w = Math.min(PANEL_W, Math.max(160, screenWidth - 8));
        int h = Math.min(PANEL_H, Math.max(92, screenHeight - 48));
        int desiredX = anchorX + Math.max(0, (anchorWidth - w) / 2);
        int x0 = Math.clamp(desiredX, 4, Math.max(4, screenWidth - w - 4));
        int y0 = anchorY - h - 4;
        if (y0 < 4) {
            y0 = Math.min(screenHeight - h - 4, anchorY + 18);
        }
        y0 = Math.clamp(y0, 4, Math.max(4, screenHeight - h - 4));
        return new Layout(x0, y0, w, h);
    }

    private int columns(Layout layout) {
        return Math.max(1, (layout.gridW + GAP) / (CELL + GAP));
    }

    private int rows(EmojiCatalog.Group group, Layout layout) {
        int columns = columns(layout);
        return (group.items().size() + columns - 1) / columns;
    }

    private double maxGridScroll(EmojiCatalog.Group group, Layout layout) {
        int contentH = rows(group, layout) * (CELL + GAP) - GAP;
        return Math.max(0.0D, contentH - layout.gridH);
    }

    private double maxGroupScroll(List<EmojiCatalog.Group> groups, Layout layout) {
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int width = 0;
        for (EmojiCatalog.Group group : groups) {
            width += tabWidth(font, group) + GAP;
        }
        return Math.max(0.0D, width - GAP - (layout.tabX1 - layout.tabX0));
    }

    private int tabWidth(Font font, EmojiCatalog.Group group) {
        return Math.clamp(font.width(group.name()) + 12, 42, 76);
    }

    private String trim(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        int ellipsis = font.width("…");
        return font.plainSubstrByWidth(text, Math.max(1, width - ellipsis)) + "…";
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean inside(double px, double py, int x0, int y0, int x1, int y1) {
        return px >= x0 && px < x1 && py >= y0 && py < y1;
    }

    private record Layout(
            int x0,
            int y0,
            int w,
            int h,
            int x1,
            int y1,
            int tabX0,
            int tabY0,
            int tabX1,
            int tabY1,
            int gridX0,
            int gridY0,
            int gridX1,
            int gridY1,
            int gridW,
            int gridH) {
        Layout(int x0, int y0, int w, int h) {
            this(
                    x0,
                    y0,
                    w,
                    h,
                    x0 + w,
                    y0 + h,
                    x0 + PAD,
                    y0 + PAD,
                    x0 + w - PAD,
                    y0 + PAD + TAB_H,
                    x0 + PAD,
                    y0 + PAD + TAB_H + GAP,
                    x0 + w - PAD,
                    y0 + h - PAD,
                    w - PAD * 2,
                    h - PAD * 2 - TAB_H - GAP);
        }

        static Layout empty() {
            return new Layout(0, 0, 0, 0);
        }
    }
}