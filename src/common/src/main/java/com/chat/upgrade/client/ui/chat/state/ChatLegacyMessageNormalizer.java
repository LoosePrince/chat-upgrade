package com.chat.upgrade.client.ui.chat.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ui.chat.InlineEmojiSlot;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class ChatLegacyMessageNormalizer {
    private static final Pattern PLAYER_PREFIX = Pattern.compile("^\\s*<([^>]{1,64})>\\s*");

    private ChatLegacyMessageNormalizer() {
    }

    public static Normalized normalize(
            @Nullable Component component,
            @Nullable List<InlineEmojiSlot> inlineEmojiSlots,
            @Nullable ChatMessageKind kind) {
        Component safeComponent = component == null ? Component.empty() : component;
        List<InlineEmojiSlot> safeSlots = List.copyOf(inlineEmojiSlots == null ? List.of() : inlineEmojiSlots);
        if (kind == null || !kind.playerAuthored()) {
            return new Normalized("", safeComponent, safeSlots);
        }
        Matcher matcher = PLAYER_PREFIX.matcher(safeComponent.getString());
        if (!matcher.find()) {
            return new Normalized("", safeComponent, safeSlots);
        }
        int bodyStart = matcher.end();
        return new Normalized(
                matcher.group(1).strip(),
                sliceFrom(safeComponent, bodyStart),
                shiftSlots(safeSlots, bodyStart));
    }

    public static String inferAuthorName(@Nullable Component component) {
        if (component == null) {
            return "";
        }
        Matcher matcher = PLAYER_PREFIX.matcher(component.getString());
        return matcher.find() ? matcher.group(1).strip() : "";
    }

    private static Component sliceFrom(Component component, int from) {
        List<StyledRun> runs = collectStyledRuns(component, Style.EMPTY);
        if (runs.isEmpty()) {
            String text = component.getString();
            return Component.literal(text.substring(Math.min(from, text.length())))
                    .withStyle(component.getStyle());
        }
        MutableComponent body = Component.empty().withStyle(component.getStyle());
        int cursor = 0;
        for (StyledRun run : runs) {
            int runEnd = cursor + run.text().length();
            if (runEnd > from) {
                int localStart = Math.max(0, from - cursor);
                append(body, run.style(), run.text().substring(localStart));
            }
            cursor = runEnd;
        }
        return body;
    }

    private static List<InlineEmojiSlot> shiftSlots(List<InlineEmojiSlot> slots, int removedChars) {
        return slots.stream()
                .filter(slot -> slot.charIndex() >= removedChars)
                .map(slot -> new InlineEmojiSlot(
                        slot.charIndex() - removedChars,
                        slot.iconUrl(),
                        slot.token()))
                .toList();
    }

    private static List<StyledRun> collectStyledRuns(Component component, Style inherited) {
        List<StyledRun> runs = new ArrayList<>();
        collectStyledRunsInto(component, inherited, runs);
        return runs;
    }

    private static void collectStyledRunsInto(Component component, Style inherited, List<StyledRun> runs) {
        Style effective = inherited.applyTo(component.getStyle());
        component.getContents().visit((style, text) -> {
            if (!text.isEmpty()) {
                runs.add(new StyledRun(effective.applyTo(style), text));
            }
            return Optional.empty();
        }, effective);
        for (Component sibling : component.getSiblings()) {
            collectStyledRunsInto(sibling, effective, runs);
        }
    }

    private static void append(MutableComponent target, Style style, String text) {
        if (text.isEmpty()) {
            return;
        }
        MutableComponent fragment = Component.literal(text);
        if (!style.isEmpty()) {
            fragment.setStyle(style);
        }
        target.append(fragment);
    }

    public record Normalized(
            String authorName,
            Component body,
            List<InlineEmojiSlot> inlineEmojiSlots) {
        public Normalized {
            authorName = authorName == null ? "" : authorName.strip();
            body = body == null ? Component.empty() : body;
            inlineEmojiSlots = List.copyOf(inlineEmojiSlots == null ? List.of() : inlineEmojiSlots);
        }
    }

    private record StyledRun(Style style, String text) {
    }
}