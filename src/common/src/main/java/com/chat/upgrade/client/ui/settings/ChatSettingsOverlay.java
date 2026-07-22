package com.chat.upgrade.client.ui.settings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ChatClientConfigRuntime;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.ui.chat.surface.ChatPanelGeometry;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceController;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceState;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.render.UiPrimitives;
import com.chat.upgrade.client.ui.render.UiTextureAtlas;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** Owns settings draft state, rendering, hit testing, preview, commit, and rollback. */
public final class ChatSettingsOverlay {
    private static final int SCREEN_MARGIN = 8;
    private static final int MAX_WIDTH = 700;
    private static final int MAX_HEIGHT = 450;
    private static final int HEADER_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 34;
    private static final int NAV_WIDTH = 132;
    private static final int OPTION_GAP = 3;
    private static final int OPTION_HEIGHT = 25;
    private static final int DESCRIBED_OPTION_HEIGHT = 40;
    private static final int COLOR_OPTION_HEIGHT = 50;
    private static final int TEXT_OPTION_HEIGHT = 59;
    private static final int HEADING_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 18;
    private static final int PLACEHOLDER_MAX_LENGTH = 256;
    private static final float TEXT_SCALE = 0.75F;

    private static final int DIM = 0xA0000000;
    private static final int PANEL = 0xF2181D26;
    private static final int PANEL_BORDER = 0xFF526176;
    private static final int HEADER = 0xFF202936;
    private static final int NAV = 0xFF141922;
    private static final int ROW = 0xA8252E3C;
    private static final int ROW_HOVER = 0xD0334153;
    private static final int ACTIVE = 0xFF4C6284;
    private static final int CONTROL = 0xFF11161D;
    private static final int TRACK = 0xFF3B4657;
    private static final int TEXT = 0xFFF2F5FA;
    private static final int MUTED = 0xFFA9B4C3;
    private static final int ERROR = 0xFFFF8585;

    private boolean open;
    private SettingsCategory category = SettingsCategory.APPEARANCE;
    private ChatUpgradeConfig baseline;
    private ChatUpgradeConfig draft;
    private double scrollY;
    private @Nullable ActiveSlider activeSlider;
    private @Nullable EditBox textEditor;
    private @Nullable SettingsOption.TextOption visibleTextOption;
    private boolean syncingTextEditor;
    private @Nullable String errorMessage;
    private int screenWidth = 1;
    private int screenHeight = 1;

    public boolean isOpen() {
        return open;
    }

    public void open(Font font, int width, int height) {
        screenWidth = Math.max(1, width);
        screenHeight = Math.max(1, height);
        baseline = ChatClientConfigRuntime.draft();
        draft = ChatClientConfigRuntime.draft();
        category = SettingsCategory.APPEARANCE;
        scrollY = 0.0D;
        activeSlider = null;
        errorMessage = null;
        open = true;
        createTextEditor(font);
        ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.SETTINGS);
        ChatSurfaceController.setFocusOwner(ChatSurfaceState.FocusOwner.OVERLAY);
        previewDraft();
    }

    public void cancel() {
        if (!open) {
            return;
        }
        ChatClientConfigRuntime.restorePreviewBaseline(baseline);
        ChatSurfaceController.previewPanelGeometry(baseline, screenWidth, screenHeight);
        finishClose();
    }

    public boolean save() {
        if (!open || draft == null) {
            return false;
        }
        try {
            ChatClientConfigRuntime.save(draft);
            ChatSurfaceController.previewPanelGeometry(ChatUpgradeConfig.get(), screenWidth, screenHeight);
            finishClose();
            return true;
        } catch (IOException exception) {
            errorMessage = I18n.get("chatupgrade.settings.error.save", exception.getMessage());
            return false;
        }
    }

    public boolean keyPressed(KeyEvent event) {
        if (!open) {
            return false;
        }
        if (event.isEscape()) {
            cancel();
            return true;
        }
        if (textEditor != null && textEditor.visible && textEditor.isFocused()) {
            textEditor.keyPressed(event);
        }
        return true;
    }

    public boolean charTyped(CharacterEvent event) {
        if (!open) {
            return false;
        }
        if (textEditor != null && textEditor.visible && textEditor.isFocused()) {
            textEditor.charTyped(event);
        }
        return true;
    }

    public boolean preeditUpdated(PreeditEvent event) {
        if (!open) {
            return false;
        }
        if (textEditor != null && textEditor.visible && textEditor.isFocused()) {
            textEditor.preeditUpdated(event);
        }
        return true;
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick, int width, int height) {
        if (!open) {
            return false;
        }
        updateScreenSize(width, height);
        if (event.button() != 0) {
            return true;
        }
        Layout layout = layout();
        if (layout.closeButton().contains(round(event.x()), round(event.y()))) {
            cancel();
            return true;
        }
        if (layout.saveButton().contains(round(event.x()), round(event.y()))) {
            save();
            return true;
        }
        if (layout.cancelButton().contains(round(event.x()), round(event.y()))) {
            cancel();
            return true;
        }
        if (layout.resetButton().contains(round(event.x()), round(event.y()))) {
            resetCurrentCategory();
            return true;
        }
        SettingsCategory selected = categoryAt(event.x(), event.y(), layout);
        if (selected != null) {
            category = selected;
            scrollY = 0.0D;
            activeSlider = null;
            setTextEditorFocused(false);
            layoutTextEditor(layout());
            errorMessage = null;
            return true;
        }
        layoutTextEditor(layout);
        if (textEditor != null && textEditor.visible) {
            if (textEditor.isMouseOver(event.x(), event.y())) {
                textEditor.setFocused(true);
                textEditor.mouseClicked(event, doubleClick);
                return true;
            }
            textEditor.setFocused(false);
        }
        for (OptionRow row : optionRows(layout)) {
            if (!row.bounds().contains(round(event.x()), round(event.y()))) {
                continue;
            }
            activateOption(row, event.x(), event.y());
            return true;
        }
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!open || event.button() != 0) {
            return false;
        }
        if (textEditor != null && textEditor.visible && textEditor.isFocused()) {
            textEditor.mouseDragged(event, dx, dy);
        }
        if (activeSlider != null) {
            updateSlider(activeSlider, event.x());
        }
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (!open) {
            return false;
        }
        if (textEditor != null && textEditor.visible && textEditor.isFocused()) {
            textEditor.mouseReleased(event);
        }
        if (event.button() == 0) {
            activeSlider = null;
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollAmount, int width, int height) {
        if (!open) {
            return false;
        }
        updateScreenSize(width, height);
        Layout layout = layout();
        if (layout.optionsViewport().contains(round(mouseX), round(mouseY))) {
            double maxScroll = maxScroll(layout);
            scrollY = Math.clamp(scrollY - scrollAmount * 20.0D, 0.0D, maxScroll);
            layoutTextEditor(layout);
        }
        return true;
    }

    public void render(
            GuiGraphicsExtractor graphics,
            Font font,
            int mouseX,
            int mouseY,
            int width,
            int height) {
        if (!open || graphics == null || font == null) {
            return;
        }
        updateScreenSize(width, height);
        Layout layout = layout();
        layoutTextEditor(layout);
        graphics.fill(0, 0, screenWidth, screenHeight, DIM);
        UiPrimitives.paintBox(graphics, layout.panel(), 7, 1, PANEL, PANEL_BORDER);
        graphics.fill(
                layout.panel().left() + 1,
                layout.panel().top() + 1,
                layout.panel().right() - 1,
                layout.headerBottom(),
                HEADER);
        paintText(
                graphics,
                font,
                I18n.get("chatupgrade.settings.title"),
                layout.panel().left() + 10,
                layout.panel().top() + 10,
                TEXT);
        paintCloseButton(graphics, layout.closeButton(), mouseX, mouseY);
        paintCategories(graphics, font, layout, mouseX, mouseY);
        paintOptions(graphics, font, layout, mouseX, mouseY);
        paintFooter(graphics, font, layout, mouseX, mouseY);
    }

    private void paintCloseButton(
            GuiGraphicsExtractor graphics,
            RichChatBounds bounds,
            int mouseX,
            int mouseY) {
        if (bounds.contains(mouseX, mouseY)) {
            UiPrimitives.fillRounded(graphics, bounds, 3, ROW_HOVER);
        }
        UiTextureAtlas.drawIcon(graphics, UiTextureAtlas.Icon.CLOSE, inset(bounds, 3), TEXT);
    }

    private void paintCategories(
            GuiGraphicsExtractor graphics,
            Font font,
            Layout layout,
            int mouseX,
            int mouseY) {
        graphics.fill(
                layout.panel().left() + 1,
                layout.headerBottom(),
                layout.navRight(),
                layout.footerTop(),
                NAV);
        int y = layout.headerBottom() + 8;
        for (SettingsCategory value : SettingsCategory.values()) {
            RichChatBounds bounds = RichChatBounds.ofSize(
                    layout.panel().left() + 6,
                    y,
                    layout.navRight() - layout.panel().left() - 12,
                    24);
            boolean selected = value == category;
            boolean hover = bounds.contains(mouseX, mouseY);
            if (selected || hover) {
                UiPrimitives.fillRounded(graphics, bounds, 4, selected ? ACTIVE : ROW_HOVER);
            }
            paintText(
                    graphics,
                    font,
                    I18n.get(value.labelKey()),
                    bounds.left() + 7,
                    bounds.top() + 8,
                    selected ? TEXT : MUTED);
            y += 28;
        }
    }

    private void paintOptions(
            GuiGraphicsExtractor graphics,
            Font font,
            Layout layout,
            int mouseX,
            int mouseY) {
        graphics.enableScissor(
                layout.optionsViewport().left(),
                layout.optionsViewport().top(),
                layout.optionsViewport().right(),
                layout.optionsViewport().bottom());
        try {
            for (OptionRow row : optionRows(layout)) {
                if (!intersects(row.bounds(), layout.optionsViewport())) {
                    continue;
                }
                if (row.option() instanceof SettingsOption.HeadingOption heading) {
                    paintText(
                            graphics,
                            font,
                            I18n.get(heading.labelKey()),
                            row.bounds().left() + 2,
                            row.bounds().top() + 7,
                            0xFF8FB8EC);
                    graphics.fill(
                            row.bounds().left(),
                            row.bounds().bottom() - 1,
                            row.bounds().right(),
                            row.bounds().bottom(),
                            0x553F516A);
                    continue;
                }
                if (row.option() instanceof SettingsOption.TextOption textOption) {
                    int background = row.bounds().contains(mouseX, mouseY) ? ROW_HOVER : ROW;
                    UiPrimitives.fillRounded(graphics, row.bounds(), 4, background);
                    paintText(
                            graphics,
                            font,
                            I18n.get(textOption.labelKey()),
                            row.bounds().left() + 7,
                            row.bounds().top() + 7,
                            TEXT);
                    paintText(
                            graphics,
                            font,
                            trim(font, I18n.get(textOption.descriptionKey()), row.bounds().width() - 14),
                            row.bounds().left() + 7,
                            row.bounds().top() + 21,
                            MUTED);
                    if (textEditor != null && textEditor.visible) {
                        textEditor.extractWidgetRenderState(graphics, mouseX, mouseY, 0.0F);
                    }
                    continue;
                }
                int background = row.bounds().contains(mouseX, mouseY) ? ROW_HOVER : ROW;
                UiPrimitives.fillRounded(graphics, row.bounds(), 4, background);
                boolean hasDescription = row.option() instanceof SettingsOption.BooleanOption booleanOption
                        && !booleanOption.descriptionKey().isBlank();
                paintText(
                        graphics,
                        font,
                        I18n.get(row.option().labelKey()),
                        row.bounds().left() + 7,
                        row.bounds().top() + (hasDescription ? 7 : 9),
                        TEXT);
                if (hasDescription) {
                    SettingsOption.BooleanOption booleanOption = (SettingsOption.BooleanOption) row.option();
                    paintText(
                            graphics,
                            font,
                            trim(font, I18n.get(booleanOption.descriptionKey()), row.control().left() - row.bounds().left() - 14),
                            row.bounds().left() + 7,
                            row.bounds().top() + 22,
                            MUTED);
                }
                paintOptionControl(graphics, font, row, mouseX, mouseY);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void paintOptionControl(
            GuiGraphicsExtractor graphics,
            Font font,
            OptionRow row,
            int mouseX,
            int mouseY) {
        SettingsOption option = row.option();
        RichChatBounds control = row.control();
        if (option instanceof SettingsOption.BooleanOption booleanOption) {
            boolean enabled = booleanOption.getter().getAsBoolean();
            UiPrimitives.fillRounded(graphics, control, 7, enabled ? ACTIVE : CONTROL);
            if (enabled) {
                UiTextureAtlas.drawIcon(
                        graphics,
                        UiTextureAtlas.Icon.CHECK,
                        RichChatBounds.ofSize(control.left() + 2, control.top() + 1, 14, 14),
                        TEXT);
            }
            String value = I18n.get(enabled ? "chatupgrade.common.on" : "chatupgrade.common.off");
            paintText(graphics, font, value, control.left() + 18, control.top() + 5, enabled ? TEXT : MUTED);
            return;
        }
        if (option instanceof SettingsOption.EnumOption enumOption) {
            UiPrimitives.fillRounded(graphics, control, 4, control.contains(mouseX, mouseY) ? ACTIVE : CONTROL);
            int index = Math.floorMod(enumOption.selectedIndex().getAsInt(), enumOption.valueLabelKeys().size());
            String value = I18n.get(enumOption.valueLabelKeys().get(index));
            paintCenteredText(graphics, font, value, control.left() + control.width() / 2, control.top() + 5, TEXT);
            return;
        }
        if (option instanceof SettingsOption.IntOption intOption) {
            paintIntSlider(graphics, font, intOption, control);
            return;
        }
        if (option instanceof SettingsOption.ColorOption colorOption) {
            paintColorControl(graphics, font, colorOption, row);
        }
    }

    private void paintIntSlider(
            GuiGraphicsExtractor graphics,
            Font font,
            SettingsOption.IntOption option,
            RichChatBounds track) {
        int value = Math.clamp(option.getter().getAsInt(), option.min(), option.max());
        int knobX = sliderX(value, option.min(), option.max(), track);
        graphics.fill(track.left(), track.top() + 6, track.right(), track.top() + 8, TRACK);
        graphics.fill(track.left(), track.top() + 6, knobX, track.top() + 8, ACTIVE);
        UiTextureAtlas.drawIcon(
                graphics,
                UiTextureAtlas.Icon.SLIDER_KNOB,
                RichChatBounds.ofSize(knobX - 5, track.top() + 1, 10, 10),
                TEXT);
        String text = formatValue(value, option.format());
        paintText(graphics, font, text, track.right() + 7, track.top() + 3, MUTED);
    }

    private void paintColorControl(
            GuiGraphicsExtractor graphics,
            Font font,
            SettingsOption.ColorOption option,
            OptionRow row) {
        int color = option.getter().getAsInt() & 0x00FFFFFF;
        RichChatBounds swatch = RichChatBounds.ofSize(row.bounds().left() + 7, row.bounds().top() + 25, 40, 14);
        UiPrimitives.paintBox(graphics, swatch, 3, 1, 0xFF000000 | color, PANEL_BORDER);
        paintText(graphics, font, String.format("#%06X", color), swatch.right() + 6, swatch.top() + 3, MUTED);
        for (int channel = 0; channel < 3; channel++) {
            RichChatBounds track = row.colorChannels().get(channel);
            int value = channelValue(color, channel);
            int knobX = sliderX(value, 0, 255, track);
            int channelColor = switch (channel) {
                case 0 -> 0xFFE45B5B;
                case 1 -> 0xFF65C37A;
                default -> 0xFF6598E8;
            };
            graphics.fill(track.left(), track.top() + 3, track.right(), track.top() + 5, TRACK);
            graphics.fill(track.left(), track.top() + 3, knobX, track.top() + 5, channelColor);
            UiTextureAtlas.drawIcon(
                    graphics,
                    UiTextureAtlas.Icon.SLIDER_KNOB,
                    RichChatBounds.ofSize(knobX - 4, track.top(), 8, 8),
                    TEXT);
        }
    }

    private void paintFooter(
            GuiGraphicsExtractor graphics,
            Font font,
            Layout layout,
            int mouseX,
            int mouseY) {
        graphics.fill(
                layout.navRight(),
                layout.footerTop(),
                layout.panel().right() - 1,
                layout.panel().bottom() - 1,
                HEADER);
        paintButton(graphics, font, layout.resetButton(), "chatupgrade.settings.reset_category", mouseX, mouseY, false);
        paintButton(graphics, font, layout.cancelButton(), "chatupgrade.settings.cancel", mouseX, mouseY, false);
        paintButton(graphics, font, layout.saveButton(), "chatupgrade.settings.save", mouseX, mouseY, true);
        if (errorMessage != null && !errorMessage.isBlank()) {
            int maxWidth = Math.max(20, layout.cancelButton().left() - layout.resetButton().right() - 12);
            String text = trim(font, errorMessage, maxWidth);
            paintText(graphics, font, text, layout.resetButton().right() + 6, layout.footerTop() + 13, ERROR);
        }
    }

    private void paintButton(
            GuiGraphicsExtractor graphics,
            Font font,
            RichChatBounds bounds,
            String labelKey,
            int mouseX,
            int mouseY,
            boolean primary) {
        int color = bounds.contains(mouseX, mouseY) ? ROW_HOVER : primary ? ACTIVE : CONTROL;
        UiPrimitives.fillRounded(graphics, bounds, 4, color);
        paintCenteredText(
                graphics,
                font,
                I18n.get(labelKey),
                bounds.left() + bounds.width() / 2,
                bounds.top() + 6,
                TEXT);
    }

    private void activateOption(OptionRow row, double mouseX, double mouseY) {
        SettingsOption option = row.option();
        if (option instanceof SettingsOption.TextOption && textEditor != null) {
            textEditor.setFocused(true);
            textEditor.setCursorPosition(textEditor.getValue().length());
            return;
        }
        if (option instanceof SettingsOption.BooleanOption booleanOption) {
            booleanOption.setter().accept(!booleanOption.getter().getAsBoolean());
            previewDraft();
            return;
        }
        if (option instanceof SettingsOption.EnumOption enumOption) {
            int size = enumOption.valueLabelKeys().size();
            enumOption.selectIndex().accept(Math.floorMod(enumOption.selectedIndex().getAsInt() + 1, size));
            previewDraft();
            return;
        }
        if (option instanceof SettingsOption.IntOption intOption && row.control().contains(round(mouseX), round(mouseY))) {
            activeSlider = new ActiveSlider(option, -1, row.control());
            updateSlider(activeSlider, mouseX);
            return;
        }
        if (option instanceof SettingsOption.ColorOption) {
            for (int channel = 0; channel < row.colorChannels().size(); channel++) {
                RichChatBounds track = row.colorChannels().get(channel);
                if (track.contains(round(mouseX), round(mouseY))) {
                    activeSlider = new ActiveSlider(option, channel, track);
                    updateSlider(activeSlider, mouseX);
                    return;
                }
            }
        }
    }

    private void updateSlider(ActiveSlider slider, double mouseX) {
        double progress = slider.track().width() <= 1
                ? 0.0D
                : Math.clamp((mouseX - slider.track().left()) / slider.track().width(), 0.0D, 1.0D);
        if (slider.option() instanceof SettingsOption.IntOption option) {
            int value = option.min() + (int) Math.round(progress * (option.max() - option.min()));
            option.setter().accept(value);
            previewDraft();
            return;
        }
        if (slider.option() instanceof SettingsOption.ColorOption option) {
            int value = Math.clamp((int) Math.round(progress * 255.0D), 0, 255);
            int color = option.getter().getAsInt() & 0x00FFFFFF;
            int shift = (2 - slider.channel()) * 8;
            int next = (color & ~(0xFF << shift)) | (value << shift);
            option.setter().accept(next);
            previewDraft();
        }
    }

    private List<OptionRow> optionRows(Layout layout) {
        List<OptionRow> rows = new ArrayList<>();
        int y = layout.optionsViewport().top() - (int) Math.round(scrollY);
        int controlRight = layout.optionsViewport().right() - 10;
        int controlWidth = Math.clamp(layout.optionsViewport().width() / 3, 94, 140);
        for (SettingsOption option : options()) {
            int height = optionHeight(option);
            RichChatBounds bounds = RichChatBounds.ofSize(
                    layout.optionsViewport().left() + 4,
                    y,
                    layout.optionsViewport().width() - 8,
                    height);
            RichChatBounds control;
            List<RichChatBounds> channels = List.of();
            if (option instanceof SettingsOption.BooleanOption) {
                control = RichChatBounds.ofSize(controlRight - 60, y + Math.max(5, (height - 16) / 2), 60, 16);
            } else if (option instanceof SettingsOption.TextOption) {
                control = RichChatBounds.ofSize(bounds.left() + 7, y + 36, Math.max(20, bounds.width() - 14), 18);
            } else if (option instanceof SettingsOption.EnumOption) {
                control = RichChatBounds.ofSize(controlRight - 124, y + 4, 124, 18);
            } else if (option instanceof SettingsOption.IntOption) {
                control = RichChatBounds.ofSize(controlRight - controlWidth - 48, y + 6, controlWidth, 12);
            } else if (option instanceof SettingsOption.ColorOption) {
                int trackLeft = controlRight - controlWidth;
                channels = List.of(
                        RichChatBounds.ofSize(trackLeft, y + 5, controlWidth, 8),
                        RichChatBounds.ofSize(trackLeft, y + 19, controlWidth, 8),
                        RichChatBounds.ofSize(trackLeft, y + 33, controlWidth, 8));
                control = RichChatBounds.ofSize(trackLeft, y + 4, controlWidth, 40);
            } else {
                control = RichChatBounds.ofSize(controlRight, y, 0, 0);
            }
            rows.add(new OptionRow(option, bounds, control, channels));
            y += height + OPTION_GAP;
        }
        return rows;
    }

    private List<SettingsOption> options() {
        if (draft == null) {
            return List.of();
        }
        return switch (category) {
            case APPEARANCE -> appearanceOptions();
            case CHAT_BEHAVIOR -> behaviorOptions();
            case MEDIA -> mediaOptions();
            case UPLOAD_COMPATIBILITY -> uploadCompatibilityOptions();
        };
    }

    private List<SettingsOption> appearanceOptions() {
        ChatUpgradeConfig.AppearanceConfig appearance = draft.appearance;
        ChatUpgradeConfig.ChatPanelConfig panel = draft.chatPanel;
        int availableWidth = Math.max(1, screenWidth - ChatPanelGeometry.SCREEN_MARGIN * 2);
        int availableHeight = Math.max(1, screenHeight - ChatPanelGeometry.SCREEN_MARGIN * 2);
        int minWidth = Math.min(ChatPanelGeometry.MIN_WIDTH, availableWidth);
        int minHeight = Math.min(ChatPanelGeometry.MIN_HEIGHT, availableHeight);
        return List.of(
                heading("chatupgrade.settings.group.panel"),
                color("chatupgrade.settings.option.panel_background", () -> appearance.panelBackgroundColor,
                        value -> appearance.panelBackgroundColor = value),
                integer("chatupgrade.settings.option.panel_opacity", () -> appearance.panelBackgroundOpacityPercent,
                        value -> appearance.panelBackgroundOpacityPercent = value, 0, 100, SettingsOption.ValueFormat.PERCENT),
                bool("chatupgrade.settings.option.panel_border", () -> appearance.panelBorderEnabled,
                        value -> appearance.panelBorderEnabled = value),
                integer("chatupgrade.settings.option.panel_border_width", () -> appearance.panelBorderWidth,
                        value -> appearance.panelBorderWidth = value, 1, 4, SettingsOption.ValueFormat.PIXELS),
                color("chatupgrade.settings.option.panel_border_color", () -> appearance.panelBorderColor,
                        value -> appearance.panelBorderColor = value),
                integer("chatupgrade.settings.option.corner_radius", () -> appearance.cornerRadius,
                        value -> appearance.cornerRadius = value, 0, 16, SettingsOption.ValueFormat.PIXELS),
                heading("chatupgrade.settings.group.messages"),
                color("chatupgrade.settings.option.message_background", () -> appearance.messageBackgroundColor,
                        value -> appearance.messageBackgroundColor = value),
                integer("chatupgrade.settings.option.message_background_opacity",
                        () -> appearance.messageBackgroundOpacityPercent,
                        value -> appearance.messageBackgroundOpacityPercent = value,
                        0,
                        100,
                        SettingsOption.ValueFormat.PERCENT),
                integer("chatupgrade.settings.option.message_gap", () -> appearance.messageGap,
                        value -> appearance.messageGap = value, 0, 16, SettingsOption.ValueFormat.PIXELS),
                integer("chatupgrade.settings.option.group_gap", () -> appearance.groupGap,
                        value -> appearance.groupGap = value, 0, 16, SettingsOption.ValueFormat.PIXELS),
                bool("chatupgrade.settings.option.vanilla_style_input", () -> appearance.vanillaStyleInput,
                        value -> appearance.vanillaStyleInput = value),
                bool("chatupgrade.settings.option.player_avatars", () -> appearance.showPlayerAvatars,
                        value -> appearance.showPlayerAvatars = value),
                bool("chatupgrade.settings.option.avatar_first_line_only", () -> appearance.avatarFirstLineOnly,
                        value -> appearance.avatarFirstLineOnly = value),
                bool("chatupgrade.settings.option.double_line", () -> appearance.doubleLineLayout,
                        value -> appearance.doubleLineLayout = value),
                bool("chatupgrade.settings.option.message_bubbles", () -> appearance.messageBubbles,
                        value -> appearance.messageBubbles = value),
                integer("chatupgrade.settings.option.bubble_padding", () -> appearance.bubblePadding,
                        value -> appearance.bubblePadding = value, 0, 16, SettingsOption.ValueFormat.PIXELS),
                color("chatupgrade.settings.option.bubble_color", () -> appearance.bubbleColor,
                        value -> appearance.bubbleColor = value),
                bool("chatupgrade.settings.option.bubble_border", () -> appearance.bubbleBorderEnabled,
                        value -> appearance.bubbleBorderEnabled = value),
                integer("chatupgrade.settings.option.bubble_border_width", () -> appearance.bubbleBorderWidth,
                        value -> appearance.bubbleBorderWidth = value, 1, 4, SettingsOption.ValueFormat.PIXELS),
                color("chatupgrade.settings.option.bubble_border_color", () -> appearance.bubbleBorderColor,
                        value -> appearance.bubbleBorderColor = value),
                bool("chatupgrade.settings.option.split_own_messages", () -> appearance.splitOwnMessages,
                        value -> appearance.splitOwnMessages = value),
                enumeration(
                        "chatupgrade.settings.option.non_player_alignment",
                        () -> appearance.nonPlayerAlignment.ordinal(),
                        index -> appearance.nonPlayerAlignment = ChatUpgradeConfig.NonPlayerAlignment.values()[index],
                        "chatupgrade.settings.value.left",
                        "chatupgrade.settings.value.center",
                        "chatupgrade.settings.value.right"),
                heading("chatupgrade.settings.group.geometry"),
                bool("chatupgrade.settings.option.panel_auto_height", panel::usesAutomaticHeight,
                        value -> panel.automaticHeight = value),
                integer("chatupgrade.settings.option.panel_left", () -> panel.left,
                        value -> panel.left = value, 0, Math.max(0, screenWidth - 1), SettingsOption.ValueFormat.PIXELS),
                integer("chatupgrade.settings.option.panel_bottom_offset", () -> panel.bottomOffset,
                        value -> {
                            panel.bottomOffset = value;
                            panel.automaticHeight = false;
                        }, 0, Math.max(0, screenHeight - 1), SettingsOption.ValueFormat.PIXELS),
                integer("chatupgrade.settings.option.panel_width", () -> panel.width,
                        value -> panel.width = value, minWidth, availableWidth, SettingsOption.ValueFormat.PIXELS),
                integer("chatupgrade.settings.option.panel_height", () -> panel.height,
                        value -> {
                            panel.height = value;
                            panel.automaticHeight = false;
                        }, minHeight, availableHeight, SettingsOption.ValueFormat.PIXELS),
                heading("chatupgrade.settings.group.context_menu"),
                integer("chatupgrade.settings.option.context_menu_scale", () -> appearance.contextMenuScalePercent,
                        value -> appearance.contextMenuScalePercent = value, 75, 150, SettingsOption.ValueFormat.PERCENT),
                color("chatupgrade.settings.option.context_menu_background", () -> appearance.contextMenuBackgroundColor,
                        value -> appearance.contextMenuBackgroundColor = value),
                bool("chatupgrade.settings.option.context_menu_border", () -> appearance.contextMenuBorderEnabled,
                        value -> appearance.contextMenuBorderEnabled = value),
                integer("chatupgrade.settings.option.context_menu_border_width", () -> appearance.contextMenuBorderWidth,
                        value -> appearance.contextMenuBorderWidth = value, 1, 4, SettingsOption.ValueFormat.PIXELS),
                color("chatupgrade.settings.option.context_menu_border_color", () -> appearance.contextMenuBorderColor,
                        value -> appearance.contextMenuBorderColor = value),
                integer("chatupgrade.settings.option.context_menu_corner_radius", () -> appearance.contextMenuCornerRadius,
                        value -> appearance.contextMenuCornerRadius = value, 0, 12, SettingsOption.ValueFormat.PIXELS));
    }

    private List<SettingsOption> behaviorOptions() {
        return List.of(
                text(
                        "chatupgrade.settings.option.input_placeholder",
                        "chatupgrade.settings.option.input_placeholder.description",
                        () -> draft.chatInputPlaceholder,
                        value -> draft.chatInputPlaceholder = value,
                        PLACEHOLDER_MAX_LENGTH),
                bool(
                        "chatupgrade.settings.option.chat_screen_mask",
                        "chatupgrade.settings.option.chat_screen_mask.description",
                        draft::usesChatScreenMask,
                        value -> draft.chatScreenMaskEnabled = value),
                bool("chatupgrade.settings.option.smooth_scroll", () -> Boolean.TRUE.equals(draft.smoothScrollEnabled),
                        value -> draft.smoothScrollEnabled = value),
                bool("chatupgrade.settings.option.debug_actions", () -> draft.debugChatActions,
                        value -> draft.debugChatActions = value));
    }

    private List<SettingsOption> mediaOptions() {
        return List.of(
                bool("chatupgrade.settings.option.compact_media_cards", () -> draft.compactMediaCards,
                        value -> draft.compactMediaCards = value),
                bool("chatupgrade.settings.option.manual_image", () -> draft.manualImageReveal,
                        value -> draft.manualImageReveal = value),
                bool("chatupgrade.settings.option.manual_audio", () -> draft.manualAudioReveal,
                        value -> draft.manualAudioReveal = value),
                bool("chatupgrade.settings.option.manual_video", () -> draft.manualVideoReveal,
                        value -> draft.manualVideoReveal = value),
                integer("chatupgrade.settings.option.max_receive", () -> toMebibytes(draft.maxReceiveBytes),
                        value -> draft.maxReceiveBytes = fromMebibytes(value), 1, 10, SettingsOption.ValueFormat.MEBIBYTES),
                integer("chatupgrade.settings.option.audio_volume", () -> draft.audioVolumePercent,
                        value -> draft.audioVolumePercent = value, 1, 100, SettingsOption.ValueFormat.PERCENT),
                integer("chatupgrade.settings.option.video_volume", () -> draft.videoVolumePercent,
                        value -> draft.videoVolumePercent = value, 1, 100, SettingsOption.ValueFormat.PERCENT));
    }

    private List<SettingsOption> uploadCompatibilityOptions() {
        return List.of(
                enumeration(
                        "chatupgrade.settings.option.upload_mode",
                        () -> draft.uploadMode.ordinal(),
                        index -> draft.uploadMode = ChatUpgradeConfig.UploadMode.values()[index],
                        "chatupgrade.settings.value.upload_auto",
                        "chatupgrade.settings.value.upload_server",
                        "chatupgrade.settings.value.upload_third_party"),
                integer("chatupgrade.settings.option.max_upload", () -> toMebibytes(draft.maxUploadBytes),
                        value -> draft.maxUploadBytes = fromMebibytes(value), 1, 10, SettingsOption.ValueFormat.MEBIBYTES),
                bool("chatupgrade.settings.option.ci_compatibility", () -> draft.ciCompatibility,
                        value -> draft.ciCompatibility = value),
                enumeration(
                        "chatupgrade.settings.option.chat_input_mode",
                        () -> draft.chatInputMode.ordinal(),
                        index -> draft.chatInputMode = ChatUpgradeConfig.ChatInputMode.values()[index],
                        "chatupgrade.settings.value.input_takeover",
                        "chatupgrade.settings.value.input_compat"));
    }

    private void createTextEditor(Font font) {
        if (font == null || draft == null) {
            textEditor = null;
            return;
        }
        textEditor = new EditBox(
                font,
                0,
                0,
                100,
                18,
                Component.translatable("chatupgrade.settings.option.input_placeholder"));
        textEditor.setMaxLength(PLACEHOLDER_MAX_LENGTH);
        textEditor.setHint(Component.translatable("chatupgrade.input.placeholder.default"));
        textEditor.setVisible(false);
        textEditor.setResponder(value -> {
            if (syncingTextEditor || visibleTextOption == null) {
                return;
            }
            visibleTextOption.setter().accept(value);
            previewDraft();
        });
        syncTextEditorValue(draft.chatInputPlaceholder);
    }

    private void layoutTextEditor(Layout layout) {
        if (textEditor == null || layout == null) {
            return;
        }
        OptionRow textRow = optionRows(layout).stream()
                .filter(row -> row.option() instanceof SettingsOption.TextOption)
                .findFirst()
                .orElse(null);
        if (textRow == null || !intersects(textRow.bounds(), layout.optionsViewport())) {
            visibleTextOption = null;
            textEditor.setVisible(false);
            textEditor.setFocused(false);
            return;
        }
        SettingsOption.TextOption option = (SettingsOption.TextOption) textRow.option();
        boolean optionChanged = visibleTextOption == null
                || !visibleTextOption.labelKey().equals(option.labelKey());
        visibleTextOption = option;
        textEditor.setMaxLength(option.maxLength());
        textEditor.setRectangle(
                textRow.control().width(),
                textRow.control().height(),
                textRow.control().left(),
                textRow.control().top());
        textEditor.setVisible(true);
        if (optionChanged) {
            syncTextEditorValue(option.getter().get());
        }
    }

    private void syncTextEditorValue(String value) {
        if (textEditor == null) {
            return;
        }
        syncingTextEditor = true;
        try {
            textEditor.setValue(value == null ? "" : value);
        } finally {
            syncingTextEditor = false;
        }
    }

    private void setTextEditorFocused(boolean focused) {
        if (textEditor != null) {
            textEditor.setFocused(focused);
        }
    }

    private void resetCurrentCategory() {
        if (draft == null) {
            return;
        }
        switch (category) {
            case APPEARANCE -> {
                draft.appearance = ChatUpgradeConfig.defaultAppearance();
                draft.chatPanel = new ChatUpgradeConfig.ChatPanelConfig();
            }
            case CHAT_BEHAVIOR -> {
                draft.chatInputPlaceholder = "";
                draft.chatScreenMaskEnabled = true;
                draft.smoothScrollEnabled = true;
                draft.debugChatActions = false;
                syncTextEditorValue("");
            }
            case MEDIA -> {
                draft.compactMediaCards = false;
                draft.manualImageReveal = false;
                draft.manualAudioReveal = false;
                draft.manualVideoReveal = false;
                draft.maxReceiveBytes = ChatUpgradeConfig.DEFAULT_MAX_RECEIVE_BYTES;
                draft.audioVolumePercent = 100;
                draft.videoVolumePercent = 100;
            }
            case UPLOAD_COMPATIBILITY -> {
                draft.uploadMode = ChatUpgradeConfig.UploadMode.AUTO;
                draft.maxUploadBytes = ChatUpgradeConfig.DEFAULT_MAX_UPLOAD_BYTES;
                draft.ciCompatibility = false;
                draft.chatInputMode = ChatUpgradeConfig.ChatInputMode.TAKEOVER;
            }
        }
        activeSlider = null;
        errorMessage = null;
        previewDraft();
    }

    private void previewDraft() {
        if (draft == null) {
            return;
        }
        draft.normalizeLimits();
        ChatClientConfigRuntime.preview(draft);
        ChatSurfaceController.previewPanelGeometry(draft, screenWidth, screenHeight);
        errorMessage = null;
    }

    private void finishClose() {
        open = false;
        baseline = null;
        draft = null;
        activeSlider = null;
        visibleTextOption = null;
        if (textEditor != null) {
            textEditor.setFocused(false);
            textEditor.setVisible(false);
        }
        textEditor = null;
        scrollY = 0.0D;
        ChatSurfaceController.finishPanelGeometryPreview();
        ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
    }

    private void updateScreenSize(int width, int height) {
        screenWidth = Math.max(1, width);
        screenHeight = Math.max(1, height);
        scrollY = Math.clamp(scrollY, 0.0D, maxScroll(layout()));
    }

    private @Nullable SettingsCategory categoryAt(double mouseX, double mouseY, Layout layout) {
        int y = layout.headerBottom() + 8;
        for (SettingsCategory value : SettingsCategory.values()) {
            RichChatBounds bounds = RichChatBounds.ofSize(
                    layout.panel().left() + 6,
                    y,
                    layout.navRight() - layout.panel().left() - 12,
                    24);
            if (bounds.contains(round(mouseX), round(mouseY))) {
                return value;
            }
            y += 28;
        }
        return null;
    }

    private Layout layout() {
        int width = Math.max(220, Math.min(MAX_WIDTH, screenWidth - SCREEN_MARGIN * 2));
        int height = Math.max(160, Math.min(MAX_HEIGHT, screenHeight - SCREEN_MARGIN * 2));
        width = Math.min(width, screenWidth);
        height = Math.min(height, screenHeight);
        int left = Math.max(0, (screenWidth - width) / 2);
        int top = Math.max(0, (screenHeight - height) / 2);
        RichChatBounds panel = RichChatBounds.ofSize(left, top, width, height);
        int navWidth = Math.min(NAV_WIDTH, Math.max(82, width / 3));
        int navRight = left + navWidth;
        int headerBottom = top + Math.min(HEADER_HEIGHT, height);
        int footerTop = Math.max(headerBottom, panel.bottom() - Math.min(FOOTER_HEIGHT, height));
        RichChatBounds viewport = new RichChatBounds(
                Math.min(panel.right(), navRight + 3),
                Math.min(panel.bottom(), headerBottom + 3),
                Math.max(navRight + 3, panel.right() - 3),
                Math.max(headerBottom + 3, footerTop - 3));
        RichChatBounds close = RichChatBounds.ofSize(panel.right() - 23, panel.top() + 6, 17, 17);
        int footerY = footerTop + Math.max(3, (panel.bottom() - footerTop - BUTTON_HEIGHT) / 2);
        RichChatBounds reset = RichChatBounds.ofSize(navRight + 8, footerY, 92, BUTTON_HEIGHT);
        RichChatBounds save = RichChatBounds.ofSize(panel.right() - 70, footerY, 62, BUTTON_HEIGHT);
        RichChatBounds cancel = RichChatBounds.ofSize(save.left() - 70, footerY, 62, BUTTON_HEIGHT);
        return new Layout(panel, viewport, close, reset, cancel, save, navRight, headerBottom, footerTop);
    }

    private double maxScroll(Layout layout) {
        int contentHeight = options().stream().mapToInt(this::optionHeight).sum()
                + Math.max(0, options().size() - 1) * OPTION_GAP;
        return Math.max(0.0D, contentHeight - layout.optionsViewport().height());
    }

    private int optionHeight(SettingsOption option) {
        if (option instanceof SettingsOption.HeadingOption) {
            return HEADING_HEIGHT;
        }
        if (option instanceof SettingsOption.BooleanOption booleanOption
                && !booleanOption.descriptionKey().isBlank()) {
            return DESCRIBED_OPTION_HEIGHT;
        }
        if (option instanceof SettingsOption.ColorOption) {
            return COLOR_OPTION_HEIGHT;
        }
        if (option instanceof SettingsOption.TextOption) {
            return TEXT_OPTION_HEIGHT;
        }
        return OPTION_HEIGHT;
    }

    private static SettingsOption.HeadingOption heading(String labelKey) {
        return new SettingsOption.HeadingOption(labelKey);
    }

    private static SettingsOption.TextOption text(
            String labelKey,
            String descriptionKey,
            java.util.function.Supplier<String> getter,
            java.util.function.Consumer<String> setter,
            int maxLength) {
        return new SettingsOption.TextOption(labelKey, descriptionKey, getter, setter, maxLength);
    }

    private static SettingsOption.BooleanOption bool(
            String labelKey,
            String descriptionKey,
            java.util.function.BooleanSupplier getter,
            java.util.function.Consumer<Boolean> setter) {
        return new SettingsOption.BooleanOption(labelKey, descriptionKey, getter, setter);
    }

    private static SettingsOption.BooleanOption bool(
            String labelKey,
            java.util.function.BooleanSupplier getter,
            java.util.function.Consumer<Boolean> setter) {
        return new SettingsOption.BooleanOption(labelKey, getter, setter);
    }

    private static SettingsOption.IntOption integer(
            String labelKey,
            java.util.function.IntSupplier getter,
            java.util.function.IntConsumer setter,
            int min,
            int max,
            SettingsOption.ValueFormat format) {
        return new SettingsOption.IntOption(labelKey, getter, setter, min, max, format);
    }

    private static SettingsOption.EnumOption enumeration(
            String labelKey,
            java.util.function.IntSupplier getter,
            java.util.function.IntConsumer setter,
            String... valueLabelKeys) {
        return new SettingsOption.EnumOption(labelKey, getter, setter, List.of(valueLabelKeys));
    }

    private static SettingsOption.ColorOption color(
            String labelKey,
            java.util.function.IntSupplier getter,
            java.util.function.IntConsumer setter) {
        return new SettingsOption.ColorOption(labelKey, getter, setter);
    }

    private static int toMebibytes(int bytes) {
        return Math.max(1, Math.round(bytes / (1024.0F * 1024.0F)));
    }

    private static int fromMebibytes(int value) {
        return Math.clamp(value, 1, 10) * 1024 * 1024;
    }

    private static int sliderX(int value, int min, int max, RichChatBounds track) {
        if (max <= min) {
            return track.left();
        }
        double progress = (Math.clamp(value, min, max) - min) / (double) (max - min);
        return track.left() + (int) Math.round(progress * track.width());
    }

    private static int channelValue(int color, int channel) {
        return color >> ((2 - channel) * 8) & 0xFF;
    }

    private static String formatValue(int value, SettingsOption.ValueFormat format) {
        return switch (format) {
            case INTEGER -> Integer.toString(value);
            case PERCENT -> value + "%";
            case PIXELS -> value + " px";
            case MEBIBYTES -> value + " MiB";
        };
    }

    private static void paintText(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int x,
            int y,
            int color) {
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(TEXT_SCALE, TEXT_SCALE);
            graphics.text(font, text, 0, 0, color, false);
        } finally {
            pose.popMatrix();
        }
    }

    private static void paintCenteredText(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int centerX,
            int y,
            int color) {
        int x = Math.round(centerX - font.width(text) * TEXT_SCALE / 2.0F);
        paintText(graphics, font, text, x, y, color);
    }

    private static String trim(Font font, String value, int maxWidth) {
        int unscaledWidth = Math.max(0, (int) Math.floor(maxWidth / TEXT_SCALE));
        if (font.width(value) <= unscaledWidth) {
            return value;
        }
        return font.plainSubstrByWidth(value, Math.max(0, unscaledWidth - font.width("…"))) + "…";
    }

    private static RichChatBounds inset(RichChatBounds bounds, int amount) {
        return new RichChatBounds(
                bounds.left() + amount,
                bounds.top() + amount,
                bounds.right() - amount,
                bounds.bottom() - amount);
    }

    private static boolean intersects(RichChatBounds left, RichChatBounds right) {
        return left.right() > right.left() && left.left() < right.right()
                && left.bottom() > right.top() && left.top() < right.bottom();
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    private record Layout(
            RichChatBounds panel,
            RichChatBounds optionsViewport,
            RichChatBounds closeButton,
            RichChatBounds resetButton,
            RichChatBounds cancelButton,
            RichChatBounds saveButton,
            int navRight,
            int headerBottom,
            int footerTop) {
    }

    private record OptionRow(
            SettingsOption option,
            RichChatBounds bounds,
            RichChatBounds control,
            List<RichChatBounds> colorChannels) {
    }

    private record ActiveSlider(SettingsOption option, int channel, RichChatBounds track) {
    }
}