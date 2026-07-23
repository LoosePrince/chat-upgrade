package com.chat.upgrade.client.ui.screen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.client.ChatUpgradeFormatters;
import com.chat.upgrade.client.media.audio.AudioEntry;
import com.chat.upgrade.client.media.audio.AudioLoader;
import com.chat.upgrade.client.media.image.ImageEntry;
import com.chat.upgrade.client.media.image.ImageLoader;
import com.chat.upgrade.client.media.model.BaseMediaEntry;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.media.video.VideoEntry;
import com.chat.upgrade.client.media.video.VideoLoader;
import com.chat.upgrade.client.ui.chat.state.ChatAuthor;
import com.chat.upgrade.client.ui.chat.state.ChatAvatar;
import com.chat.upgrade.client.ui.chat.state.RichChatMessage;
import com.chat.upgrade.client.ui.chat.state.RichChatMessageStatus;
import com.chat.upgrade.client.ui.chat.state.RichChatStateStore;

import net.minecraft.client.resources.language.I18n;

/** Builds profile and attachment details from the current chat state snapshot. */
public final class ChatDetailsModelFactory {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final int RECENT_MESSAGE_LIMIT = 5;

    private ChatDetailsModelFactory() {
    }

    public static ChatDetailsModel profile(RichChatMessage selectedMessage) {
        if (selectedMessage == null) {
            throw new IllegalArgumentException("selectedMessage must not be null");
        }
        ChatAuthor author = selectedMessage.author();
        String identityKey = author.identityKey();
        List<RichChatMessage> authoredMessages = RichChatStateStore.snapshotNewestFirst().stream()
                .filter(message -> message.status() != RichChatMessageStatus.DELETED)
                .filter(message -> sameAuthor(author, identityKey, message.author()))
                .toList();
        if (authoredMessages.isEmpty()) {
            authoredMessages = List.of(selectedMessage);
        }

        long mediaCount = authoredMessages.stream().mapToLong(message -> message.attachments().size()).sum();
        long sourceCount = authoredMessages.stream().map(RichChatMessage::source).distinct().count();
        long firstSeen = authoredMessages.stream()
                .mapToLong(RichChatMessage::serverTimestampMs)
                .filter(value -> value > 0L)
                .min()
                .orElse(0L);
        long lastSeen = authoredMessages.stream()
                .mapToLong(RichChatMessage::serverTimestampMs)
                .filter(value -> value > 0L)
                .max()
                .orElse(0L);
        String teamName = author.team().present()
                ? author.team().teamName()
                : I18n.get("chatupgrade.common.na");
        int accent = author.team().colorRgb() >= 0
                ? 0xFF000000 | author.team().colorRgb()
                : 0xFF64C8FF;

        List<ChatDetailsModel.Section> sections = new ArrayList<>();
        sections.add(new ChatDetailsModel.Section(
                I18n.get("chatupgrade.details.section.identity"),
                List.of(
                        field("profile.name", "chatupgrade.details.field.name", author.visibleName()),
                        field("profile.identity", "chatupgrade.details.field.identity", identityKey),
                        field("profile.uuid", "chatupgrade.details.field.uuid",
                                author.playerId() == null ? "" : author.playerId().toString()),
                        field("profile.team", "chatupgrade.details.field.team", teamName),
                        field("profile.local", "chatupgrade.details.field.local_player",
                                I18n.get(author.localPlayer() ? "chatupgrade.common.yes" : "chatupgrade.common.no")),
                        field("profile.first_seen", "chatupgrade.details.field.first_seen", formatTimestamp(firstSeen)),
                        field("profile.last_seen", "chatupgrade.details.field.last_seen", formatTimestamp(lastSeen)))));

        List<ChatDetailsModel.Field> recent = authoredMessages.stream()
                .sorted(Comparator.comparingLong(RichChatMessage::serverTimestampMs).reversed())
                .limit(RECENT_MESSAGE_LIMIT)
                .map(ChatDetailsModelFactory::recentMessageField)
                .toList();
        if (!recent.isEmpty()) {
            sections.add(new ChatDetailsModel.Section(
                    I18n.get("chatupgrade.details.section.recent_messages"),
                    recent));
        }

        return new ChatDetailsModel(
                ChatDetailsModel.Kind.PROFILE,
                author.visibleName(),
                teamName,
                I18n.get(author.localPlayer()
                        ? "chatupgrade.details.badge.local_player"
                        : "chatupgrade.details.badge.player"),
                accent,
                ChatDetailsModel.HeroVisual.player(ChatAvatar.forMessage(author, selectedMessage.kind())),
                new ChatDetailsModel.HeroStats(
                        String.valueOf(authoredMessages.size()),
                        String.valueOf(mediaCount),
                        String.valueOf(sourceCount)),
                sections,
                null);
    }

    public static ChatDetailsModel attachment(RichChatMessage sourceMessage, RichAttachment attachment) {
        if (sourceMessage == null || attachment == null) {
            throw new IllegalArgumentException("sourceMessage and attachment must not be null");
        }
        List<RichChatMessage> relatedMessages = RichChatStateStore.snapshotNewestFirst().stream()
                .filter(message -> message.status() != RichChatMessageStatus.DELETED)
                .filter(message -> message.attachments().stream()
                        .anyMatch(candidate -> sameAttachment(attachment, candidate)))
                .toList();
        if (relatedMessages.isEmpty()) {
            relatedMessages = List.of(sourceMessage);
        }

        RuntimeMedia runtime = runtimeMedia(attachment);
        String typeLabel = typeLabel(attachment.type());
        List<ChatDetailsModel.Section> sections = new ArrayList<>();
        sections.add(new ChatDetailsModel.Section(
                I18n.get("chatupgrade.details.section.file"),
                List.of(
                        field("file.name", "chatupgrade.details.field.name", attachment.displayName()),
                        field("file.type", "chatupgrade.details.field.type", typeLabel),
                        field("file.url", "chatupgrade.details.field.url", attachment.urlOrNull()),
                        field("file.source", "chatupgrade.details.field.attachment_source",
                                attachmentSourceLabel(attachment)),
                        field("file.schema", "chatupgrade.details.field.schema", attachment.schemaVersion()),
                        field("file.media_id", "chatupgrade.details.field.media_id", attachment.mediaId().orElse("")),
                        field("file.attachment_id", "chatupgrade.details.field.attachment_id",
                                attachment.attachmentId().orElse("")))));
        sections.add(new ChatDetailsModel.Section(
                I18n.get("chatupgrade.details.section.media"),
                runtime.fields()));
        sections.add(new ChatDetailsModel.Section(
                I18n.get("chatupgrade.details.section.source"),
                List.of(
                        field("source.author", "chatupgrade.details.field.source_author",
                                sourceMessage.author().visibleName()),
                        field("source.author_identity", "chatupgrade.details.field.source_identity",
                                sourceMessage.author().identityKey()),
                        field("source.message", "chatupgrade.details.field.message_id", sourceMessage.messageId()),
                        field("source.pipeline", "chatupgrade.details.field.message_source",
                                messageSourceLabel(sourceMessage)),
                        field("source.time", "chatupgrade.details.field.sent_at",
                                formatTimestamp(sourceMessage.serverTimestampMs())),
                        field("source.content", "chatupgrade.details.field.message_content",
                                messageExcerpt(sourceMessage)))));

        List<ChatDetailsModel.Field> related = relatedMessages.stream()
                .limit(RECENT_MESSAGE_LIMIT)
                .map(ChatDetailsModelFactory::relatedMessageField)
                .toList();
        sections.add(new ChatDetailsModel.Section(
                I18n.get("chatupgrade.details.section.related_messages", relatedMessages.size()),
                related));

        ChatDetailsModel.Preview preview = switch (attachment.type()) {
            case IMAGE, VIDEO -> attachment.hasRenderableUrl()
                    ? new ChatDetailsModel.Preview(
                            attachment.type(),
                            attachment.requireRenderableUrl(),
                            attachment.displayName())
                    : null;
            case AUDIO -> null;
        };
        return new ChatDetailsModel(
                ChatDetailsModel.Kind.ATTACHMENT,
                attachment.displayName(),
                typeLabel + " · " + sourceMessage.author().visibleName(),
                I18n.get("chatupgrade.details.badge.file"),
                accentFor(attachment.type()),
                ChatDetailsModel.HeroVisual.media(attachment.type()),
                new ChatDetailsModel.HeroStats(
                        String.valueOf(relatedMessages.size()),
                        runtime.size(),
                        runtime.summary()),
                sections,
                preview);
    }

    private static RuntimeMedia runtimeMedia(RichAttachment attachment) {
        if (!attachment.hasRenderableUrl()) {
            return RuntimeMedia.empty();
        }
        String url = attachment.requireRenderableUrl();
        BaseMediaEntry<?, ?, ?> entry = switch (attachment.type()) {
            case IMAGE -> ImageLoader.getIfPresent(url);
            case AUDIO -> AudioLoader.getIfPresent(url);
            case VIDEO -> VideoLoader.getIfPresent(url);
        };
        List<ChatDetailsModel.Field> fields = new ArrayList<>();
        fields.add(field(
                "media.state",
                "chatupgrade.details.field.load_state",
                entry == null ? I18n.get("chatupgrade.common.not_loaded") : stateLabel(entry.getState().name())));
        int byteLength = entry == null ? -1 : entry.getFetchedByteLength();
        String size = byteLength < 0 ? I18n.get("chatupgrade.common.na") : ChatUpgradeFormatters.formatBytes(byteLength);
        fields.add(field("media.size", "chatupgrade.details.field.file_size", byteLength < 0 ? "" : size));
        fields.add(field("media.content_type", "chatupgrade.details.field.content_type",
                entry == null ? "" : entry.getContentType()));
        fields.add(field("media.md5", "chatupgrade.details.field.md5", entry == null ? "" : entry.getMd5Hex()));

        String summary = I18n.get("chatupgrade.common.na");
        if (attachment.type() == InlineResourceType.IMAGE) {
            ImageEntry image = ImageLoader.getIfPresent(url);
            String resolution = image == null || image.getRawPixelWidth() <= 0 || image.getRawPixelHeight() <= 0
                    ? ""
                    : image.getRawPixelWidth() + " × " + image.getRawPixelHeight();
            fields.add(field("media.resolution", "chatupgrade.details.field.resolution", resolution));
            fields.add(field("media.format", "chatupgrade.details.field.format",
                    image == null ? "" : image.getDecodedFormatName()));
            fields.add(field("media.frames", "chatupgrade.details.field.frames",
                    image == null || image.getAnimationFrameCount() <= 0 ? "" : image.getAnimationFrameCount()));
            if (!resolution.isBlank()) {
                summary = resolution;
            }
        } else if (attachment.type() == InlineResourceType.AUDIO) {
            AudioEntry audio = AudioLoader.getIfPresent(url);
            long duration = audio == null ? 0L : audio.getDurationMs();
            fields.add(field("media.duration", "chatupgrade.details.field.duration",
                    duration <= 0L ? "" : ChatUpgradeFormatters.formatMs(duration)));
            if (duration > 0L) {
                summary = ChatUpgradeFormatters.formatMs(duration);
            }
        } else {
            VideoEntry video = VideoLoader.getIfPresent(url);
            String resolution = video == null || video.getRawWidth() <= 0 || video.getRawHeight() <= 0
                    ? ""
                    : video.getRawWidth() + " × " + video.getRawHeight();
            long duration = video == null ? 0L : video.getDurationMs();
            fields.add(field("media.resolution", "chatupgrade.details.field.resolution", resolution));
            fields.add(field("media.duration", "chatupgrade.details.field.duration",
                    duration <= 0L ? "" : ChatUpgradeFormatters.formatMs(duration)));
            if (!resolution.isBlank()) {
                summary = resolution;
            } else if (duration > 0L) {
                summary = ChatUpgradeFormatters.formatMs(duration);
            }
        }
        return new RuntimeMedia(List.copyOf(fields), size, summary);
    }

    private static ChatDetailsModel.Field recentMessageField(RichChatMessage message) {
        String label = formatTimestamp(message.serverTimestampMs());
        if (label.equals(I18n.get("chatupgrade.common.na"))) {
            label = message.source().name();
        }
        String excerpt = messageExcerpt(message);
        return new ChatDetailsModel.Field(
                "recent." + message.messageId(),
                label,
                excerpt,
                message.messageId() + "\n" + excerpt);
    }

    private static ChatDetailsModel.Field relatedMessageField(RichChatMessage message) {
        String label = message.author().visibleName();
        String timestamp = formatTimestamp(message.serverTimestampMs());
        if (!timestamp.equals(I18n.get("chatupgrade.common.na"))) {
            label += " · " + timestamp;
        }
        String excerpt = messageExcerpt(message);
        return new ChatDetailsModel.Field(
                "related." + message.messageId(),
                label,
                excerpt,
                message.messageId() + "\n" + message.author().visibleName() + "\n" + excerpt);
    }

    private static ChatDetailsModel.Field field(String key, String labelKey, @Nullable Object value) {
        String copyValue = value == null ? "" : String.valueOf(value).trim();
        String visibleValue = copyValue.isBlank() ? I18n.get("chatupgrade.common.na") : copyValue;
        return new ChatDetailsModel.Field(key, I18n.get(labelKey), visibleValue, copyValue);
    }

    private static String messageExcerpt(RichChatMessage message) {
        String excerpt = message.plainText().replaceAll("\\s+", " ").trim();
        if (excerpt.isBlank() && !message.attachments().isEmpty()) {
            excerpt = "[" + message.attachments().getFirst().displayName() + "]";
        }
        if (excerpt.length() > 120) {
            return excerpt.substring(0, 119) + "…";
        }
        return excerpt.isBlank() ? I18n.get("chatupgrade.common.na") : excerpt;
    }

    private static String formatTimestamp(long timestampMs) {
        return timestampMs <= 0L
                ? I18n.get("chatupgrade.common.na")
                : DATE_TIME.format(Instant.ofEpochMilli(timestampMs));
    }

    private static boolean sameAuthor(ChatAuthor selected, String identityKey, ChatAuthor candidate) {
        if (!identityKey.isBlank()) {
            return identityKey.equals(candidate.identityKey());
        }
        return selected.searchableName().equalsIgnoreCase(candidate.searchableName());
    }

    private static boolean sameAttachment(RichAttachment left, RichAttachment right) {
        if (left.type() != right.type()) {
            return false;
        }
        if (samePresent(left.attachmentId().orElse(""), right.attachmentId().orElse(""))) {
            return true;
        }
        if (samePresent(left.mediaId().orElse(""), right.mediaId().orElse(""))) {
            return true;
        }
        return samePresent(left.urlOrNull(), right.urlOrNull());
    }

    private static boolean samePresent(@Nullable String left, @Nullable String right) {
        return left != null && !left.isBlank() && left.equals(right);
    }

    private static String typeLabel(InlineResourceType type) {
        return I18n.get(switch (type) {
            case IMAGE -> "chatupgrade.type.image";
            case AUDIO -> "chatupgrade.type.audio";
            case VIDEO -> "chatupgrade.type.video";
        });
    }

    private static String attachmentSourceLabel(RichAttachment attachment) {
        return I18n.get(switch (attachment.source()) {
            case BRACKET_PROTOCOL -> "chatupgrade.details.source.bracket";
            case STRUCTURED_PACKET -> "chatupgrade.details.source.structured";
            case LOCAL_DRAFT -> "chatupgrade.details.source.local";
        });
    }

    private static String messageSourceLabel(RichChatMessage message) {
        return I18n.get(switch (message.source()) {
            case VANILLA_TEXT -> "chatupgrade.details.source.vanilla";
            case BRACKET_PROTOCOL -> "chatupgrade.details.source.bracket";
            case STRUCTURED_PACKET -> "chatupgrade.details.source.structured";
            case LOCAL_DRAFT -> "chatupgrade.details.source.local";
            case LOCAL_SYSTEM -> "chatupgrade.details.source.system";
        });
    }

    private static String stateLabel(String state) {
        return I18n.get(switch (state) {
            case "LOADED" -> "chatupgrade.common.loaded";
            case "FAILED" -> "chatupgrade.common.failed";
            default -> "chatupgrade.details.state.loading";
        });
    }

    private static int accentFor(InlineResourceType type) {
        return switch (type) {
            case IMAGE -> 0xFF67D5B5;
            case AUDIO -> 0xFFB39DDB;
            case VIDEO -> 0xFF64C8FF;
        };
    }

    private record RuntimeMedia(List<ChatDetailsModel.Field> fields, String size, String summary) {
        private static RuntimeMedia empty() {
            return new RuntimeMedia(
                    List.of(field("media.state", "chatupgrade.details.field.load_state", "")),
                    I18n.get("chatupgrade.common.na"),
                    I18n.get("chatupgrade.common.na"));
        }
    }
}