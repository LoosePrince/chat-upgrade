package com.chat.upgrade.client.ui.chat.interaction;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ui.chat.InlineEmojiLayout;
import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

/**
 * Owns one message-local text range. Hit testing stays in the pointer router;
 * renderers and copy actions consume the same immutable line/range model.
 */
public final class ChatTextSelectionState {
    public record SelectableLine(
            int order,
            RichChatBounds bounds,
            String text,
            List<Integer> charOffsets,
            List<Integer> prefixWidths) {
        public SelectableLine {
            if (bounds == null) {
                throw new IllegalArgumentException("bounds must not be null");
            }
            text = text == null ? "" : text;
            charOffsets = charOffsets == null ? List.of(0) : List.copyOf(charOffsets);
            prefixWidths = prefixWidths == null ? List.of(0) : List.copyOf(prefixWidths);
            if (charOffsets.isEmpty()
                    || charOffsets.size() != prefixWidths.size()
                    || charOffsets.getFirst() != 0
                    || charOffsets.getLast() != text.length()) {
                throw new IllegalArgumentException("character offsets and prefix widths must describe the full line");
            }
        }

        public static SelectableLine fromRendered(
                int order,
                RichChatBounds bounds,
                FormattedCharSequence rendered,
                Font font) {
            if (rendered == null || font == null) {
                throw new IllegalArgumentException("rendered text and font must not be null");
            }
            StringBuilder plain = new StringBuilder();
            List<Glyph> glyphs = new ArrayList<>();
            rendered.accept((index, style, codePoint) -> {
                if (InlineEmojiLayout.isSlot(codePoint, style)) {
                    plain.append(' ');
                } else {
                    plain.appendCodePoint(codePoint);
                }
                glyphs.add(new Glyph(plain.length()));
                return true;
            });
            List<Integer> offsets = new ArrayList<>(glyphs.size() + 1);
            List<Integer> widths = new ArrayList<>(glyphs.size() + 1);
            offsets.add(0);
            widths.add(0);
            for (Glyph glyph : glyphs) {
                offsets.add(glyph.endOffset());
                widths.add(InlineEmojiLayout.prefixWidth(font, rendered, glyph.endOffset()));
            }
            return new SelectableLine(order, bounds, plain.toString(), offsets, widths);
        }

        public int renderedWidth() {
            return prefixWidths.getLast();
        }

        public int charIndexAt(float relativeX) {
            if (relativeX <= 0.0F || text.isEmpty()) {
                return 0;
            }
            if (relativeX >= renderedWidth()) {
                return text.length();
            }
            for (int index = 1; index < prefixWidths.size(); index++) {
                int previousWidth = prefixWidths.get(index - 1);
                int nextWidth = prefixWidths.get(index);
                if (relativeX < (previousWidth + nextWidth) / 2.0F) {
                    return charOffsets.get(index - 1);
                }
            }
            return text.length();
        }

        public int widthAtCharIndex(int charIndex) {
            int clamped = Math.clamp(charIndex, 0, text.length());
            for (int index = 0; index < charOffsets.size(); index++) {
                if (charOffsets.get(index) >= clamped) {
                    return prefixWidths.get(index);
                }
            }
            return renderedWidth();
        }
    }

    public record LineSelection(int startIndex, int endIndex, int startPixel, int endPixel) {
        public LineSelection {
            startIndex = Math.max(0, startIndex);
            endIndex = Math.max(startIndex, endIndex);
            startPixel = Math.max(0, startPixel);
            endPixel = Math.max(startPixel, endPixel);
        }
    }

    private static @Nullable RichChatMessage selectedMessage;
    private static List<SelectableLine> lines = List.of();
    private static boolean selecting;
    private static int anchorLineIndex;
    private static int anchorCharIndex;
    private static int pointerLineIndex;
    private static int pointerCharIndex;

    private ChatTextSelectionState() {
    }

    public static void begin(
            RichChatMessage message,
            List<SelectableLine> selectableLines,
            int lineIndex,
            int charIndex) {
        if (message == null || selectableLines == null || selectableLines.isEmpty()) {
            clear();
            return;
        }
        selectedMessage = message;
        lines = List.copyOf(selectableLines);
        selecting = true;
        anchorLineIndex = clampLineIndex(lineIndex);
        anchorCharIndex = clampCharIndex(anchorLineIndex, charIndex);
        pointerLineIndex = anchorLineIndex;
        pointerCharIndex = anchorCharIndex;
    }

    public static void update(int lineIndex, int charIndex) {
        if (!selecting || lines.isEmpty()) {
            return;
        }
        pointerLineIndex = clampLineIndex(lineIndex);
        pointerCharIndex = clampCharIndex(pointerLineIndex, charIndex);
    }

    public static boolean finish() {
        if (!selecting) {
            return false;
        }
        selecting = false;
        if (anchorLineIndex == pointerLineIndex && anchorCharIndex == pointerCharIndex) {
            clear();
            return false;
        }
        return true;
    }

    public static boolean isSelecting() {
        return selecting;
    }

    public static boolean hasSelection() {
        return !selecting && selectedMessage != null && !selectedText().isEmpty();
    }

    public static boolean isSelectingMessage(String messageId) {
        return selectedMessage != null
                && messageId != null
                && messageId.equals(selectedMessage.messageId());
    }

    public static String messageId() {
        return selectedMessage == null ? "" : selectedMessage.messageId();
    }

    public static void clearIfMessage(String messageId) {
        if (selectedMessage != null
                && messageId != null
                && messageId.equals(selectedMessage.messageId())) {
            clear();
        }
    }

    public static void clearIfAuthor(String authorKey) {
        if (selectedMessage != null
                && authorKey != null
                && !authorKey.isBlank()
                && authorKey.equalsIgnoreCase(selectedMessage.author().identityKey())) {
            clear();
        }
    }

    public static @Nullable LineSelection selectionFor(String messageId, int lineOrder) {
        if (!isSelectingMessage(messageId)) {
            return null;
        }
        int lineIndex = lineIndexForOrder(lineOrder);
        if (lineIndex < 0) {
            return null;
        }
        Position start = orderedStart();
        Position end = orderedEnd();
        if (lineIndex < start.lineIndex() || lineIndex > end.lineIndex()) {
            return null;
        }
        int lineLength = lines.get(lineIndex).text().length();
        int startIndex = lineIndex == start.lineIndex() ? start.charIndex() : 0;
        int endIndex = lineIndex == end.lineIndex() ? end.charIndex() : lineLength;
        if (endIndex <= startIndex) {
            return null;
        }
        return new LineSelection(
                startIndex,
                endIndex,
                lines.get(lineIndex).widthAtCharIndex(startIndex),
                lines.get(lineIndex).widthAtCharIndex(endIndex));
    }

    public static String selectedTextFor(String messageId) {
        return isSelectingMessage(messageId) && !selecting ? selectedText() : "";
    }

    public static String selectedText() {
        if (selectedMessage == null || lines.isEmpty()) {
            return "";
        }
        Position start = orderedStart();
        Position end = orderedEnd();
        StringBuilder selected = new StringBuilder();
        for (int lineIndex = start.lineIndex(); lineIndex <= end.lineIndex(); lineIndex++) {
            if (lineIndex > start.lineIndex()) {
                selected.append('\n');
            }
            String text = lines.get(lineIndex).text();
            int from = lineIndex == start.lineIndex() ? start.charIndex() : 0;
            int to = lineIndex == end.lineIndex() ? end.charIndex() : text.length();
            if (to > from) {
                selected.append(text, from, to);
            }
        }
        return selected.toString();
    }

    public static void copySelection(Minecraft minecraft) {
        String selected = selectedText();
        if (minecraft != null && hasSelection() && !selected.isEmpty()) {
            minecraft.keyboardHandler.setClipboard(selected);
        }
    }

    public static void cancel() {
        clear();
    }

    public static void clear() {
        selectedMessage = null;
        lines = List.of();
        selecting = false;
        anchorLineIndex = 0;
        anchorCharIndex = 0;
        pointerLineIndex = 0;
        pointerCharIndex = 0;
    }

    private static int clampLineIndex(int lineIndex) {
        return Math.clamp(lineIndex, 0, Math.max(0, lines.size() - 1));
    }

    private static int clampCharIndex(int lineIndex, int charIndex) {
        return Math.clamp(charIndex, 0, lines.get(lineIndex).text().length());
    }

    private static int lineIndexForOrder(int lineOrder) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).order() == lineOrder) {
                return index;
            }
        }
        return -1;
    }

    private static Position orderedStart() {
        return compare(anchorLineIndex, anchorCharIndex, pointerLineIndex, pointerCharIndex) <= 0
                ? new Position(anchorLineIndex, anchorCharIndex)
                : new Position(pointerLineIndex, pointerCharIndex);
    }

    private static Position orderedEnd() {
        return compare(anchorLineIndex, anchorCharIndex, pointerLineIndex, pointerCharIndex) <= 0
                ? new Position(pointerLineIndex, pointerCharIndex)
                : new Position(anchorLineIndex, anchorCharIndex);
    }

    private static int compare(int firstLine, int firstChar, int secondLine, int secondChar) {
        int lineComparison = Integer.compare(firstLine, secondLine);
        return lineComparison != 0 ? lineComparison : Integer.compare(firstChar, secondChar);
    }

    private record Glyph(int endOffset) {
    }

    private record Position(int lineIndex, int charIndex) {
    }
}
