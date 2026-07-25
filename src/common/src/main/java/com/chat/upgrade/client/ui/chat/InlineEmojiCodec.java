package com.chat.upgrade.client.ui.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.chat.upgrade.client.emoji.TwikooOwoRegistry;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class InlineEmojiCodec {
    private static final Pattern INLINE_EMOJI = Pattern.compile("\\[:([^\\]\\r\\n]+)]");
    private static final String UNKNOWN_EMOJI_TEXT = "[未知表情]";

    private InlineEmojiCodec() {
    }

    public record DecodedEmoji(Component modified, List<InlineEmojiSlot> slots) {
        public boolean hasSlots() {
            return slots != null && !slots.isEmpty();
        }
    }

    public static DecodedEmoji decodeIncoming(Component original) {
        List<StyledRun> runs = collectStyledRuns(original, Style.EMPTY);
        if (runs.isEmpty()) {
            return new DecodedEmoji(original, List.of());
        }

        StringBuilder joined = new StringBuilder();
        for (StyledRun run : runs) {
            joined.append(run.text);
        }
        String fullText = joined.toString();
        Matcher matcher = INLINE_EMOJI.matcher(fullText);
        if (!matcher.find()) {
            return new DecodedEmoji(original, List.of());
        }

        MutableComponent out = Component.empty().withStyle(original.getStyle());
        List<InlineEmojiSlot> slots = new ArrayList<>();
        int sourceCursor = 0;
        int outputCharIndex = 0;

        do {
            int start = matcher.start();
            int end = matcher.end();
            String token = matcher.group(1);
            String icon = TwikooOwoRegistry.resolveIconByToken(token);

            outputCharIndex += appendOriginalRange(out, runs, sourceCursor, start);
            if (icon == null || icon.isBlank()) {
                appendStyledText(out, styleAt(runs, fullText.length(), start), UNKNOWN_EMOJI_TEXT);
                outputCharIndex += UNKNOWN_EMOJI_TEXT.length();
            } else {
                Style markerStyle = InlineEmojiLayout.slotStyle(styleAt(runs, fullText.length(), start));
                appendStyledText(out, markerStyle, InlineEmojiLayout.slotText());
                slots.add(new InlineEmojiSlot(outputCharIndex, icon, token));
                outputCharIndex += InlineEmojiLayout.slotText().length();
            }
            sourceCursor = end;
        } while (matcher.find());

        appendOriginalRange(out, runs, sourceCursor, fullText.length());
        return new DecodedEmoji(out, slots.isEmpty() ? List.of() : List.copyOf(slots));
    }

    private static int appendOriginalRange(
            MutableComponent out,
            List<StyledRun> runs,
            int from,
            int to) {
        if (from >= to) {
            return 0;
        }
        int written = 0;
        int g = 0;
        for (StyledRun run : runs) {
            int runStart = g;
            int runEnd = g + run.text.length();
            g = runEnd;
            if (runEnd <= from || runStart >= to) {
                continue;
            }
            int localStart = Math.max(from, runStart) - runStart;
            int localEnd = Math.min(to, runEnd) - runStart;
            if (localEnd <= localStart) {
                continue;
            }
            String fragment = run.text.substring(localStart, localEnd);
            appendStyledText(out, run.style, fragment);
            written += fragment.length();
        }
        return written;
    }

    private static void appendStyledText(MutableComponent out, Style style, String fragment) {
        if (fragment.isEmpty()) {
            return;
        }
        MutableComponent bit = Component.literal(fragment);
        if (!style.isEmpty()) {
            bit.setStyle(style);
        }
        out.append(bit);
    }

    private static Style styleAt(List<StyledRun> runs, int fullLength, int index) {
        int clamped = Math.max(0, Math.min(index, Math.max(0, fullLength - 1)));
        int g = 0;
        for (StyledRun run : runs) {
            int runEnd = g + run.text.length();
            if (clamped < runEnd) {
                return run.style;
            }
            g = runEnd;
        }
        return Style.EMPTY;
    }

    private record StyledRun(Style style, String text) {
    }

    private static List<StyledRun> collectStyledRuns(Component c, Style inherited) {
        List<StyledRun> out = new ArrayList<>();
        collectStyledRunsInto(c, inherited, out);
        return out;
    }

    private static void collectStyledRunsInto(Component c, Style inherited, List<StyledRun> out) {
        Style here = inherited.applyTo(c.getStyle());
        c.getContents().visit((st, text) -> {
            if (!text.isEmpty()) {
                out.add(new StyledRun(here.applyTo(st), text));
            }
            return Optional.empty();
        }, here);
        for (Component sibling : c.getSiblings()) {
            collectStyledRunsInto(sibling, here, out);
        }
    }
}
