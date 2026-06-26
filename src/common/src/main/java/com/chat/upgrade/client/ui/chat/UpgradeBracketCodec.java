package com.chat.upgrade.client.ui.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.net.ServerMediaUrl;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.ChatUpgradeFormatters;
import com.chat.upgrade.client.media.audio.AudioEntry;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.audio.AudioPlayerService;
import com.chat.upgrade.client.media.image.ImageEntry;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.net.servermedia.ServerMediaClient;
import com.chat.upgrade.client.media.video.VideoEntry;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.media.video.VideoPlayerService;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Encodes and decodes bracketed URL payloads {@code [[tag,url=…]]} for chat.
 */
public final class UpgradeBracketCodec {
    /** Standard Chat Upgrade bracket tag for outgoing messages. */
    public static final String WIRE_TAG_NATIVE = "ChatUpgrade";

    /** Supported CICode bracket tag used when {@link ChatUpgradeConfig#ciCompatibility} is enabled. */
    public static final String WIRE_TAG_CI_COMPAT = "CICode";

    /**
     * Shown when the URL payload was replaced in chat ({@link #buildPlaceholder})
     * but the resource could not be loaded.
     * Used by
     * {@link #replaceVisiblePlaceholderWithLoadFailed(FormattedCharSequence)} on
     * HUD lines.
     */
    public static final String LOAD_FAILED_VISIBLE = I18n.get("chatupgrade.visible.image.load_failed");
    public static final String AUDIO_LOAD_FAILED_VISIBLE = I18n.get("chatupgrade.visible.audio.load_failed");
    public static final String VIDEO_LOAD_FAILED_VISIBLE = I18n.get("chatupgrade.visible.video.load_failed");

    /**
     * Shown when the remote image exceeds
     * {@link ChatUpgradeConfig#maxReceiveBytes}.
     */
    public static final String IMAGE_OVERSIZE_VISIBLE = I18n.get("chatupgrade.visible.image.oversize");
    public static final String AUDIO_OVERSIZE_VISIBLE = I18n.get("chatupgrade.visible.audio.oversize");
    public static final String VIDEO_OVERSIZE_VISIBLE = I18n.get("chatupgrade.visible.video.oversize");

    private static final Pattern BRACKET_PAYLOAD = Pattern.compile(
            "\\[\\[(ChatUpgrade|CICode),([^\\]]+)\\]\\]");

    private static final Pattern BRACKET_PAYLOAD_LOOSE = Pattern.compile(
            "\\[\\[(?:ChatUpgrade|CICode),url=[^\\]]+(?:,[^\\]]+)?\\]\\]");

    /**
     * Matches image/audio placeholders and fullwidth-colon variants after wrapping
     * / font shaping.
     */
    private static final Pattern VISIBLE_PLACEHOLDER = Pattern.compile("\\[(?:.+?)[:：]\\s*[^\\]]+\\]");

    private UpgradeBracketCodec() {
    }

    public record DecodedBracket(
            Component modified,
            Optional<RichAttachment> attachment) {
        public boolean hasUrl() {
            return attachment.isPresent() && attachment.get().hasRenderableUrl();
        }

        public @Nullable String url() {
            return attachment.map(RichAttachment::urlOrNull).orElse(null);
        }

        public @Nullable String name() {
            return attachment.map(RichAttachment::displayName).orElse(null);
        }

        public InlineResourceType resourceType() {
            return attachment.map(RichAttachment::type).orElse(InlineResourceType.IMAGE);
        }
    }

    public static DecodedBracket decodeIncoming(Component original) {
        Matcher m = BRACKET_PAYLOAD.matcher(buildFullText(original));
        if (!m.find()) {
            return new DecodedBracket(original, Optional.empty());
        }
        String attrs = m.group(2);
        Map<String, String> kv = parsePayloadAttributes(attrs);
        String url = kv.getOrDefault("url", "").trim();
        if (url.isBlank()) {
            return new DecodedBracket(original, Optional.empty());
        }
        InlineResourceType type = InlineResourceType.fromWire(kv.get("type"));
        String defaultName = switch (type) {
            case IMAGE -> I18n.get("chatupgrade.type.image");
            case AUDIO -> I18n.get("chatupgrade.type.audio");
            case VIDEO -> I18n.get("chatupgrade.type.video");
        };
        String name = kv.getOrDefault("name", defaultName).trim();
        String matched = m.group(0);

        RichAttachment attachment = resolveIncomingAttachment(url, name, type);
        Component modified = replaceMatchedPayload(original, matched, attachment);
        return new DecodedBracket(modified, Optional.of(attachment));
    }

    private static RichAttachment resolveIncomingAttachment(String url, String name, InlineResourceType type) {
        Optional<RichAttachment> cachedAttachment = ServerMediaClient.cachedAttachmentForUrl(url)
                .map(RichAttachment::fromStructured);
        if (cachedAttachment.isPresent()) {
            return cachedAttachment.get();
        }
        ServerMediaClient.requestAttachmentForUrlIfNeeded(url);
        return RichAttachment.bracketProtocol(url, name, type);
    }

    private static Component replaceMatchedPayload(
            Component component,
            String exactPayload,
            RichAttachment attachment) {
        String name = attachment.displayName();
        String url = attachment.urlOrNull();
        InlineResourceType type = attachment.type();
        String probe = buildFullText(component);
        if (!probe.contains("[[" + WIRE_TAG_NATIVE) && !probe.contains("[[" + WIRE_TAG_CI_COMPAT)) {
            return component;
        }

        List<StyledRun> runs = collectStyledRuns(component, Style.EMPTY);
        if (runs.isEmpty()) {
            return replaceMatchedPayloadFlatten(component, probe, exactPayload, name, url, type);
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
                out.append(buildPlaceholderComponent(type, name, url));
                inserted = true;
            }
            if (re > replaceEnd) {
                appendStyledFragment(out, run.style, run.text.substring(replaceEnd - rb));
            }
        }

        if (!inserted) {
            return replaceMatchedPayloadFlatten(component, fullText, exactPayload, name, url, type);
        }
        return out;
    }

    /**
     * Fallback when structured visit yields no runs (e.g. unusual {@link Component}
     * types).
     */
    private static Component replaceMatchedPayloadFlatten(
            Component component,
            String fullText,
            String exactPayload,
            String name,
            String url,
            InlineResourceType type) {
        String replaced = fullText.replace(exactPayload, buildPlaceholder(type, name));
        if (replaced.equals(fullText)) {
            replaced = BRACKET_PAYLOAD_LOOSE.matcher(fullText)
                    .replaceFirst(Matcher.quoteReplacement(buildPlaceholder(type, name)));
        }
        return Component.literal(replaced).withStyle(component.getStyle());
    }

    private static Map<String, String> parsePayloadAttributes(String attrs) {
        Map<String, String> out = new HashMap<>();
        for (String token : attrs.split(",")) {
            int eq = token.indexOf('=');
            if (eq <= 0 || eq >= token.length() - 1) {
                continue;
            }
            String k = token.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String v = token.substring(eq + 1).trim();
            if (!k.isBlank() && !v.isBlank()) {
                out.put(k, v);
            }
        }
        return out;
    }

    private record StyledRun(Style style, String text) {
    }

    /**
     * Depth-first plain-text segments in the same order as
     * {@link #buildFullText(Component)}, each with the effective
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

    public static Component buildPlaceholderComponent(InlineResourceType type, String name, String imageUrl) {
        Style style = Style.EMPTY.withColor(ChatFormatting.AQUA).withItalic(true)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(buildLabelHoverText(type, name, imageUrl))));
        boolean manualRevealEnabled = switch (type) {
            case IMAGE -> ChatUpgradeConfig.get().manualImageReveal;
            case AUDIO -> ChatUpgradeConfig.get().manualAudioReveal;
            case VIDEO -> ChatUpgradeConfig.get().manualVideoReveal;
        };
        if (imageUrl != null && !imageUrl.isBlank()) {
            String actionText = manualRevealEnabled
                    ? switch (type) {
                        case IMAGE -> I18n.get("chatupgrade.placeholder.click_reload_image");
                        case AUDIO -> I18n.get("chatupgrade.placeholder.click_reload_audio");
                        case VIDEO -> I18n.get("chatupgrade.placeholder.click_reload_video");
                    }
                    : I18n.get("chatupgrade.placeholder.click_force_reload");
            style = style.withUnderlined(true)
                    .withClickEvent(ManualRevealClickEvent.forResource(type, imageUrl))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Component.literal(actionText + "\n" + buildLabelHoverText(type, name, imageUrl))));
        }
        String label = switch (type) {
            case IMAGE -> I18n.get("chatupgrade.type.image");
            case AUDIO -> I18n.get("chatupgrade.type.audio");
            case VIDEO -> I18n.get("chatupgrade.type.video");
        };
        MutableComponent left = Component.literal("[" + label + ": " + name + "]").withStyle(style);
        MutableComponent urlToken = Component.literal(" [url]").withStyle(buildUrlTokenStyle(imageUrl));
        return left.append(urlToken);
    }

    private static String buildPlaceholder(InlineResourceType type, String name) {
        String label = switch (type) {
            case IMAGE -> I18n.get("chatupgrade.type.image");
            case AUDIO -> I18n.get("chatupgrade.type.audio");
            case VIDEO -> I18n.get("chatupgrade.type.video");
        };
        return "[" + label + ": " + name + "] [url]";
    }

    private static Style buildUrlTokenStyle(String url) {
        Style style = Style.EMPTY.withColor(ChatFormatting.GRAY).withUnderlined(true).withItalic(false)
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chatupgrade.hover.open_url", (url == null ? "" : url))));
        if (url == null || url.isBlank()) {
            return style;
        }
        try {
            return style.withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url)));
        } catch (Exception e) {
            return style;
        }
    }

    private static String buildLabelHoverText(InlineResourceType type, String name, String url) {
        String typeText = switch (type) {
            case IMAGE -> I18n.get("chatupgrade.type.image");
            case AUDIO -> I18n.get("chatupgrade.type.audio");
            case VIDEO -> I18n.get("chatupgrade.type.video");
        };
        String currentWireTag = ChatUpgradeConfig.get().ciCompatibility ? WIRE_TAG_CI_COMPAT : WIRE_TAG_NATIVE;
        String receiveLimit = ChatUpgradeConfig.formatBytesHuman(ChatUpgradeConfig.get().maxReceiveBytes);
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.get("chatupgrade.hover.resource_type")).append(": ").append(typeText).append('\n');
        sb.append(I18n.get("chatupgrade.hover.display_name")).append(": ")
                .append(name == null || name.isBlank() ? I18n.get("chatupgrade.common.na") : name).append('\n');
        sb.append(I18n.get("chatupgrade.hover.url")).append(": ")
                .append(url == null || url.isBlank() ? I18n.get("chatupgrade.common.na") : url).append('\n');
        if (type == InlineResourceType.IMAGE) {
            sb.append(I18n.get("chatupgrade.hover.parse_mode")).append(": ").append(currentWireTag).append('\n');
        }
        sb.append(I18n.get("chatupgrade.hover.receive_limit")).append(": ").append(receiveLimit).append('\n');
        appendResourceDetails(sb, type, url);
        sb.append(I18n.get("chatupgrade.hover.interaction")).append(":\n");
        sb.append(" - ").append(I18n.get("chatupgrade.hover.interaction.open_url")).append('\n');
        sb.append(" - ").append(I18n.get("chatupgrade.hover.interaction.hover_preview"));
        return sb.toString();
    }

    private static void appendResourceDetails(StringBuilder sb, InlineResourceType type, String url) {
        if (url == null || url.isBlank()) {
            sb.append(I18n.get("chatupgrade.detail.status")).append(": ").append(I18n.get("chatupgrade.detail.not_cached")).append('\n');
            return;
        }
        switch (type) {
            case IMAGE -> {
                ImageEntry e = ImageLoader.getIfPresent(url);
                if (e == null) {
                    sb.append(I18n.get("chatupgrade.detail.status")).append(": ").append(I18n.get("chatupgrade.detail.not_cached")).append('\n');
                    return;
                }
                sb.append(I18n.get("chatupgrade.detail.status")).append(": ").append(switch (e.getState()) {
                    case LOADING -> I18n.get("chatupgrade.detail.loading", e.getLoadPhase() == ImageEntry.LoadPhase.DECODE
                            ? I18n.get("chatupgrade.detail.phase.decode")
                            : I18n.get("chatupgrade.detail.phase.download"));
                    case LOADED -> I18n.get("chatupgrade.detail.loaded");
                    case FAILED -> {
                        ImageEntry.FailureKind fk = e.getFailureKind();
                        yield switch (fk) {
                            case RESPONSE_BODY_TOO_LARGE -> I18n.get("chatupgrade.detail.failed.too_large");
                            case UNKNOWN -> I18n.get("chatupgrade.detail.failed.unknown");
                        };
                    }
                }).append('\n');
                sb.append(I18n.get("chatupgrade.detail.transferred")).append(": ")
                        .append(ChatUpgradeFormatters.formatBytes(e.getFetchedByteLength())).append('\n');
                sb.append("MD5: ").append(e.getMd5Hex() == null ? I18n.get("chatupgrade.common.na") : e.getMd5Hex()).append('\n');
                if (e.isLoaded()) {
                    sb.append(I18n.get("chatupgrade.detail.pixel_size")).append(": ").append(e.getRawPixelWidth()).append('×').append(e.getRawPixelHeight())
                            .append('\n');
                    sb.append(I18n.get("chatupgrade.detail.preview_size")).append(": ").append(e.getWidth()).append('×').append(e.getHeight()).append('\n');
                    sb.append(I18n.get("chatupgrade.detail.texture_size")).append(": ").append(e.getTextureWidth()).append('×').append(e.getTextureHeight())
                            .append('\n');
                    if (e.isAnimated()) {
                        sb.append(I18n.get("chatupgrade.detail.animated", e.getAnimationFrameCount())).append('\n');
                    }
                    sb.append(I18n.get("chatupgrade.detail.pixel_format")).append(": ")
                            .append(e.getDecodedFormatName() == null ? I18n.get("chatupgrade.common.na") : e.getDecodedFormatName())
                            .append('\n');
                }
                sb.append(I18n.get("chatupgrade.detail.manual_reveal")).append('=')
                        .append(ChatUpgradeConfig.get().manualImageReveal ? I18n.get("chatupgrade.common.on") : I18n.get("chatupgrade.common.off"))
                        .append('\n');
            }
            case AUDIO -> {
                AudioEntry e = AudioLoader.getIfPresent(url);
                if (e == null) {
                    sb.append(I18n.get("chatupgrade.detail.status")).append(": ").append(I18n.get("chatupgrade.detail.not_cached")).append('\n');
                    return;
                }
                sb.append(I18n.get("chatupgrade.detail.status")).append(": ").append(switch (e.getState()) {
                    case LOADING -> I18n.get("chatupgrade.detail.loading", e.getLoadPhase() == AudioEntry.LoadPhase.DECODE
                            ? I18n.get("chatupgrade.detail.phase.decode")
                            : I18n.get("chatupgrade.detail.phase.download"));
                    case LOADED -> AudioPlayerService.isPlaying(url) ? I18n.get("chatupgrade.detail.playing")
                            : I18n.get("chatupgrade.detail.loaded_paused");
                    case FAILED -> {
                        AudioEntry.FailureKind fk = e.getFailureKind();
                        yield switch (fk) {
                            case RESPONSE_BODY_TOO_LARGE -> I18n.get("chatupgrade.detail.failed.too_large");
                            case UNSUPPORTED_AUDIO_FORMAT -> I18n.get("chatupgrade.detail.failed.audio_unsupported");
                            case UNKNOWN -> I18n.get("chatupgrade.detail.failed.unknown");
                        };
                    }
                }).append('\n');
                sb.append(I18n.get("chatupgrade.detail.transferred")).append(": ")
                        .append(ChatUpgradeFormatters.formatBytes(e.getFetchedByteLength())).append('\n');
                sb.append("MD5: ").append(e.getMd5Hex() == null ? I18n.get("chatupgrade.common.na") : e.getMd5Hex()).append('\n');
                sb.append(I18n.get("chatupgrade.detail.duration")).append(": ").append(ChatUpgradeFormatters.formatMs(e.getDurationMs())).append('\n');
                sb.append(I18n.get("chatupgrade.detail.volume")).append(": ").append(ChatUpgradeConfig.get().audioVolumePercent).append("%\n");
                sb.append(I18n.get("chatupgrade.detail.manual_reveal")).append('=')
                        .append(ChatUpgradeConfig.get().manualAudioReveal ? I18n.get("chatupgrade.common.on") : I18n.get("chatupgrade.common.off")).append('\n');
            }
            case VIDEO -> {
                VideoEntry e = VideoLoader.getIfPresent(url);
                if (e == null) {
                    sb.append(I18n.get("chatupgrade.detail.status")).append(": ").append(I18n.get("chatupgrade.detail.not_cached")).append('\n');
                    return;
                }
                sb.append(I18n.get("chatupgrade.detail.status")).append(": ").append(switch (e.getState()) {
                    case LOADING -> I18n.get("chatupgrade.detail.loading", e.getLoadPhase() == VideoEntry.LoadPhase.DECODE
                            ? I18n.get("chatupgrade.detail.phase.decode")
                            : I18n.get("chatupgrade.detail.phase.download"));
                    case LOADED -> VideoPlayerService.isPlaying(url) ? I18n.get("chatupgrade.detail.playing")
                            : I18n.get("chatupgrade.detail.loaded_paused");
                    case FAILED -> {
                        VideoEntry.FailureKind fk = e.getFailureKind();
                        yield switch (fk) {
                            case RESPONSE_BODY_TOO_LARGE -> I18n.get("chatupgrade.detail.failed.too_large");
                            case UNSUPPORTED_VIDEO_FORMAT -> I18n.get("chatupgrade.detail.failed.video_unsupported");
                            case UNKNOWN -> I18n.get("chatupgrade.detail.failed.unknown");
                        };
                    }
                }).append('\n');
                sb.append(I18n.get("chatupgrade.detail.transferred")).append(": ")
                        .append(ChatUpgradeFormatters.formatBytes(e.getFetchedByteLength())).append('\n');
                sb.append("MD5: ").append(e.getMd5Hex() == null ? I18n.get("chatupgrade.common.na") : e.getMd5Hex()).append('\n');
                sb.append(I18n.get("chatupgrade.detail.duration")).append(": ").append(ChatUpgradeFormatters.formatMs(e.getDurationMs())).append('\n');
                sb.append(I18n.get("chatupgrade.detail.volume")).append(": ").append(ChatUpgradeConfig.get().videoVolumePercent).append("%\n");
                sb.append(I18n.get("chatupgrade.detail.manual_reveal")).append('=')
                        .append(ChatUpgradeConfig.get().manualVideoReveal ? I18n.get("chatupgrade.common.on") : I18n.get("chatupgrade.common.off")).append('\n');
                if (e.isLoaded()) {
                    sb.append(I18n.get("chatupgrade.detail.pixel_size")).append(": ").append(e.getRawWidth()).append('×').append(e.getRawHeight()).append('\n');
                    sb.append(I18n.get("chatupgrade.detail.preview_size")).append(": ").append(e.getDisplayWidth()).append('×').append(e.getDisplayHeight())
                            .append('\n');
                }
            }
        }
    }

    /**
     * When an inline preview fails to load asynchronously, the HUD still shows
     * {@link #buildPlaceholder(String)} on
     * {@link net.minecraft.client.multiplayer.chat.GuiMessage.Line}s. The chat list
     * is updated at runtime (see
     * {@link UpgradePhantomHudLayout}) by replacing that visible segment with
     * {@link #LOAD_FAILED_VISIBLE} while
     * keeping the rest of the line (e.g. player name).
     *
     * @return a new sequence if a placeholder was replaced; {@code null} if none
     *         matched
     */
    public static @Nullable FormattedCharSequence replaceVisiblePlaceholderWithLoadFailed(FormattedCharSequence seq) {
        return replaceVisiblePlaceholderWithVisibleText(seq, LOAD_FAILED_VISIBLE);
    }

    public static @Nullable FormattedCharSequence replaceVisiblePlaceholderWithOversize(FormattedCharSequence seq) {
        return replaceVisiblePlaceholderWithVisibleText(seq, IMAGE_OVERSIZE_VISIBLE);
    }

    public static @Nullable FormattedCharSequence replaceVisibleAudioPlaceholderWithLoadFailed(
            FormattedCharSequence seq) {
        return replaceVisiblePlaceholderWithVisibleText(seq, AUDIO_LOAD_FAILED_VISIBLE);
    }

    public static @Nullable FormattedCharSequence replaceVisibleAudioPlaceholderWithOversize(
            FormattedCharSequence seq) {
        return replaceVisiblePlaceholderWithVisibleText(seq, AUDIO_OVERSIZE_VISIBLE);
    }

    public static @Nullable FormattedCharSequence replaceVisibleVideoPlaceholderWithLoadFailed(
            FormattedCharSequence seq) {
        return replaceVisiblePlaceholderWithVisibleText(seq, VIDEO_LOAD_FAILED_VISIBLE);
    }

    public static @Nullable FormattedCharSequence replaceVisibleVideoPlaceholderWithOversize(
            FormattedCharSequence seq) {
        return replaceVisiblePlaceholderWithVisibleText(seq, VIDEO_OVERSIZE_VISIBLE);
    }

    /**
     * Replaces the first {@link #VISIBLE_PLACEHOLDER} match with
     * {@code visibleReplacement} (plain text, one style).
     *
     * @return a new sequence if matched; {@code null} if none
     */
    public static @Nullable FormattedCharSequence replaceVisiblePlaceholderWithVisibleText(
            FormattedCharSequence seq,
            String visibleReplacement) {
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

    public static @Nullable FormattedCharSequence refreshVisiblePlaceholderHover(
            FormattedCharSequence seq,
            InlineResourceType type,
            @Nullable String url,
            @Nullable String nameHint) {
        PlainAndStyles ps = extractPlainAndStyles(seq);
        Matcher m = VISIBLE_PLACEHOLDER.matcher(ps.plain);
        if (!m.find()) {
            return null;
        }
        int start = m.start();
        int end = m.end();
        String matched = ps.plain.substring(start, end);
        String parsedName = parseNameFromVisiblePlaceholder(matched);
        String name = (nameHint != null && !nameHint.isBlank()) ? nameHint : parsedName;
        Style base = ps.styleAt(start);
        Style refreshed = base
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(buildLabelHoverText(type, name, url))));
        List<FormattedCharSequence> parts = new ArrayList<>(3);
        if (start > 0) {
            parts.add(span(ps, 0, start));
        }
        parts.add(FormattedCharSequence.forward(matched, refreshed));
        if (end < ps.plain.length()) {
            parts.add(span(ps, end, ps.plain.length()));
        }
        return compositeSequences(parts);
    }

    private static String parseNameFromVisiblePlaceholder(String text) {
        int colon = text.indexOf(':');
        if (colon < 0) {
            colon = text.indexOf('：');
        }
        if (colon < 0) {
            return "—";
        }
        int close = text.lastIndexOf(']');
        if (close < 0 || close <= colon + 1) {
            close = text.length();
        }
        String raw = text.substring(colon + 1, close).trim();
        return raw.isBlank() ? "—" : raw;
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

    /**
     * Outgoing payload; tag depends on {@link ChatUpgradeConfig#ciCompatibility}.
     */
    public static String buildSendPayload(String url, String name) {
        return buildSendPayload(url, name, InlineResourceType.IMAGE);
    }

    public static String buildSendPayload(String url, String name, InlineResourceType type) {
        boolean useCiCompat = type == InlineResourceType.IMAGE
                && ChatUpgradeConfig.get().ciCompatibility
                && !ServerMediaUrl.isServerMediaUrl(url);
        return useCiCompat
                ? encodeCiCompatTagBlock(url, name, type)
                : encodeNativeTagBlock(url, name, type);
    }

    public static String encodeNativeTagBlock(String url, String name) {
        return encodeNativeTagBlock(url, name, InlineResourceType.IMAGE);
    }

    public static String encodeNativeTagBlock(String url, String name, InlineResourceType type) {
        String typeField = switch (type) {
            case IMAGE -> "";
            case AUDIO -> ",type=audio";
            case VIDEO -> ",type=video";
        };
        if (name != null && !name.isBlank()) {
            return "[[" + WIRE_TAG_NATIVE + ",url=" + url + ",name=" + name + typeField + "]]";
        }
        return "[[" + WIRE_TAG_NATIVE + ",url=" + url + typeField + "]]";
    }

    public static String encodeCiCompatTagBlock(String url, String name) {
        return encodeCiCompatTagBlock(url, name, InlineResourceType.IMAGE);
    }

    public static String encodeCiCompatTagBlock(String url, String name, InlineResourceType type) {
        String typeField = switch (type) {
            case IMAGE -> "";
            case AUDIO -> ",type=audio";
            case VIDEO -> ",type=video";
        };
        if (name != null && !name.isBlank()) {
            return "[[" + WIRE_TAG_CI_COMPAT + ",url=" + url + ",name=" + name + typeField + "]]";
        }
        return "[[" + WIRE_TAG_CI_COMPAT + ",url=" + url + typeField + "]]";
    }
}
