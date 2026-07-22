package com.chat.upgrade.client.ui.chat.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.emoji.EmojiCatalog;
import com.chat.upgrade.client.emoji.TwikooOwoRegistry;
import com.chat.upgrade.client.media.image.ImageEntry;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceRuntime;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceController;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.render.UiPrimitives;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

public final class EmojiPickerPopover {
    private static final int SCREEN_MARGIN = 4;
    private static final int BASE_PANEL_WIDTH = 300;
    private static final int BASE_PANEL_HEIGHT = 172;
    private static final int BASE_MIN_PANEL_WIDTH = 160;
    private static final int BASE_MIN_PANEL_HEIGHT = 112;
    private static final int BASE_PADDING = 6;
    private static final int BASE_TAB_HEIGHT = 20;
    private static final int BASE_SEARCH_HEIGHT = 18;
    private static final int BASE_CELL_SIZE = 24;
    private static final int BASE_GAP = 4;
    private static final int BASE_PREVIEW_WIDTH = 76;
    private static final int BASE_PREVIEW_HEIGHT = 84;
    private static final int BASE_PREVIEW_IMAGE_SIZE = 48;

    private boolean visible;
    private double gridScrollY;
    private double groupScrollX;
    private @Nullable String selectedGroupId;
    private @Nullable EmojiCatalog.Item hoveredItem;
    private String searchQuery = "";
    private final List<String> recentTokens = new ArrayList<>();

    public String searchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(@Nullable String query) {
        String normalized = query == null ? "" : query.trim();
        if (!normalized.equals(searchQuery)) {
            searchQuery = normalized;
            gridScrollY = 0.0D;
            groupScrollX = 0.0D;
        }
    }

    public void clearSearch() {
        setSearchQuery("");
    }

    public boolean isSearchFocused(
            double mouseX,
            double mouseY,
            int screenWidth,
            int screenHeight,
            int anchorX,
            int anchorY,
            int anchorWidth) {
        return layout(screenWidth, screenHeight, anchorX, anchorY, anchorWidth)
                .searchBounds()
                .contains((int) Math.round(mouseX), (int) Math.round(mouseY));
    }

    public RichChatBounds searchBounds(
            int screenWidth,
            int screenHeight,
            int anchorX,
            int anchorY,
            int anchorWidth) {
        return layout(screenWidth, screenHeight, anchorX, anchorY, anchorWidth).searchBounds();
    }

    public void remember(EmojiCatalog.Item item) {
        if (item == null || item.token().isBlank()) {
            return;
        }
        recentTokens.remove(item.token());
        recentTokens.addFirst(item.token());
        while (recentTokens.size() > 24) {
            recentTokens.removeLast();
        }
    }

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
        } else {
            open();
        }
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
            GuiGraphicsExtractor graphics,
            Font font,
            int mouseX,
            int mouseY,
            int screenWidth,
            int screenHeight,
            int anchorX,
            int anchorY,
            int anchorWidth) {
        if (!visible || graphics == null || font == null) {
            return;
        }
        EmojiCatalog catalog = TwikooOwoRegistry.catalog();
        Layout layout = layout(screenWidth, screenHeight, anchorX, anchorY, anchorWidth);
        ChatAppearanceSnapshot appearance = layout.appearance();
        Density density = layout.density();
        hoveredItem = null;

        UiPrimitives.paintBox(
                graphics,
                layout.panelBounds(),
                density.radius(),
                density.borderWidth(),
                appearance.contextMenu().background(),
                appearance.contextMenu().border());
        UiPrimitives.paintBox(
                graphics,
                layout.searchBounds(),
                density.radius(),
                density.borderWidth(),
                appearance.media().controlBackground(),
                appearance.contextMenu().border());

        if (catalog.isEmpty()) {
            graphics.centeredText(
                    font,
                    I18n.get("chatupgrade.emoji.picker.loading_or_empty"),
                    layout.panelBounds().left() + layout.panelBounds().width() / 2,
                    layout.panelBounds().top() + layout.panelBounds().height() / 2 - font.lineHeight / 2,
                    appearance.surface().muted());
            return;
        }

        EmojiCatalog.Group selected = selectedGroup(catalog);
        List<EmojiCatalog.Group> groups = displayGroups(catalog);
        renderGroups(graphics, font, groups, selected, mouseX, mouseY, layout);
        renderGrid(graphics, font, selected, mouseX, mouseY, layout);
        if (hoveredItem != null && layout.previewBounds().width() > 0) {
            renderPreview(graphics, font, hoveredItem, layout);
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
        Layout layout = layout(screenWidth, screenHeight, anchorX, anchorY, anchorWidth);
        if (!UiPrimitives.containsRounded(
                layout.panelBounds(),
                (int) Math.round(event.x()),
                (int) Math.round(event.y()),
                layout.density().radius())) {
            return ClickResult.outside();
        }
        if (layout.searchBounds().contains((int) Math.round(event.x()), (int) Math.round(event.y()))) {
            return ClickResult.consumed();
        }
        EmojiCatalog catalog = TwikooOwoRegistry.catalog();
        if (catalog.isEmpty()) {
            return ClickResult.consumed();
        }
        EmojiCatalog.Group group = selectedGroup(catalog);
        @Nullable String groupId = hitGroup(displayGroups(catalog), event.x(), event.y(), layout);
        if (groupId != null) {
            searchQuery = "";
            selectedGroupId = groupId;
            gridScrollY = 0.0D;
            return ClickResult.consumed();
        }
        @Nullable EmojiCatalog.Item item = hitItem(group, event.x(), event.y(), layout);
        if (item != null) {
            remember(item);
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
        if (!UiPrimitives.containsRounded(
                layout.panelBounds(),
                (int) Math.round(mouseX),
                (int) Math.round(mouseY),
                layout.density().radius())) {
            return false;
        }
        if (layout.searchBounds().contains((int) Math.round(mouseX), (int) Math.round(mouseY))) {
            return true;
        }
        if (layout.tabBounds().contains((int) Math.round(mouseX), (int) Math.round(mouseY))) {
            groupScrollX = clamp(
                    groupScrollX - scrollY * layout.density().tabScrollStep(),
                    0.0D,
                    maxGroupScroll(displayGroups(catalog), layout));
            return true;
        }
        EmojiCatalog.Group group = selectedGroup(catalog);
        gridScrollY = clamp(
                gridScrollY - scrollY * layout.density().gridScrollStep(),
                0.0D,
                maxGridScroll(group, layout));
        return true;
    }

    private void renderGroups(
            GuiGraphicsExtractor graphics,
            Font font,
            List<EmojiCatalog.Group> groups,
            EmojiCatalog.Group selected,
            int mouseX,
            int mouseY,
            Layout layout) {
        Density density = layout.density();
        ChatAppearanceSnapshot appearance = layout.appearance();
        RichChatBounds tabs = layout.tabBounds();
        UiPrimitives.fillRounded(
                graphics,
                tabs,
                density.radius(),
                appearance.media().controlBackground());
        groupScrollX = clamp(groupScrollX, 0.0D, maxGroupScroll(groups, layout));
        int cursor = tabs.left() - (int) Math.round(groupScrollX);
        graphics.enableScissor(tabs.left(), tabs.top(), tabs.right(), tabs.bottom());
        try {
            for (EmojiCatalog.Group group : groups) {
                int tabWidth = tabWidth(font, group, density);
                RichChatBounds tab = RichChatBounds.ofSize(cursor, tabs.top(), tabWidth, tabs.height());
                cursor = tab.right() + density.gap();
                if (tab.right() <= tabs.left() || tab.left() >= tabs.right()) {
                    continue;
                }
                boolean active = group.id().equals(selected.id());
                boolean hover = tab.contains(mouseX, mouseY);
                int background = active || hover
                        ? appearance.media().controlActiveBackground()
                        : appearance.media().controlBackground();
                UiPrimitives.fillRounded(graphics, tab, density.radius(), background);
                String name = trim(font, group.name(), Math.max(1, tab.width() - density.tabTextPadding() * 2));
                graphics.text(
                        font,
                        name,
                        tab.left() + density.tabTextPadding(),
                        tab.top() + Math.max(1, (tab.height() - font.lineHeight) / 2),
                        active ? appearance.surface().title() : appearance.surface().muted(),
                        false);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderGrid(
            GuiGraphicsExtractor graphics,
            Font font,
            EmojiCatalog.Group group,
            int mouseX,
            int mouseY,
            Layout layout) {
        ChatAppearanceSnapshot appearance = layout.appearance();
        Density density = layout.density();
        RichChatBounds grid = layout.gridBounds();
        UiPrimitives.fillRounded(
                graphics,
                grid,
                density.radius(),
                appearance.media().pendingBackground());
        List<EmojiCatalog.Item> items = group.items();
        if (items.isEmpty()) {
            graphics.centeredText(
                    font,
                    I18n.get("chatupgrade.emoji.picker.empty_group"),
                    grid.left() + grid.width() / 2,
                    grid.top() + grid.height() / 2 - font.lineHeight / 2,
                    appearance.surface().muted());
            return;
        }

        gridScrollY = clamp(gridScrollY, 0.0D, maxGridScroll(group, layout));
        int columns = columns(layout);
        int rowStep = density.rowStep();
        int firstRow = Math.max(0, (int) Math.floor(gridScrollY / rowStep));
        int rowCount = (items.size() + columns - 1) / columns;
        int lastRow = Math.min(rowCount, firstRow + grid.height() / rowStep + 3);
        int offsetY = (int) Math.round(gridScrollY - firstRow * rowStep);

        graphics.enableScissor(grid.left(), grid.top(), grid.right(), grid.bottom());
        try {
            for (int row = firstRow; row < lastRow; row++) {
                int itemY = grid.top() + (row - firstRow) * rowStep - offsetY;
                for (int column = 0; column < columns; column++) {
                    int index = row * columns + column;
                    if (index >= items.size()) {
                        break;
                    }
                    int itemX = grid.left() + column * rowStep;
                    RichChatBounds itemBounds = RichChatBounds.ofSize(
                            itemX,
                            itemY,
                            density.cellSize(),
                            density.cellSize());
                    if (itemBounds.bottom() <= grid.top() || itemBounds.top() >= grid.bottom()) {
                        continue;
                    }
                    EmojiCatalog.Item item = items.get(index);
                    boolean hover = itemBounds.contains(mouseX, mouseY);
                    if (hover) {
                        hoveredItem = item;
                    }
                    renderItem(graphics, font, item, itemBounds, hover, layout);
                }
            }
        } finally {
            graphics.disableScissor();
        }
        renderScrollBar(graphics, group, layout);
    }

    private void renderItem(
            GuiGraphicsExtractor graphics,
            Font font,
            EmojiCatalog.Item item,
            RichChatBounds bounds,
            boolean hover,
            Layout layout) {
        ChatAppearanceSnapshot appearance = layout.appearance();
        Density density = layout.density();
        UiPrimitives.paintBox(
                graphics,
                bounds,
                density.radius(),
                density.itemBorderWidth(),
                hover ? appearance.media().controlActiveBackground() : appearance.media().controlBackground(),
                hover ? appearance.message().systemBorder() : appearance.contextMenu().border());

        int imageInset = density.imageInset();
        RichChatBounds imageBounds = RichChatBounds.ofSize(
                bounds.left() + imageInset,
                bounds.top() + imageInset,
                Math.max(1, bounds.width() - imageInset * 2),
                Math.max(1, bounds.height() - imageInset * 2));
        ImageEntry entry = ImageLoader.getOrLoad(item.loaderUrl());
        switch (entry.getState()) {
            case FAILED -> graphics.centeredText(
                    font,
                    "?",
                    bounds.left() + bounds.width() / 2,
                    bounds.top() + Math.max(1, (bounds.height() - font.lineHeight) / 2),
                    appearance.media().failureText());
            case LOADING -> UiPrimitives.fillRounded(
                    graphics,
                    imageBounds,
                    density.radius(),
                    appearance.media().emojiLoadingBackground());
            case LOADED -> UiPrimitives.withRoundedClip(
                    graphics,
                    imageBounds,
                    density.radius(),
                    () -> blitImage(graphics, entry, imageBounds.left(), imageBounds.top(), imageBounds.width(), 1.0F));
        }
    }

    private void renderPreview(
            GuiGraphicsExtractor graphics,
            Font font,
            EmojiCatalog.Item item,
            Layout layout) {
        ChatAppearanceSnapshot appearance = layout.appearance();
        Density density = layout.density();
        RichChatBounds preview = layout.previewBounds();
        UiPrimitives.paintBox(
                graphics,
                preview,
                density.radius(),
                density.borderWidth(),
                appearance.contextMenu().background(),
                appearance.contextMenu().border());

        int imageSize = Math.max(
                1,
                Math.min(
                        density.previewImageSize(),
                        Math.min(
                                preview.width() - density.padding() * 2,
                                preview.height() - density.padding() * 2 - font.lineHeight)));
        RichChatBounds imageBounds = RichChatBounds.ofSize(
                preview.left() + (preview.width() - imageSize) / 2,
                preview.top() + density.padding(),
                imageSize,
                imageSize);
        ImageEntry entry = ImageLoader.getOrLoad(item.loaderUrl());
        switch (entry.getState()) {
            case FAILED -> graphics.centeredText(
                    font,
                    I18n.get("chatupgrade.emoji.picker.failed"),
                    preview.left() + preview.width() / 2,
                    imageBounds.top() + Math.max(1, (imageBounds.height() - font.lineHeight) / 2),
                    appearance.media().failureText());
            case LOADING -> {
                UiPrimitives.fillRounded(
                        graphics,
                        imageBounds,
                        density.radius(),
                        appearance.media().emojiLoadingBackground());
                graphics.centeredText(
                        font,
                        "...",
                        preview.left() + preview.width() / 2,
                        imageBounds.top() + Math.max(1, (imageBounds.height() - font.lineHeight) / 2),
                        appearance.surface().muted());
            }
            case LOADED -> UiPrimitives.withRoundedClip(
                    graphics,
                    imageBounds,
                    density.radius(),
                    () -> blitImage(graphics, entry, imageBounds.left(), imageBounds.top(), imageSize, 1.0F));
        }
        graphics.centeredText(
                font,
                trim(font, item.token(), Math.max(1, preview.width() - density.padding() * 2)),
                preview.left() + preview.width() / 2,
                preview.bottom() - density.padding() - font.lineHeight,
                appearance.surface().title());
    }

    private void renderScrollBar(
            GuiGraphicsExtractor graphics,
            EmojiCatalog.Group group,
            Layout layout) {
        double maxScroll = maxGridScroll(group, layout);
        if (maxScroll <= 0.0D) {
            return;
        }
        Density density = layout.density();
        RichChatBounds grid = layout.gridBounds();
        int trackHeight = Math.max(1, grid.height() - density.scrollbarInset() * 2);
        int contentHeight = rows(group, layout) * density.rowStep() - density.gap();
        int thumbHeight = Math.clamp(
                (int) Math.round(trackHeight * (grid.height() / (double) Math.max(grid.height(), contentHeight))),
                Math.min(density.minimumThumbHeight(), trackHeight),
                trackHeight);
        int trackX = grid.right() - density.scrollbarInset() - density.scrollbarWidth();
        int trackTop = grid.top() + density.scrollbarInset();
        RichChatBounds track = RichChatBounds.ofSize(
                trackX,
                trackTop,
                density.scrollbarWidth(),
                trackHeight);
        int thumbTop = trackTop + (int) Math.round((trackHeight - thumbHeight) * (gridScrollY / maxScroll));
        RichChatBounds thumb = RichChatBounds.ofSize(trackX, thumbTop, density.scrollbarWidth(), thumbHeight);
        UiPrimitives.fillRounded(graphics, track, density.scrollbarWidth() / 2, layout.appearance().scrollbar().track());
        UiPrimitives.fillRounded(graphics, thumb, density.scrollbarWidth() / 2, layout.appearance().scrollbar().thumb());
    }

    private @Nullable String hitGroup(
            List<EmojiCatalog.Group> groups,
            double mouseX,
            double mouseY,
            Layout layout) {
        RichChatBounds tabs = layout.tabBounds();
        if (!tabs.contains((int) Math.round(mouseX), (int) Math.round(mouseY))) {
            return null;
        }
        Font font = Minecraft.getInstance().font;
        int cursor = tabs.left() - (int) Math.round(groupScrollX);
        for (EmojiCatalog.Group group : groups) {
            int width = tabWidth(font, group, layout.density());
            RichChatBounds tab = RichChatBounds.ofSize(cursor, tabs.top(), width, tabs.height());
            cursor = tab.right() + layout.density().gap();
            if (tab.contains((int) Math.round(mouseX), (int) Math.round(mouseY))) {
                return group.id();
            }
        }
        return null;
    }

    private @Nullable EmojiCatalog.Item hitItem(
            EmojiCatalog.Group group,
            double mouseX,
            double mouseY,
            Layout layout) {
        RichChatBounds grid = layout.gridBounds();
        if (!grid.contains((int) Math.round(mouseX), (int) Math.round(mouseY))) {
            return null;
        }
        Density density = layout.density();
        int rowStep = density.rowStep();
        int localX = (int) Math.floor(mouseX) - grid.left();
        int localY = (int) Math.floor(mouseY) - grid.top() + (int) Math.round(gridScrollY);
        int column = localX / rowStep;
        int row = localY / rowStep;
        int cellX = localX % rowStep;
        int cellY = localY % rowStep;
        int columns = columns(layout);
        if (column < 0 || column >= columns || cellX >= density.cellSize() || cellY >= density.cellSize()) {
            return null;
        }
        int index = row * columns + column;
        List<EmojiCatalog.Item> items = group.items();
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    private EmojiCatalog.Group selectedGroup(EmojiCatalog catalog) {
        List<EmojiCatalog.Group> groups = displayGroups(catalog);
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

    private List<EmojiCatalog.Group> displayGroups(EmojiCatalog catalog) {
        List<EmojiCatalog.Item> allItems = catalog.groups().stream()
                .flatMap(group -> group.items().stream())
                .toList();
        if (!searchQuery.isBlank()) {
            String query = searchQuery.toLowerCase(Locale.ROOT);
            List<EmojiCatalog.Item> matches = allItems.stream()
                    .filter(item -> item.token().toLowerCase(Locale.ROOT).contains(query))
                    .toList();
            return List.of(new EmojiCatalog.Group(
                    "search",
                    I18n.get("chatupgrade.emoji.picker.search"),
                    matches));
        }
        List<EmojiCatalog.Group> groups = new ArrayList<>();
        if (!recentTokens.isEmpty()) {
            List<EmojiCatalog.Item> recent = recentTokens.stream()
                    .map(catalog::itemByToken)
                    .filter(Objects::nonNull)
                    .toList();
            if (!recent.isEmpty()) {
                groups.add(new EmojiCatalog.Group(
                        "recent",
                        I18n.get("chatupgrade.emoji.picker.recent"),
                        recent));
            }
        }
        groups.addAll(catalog.groups());
        return List.copyOf(groups);
    }

    private void blitImage(
            GuiGraphicsExtractor graphics,
            ImageEntry entry,
            int x,
            int y,
            int size,
            float opacity) {
        Identifier textureId = entry.isAnimated()
                ? entry.textureIdAtMillis(Util.getMillis())
                : entry.getTextureId();
        if (textureId == null) {
            return;
        }
        graphics.blit(
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

    private Layout layout(
            int screenWidth,
            int screenHeight,
            int anchorX,
            int anchorY,
            int anchorWidth) {
        ChatAppearanceSnapshot appearance = ChatAppearanceRuntime.current();
        Density density = Density.at(appearance);
        RichChatBounds screen = new RichChatBounds(
                SCREEN_MARGIN,
                SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN, screenWidth - SCREEN_MARGIN),
                Math.max(SCREEN_MARGIN, screenHeight - SCREEN_MARGIN));
        RichChatBounds panel = ChatSurfaceController.state().frame().panelBounds();
        int anchorCenterX = anchorX + Math.max(1, anchorWidth) / 2;
        boolean anchoredToMergedPanel = !appearance.vanillaStyleInput()
                && panel.contains(anchorCenterX, anchorY);
        RichChatBounds available = anchoredToMergedPanel ? intersection(screen, panel) : screen;
        if (available.width() <= 0 || available.height() <= 0) {
            available = screen;
        }

        int panelWidth = Math.min(density.panelWidth(), available.width());
        int panelHeight = Math.min(density.panelHeight(), available.height());
        panelWidth = Math.max(Math.min(density.minimumPanelWidth(), available.width()), panelWidth);
        panelHeight = Math.max(Math.min(density.minimumPanelHeight(), available.height()), panelHeight);
        int desiredX = anchorX + Math.max(0, (anchorWidth - panelWidth) / 2);
        int panelX = clampPosition(desiredX, available.left(), available.right() - panelWidth);
        int preferredAboveY = anchorY - density.gap() - panelHeight;
        int preferredBelowY = anchorY + density.anchorHeight() + density.gap();
        int panelY;
        if (preferredAboveY >= available.top()) {
            panelY = preferredAboveY;
        } else if (preferredBelowY + panelHeight <= available.bottom()) {
            panelY = preferredBelowY;
        } else {
            panelY = clampPosition(preferredAboveY, available.top(), available.bottom() - panelHeight);
        }

        RichChatBounds panelBounds = RichChatBounds.ofSize(panelX, panelY, panelWidth, panelHeight);
        int padding = Math.min(
                density.padding(),
                Math.max(1, Math.min(panelBounds.width(), panelBounds.height()) / 8));
        int contentLeft = panelBounds.left() + padding;
        int contentRight = Math.max(contentLeft + 1, panelBounds.right() - padding);
        int searchHeight = Math.min(
                density.searchHeight(),
                Math.max(1, panelBounds.height() - padding * 2));
        RichChatBounds search = new RichChatBounds(
                contentLeft,
                panelBounds.top() + padding,
                contentRight,
                panelBounds.top() + padding + searchHeight);
        int tabTop = Math.min(panelBounds.bottom() - padding, search.bottom() + density.gap());
        int tabHeight = Math.min(density.tabHeight(), Math.max(1, panelBounds.bottom() - padding - tabTop));
        RichChatBounds tabs = new RichChatBounds(contentLeft, tabTop, contentRight, tabTop + tabHeight);
        int gridTop = Math.min(panelBounds.bottom() - padding, tabs.bottom() + density.gap());
        RichChatBounds grid = new RichChatBounds(
                contentLeft,
                gridTop,
                contentRight,
                Math.max(gridTop, panelBounds.bottom() - padding));
        RichChatBounds preview = previewBounds(panelBounds, screen, density);
        return new Layout(appearance, density, panelBounds, search, tabs, grid, preview);
    }

    private RichChatBounds previewBounds(
            RichChatBounds panel,
            RichChatBounds screen,
            Density density) {
        int width = Math.min(density.previewWidth(), screen.width());
        int height = Math.min(density.previewHeight(), screen.height());
        int rightX = panel.right() + density.gap();
        int leftX = panel.left() - density.gap() - width;
        int x;
        if (rightX + width <= screen.right()) {
            x = rightX;
        } else if (leftX >= screen.left()) {
            x = leftX;
        } else {
            return RichChatBounds.ofSize(0, 0, 0, 0);
        }
        int y = clampPosition(panel.top(), screen.top(), screen.bottom() - height);
        return RichChatBounds.ofSize(x, y, width, height);
    }

    private int columns(Layout layout) {
        int rowStep = layout.density().rowStep();
        return Math.max(1, (layout.gridBounds().width() + layout.density().gap()) / rowStep);
    }

    private int rows(EmojiCatalog.Group group, Layout layout) {
        int columns = columns(layout);
        return (group.items().size() + columns - 1) / columns;
    }

    private double maxGridScroll(EmojiCatalog.Group group, Layout layout) {
        int contentHeight = Math.max(
                0,
                rows(group, layout) * layout.density().rowStep() - layout.density().gap());
        return Math.max(0.0D, contentHeight - layout.gridBounds().height());
    }

    private double maxGroupScroll(List<EmojiCatalog.Group> groups, Layout layout) {
        Font font = Minecraft.getInstance().font;
        int width = 0;
        for (EmojiCatalog.Group group : groups) {
            width += tabWidth(font, group, layout.density()) + layout.density().gap();
        }
        width = Math.max(0, width - layout.density().gap());
        return Math.max(0.0D, width - layout.tabBounds().width());
    }

    private int tabWidth(Font font, EmojiCatalog.Group group, Density density) {
        return Math.clamp(
                font.width(group.name()) + density.tabTextPadding() * 2,
                density.minimumTabWidth(),
                density.maximumTabWidth());
    }

    private String trim(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        int ellipsis = font.width("…");
        return font.plainSubstrByWidth(text, Math.max(1, width - ellipsis)) + "…";
    }

    private RichChatBounds intersection(RichChatBounds first, RichChatBounds second) {
        return new RichChatBounds(
                Math.max(first.left(), second.left()),
                Math.max(first.top(), second.top()),
                Math.min(first.right(), second.right()),
                Math.min(first.bottom(), second.bottom()));
    }

    private int clampPosition(int value, int minimum, int maximum) {
        return maximum <= minimum ? minimum : Math.clamp(value, minimum, maximum);
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Layout(
            ChatAppearanceSnapshot appearance,
            Density density,
            RichChatBounds panelBounds,
            RichChatBounds searchBounds,
            RichChatBounds tabBounds,
            RichChatBounds gridBounds,
            RichChatBounds previewBounds) {
    }

    private record Density(
            int panelWidth,
            int panelHeight,
            int minimumPanelWidth,
            int minimumPanelHeight,
            int padding,
            int tabHeight,
            int searchHeight,
            int cellSize,
            int gap,
            int previewWidth,
            int previewHeight,
            int previewImageSize,
            int radius,
            int borderWidth,
            int itemBorderWidth,
            int imageInset,
            int tabTextPadding,
            int minimumTabWidth,
            int maximumTabWidth,
            int anchorHeight,
            int tabScrollStep,
            int gridScrollStep,
            int scrollbarWidth,
            int scrollbarInset,
            int minimumThumbHeight) {
        private static Density at(ChatAppearanceSnapshot appearance) {
            int scalePercent = Math.clamp(appearance.contextMenu().scalePercent(), 75, 150);
            return new Density(
                    scale(BASE_PANEL_WIDTH, scalePercent),
                    scale(BASE_PANEL_HEIGHT, scalePercent),
                    scale(BASE_MIN_PANEL_WIDTH, scalePercent),
                    scale(BASE_MIN_PANEL_HEIGHT, scalePercent),
                    scale(BASE_PADDING, scalePercent),
                    scale(BASE_TAB_HEIGHT, scalePercent),
                    scale(BASE_SEARCH_HEIGHT, scalePercent),
                    scale(BASE_CELL_SIZE, scalePercent),
                    scale(BASE_GAP, scalePercent),
                    scale(BASE_PREVIEW_WIDTH, scalePercent),
                    scale(BASE_PREVIEW_HEIGHT, scalePercent),
                    scale(BASE_PREVIEW_IMAGE_SIZE, scalePercent),
                    Math.max(0, appearance.cornerRadius()),
                    Math.max(0, appearance.contextMenu().borderWidth()),
                    1,
                    scale(3, scalePercent),
                    scale(6, scalePercent),
                    scale(42, scalePercent),
                    scale(76, scalePercent),
                    scale(18, scalePercent),
                    scale(28, scalePercent),
                    scale(18, scalePercent),
                    scale(2, scalePercent),
                    scale(2, scalePercent),
                    scale(14, scalePercent));
        }

        private int rowStep() {
            return cellSize + gap;
        }

        private static int scale(int value, int percent) {
            return Math.max(1, Math.round(value * percent / 100.0F));
        }
    }
}