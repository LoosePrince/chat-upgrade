package com.chat.upgrade.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Encodes and decodes bracketed URL payloads {@code [[tag,url=…]]} for chat. */
public final class UpgradeBracketCodec {
    /** Default tag for outgoing messages. */
    public static final String WIRE_TAG_NATIVE = "ChatUpgrade";

    /** Tag used when {@link ChatUpgradeConfig#ciCompatibility} is enabled. */
    public static final String WIRE_TAG_LEGACY = "CICode";

    /**
     * Shown when the URL payload was replaced in chat ({@link #buildPlaceholder}) but the resource could not be loaded.
     * Used by {@link #replaceVisiblePlaceholderWithLoadFailed(FormattedCharSequence)} on HUD lines.
     */
    public static final String LOAD_FAILED_VISIBLE = "[图片：加载失败]";

    /** Shown when the remote image exceeds {@link ChatUpgradeConfig#maxReceiveBytes}. */
    public static final String IMAGE_OVERSIZE_VISIBLE = "[图片：图片过大]";

    private static final Pattern BRACKET_PAYLOAD = Pattern.compile(
            "\\[\\[(ChatUpgrade|CICode),url=([^,\\]]+)(?:,name=([^\\]]+))?\\]\\]");

    private static final Pattern BRACKET_PAYLOAD_LOOSE = Pattern.compile(
            "\\[\\[(?:ChatUpgrade|CICode),url=[^\\]]+(?:,[^\\]]+)?\\]\\]");

    /** Matches {@link #buildPlaceholder(String)} and fullwidth-colon variants after wrapping / font shaping. */
    private static final Pattern VISIBLE_PLACEHOLDER = Pattern.compile("\\[图片[:：]\\s*[^\\]]+\\]");

    private UpgradeBracketCodec() {}

    public record DecodedBracket(Component modified, @Nullable String url, @Nullable String name) {
        public boolean hasUrl() {
            return url != null;
        }
    }

    public static DecodedBracket decodeIncoming(Component original) {
        Matcher m = BRACKET_PAYLOAD.matcher(buildFullText(original));
        if (!m.find()) {
            return new DecodedBracket(original, null, null);
        }
        String url = m.group(2).trim();
        String name = m.group(3) != null ? m.group(3).trim() : "图片";
        String matched = m.group(0);

        Component modified = replaceMatchedPayload(original, matched, name, url);
        return new DecodedBracket(modified, url, name);
    }

    private static Component replaceMatchedPayload(Component component, String exactPayload, String name, String url) {
        String probe = buildFullText(component);
        if (!probe.contains("[[" + WIRE_TAG_NATIVE) && !probe.contains("[[" + WIRE_TAG_LEGACY)) {
            return component;
        }

        List<StyledRun> runs = collectStyledRuns(component, Style.EMPTY);
        if (runs.isEmpty()) {
            return replaceMatchedPayloadFlatten(component, probe, exactPayload, name, url);
        }

        StringBuilder joined = new StringBuilder();
        for (StyledRun run : runs) {
            joined.append(run.text);
        }
        String fullText = joined.toString();

        int replaceStart = fullText.indexOf(exactPayload);
        int replaceEnd;
        if (replaceStart >= 0) {
            replaceEnd = replaceStart + exactPayload.length();
        } else {
            Matcher fm = BRACKET_PAYLOAD_LOOSE.matcher(fullText);
            if (!fm.find()) {
                return component;
            }
            replaceStart = fm.start();
            replaceEnd = fm.end();
        }

        MutableComponent out = Component.empty().withStyle(component.getStyle());
        boolean inserted = false;
        int g = 0;
        for (StyledRun run : runs) {
            int rb = g;
            int re = g + run.text.length();
            g = re;

            if (re <= replaceStart) {
                appendStyledFragment(out, run.style, run.text);
                continue;
            }
            if (rb >= replaceEnd) {
                appendStyledFragment(out, run.style, run.text);
                continue;
            }
            if (rb < replaceStart) {
                appendStyledFragment(out, run.style, run.text.substring(0, replaceStart - rb));
            }
            if (!inserted) {
                out.append(buildPlaceholderComponent(name, url));
                inserted = true;
            }
            if (re > replaceEnd) {
                appendStyledFragment(out, run.style, run.text.substring(replaceEnd - rb));
            }
        }

        if (!inserted) {
            return replaceMatchedPayloadFlatten(component, fullText, exactPayload, name, url);
        }
        return out;
    }

    /** Fallback when structured visit yields no runs (e.g. unusual {@link Component} types). */
    private static Component replaceMatchedPayloadFlatten(Component component, String fullText, String exactPayload, String name, String url) {
        String replaced = fullText.replace(exactPayload, buildPlaceholder(name));
        if (replaced.equals(fullText)) {
            replaced = BRACKET_PAYLOAD_LOOSE.matcher(fullText).replaceFirst(Matcher.quoteReplacement(buildPlaceholder(name)));
        }
        return Component.literal(replaced).withStyle(component.getStyle());
    }

    private record StyledRun(Style style, String text) {}

    /**
     * Depth-first plain-text segments in the same order as {@link #buildFullText(Component)}, each with the effective
     * style used when that substring is drawn.
     */
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

    private static void appendStyledFragment(MutableComponent out, Style style, String fragment) {
        if (fragment.isEmpty()) {
            return;
        }
        MutableComponent bit = Component.literal(fragment);
        if (!style.isEmpty()) {
            bit.setStyle(style);
        }
        out.append(bit);
    }

    private static String buildFullText(Component component) {
        StringBuilder sb = new StringBuilder();
        component.visit(s -> {
            sb.append(s);
            return Optional.empty();
        });
        return sb.toString();
    }

    public static Component buildPlaceholderComponent(String name, String imageUrl) {
        Style style = Style.EMPTY.withColor(ChatFormatting.AQUA).withItalic(true);
        if (ChatUpgradeConfig.get().manualImageReveal && imageUrl != null && !imageUrl.isBlank()) {
            style = style.withUnderlined(true)
                    .withClickEvent(ManualRevealClickEvent.forUrl(imageUrl))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("点击加载图片预览")));
        }
        return Component.literal("[图片: " + name + "]").withStyle(style);
    }

    private static String buildPlaceholder(String name) {
        return "[图片: " + name + "]";
    }

    /**
     * When an inline preview fails to load asynchronously, the HUD still shows {@link #buildPlaceholder(String)} on
     * {@link net.minecraft.client.multiplayer.chat.GuiMessage.Line}s. The chat list is updated at runtime (see
     * {@link UpgradePhantomHudLayout}) by replacing that visible segment with {@link #LOAD_FAILED_VISIBLE} while
     * keeping the rest of the line (e.g. player name).
     *
     * @return a new sequence if a placeholder was replaced; {@code null} if none matched
     */
    public static @Nullable FormattedCharSequence replaceVisiblePlaceholderWithLoadFailed(FormattedCharSequence seq) {
        return replaceVisiblePlaceholderWithVisibleText(seq, LOAD_FAILED_VISIBLE);
    }

    public static @Nullable FormattedCharSequence replaceVisiblePlaceholderWithOversize(FormattedCharSequence seq) {
        return replaceVisiblePlaceholderWithVisibleText(seq, IMAGE_OVERSIZE_VISIBLE);
    }

    /**
     * Replaces the first {@link #VISIBLE_PLACEHOLDER} match with {@code visibleReplacement} (plain text, one style).
     *
     * @return a new sequence if matched; {@code null} if none
     */
    public static @Nullable FormattedCharSequence replaceVisiblePlaceholderWithVisibleText(
            FormattedCharSequence seq,
            String visibleReplacement
    ) {
        PlainAndStyles ps = extractPlainAndStyles(seq);
        Matcher m = VISIBLE_PLACEHOLDER.matcher(ps.plain);
        if (!m.find()) {
            return null;
        }
        int start = m.start();
        int end = m.end();
        List<FormattedCharSequence> parts = new ArrayList<>(3);
        if (start > 0) {
            parts.add(span(ps, 0, start));
        }
        parts.add(FormattedCharSequence.forward(visibleReplacement, ps.styleAt(start)));
        if (end < ps.plain.length()) {
            parts.add(span(ps, end, ps.plain.length()));
        }
        return compositeSequences(parts);
    }

    private static FormattedCharSequence compositeSequences(List<FormattedCharSequence> parts) {
        parts.removeIf(p -> p == FormattedCharSequence.EMPTY);
        if (parts.isEmpty()) {
            return FormattedCharSequence.EMPTY;
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return FormattedCharSequence.composite(parts);
    }

    private static FormattedCharSequence span(PlainAndStyles ps, int from, int to) {
        if (from >= to) {
            return FormattedCharSequence.EMPTY;
        }
        List<FormattedCharSequence> sub = new ArrayList<>();
        int i = from;
        while (i < to) {
            Style s = ps.styles.get(i);
            int j = i + 1;
            while (j < to && ps.styles.get(j).equals(s)) {
                j++;
            }
            sub.add(FormattedCharSequence.forward(ps.plain.substring(i, j), s));
            i = j;
        }
        return compositeSequences(sub);
    }

    private static PlainAndStyles extractPlainAndStyles(FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        seq.accept((index, style, codePoint) -> {
            if (Character.isBmpCodePoint(codePoint)) {
                sb.append((char) codePoint);
                styles.add(style);
            } else {
                sb.append(Character.toChars(codePoint));
                styles.add(style);
                styles.add(style);
            }
            return true;
        });
        return new PlainAndStyles(sb.toString(), styles);
    }

    private static final class PlainAndStyles {
        final String plain;
        final List<Style> styles;

        PlainAndStyles(String plain, List<Style> styles) {
            this.plain = plain;
            this.styles = styles;
        }

        Style styleAt(int index) {
            return styles.get(index);
        }
    }

    /** Outgoing payload; tag depends on {@link ChatUpgradeConfig#ciCompatibility}. */
    public static String buildSendPayload(String url, String name) {
        return ChatUpgradeConfig.get().ciCompatibility
                ? encodeLegacyTagBlock(url, name)
                : encodeNativeTagBlock(url, name);
    }

    public static String encodeNativeTagBlock(String url, String name) {
        if (name != null && !name.isBlank()) {
            return "[[" + WIRE_TAG_NATIVE + ",url=" + url + ",name=" + name + "]]";
        }
        return "[[" + WIRE_TAG_NATIVE + ",url=" + url + "]]";
    }

    public static String encodeLegacyTagBlock(String url, String name) {
        if (name != null && !name.isBlank()) {
            return "[[" + WIRE_TAG_LEGACY + ",url=" + url + ",name=" + name + "]]";
        }
        return "[[" + WIRE_TAG_LEGACY + ",url=" + url + "]]";
    }
}
