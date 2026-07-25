package com.chat.upgrade.client.ui.chat;

import com.chat.upgrade.ChatUpgrade;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Defines the native text slot occupied by one inline emoji. The slot uses a
 * dedicated space glyph so wrapping, text drawing and overlay geometry share
 * one advance independent of the active text font.
 */
public final class InlineEmojiLayout {
    private static final int SLOT_CODE_POINT = 0xE000;
    private static final String SLOT_TEXT = new String(Character.toChars(SLOT_CODE_POINT));
    private static final String SLOT_INSERTION = "chatupgrade:inline-emoji-slot";
    private static final int SLOT_ADVANCE_PX = 9;
    private static final int SIDE_GAP_PX = 1;
    private static final int ICON_SIZE_PX = SLOT_ADVANCE_PX - SIDE_GAP_PX * 2;
    private static final FontDescription SLOT_FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath(ChatUpgrade.MOD_ID, "inline_emoji_slot"));

    private InlineEmojiLayout() {
    }

    public record Placement(int x, int y, int size) {
    }

    public static String slotText() {
        return SLOT_TEXT;
    }

    public static Style slotStyle(Style source) {
        Style base = source == null ? Style.EMPTY : source;
        return base
                .withInsertion(SLOT_INSERTION)
                .withFont(SLOT_FONT)
                .withBold(false)
                .withItalic(false)
                .withUnderlined(false)
                .withStrikethrough(false)
                .withObfuscated(false);
    }

    public static boolean isSlot(int codePoint, Style style) {
        return codePoint == SLOT_CODE_POINT
                && style != null
                && SLOT_INSERTION.equals(style.getInsertion());
    }

    public static Placement place(
            Font font,
            FormattedCharSequence line,
            int charIndex,
            int textX,
            int textY) {
        int slotX = textX + prefixWidth(font, line, charIndex);
        return new Placement(
                slotX + SIDE_GAP_PX,
                textY + SIDE_GAP_PX,
                ICON_SIZE_PX);
    }

    public static int prefixWidth(Font font, FormattedCharSequence line, int charIndex) {
        if (font == null || line == null || charIndex <= 0) {
            return 0;
        }
        int targetIndex = Math.min(charIndex, utf16Length(line));
        return font.width(prefix(line, targetIndex));
    }

    private static FormattedCharSequence prefix(FormattedCharSequence line, int targetIndex) {
        return sink -> {
            int[] logicalIndex = new int[] { 0 };
            return line.accept((index, style, codePoint) -> {
                if (logicalIndex[0] >= targetIndex) {
                    return false;
                }
                int charCount = Character.charCount(codePoint);
                if (logicalIndex[0] + charCount > targetIndex) {
                    return false;
                }
                logicalIndex[0] += charCount;
                return sink.accept(index, style, codePoint) && logicalIndex[0] < targetIndex;
            });
        };
    }

    private static int utf16Length(FormattedCharSequence line) {
        int[] length = new int[] { 0 };
        line.accept((index, style, codePoint) -> {
            length[0] += Character.charCount(codePoint);
            return true;
        });
        return length[0];
    }
}