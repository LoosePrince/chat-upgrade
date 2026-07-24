package com.chat.upgrade.client.ui.chat.input;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.chat.upgrade.client.ui.chat.surface.ChatAppearanceSnapshot;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceController;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.render.UiPrimitives;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Contextual player-id completion for the mention token surrounding the input cursor. */
public final class MentionCompletionPopover {
    private static final int MAX_CANDIDATES = 6;
    private static final int ROW_HEIGHT = 18;
    private static final int PANEL_PADDING = 3;
    private static final int MIN_WIDTH = 96;
    private static final int MAX_WIDTH = 180;
    private static final long ONLINE_PLAYER_CACHE_MS = 250L;

    private List<String> candidates = List.of();
    private List<String> onlinePlayerNames = List.of();
    private @Nullable ClientPacketListener onlinePlayerConnection;
    private long onlinePlayerCacheExpiresAt;
    private @Nullable Query query;
    private int selectedIndex;
    private @Nullable RichChatBounds panelBounds;

    public boolean isVisible() {
        return query != null && !candidates.isEmpty();
    }

    public void close() {
        query = null;
        candidates = List.of();
        selectedIndex = 0;
        panelBounds = null;
    }

    public void refresh(Minecraft minecraft, EditBox input) {
        if (minecraft == null || input == null || !input.isFocused() || minecraft.level == null) {
            close();
            return;
        }
        Query nextQuery = queryAtCursor(input.getValue(), input.getCursorPosition());
        if (nextQuery == null) {
            close();
            return;
        }
        String selectedName = selectedCandidate();
        List<String> nextCandidates = candidates(minecraft, nextQuery.fragment());
        query = nextQuery;
        candidates = nextCandidates;
        if (candidates.isEmpty()) {
            selectedIndex = 0;
            panelBounds = null;
            return;
        }
        int retainedIndex = selectedName == null ? -1 : indexOfIgnoreCase(candidates, selectedName);
        selectedIndex = retainedIndex >= 0
                ? retainedIndex
                : Math.clamp(selectedIndex, 0, candidates.size() - 1);
    }

    public boolean keyPressed(KeyEvent event, EditBox input) {
        if (!isVisible() || event == null || input == null) {
            return false;
        }
        return switch (event.key()) {
            case GLFW.GLFW_KEY_UP -> {
                selectedIndex = Math.floorMod(selectedIndex - 1, candidates.size());
                yield true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                selectedIndex = Math.floorMod(selectedIndex + 1, candidates.size());
                yield true;
            }
            case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                applySelection(input, selectedIndex);
                yield true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                close();
                yield true;
            }
            default -> false;
        };
    }

    public boolean mouseClicked(MouseButtonEvent event, EditBox input) {
        if (!isVisible() || event == null || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || input == null || panelBounds == null) {
            return false;
        }
        int mouseX = (int) Math.round(event.x());
        int mouseY = (int) Math.round(event.y());
        if (!panelBounds.contains(mouseX, mouseY)) {
            return false;
        }
        int index = (mouseY - panelBounds.top() - PANEL_PADDING) / ROW_HEIGHT;
        if (index >= 0 && index < candidates.size()) {
            applySelection(input, index);
        }
        return true;
    }

    public void render(
            GuiGraphicsExtractor graphics,
            Font font,
            EditBox input,
            int mouseX,
            int mouseY,
            int screenWidth,
            int screenHeight) {
        if (!isVisible() || graphics == null || font == null || input == null || query == null) {
            panelBounds = null;
            return;
        }
        int contentWidth = candidates.stream()
                .mapToInt(name -> font.width("@" + name))
                .max()
                .orElse(MIN_WIDTH);
        int width = Math.clamp(contentWidth + 14, MIN_WIDTH, MAX_WIDTH);
        int height = PANEL_PADDING * 2 + candidates.size() * ROW_HEIGHT;
        String prefix = input.getValue().substring(0, Math.clamp(query.atIndex(), 0, input.getValue().length()));
        int preferredLeft = input.getX() + font.width(prefix);
        int left = Math.clamp(preferredLeft, 3, Math.max(3, screenWidth - width - 3));
        int preferredTop = input.getY() - height - 3;
        int top = preferredTop >= 3
                ? preferredTop
                : Math.min(Math.max(3, input.getY() + input.getHeight() + 3), Math.max(3, screenHeight - height - 3));
        panelBounds = RichChatBounds.ofSize(left, top, width, height);

        ChatAppearanceSnapshot appearance = ChatSurfaceController.state().appearance();
        UiPrimitives.paintBox(
                graphics,
                panelBounds,
                Math.max(2, appearance.contextMenu().cornerRadius()),
                Math.max(1, appearance.contextMenu().borderWidth()),
                appearance.contextMenu().background(),
                appearance.contextMenu().border());
        for (int index = 0; index < candidates.size(); index++) {
            RichChatBounds row = RichChatBounds.ofSize(
                    panelBounds.left() + PANEL_PADDING,
                    panelBounds.top() + PANEL_PADDING + index * ROW_HEIGHT,
                    panelBounds.width() - PANEL_PADDING * 2,
                    ROW_HEIGHT);
            boolean hovered = row.contains(mouseX, mouseY);
            if (index == selectedIndex || hovered) {
                UiPrimitives.fillRounded(
                        graphics,
                        row,
                        3,
                        appearance.media().controlActiveBackground());
            }
            String value = font.plainSubstrByWidth("@" + candidates.get(index), Math.max(1, row.width() - 8));
            graphics.text(
                    font,
                    value,
                    row.left() + 4,
                    row.top() + Math.max(1, (ROW_HEIGHT - font.lineHeight) / 2),
                    index == selectedIndex ? appearance.surface().title() : appearance.message().text(),
                    false);
        }
    }

    private void applySelection(EditBox input, int index) {
        if (query == null || index < 0 || index >= candidates.size()) {
            return;
        }
        String value = input.getValue();
        int start = Math.clamp(query.atIndex(), 0, value.length());
        int end = Math.clamp(query.cursorIndex(), start, value.length());
        String insertion = "@" + candidates.get(index) + " ";
        String next = value.substring(0, start) + insertion + value.substring(end);
        input.setValue(next);
        input.setCursorPosition(Math.min(next.length(), start + insertion.length()));
        close();
    }

    private @Nullable String selectedCandidate() {
        return selectedIndex >= 0 && selectedIndex < candidates.size() ? candidates.get(selectedIndex) : null;
    }

    private List<String> candidates(Minecraft minecraft, String fragment) {
        String normalizedFragment = fragment.toLowerCase(Locale.ROOT);
        List<String> names = onlinePlayerNames(minecraft);
        Stream<String> startsWith = names.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(normalizedFragment));
        Stream<String> contains = names.stream()
                .filter(name -> !name.toLowerCase(Locale.ROOT).startsWith(normalizedFragment))
                .filter(name -> name.toLowerCase(Locale.ROOT).contains(normalizedFragment));
        return Stream.concat(startsWith, contains)
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private List<String> onlinePlayerNames(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        long now = System.currentTimeMillis();
        if (connection == null) {
            onlinePlayerConnection = null;
            onlinePlayerNames = List.of();
            onlinePlayerCacheExpiresAt = now + ONLINE_PLAYER_CACHE_MS;
            return onlinePlayerNames;
        }
        if (connection == onlinePlayerConnection && now < onlinePlayerCacheExpiresAt) {
            return onlinePlayerNames;
        }
        Map<String, String> uniqueNames = new LinkedHashMap<>();
        for (PlayerInfo playerInfo : connection.getOnlinePlayers()) {
            if (playerInfo == null || playerInfo.getProfile() == null) {
                continue;
            }
            String name = playerInfo.getProfile().name();
            if (name != null && !name.isBlank()) {
                uniqueNames.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            }
        }
        onlinePlayerConnection = connection;
        onlinePlayerNames = uniqueNames.values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        onlinePlayerCacheExpiresAt = now + ONLINE_PLAYER_CACHE_MS;
        return onlinePlayerNames;
    }

    private static @Nullable Query queryAtCursor(String value, int cursorIndex) {
        String safeValue = value == null ? "" : value;
        int cursor = Math.clamp(cursorIndex, 0, safeValue.length());
        for (int index = cursor - 1; index >= 0; index--) {
            char character = safeValue.charAt(index);
            if (character == '@') {
                return new Query(index, cursor, safeValue.substring(index + 1, cursor));
            }
            if (!isPlayerIdCharacter(character)) {
                return null;
            }
        }
        return null;
    }

    private static boolean isPlayerIdCharacter(char character) {
        return character == '_' || Character.isLetterOrDigit(character);
    }

    private static int indexOfIgnoreCase(List<String> values, String target) {
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).equalsIgnoreCase(target)) {
                return index;
            }
        }
        return -1;
    }

    private record Query(int atIndex, int cursorIndex, String fragment) {
    }
}