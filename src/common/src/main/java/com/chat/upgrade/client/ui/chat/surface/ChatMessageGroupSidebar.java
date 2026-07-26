package com.chat.upgrade.client.ui.chat.surface;

import java.util.ArrayList;
import java.util.List;

import com.chat.upgrade.client.ui.chat.state.ChatMessageGroupKey;
import com.chat.upgrade.client.ui.chat.state.ChatMessageGroupStore;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.render.UiPrimitives;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

/** Owns message-group sidebar layout, painting, scrolling, and hit testing. */
public final class ChatMessageGroupSidebar {
    private static final int OUTER_PADDING = 5;
    private static final int ROW_GAP = 3;
    private static final int FIXED_ROW_HEIGHT = 21;
    private static final int PRIVATE_SECTION_DIVIDER_HEIGHT = 7;

    private static int scrollPx;
    private static ChatMessageGroupKey lastSelected = ChatMessageGroupKey.all();

    private ChatMessageGroupSidebar() {
    }

    public static void paint(GuiGraphicsExtractor graphics, Font font, ChatSurfaceFrame frame) {
        if (graphics == null || font == null || frame == null || !frame.messageGroupingVisible()) {
            return;
        }
        RichChatBounds sidebar = frame.messageGroupSidebarBounds();
        ChatAppearanceSnapshot appearance = frame.appearance();
        ChatMessageGroupKey selected = ChatMessageGroupStore.selected();
        ensureSelectedVisible(frame, selected);

        int separatorX = frame.preferences().messageGroupPosition()
                        == com.chat.upgrade.client.ChatUpgradeConfig.MessageGroupPosition.RIGHT
                ? sidebar.left()
                : sidebar.right() - 1;
        graphics.fill(separatorX, sidebar.top(), separatorX + 1, sidebar.bottom(), appearance.surface().separator());

        for (SidebarRow row : rows(frame)) {
            if (!intersects(row.bounds(), sidebar)) {
                continue;
            }
            if (row instanceof PrivateSectionDivider divider) {
                paintPrivateSectionDivider(graphics, divider, appearance.surface().separator());
                continue;
            }
            GroupRow groupRow = (GroupRow) row;
            boolean active = groupRow.group().equals(selected);
            int fill = active
                    ? appearance.media().controlActiveBackground()
                    : appearance.media().controlBackground();
            int border = active
                    ? appearance.message().playerBorder()
                    : appearance.surface().panelBorder();
            UiPrimitives.paintBox(
                    graphics,
                    groupRow.bounds(),
                    Math.min(4, frame.appearance().cornerRadius()),
                    active ? 1 : 0,
                    fill,
                    border);
            paintLabel(graphics, font, groupRow, appearance.surface().title());
        }
    }

    public static boolean selectAt(ChatSurfaceFrame frame, double pointerX, double pointerY, int button) {
        if (frame == null || button != 0 || !frame.messageGroupingVisible()) {
            return false;
        }
        RichChatBounds sidebar = frame.messageGroupSidebarBounds();
        int x = (int) Math.round(pointerX);
        int y = (int) Math.round(pointerY);
        if (!sidebar.contains(x, y)) {
            return false;
        }
        for (SidebarRow row : rows(frame)) {
            if (!row.bounds().contains(x, y) || !intersects(row.bounds(), sidebar)) {
                continue;
            }
            if (row instanceof GroupRow groupRow) {
                ChatMessageGroupStore.select(groupRow.group());
                return true;
            }
            return false;
        }
        return true;
    }

    public static boolean scrollAt(
            ChatSurfaceFrame frame,
            double pointerX,
            double pointerY,
            double scrollAmount) {
        if (frame == null || !frame.messageGroupingVisible()) {
            return false;
        }
        RichChatBounds sidebar = frame.messageGroupSidebarBounds();
        int x = (int) Math.round(pointerX);
        int y = (int) Math.round(pointerY);
        if (!sidebar.contains(x, y)
                || rows(frame).stream().noneMatch(row -> row instanceof GroupRow
                        && row.bounds().contains(x, y)
                        && intersects(row.bounds(), sidebar))) {
            return false;
        }
        int maxScroll = maxScroll(frame);
        scrollPx = Math.clamp(scrollPx - (int) Math.round(scrollAmount * FIXED_ROW_HEIGHT), 0, maxScroll);
        return true;
    }

    public static void clearSession() {
        scrollPx = 0;
        lastSelected = ChatMessageGroupKey.all();
    }

    private static void ensureSelectedVisible(ChatSurfaceFrame frame, ChatMessageGroupKey selected) {
        if (selected.equals(lastSelected)) {
            scrollPx = Math.clamp(scrollPx, 0, maxScroll(frame));
            return;
        }
        lastSelected = selected;
        List<SidebarRow> currentRows = rows(frame);
        RichChatBounds sidebar = frame.messageGroupSidebarBounds();
        currentRows.stream()
                .filter(GroupRow.class::isInstance)
                .map(GroupRow.class::cast)
                .filter(row -> row.group().equals(selected))
                .findFirst()
                .ifPresent(row -> {
                    int visibleTop = sidebar.top() + OUTER_PADDING;
                    int visibleBottom = sidebar.bottom() - OUTER_PADDING;
                    if (row.bounds().top() < visibleTop) {
                        scrollPx = Math.max(0, scrollPx - (visibleTop - row.bounds().top()));
                    } else if (row.bounds().bottom() > visibleBottom) {
                        scrollPx = Math.min(maxScroll(frame), scrollPx + row.bounds().bottom() - visibleBottom);
                    }
                });
    }

    private static List<SidebarRow> rows(ChatSurfaceFrame frame) {
        RichChatBounds sidebar = frame.messageGroupSidebarBounds();
        int left = sidebar.left() + OUTER_PADDING;
        int width = Math.max(1, sidebar.width() - OUTER_PADDING * 2);
        int y = sidebar.top() + OUTER_PADDING - scrollPx;
        List<ChatMessageGroupKey> groups = ChatMessageGroupStore.groups();
        List<SidebarRow> rows = new ArrayList<>();
        boolean sawFixedGroup = false;
        boolean privateSectionStarted = false;
        for (ChatMessageGroupKey group : groups) {
            boolean startsPrivateSection = group.type() == ChatMessageGroupKey.Type.PRIVATE_PEER
                    && !privateSectionStarted
                    && sawFixedGroup;
            if (!rows.isEmpty()) {
                y += ROW_GAP;
            }
            if (startsPrivateSection) {
                rows.add(new PrivateSectionDivider(RichChatBounds.ofSize(
                        left,
                        y,
                        width,
                        PRIVATE_SECTION_DIVIDER_HEIGHT)));
                y += PRIVATE_SECTION_DIVIDER_HEIGHT + ROW_GAP;
            }
            rows.add(new GroupRow(group, RichChatBounds.ofSize(left, y, width, FIXED_ROW_HEIGHT)));
            y += FIXED_ROW_HEIGHT;
            sawFixedGroup |= group.type() != ChatMessageGroupKey.Type.PRIVATE_PEER;
            privateSectionStarted |= group.type() == ChatMessageGroupKey.Type.PRIVATE_PEER;
        }
        return List.copyOf(rows);
    }

    private static int maxScroll(ChatSurfaceFrame frame) {
        List<ChatMessageGroupKey> groups = ChatMessageGroupStore.groups();
        int contentHeight = OUTER_PADDING * 2 + groups.size() * FIXED_ROW_HEIGHT;
        contentHeight += Math.max(0, groups.size() - 1) * ROW_GAP;
        if (hasPrivateSectionDivider(groups)) {
            contentHeight += PRIVATE_SECTION_DIVIDER_HEIGHT + ROW_GAP;
        }
        return Math.max(0, contentHeight - frame.messageGroupSidebarBounds().height());
    }

    private static boolean hasPrivateSectionDivider(List<ChatMessageGroupKey> groups) {
        boolean sawFixedGroup = false;
        boolean privateSectionStarted = false;
        for (ChatMessageGroupKey group : groups) {
            if (group.type() == ChatMessageGroupKey.Type.PRIVATE_PEER
                    && !privateSectionStarted
                    && sawFixedGroup) {
                return true;
            }
            sawFixedGroup |= group.type() != ChatMessageGroupKey.Type.PRIVATE_PEER;
            privateSectionStarted |= group.type() == ChatMessageGroupKey.Type.PRIVATE_PEER;
        }
        return false;
    }

    private static void paintPrivateSectionDivider(
            GuiGraphicsExtractor graphics,
            PrivateSectionDivider divider,
            int color) {
        RichChatBounds bounds = divider.bounds();
        int y = bounds.top() + bounds.height() / 2;
        graphics.fill(bounds.left() + 4, y, bounds.right() - 4, y + 1, color);
    }

    private static void paintLabel(
            GuiGraphicsExtractor graphics,
            Font font,
            GroupRow row,
            int color) {
        String label = label(row.group());
        RichChatBounds bounds = row.bounds();
        int availableWidth = Math.max(1, bounds.width() - 8);
        if (row.group().type() != ChatMessageGroupKey.Type.PRIVATE_PEER) {
            String visible = font.plainSubstrByWidth(label, availableWidth);
            graphics.centeredText(
                    font,
                    visible,
                    bounds.left() + bounds.width() / 2,
                    bounds.top() + Math.max(1, (bounds.height() - font.lineHeight) / 2),
                    color);
            return;
        }
        int labelWidth = font.width(label);
        float scale = labelWidth > availableWidth
                ? (float) availableWidth / (float) labelWidth
                : 1.0F;
        int scaledLineHeight = Math.max(1, Math.round(font.lineHeight * scale));
        paintScaledCenteredText(
                graphics,
                font,
                label,
                bounds,
                bounds.top() + Math.max(1, (bounds.height() - scaledLineHeight) / 2),
                scale,
                color);
    }

    private static void paintScaledCenteredText(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            RichChatBounds bounds,
            int y,
            float scale,
            int color) {
        float scaledWidth = font.width(text) * scale;
        int x = Math.round(bounds.left() + (bounds.width() - scaledWidth) / 2.0F);
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(scale, scale);
            graphics.text(font, text, 0, 0, color, false);
        } finally {
            pose.popMatrix();
        }
    }

    private static String label(ChatMessageGroupKey group) {
        return switch (group.type()) {
            case ALL -> I18n.get("chatupgrade.message_group.all");
            case SYSTEM -> I18n.get("chatupgrade.message_group.system");
            case CHAT -> I18n.get("chatupgrade.message_group.chat");
            case PRIVATE_PEER -> {
                String playerId = ChatMessageGroupStore.privatePeerPlayerId(group.peerId());
                yield playerId.isBlank()
                        ? I18n.get("chatupgrade.message_group.unknown_player")
                        : playerId;
            }
        };
    }

    private static boolean intersects(RichChatBounds left, RichChatBounds right) {
        return left.right() > right.left() && left.left() < right.right()
                && left.bottom() > right.top() && left.top() < right.bottom();
    }

    private interface SidebarRow {
        RichChatBounds bounds();
    }

    private record GroupRow(ChatMessageGroupKey group, RichChatBounds bounds) implements SidebarRow {
    }

    private record PrivateSectionDivider(RichChatBounds bounds) implements SidebarRow {
    }
}