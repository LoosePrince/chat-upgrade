package com.chat.upgrade.client.ui.screen;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.MinecraftGuiBridge;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.ui.chat.state.ChatAvatar;
import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceRuntime;
import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.animation.UiMotion;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.render.UiPrimitives;
import com.chat.upgrade.client.ui.render.UiTextureAtlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Modal, scrollable profile/file details view opened from the chat context menu. */
public final class ChatDetailsScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 560;
    private static final int PANEL_MAX_HEIGHT = 420;
    private static final int HERO_HEIGHT = 120;
    private static final int FOOTER_HEIGHT = 42;
    private static final int SECTION_HEIGHT = 22;
    private static final int ROW_HEIGHT = 21;
    private static final int PANEL_PADDING = 12;
    private static final int BUTTON_HEIGHT = 24;
    private static final long COPIED_FEEDBACK_MS = 1_500L;

    private final @Nullable Screen parent;
    private final ChatDetailsModel model;
    private int scrollOffset;
    private String copiedKey = "";
    private long copiedUntilMs;

    private ChatDetailsScreen(@Nullable Screen parent, ChatDetailsModel model) {
        super(Component.translatable("chatupgrade.details.screen.title"));
        this.parent = parent;
        this.model = model;
        UiMotion.begin(UiMotion.CHAT_DETAILS);
    }

    public static void openProfile(Minecraft minecraft, RichChatMessage message) {
        if (minecraft == null || message == null) {
            return;
        }
        Screen parent = MinecraftGuiBridge.currentScreen(minecraft);
        MinecraftGuiBridge.setScreen(minecraft, new ChatDetailsScreen(parent, ChatDetailsModelFactory.profile(message)));
    }

    public static void openAttachment(Minecraft minecraft, RichChatMessage message,
            com.chat.upgrade.client.media.model.RichAttachment attachment) {
        if (minecraft == null || message == null || attachment == null) {
            return;
        }
        Screen parent = MinecraftGuiBridge.currentScreen(minecraft);
        MinecraftGuiBridge.setScreen(
                minecraft,
                new ChatDetailsScreen(parent, ChatDetailsModelFactory.attachment(message, attachment)));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, ChatAppearanceRuntime.current().media().scrim());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        ChatAppearanceSnapshot appearance = ChatAppearanceRuntime.current();
        ChatAppearanceSnapshot.Media tokens = appearance.media();
        Layout layout = layout();
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll(layout));
        var motionPose = graphics.pose();
        motionPose.pushMatrix();
        motionPose.translate(0, UiMotion.enterFromBottom(UiMotion.CHAT_DETAILS, 16));

        UiPrimitives.paintBox(
                graphics,
                layout.panel(),
                Math.max(4, appearance.cornerRadius()),
                1,
                tokens.cardBackground(),
                tokens.cardBorder());
        paintHero(graphics, layout, appearance);
        paintSections(graphics, layout, appearance, mouseX, mouseY);
        paintFooter(graphics, layout, appearance, mouseX, mouseY);
        motionPose.popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }
        Layout layout = layout();
        if (!contains(layout.panel(), event.x(), event.y())) {
            onClose();
            return true;
        }
        if (contains(layout.close(), event.x(), event.y())) {
            onClose();
            return true;
        }
        if (contains(layout.copyAll(), event.x(), event.y())) {
            copy("all", model.copyText());
            return true;
        }
        if (layout.preview() != null && contains(layout.preview(), event.x(), event.y())) {
            openPreview();
            return true;
        }
        ChatDetailsModel.Field field = fieldAt(layout, event.x(), event.y());
        if (field != null && !field.copyValue().isBlank()) {
            copy(field.key(), field.copyValue());
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout layout = layout();
        if (!contains(layout.body(), mouseX, mouseY) || maxScroll(layout) <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scrollOffset = Math.clamp(scrollOffset - (int) Math.round(scrollY * ROW_HEIGHT * 1.5D), 0, maxScroll(layout));
        return true;
    }

    @Override
    public void onClose() {
        UiMotion.end(UiMotion.CHAT_DETAILS);
        MinecraftGuiBridge.setScreen(Minecraft.getInstance(), parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void paintHero(
            GuiGraphicsExtractor graphics,
            Layout layout,
            ChatAppearanceSnapshot appearance) {
        ChatAppearanceSnapshot.Media tokens = appearance.media();
        RichChatBounds hero = layout.hero();
        graphics.fill(hero.left(), hero.top(), hero.right(), hero.bottom(), tokens.mediaBackground());
        graphics.fill(hero.left(), hero.top(), hero.left() + 4, hero.bottom(), model.accentColor());

        paintHeroVisual(graphics, layout.avatar(), tokens);

        String title = font.plainSubstrByWidth(model.title(), Math.max(1, layout.title().width()));
        String subtitle = font.plainSubstrByWidth(model.subtitle(), Math.max(1, layout.subtitle().width()));
        graphics.text(font, title, layout.title().left(), layout.title().top(), tokens.text(), false);
        graphics.text(font, subtitle, layout.subtitle().left(), layout.subtitle().top(), tokens.muted(), false);
        paintBadge(graphics, layout.badge(), model.badge(), tokens, appearance.cornerRadius());
        paintStats(graphics, layout, tokens, appearance.cornerRadius());
        MediaPreviewChrome.paintIconButton(
                graphics,
                layout.close(),
                UiTextureAtlas.Icon.CLOSE,
                tokens,
                appearance.cornerRadius());
    }

    private void paintHeroVisual(
            GuiGraphicsExtractor graphics,
            RichChatBounds bounds,
            ChatAppearanceSnapshot.Media tokens) {
        ChatAvatar playerAvatar = model.hero().playerAvatar();
        int radius = Math.min(bounds.width(), bounds.height()) / 2;
        int fill = playerAvatar == null
                ? UiPrimitives.withOpacity(model.accentColor(), 0.34F)
                : 0xFF000000 | playerAvatar.backgroundRgb();
        UiPrimitives.fillRounded(graphics, bounds, radius, fill);

        Runnable paintVisual;
        if (playerAvatar != null) {
            if (playerAvatar.skinTexture() != null) {
                paintVisual = () -> UiTextureAtlas.drawPlayerHead(
                        graphics,
                        playerAvatar.skinTexture(),
                        bounds,
                        0xFFFFFFFF);
            } else {
                paintVisual = () -> graphics.centeredText(
                        font,
                        playerAvatar.glyph(),
                        bounds.left() + bounds.width() / 2,
                        bounds.top() + Math.max(1, (bounds.height() - font.lineHeight) / 2),
                        0xFF000000 | playerAvatar.foregroundRgb());
            }
        } else {
            RichChatBounds icon = RichChatBounds.ofSize(
                    bounds.left() + 11,
                    bounds.top() + 11,
                    Math.max(1, bounds.width() - 22),
                    Math.max(1, bounds.height() - 22));
            UiTextureAtlas.Icon iconType = switch (model.hero().mediaType()) {
                case IMAGE -> UiTextureAtlas.Icon.IMAGE;
                case AUDIO -> UiTextureAtlas.Icon.AUDIO;
                case VIDEO -> UiTextureAtlas.Icon.VIDEO;
            };
            paintVisual = () -> UiTextureAtlas.drawIcon(graphics, iconType, icon, tokens.text());
        }
        paintVisual.run();
        UiPrimitives.strokeRounded(graphics, bounds, radius, 1, model.accentColor());
    }

    private void paintBadge(
            GuiGraphicsExtractor graphics,
            RichChatBounds bounds,
            String text,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        UiPrimitives.fillRounded(
                graphics,
                bounds,
                Math.min(cornerRadius, bounds.height() / 2),
                UiPrimitives.withOpacity(model.accentColor(), 0.22F));
        String visible = font.plainSubstrByWidth(text, Math.max(1, bounds.width() - 8));
        graphics.centeredText(
                font,
                visible,
                bounds.left() + bounds.width() / 2,
                bounds.top() + Math.max(1, (bounds.height() - font.lineHeight) / 2),
                tokens.text());
    }

    private void paintStats(
            GuiGraphicsExtractor graphics,
            Layout layout,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius) {
        String[] values = {
                model.stats().primary(),
                model.stats().secondary(),
                model.stats().tertiary()
        };
        String[] labels = model.kind() == ChatDetailsModel.Kind.PROFILE
                ? new String[] {
                        I18n.get("chatupgrade.details.stat.messages"),
                        I18n.get("chatupgrade.details.stat.media"),
                        I18n.get("chatupgrade.details.stat.sources")
                }
                : new String[] {
                        I18n.get("chatupgrade.details.stat.related"),
                        I18n.get("chatupgrade.details.stat.size"),
                        I18n.get("chatupgrade.details.stat.media_info")
                };
        for (int index = 0; index < layout.stats().length; index++) {
            RichChatBounds bounds = layout.stats()[index];
            UiPrimitives.fillRounded(
                    graphics,
                    bounds,
                    Math.min(cornerRadius, bounds.height() / 2),
                    tokens.controlBackground());
            String value = font.plainSubstrByWidth(values[index], Math.max(1, bounds.width() - 8));
            String label = font.plainSubstrByWidth(labels[index], Math.max(1, bounds.width() - 8));
            graphics.centeredText(
                    font,
                    value,
                    bounds.left() + bounds.width() / 2,
                    bounds.top() + 4,
                    tokens.text());
            graphics.centeredText(
                    font,
                    label,
                    bounds.left() + bounds.width() / 2,
                    bounds.top() + 16,
                    tokens.muted());
        }
    }

    private void paintSections(
            GuiGraphicsExtractor graphics,
            Layout layout,
            ChatAppearanceSnapshot appearance,
            int mouseX,
            int mouseY) {
        ChatAppearanceSnapshot.Media tokens = appearance.media();
        int contentY = layout.body().top() - scrollOffset;
        graphics.enableScissor(
                layout.body().left(),
                layout.body().top(),
                layout.body().right(),
                layout.body().bottom());
        try {
            for (ChatDetailsModel.Section section : model.sections()) {
                if (contentY + SECTION_HEIGHT > layout.body().top() && contentY < layout.body().bottom()) {
                    graphics.text(
                            font,
                            section.title(),
                            layout.body().left() + 4,
                            contentY + 7,
                            model.accentColor(),
                            false);
                }
                contentY += SECTION_HEIGHT;
                for (ChatDetailsModel.Field field : section.fields()) {
                    RichChatBounds row = RichChatBounds.ofSize(
                            layout.body().left(),
                            contentY,
                            layout.body().width() - (maxScroll(layout) > 0 ? 5 : 0),
                            ROW_HEIGHT);
                    if (row.bottom() > layout.body().top() && row.top() < layout.body().bottom()) {
                        paintFieldRow(graphics, row, field, tokens, appearance.cornerRadius(), mouseX, mouseY);
                    }
                    contentY += ROW_HEIGHT;
                }
            }
            paintScrollbar(graphics, layout, tokens);
        } finally {
            graphics.disableScissor();
        }
    }

    private void paintFieldRow(
            GuiGraphicsExtractor graphics,
            RichChatBounds row,
            ChatDetailsModel.Field field,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius,
            int mouseX,
            int mouseY) {
        boolean hovered = row.contains(mouseX, mouseY);
        if (hovered) {
            UiPrimitives.fillRounded(
                    graphics,
                    row,
                    Math.min(cornerRadius, row.height() / 2),
                    tokens.controlHoverBackground());
        }
        int labelWidth = Math.min(116, Math.max(72, row.width() / 4));
        String label = font.plainSubstrByWidth(field.label(), Math.max(1, labelWidth - 8));
        String copyLabel = copied(field.key())
                ? I18n.get("chatupgrade.details.copied")
                : I18n.get("chatupgrade.details.copy");
        int copyWidth = field.copyValue().isBlank() ? 0 : font.width(copyLabel) + 8;
        int valueWidth = Math.max(1, row.width() - labelWidth - copyWidth - 12);
        String value = font.plainSubstrByWidth(field.value(), valueWidth);
        int textY = row.top() + Math.max(1, (ROW_HEIGHT - font.lineHeight) / 2);
        graphics.text(font, label, row.left() + 6, textY, tokens.muted(), false);
        graphics.text(font, value, row.left() + labelWidth, textY, tokens.text(), false);
        if (copyWidth > 0) {
            graphics.text(
                    font,
                    copyLabel,
                    row.right() - copyWidth,
                    textY,
                    hovered || copied(field.key()) ? model.accentColor() : tokens.muted(),
                    false);
        }
    }

    private void paintScrollbar(
            GuiGraphicsExtractor graphics,
            Layout layout,
            ChatAppearanceSnapshot.Media tokens) {
        int max = maxScroll(layout);
        if (max <= 0) {
            return;
        }
        RichChatBounds track = RichChatBounds.ofSize(
                layout.body().right() - 3,
                layout.body().top(),
                2,
                layout.body().height());
        graphics.fill(track.left(), track.top(), track.right(), track.bottom(), tokens.progressTrack());
        int thumbHeight = Math.max(18, Math.round(track.height() * (layout.body().height() / (float) contentHeight())));
        int travel = Math.max(0, track.height() - thumbHeight);
        int thumbTop = track.top() + Math.round(travel * (scrollOffset / (float) max));
        UiPrimitives.fillRounded(
                graphics,
                RichChatBounds.ofSize(track.left(), thumbTop, track.width(), thumbHeight),
                1,
                tokens.progressFill());
    }

    private void paintFooter(
            GuiGraphicsExtractor graphics,
            Layout layout,
            ChatAppearanceSnapshot appearance,
            int mouseX,
            int mouseY) {
        ChatAppearanceSnapshot.Media tokens = appearance.media();
        graphics.fill(
                layout.footer().left() + PANEL_PADDING,
                layout.footer().top(),
                layout.footer().right() - PANEL_PADDING,
                layout.footer().top() + 1,
                tokens.cardBorder());
        paintTextButton(
                graphics,
                layout.copyAll(),
                copied("all")
                        ? I18n.get("chatupgrade.details.copied")
                        : I18n.get("chatupgrade.details.copy_all"),
                tokens,
                appearance.cornerRadius(),
                layout.copyAll().contains(mouseX, mouseY));
        if (layout.preview() != null) {
            paintTextButton(
                    graphics,
                    layout.preview(),
                    I18n.get("chatupgrade.details.open_preview"),
                    tokens,
                    appearance.cornerRadius(),
                    layout.preview().contains(mouseX, mouseY));
        }
    }

    private void paintTextButton(
            GuiGraphicsExtractor graphics,
            RichChatBounds bounds,
            String label,
            ChatAppearanceSnapshot.Media tokens,
            int cornerRadius,
            boolean hovered) {
        UiPrimitives.fillRounded(
                graphics,
                bounds,
                Math.min(cornerRadius, bounds.height() / 2),
                hovered ? tokens.controlHoverBackground() : tokens.controlBackground());
        String visible = font.plainSubstrByWidth(label, Math.max(1, bounds.width() - 12));
        graphics.centeredText(
                font,
                visible,
                bounds.left() + bounds.width() / 2,
                bounds.top() + Math.max(1, (bounds.height() - font.lineHeight) / 2),
                tokens.text());
    }

    private @Nullable ChatDetailsModel.Field fieldAt(Layout layout, double mouseX, double mouseY) {
        if (!contains(layout.body(), mouseX, mouseY)) {
            return null;
        }
        int contentY = layout.body().top() - scrollOffset;
        for (ChatDetailsModel.Section section : model.sections()) {
            contentY += SECTION_HEIGHT;
            for (ChatDetailsModel.Field field : section.fields()) {
                RichChatBounds row = RichChatBounds.ofSize(
                        layout.body().left(),
                        contentY,
                        layout.body().width(),
                        ROW_HEIGHT);
                if (contains(row, mouseX, mouseY)) {
                    return field;
                }
                contentY += ROW_HEIGHT;
            }
        }
        return null;
    }

    private void openPreview() {
        ChatDetailsModel.Preview preview = model.preview();
        if (preview == null) {
            return;
        }
        if (preview.type() == InlineResourceType.IMAGE) {
            ImagePreviewScreen.open(preview.url(), preview.displayName());
        } else if (preview.type() == InlineResourceType.VIDEO) {
            VideoPreviewScreen.open(preview.url(), preview.displayName());
        }
    }

    private void copy(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.keyboardHandler.setClipboard(value);
        copiedKey = key;
        copiedUntilMs = Util.getMillis() + COPIED_FEEDBACK_MS;
    }

    private boolean copied(String key) {
        return key.equals(copiedKey) && Util.getMillis() < copiedUntilMs;
    }

    private int contentHeight() {
        int height = 0;
        for (ChatDetailsModel.Section section : model.sections()) {
            height += SECTION_HEIGHT + section.fields().size() * ROW_HEIGHT;
        }
        return height;
    }

    private int maxScroll(Layout layout) {
        return Math.max(0, contentHeight() - layout.body().height());
    }

    private Layout layout() {
        int panelWidth = Math.min(PANEL_MAX_WIDTH, Math.max(240, width - 24));
        int panelHeight = Math.min(PANEL_MAX_HEIGHT, Math.max(210, height - 24));
        int left = Math.max(4, (width - panelWidth) / 2);
        int top = Math.max(4, (height - panelHeight) / 2);
        RichChatBounds panel = RichChatBounds.ofSize(left, top, panelWidth, panelHeight);
        RichChatBounds hero = new RichChatBounds(left, top, panel.right(), Math.min(panel.bottom(), top + HERO_HEIGHT));
        RichChatBounds footer = new RichChatBounds(
                left,
                Math.max(hero.bottom(), panel.bottom() - FOOTER_HEIGHT),
                panel.right(),
                panel.bottom());
        RichChatBounds body = new RichChatBounds(
                left + PANEL_PADDING,
                hero.bottom(),
                Math.max(left + PANEL_PADDING, panel.right() - PANEL_PADDING),
                footer.top());
        RichChatBounds close = RichChatBounds.ofSize(panel.right() - PANEL_PADDING - 22, panel.top() + 10, 22, 22);
        RichChatBounds avatar = RichChatBounds.ofSize(panel.left() + 18, panel.top() + 14, 52, 52);
        RichChatBounds title = new RichChatBounds(
                avatar.right() + 13,
                panel.top() + 19,
                Math.max(avatar.right() + 14, close.left() - 8),
                panel.top() + 31);
        RichChatBounds subtitle = new RichChatBounds(
                title.left(),
                panel.top() + 36,
                title.right(),
                panel.top() + 47);
        int badgeWidth = Math.min(110, Math.max(58, font.width(model.badge()) + 12));
        RichChatBounds badge = RichChatBounds.ofSize(title.left(), panel.top() + 52, badgeWidth, 16);

        int statsLeft = panel.left() + 18;
        int statsRight = panel.right() - 18;
        int statsGap = 6;
        int statsWidth = Math.max(1, (statsRight - statsLeft - statsGap * 2) / 3);
        RichChatBounds[] stats = new RichChatBounds[3];
        for (int index = 0; index < stats.length; index++) {
            int x = statsLeft + index * (statsWidth + statsGap);
            int right = index == stats.length - 1 ? statsRight : x + statsWidth;
            stats[index] = new RichChatBounds(x, panel.top() + 78, right, panel.top() + 110);
        }

        int buttonTop = footer.top() + Math.max(1, (footer.height() - BUTTON_HEIGHT) / 2);
        int previewWidth = model.preview() == null ? 0 : 112;
        RichChatBounds preview = model.preview() == null
                ? null
                : RichChatBounds.ofSize(footer.right() - PANEL_PADDING - previewWidth, buttonTop, previewWidth, BUTTON_HEIGHT);
        int copyRight = preview == null ? footer.right() - PANEL_PADDING : preview.left() - 6;
        RichChatBounds copyAll = RichChatBounds.ofSize(Math.max(footer.left() + PANEL_PADDING, copyRight - 98),
                buttonTop, 98, BUTTON_HEIGHT);
        return new Layout(panel, hero, body, footer, avatar, title, subtitle, badge, close, stats, copyAll, preview);
    }

    private static boolean contains(RichChatBounds bounds, double x, double y) {
        return bounds != null
                && x >= bounds.left()
                && x < bounds.right()
                && y >= bounds.top()
                && y < bounds.bottom();
    }

    private record Layout(
            RichChatBounds panel,
            RichChatBounds hero,
            RichChatBounds body,
            RichChatBounds footer,
            RichChatBounds avatar,
            RichChatBounds title,
            RichChatBounds subtitle,
            RichChatBounds badge,
            RichChatBounds close,
            RichChatBounds[] stats,
            RichChatBounds copyAll,
            @Nullable RichChatBounds preview) {
    }
}