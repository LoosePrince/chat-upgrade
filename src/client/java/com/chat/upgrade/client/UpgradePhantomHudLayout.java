package com.chat.upgrade.client;

import com.chat.upgrade.client.mixininterface.GuiMessageLineReadable;
import com.chat.upgrade.client.mixininterface.ImageAttachable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Extra preview rows, failure substitution, and per-line paint dispatch for decoded URL payloads. */
public final class UpgradePhantomHudLayout {
    private static final int AUDIO_PHANTOM_COUNT = 3;
    private static final int VIDEO_PHANTOM_COUNT = 7;
    private static final ConcurrentHashMap<String, Set<GuiMessage>> URL_TO_MESSAGE_PARENTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Set<GuiMessage>> AUDIO_URL_TO_MESSAGE_PARENTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Set<GuiMessage>> VIDEO_URL_TO_MESSAGE_PARENTS = new ConcurrentHashMap<>();

    private UpgradePhantomHudLayout() {}

    public static void clearLayoutRegistrations() {
        URL_TO_MESSAGE_PARENTS.clear();
        AUDIO_URL_TO_MESSAGE_PARENTS.clear();
        VIDEO_URL_TO_MESSAGE_PARENTS.clear();
    }

    public static void dispatchLinePaint(GuiMessage.Line line, int messageY, float opacity) {
        UpgradeHudInlinePaint.paintLinePreview(line, messageY, opacity);
    }

    public static void onUrlMessageCommitted(String url, GuiMessage parent, List<GuiMessage.Line> trimmedMessages) {
        registerUrlParent(url, parent);
        syncLayoutForUrl(url, trimmedMessages);
    }

    public static void onAudioMessageCommitted(String url, GuiMessage parent, List<GuiMessage.Line> trimmedMessages) {
        registerAudioParent(url, parent);
        syncLayoutForAudio(url, trimmedMessages);
    }
    public static void onVideoMessageCommitted(String url, GuiMessage parent, List<GuiMessage.Line> trimmedMessages) {
        registerVideoParent(url, parent);
        syncLayoutForVideo(url, trimmedMessages);
    }

    private static void registerUrlParent(String url, GuiMessage parent) {
        URL_TO_MESSAGE_PARENTS.computeIfAbsent(url, k -> ConcurrentHashMap.newKeySet()).add(parent);
    }
    private static void registerAudioParent(String url, GuiMessage parent) {
        AUDIO_URL_TO_MESSAGE_PARENTS.computeIfAbsent(url, k -> ConcurrentHashMap.newKeySet()).add(parent);
    }
    private static void registerVideoParent(String url, GuiMessage parent) {
        VIDEO_URL_TO_MESSAGE_PARENTS.computeIfAbsent(url, k -> ConcurrentHashMap.newKeySet()).add(parent);
    }

    public static void notifyUrlEntryChanged(String url) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        if (mc.gui.getChat() instanceof UpgradeChatHudSync sync) {
            sync.refreshInlineLayoutForUrl(url);
        }
    }
    public static void notifyAudioEntryChanged(String url) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        if (mc.gui.getChat() instanceof UpgradeChatHudSync sync) {
            sync.refreshInlineLayoutForUrl("audio:" + url);
        }
    }
    public static void notifyVideoEntryChanged(String url) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        if (mc.gui.getChat() instanceof UpgradeChatHudSync sync) {
            sync.refreshInlineLayoutForUrl("video:" + url);
        }
    }

    public static void syncLayoutForUrl(String url, List<GuiMessage.Line> trimmedMessages) {
        ImageEntry entry = ImageLoader.getIfPresent(url);
        if (entry == null) {
            return;
        }
        switch (entry.getState()) {
            case FAILED -> handleFailed(url, trimmedMessages, entry.getFailureKind());
            case LOADING, LOADED -> ensurePreviewPhantoms(url, trimmedMessages, entry.getState() == ImageEntry.State.LOADED);
        }
    }
    public static void syncLayoutForAudio(String url, List<GuiMessage.Line> trimmedMessages) {
        AudioEntry entry = AudioLoader.getIfPresent(url);
        if (entry == null) {
            return;
        }
        switch (entry.getState()) {
            case FAILED -> handleFailedAudio(url, trimmedMessages, entry.getFailureKind());
            case LOADING, LOADED -> ensureAudioPhantoms(url, trimmedMessages, entry.getState() == AudioEntry.State.LOADED);
        }
    }
    public static void syncLayoutForVideo(String url, List<GuiMessage.Line> trimmedMessages) {
        VideoEntry entry = VideoLoader.getIfPresent(url);
        if (entry == null) {
            return;
        }
        switch (entry.getState()) {
            case FAILED -> handleFailedVideo(url, trimmedMessages, entry.getFailureKind());
            case LOADING, LOADED -> ensureVideoPhantoms(url, trimmedMessages, entry.getState() == VideoEntry.State.LOADED);
        }
    }

    private static void ensurePreviewPhantoms(String url, List<GuiMessage.Line> trimmedMessages, boolean loaded) {
        Set<GuiMessage> parents = URL_TO_MESSAGE_PARENTS.get(url);
        if (parents == null || parents.isEmpty()) {
            return;
        }
        for (GuiMessage parent : new HashSet<>(parents)) {
            applyHoverRefreshOnTextLines(trimmedMessages, parent, InlineResourceType.IMAGE, url);
            if (!hasPhantomTopForUrl(trimmedMessages, parent, url)) {
                insertPhantomBlock(trimmedMessages, parent, url, InlineResourceType.IMAGE);
            }
        }
        if (loaded) {
            URL_TO_MESSAGE_PARENTS.remove(url);
        }
    }
    private static void ensureAudioPhantoms(String url, List<GuiMessage.Line> trimmedMessages, boolean loaded) {
        Set<GuiMessage> parents = AUDIO_URL_TO_MESSAGE_PARENTS.get(url);
        if (parents == null || parents.isEmpty()) {
            return;
        }
        for (GuiMessage parent : new HashSet<>(parents)) {
            applyHoverRefreshOnTextLines(trimmedMessages, parent, InlineResourceType.AUDIO, url);
            if (!hasPhantomTopForAudio(trimmedMessages, parent, url)) {
                insertPhantomBlock(trimmedMessages, parent, url, InlineResourceType.AUDIO);
            }
        }
        if (loaded) {
            AUDIO_URL_TO_MESSAGE_PARENTS.remove(url);
        }
    }
    private static void ensureVideoPhantoms(String url, List<GuiMessage.Line> trimmedMessages, boolean loaded) {
        Set<GuiMessage> parents = VIDEO_URL_TO_MESSAGE_PARENTS.get(url);
        if (parents == null || parents.isEmpty()) {
            return;
        }
        for (GuiMessage parent : new HashSet<>(parents)) {
            applyHoverRefreshOnTextLines(trimmedMessages, parent, InlineResourceType.VIDEO, url);
            if (!hasPhantomTopForVideo(trimmedMessages, parent, url)) {
                insertPhantomBlock(trimmedMessages, parent, url, InlineResourceType.VIDEO);
            }
        }
        if (loaded) {
            VIDEO_URL_TO_MESSAGE_PARENTS.remove(url);
        }
    }

    private static void handleFailed(String url, List<GuiMessage.Line> trimmedMessages, ImageEntry.FailureKind failureKind) {
        Set<GuiMessage> parents = new HashSet<>();
        Set<GuiMessage> registered = URL_TO_MESSAGE_PARENTS.remove(url);
        if (registered != null) {
            parents.addAll(registered);
        }
        for (int i = 0; i < trimmedMessages.size(); i++) {
            GuiMessage.Line line = trimmedMessages.get(i);
            ImageAttachable a = (ImageAttachable) (Object) line;
            if (url.equals(a.chatupgrade$getImageUrl())) {
                parents.add(line.parent());
            }
        }
        for (GuiMessage parent : parents) {
            stripPhantomBlock(trimmedMessages, parent, url, InlineResourceType.IMAGE);
            applyImageFailureOnTextLines(trimmedMessages, parent, failureKind);
        }
    }
    private static void handleFailedAudio(String url, List<GuiMessage.Line> trimmedMessages, AudioEntry.FailureKind failureKind) {
        Set<GuiMessage> parents = new HashSet<>();
        Set<GuiMessage> registered = AUDIO_URL_TO_MESSAGE_PARENTS.remove(url);
        if (registered != null) {
            parents.addAll(registered);
        }
        for (int i = 0; i < trimmedMessages.size(); i++) {
            GuiMessage.Line line = trimmedMessages.get(i);
            ImageAttachable a = (ImageAttachable) (Object) line;
            if (a.chatupgrade$getResourceType() == InlineResourceType.AUDIO && url.equals(a.chatupgrade$getImageUrl())) {
                parents.add(line.parent());
            }
        }
        for (GuiMessage parent : parents) {
            stripPhantomBlock(trimmedMessages, parent, url, InlineResourceType.AUDIO);
            applyAudioFailureOnTextLines(trimmedMessages, parent, failureKind);
        }
    }
    private static void handleFailedVideo(String url, List<GuiMessage.Line> trimmedMessages, VideoEntry.FailureKind failureKind) {
        Set<GuiMessage> parents = new HashSet<>();
        Set<GuiMessage> registered = VIDEO_URL_TO_MESSAGE_PARENTS.remove(url);
        if (registered != null) {
            parents.addAll(registered);
        }
        for (int i = 0; i < trimmedMessages.size(); i++) {
            GuiMessage.Line line = trimmedMessages.get(i);
            ImageAttachable a = (ImageAttachable) (Object) line;
            if (a.chatupgrade$getResourceType() == InlineResourceType.VIDEO && url.equals(a.chatupgrade$getImageUrl())) {
                parents.add(line.parent());
            }
        }
        for (GuiMessage parent : parents) {
            stripPhantomBlock(trimmedMessages, parent, url, InlineResourceType.VIDEO);
            applyVideoFailureOnTextLines(trimmedMessages, parent, failureKind);
        }
    }

    public static boolean isPhantomLine(GuiMessage.Line line) {
        ImageAttachable a = (ImageAttachable) (Object) line;
        return a.chatupgrade$getImageUrl() != null || a.chatupgrade$isImageContinuation();
    }

    private static boolean hasPhantomTopForUrl(List<GuiMessage.Line> trim, GuiMessage parent, String url) {
        for (GuiMessage.Line line : trim) {
            if (!line.parent().equals(parent)) {
                continue;
            }
            ImageAttachable a = (ImageAttachable) (Object) line;
            if (a.chatupgrade$getResourceType() == InlineResourceType.IMAGE && url.equals(a.chatupgrade$getImageUrl())) {
                return true;
            }
        }
        return false;
    }
    private static boolean hasPhantomTopForAudio(List<GuiMessage.Line> trim, GuiMessage parent, String url) {
        for (GuiMessage.Line line : trim) {
            if (!line.parent().equals(parent)) {
                continue;
            }
            ImageAttachable a = (ImageAttachable) (Object) line;
            if (a.chatupgrade$getResourceType() == InlineResourceType.AUDIO && url.equals(a.chatupgrade$getImageUrl())) {
                return true;
            }
        }
        return false;
    }
    private static boolean hasPhantomTopForVideo(List<GuiMessage.Line> trim, GuiMessage parent, String url) {
        for (GuiMessage.Line line : trim) {
            if (!line.parent().equals(parent)) {
                continue;
            }
            ImageAttachable a = (ImageAttachable) (Object) line;
            if (a.chatupgrade$getResourceType() == InlineResourceType.VIDEO && url.equals(a.chatupgrade$getImageUrl())) {
                return true;
            }
        }
        return false;
    }

    private static int lastTextLineIndex(List<GuiMessage.Line> trim, GuiMessage parent) {
        int last = -1;
        for (int i = 0; i < trim.size(); i++) {
            GuiMessage.Line line = trim.get(i);
            if (!line.parent().equals(parent)) {
                continue;
            }
            if (!isPhantomLine(line)) {
                last = i;
            }
        }
        return last;
    }

    private static void insertPhantomBlock(List<GuiMessage.Line> trim, GuiMessage parent, String url, InlineResourceType type) {
        int lastText = lastTextLineIndex(trim, parent);
        if (lastText < 0) {
            return;
        }
        int insertAt = lastText + 1;
        int phantomCount = switch (type) {
            case IMAGE -> ImageLoader.PHANTOM_COUNT;
            case AUDIO -> AUDIO_PHANTOM_COUNT;
            case VIDEO -> VIDEO_PHANTOM_COUNT;
        };
        UpgradePhantomCoordinator.nextPhantomTopType = type;
        for (int i = 0; i < phantomCount - 1; i++) {
            UpgradePhantomCoordinator.nextPhantomContinuation = true;
            trim.add(insertAt, new GuiMessage.Line(parent, FormattedCharSequence.EMPTY, false));
        }
        UpgradePhantomCoordinator.nextPhantomTopUrl = url;
        trim.add(insertAt + phantomCount - 1, new GuiMessage.Line(parent, FormattedCharSequence.EMPTY, false));
    }

    private static void stripPhantomBlock(List<GuiMessage.Line> trim, GuiMessage parent, String url, InlineResourceType type) {
        for (int j = trim.size() - 1; j >= 0; j--) {
            GuiMessage.Line line = trim.get(j);
            if (!line.parent().equals(parent)) {
                continue;
            }
            ImageAttachable a = (ImageAttachable) (Object) line;
            if (a.chatupgrade$getResourceType() == type
                    && (url.equals(a.chatupgrade$getImageUrl()) || a.chatupgrade$isImageContinuation())) {
                trim.remove(j);
            }
        }
    }

    private static void applyImageFailureOnTextLines(List<GuiMessage.Line> trim, GuiMessage parent, ImageEntry.FailureKind failureKind) {
        for (int j = 0; j < trim.size(); j++) {
            GuiMessage.Line line = trim.get(j);
            if (!line.parent().equals(parent)) {
                continue;
            }
            if (isPhantomLine(line)) {
                continue;
            }
            GuiMessageLineReadable readable = (GuiMessageLineReadable) (Object) line;
            FormattedCharSequence updated = switch (failureKind) {
                case RESPONSE_BODY_TOO_LARGE -> UpgradeBracketCodec.replaceVisiblePlaceholderWithOversize(readable.chatupgrade$content());
                case UNKNOWN -> UpgradeBracketCodec.replaceVisiblePlaceholderWithLoadFailed(readable.chatupgrade$content());
            };
            if (updated != null) {
                trim.set(j, new GuiMessage.Line(parent, updated, readable.chatupgrade$endOfEntry()));
            }
        }
    }
    private static void applyAudioFailureOnTextLines(List<GuiMessage.Line> trim, GuiMessage parent, AudioEntry.FailureKind failureKind) {
        for (int j = 0; j < trim.size(); j++) {
            GuiMessage.Line line = trim.get(j);
            if (!line.parent().equals(parent)) {
                continue;
            }
            if (isPhantomLine(line)) {
                continue;
            }
            GuiMessageLineReadable readable = (GuiMessageLineReadable) (Object) line;
            FormattedCharSequence updated = switch (failureKind) {
                case RESPONSE_BODY_TOO_LARGE -> UpgradeBracketCodec.replaceVisibleAudioPlaceholderWithOversize(readable.chatupgrade$content());
                case UNKNOWN, UNSUPPORTED_AUDIO_FORMAT -> UpgradeBracketCodec.replaceVisibleAudioPlaceholderWithLoadFailed(readable.chatupgrade$content());
            };
            if (updated != null) {
                trim.set(j, new GuiMessage.Line(parent, updated, readable.chatupgrade$endOfEntry()));
            }
        }
    }
    private static void applyVideoFailureOnTextLines(List<GuiMessage.Line> trim, GuiMessage parent, VideoEntry.FailureKind failureKind) {
        for (int j = 0; j < trim.size(); j++) {
            GuiMessage.Line line = trim.get(j);
            if (!line.parent().equals(parent)) {
                continue;
            }
            if (isPhantomLine(line)) {
                continue;
            }
            GuiMessageLineReadable readable = (GuiMessageLineReadable) (Object) line;
            FormattedCharSequence updated = switch (failureKind) {
                case RESPONSE_BODY_TOO_LARGE -> UpgradeBracketCodec.replaceVisibleVideoPlaceholderWithOversize(readable.chatupgrade$content());
                case UNKNOWN, UNSUPPORTED_VIDEO_FORMAT -> UpgradeBracketCodec.replaceVisibleVideoPlaceholderWithLoadFailed(readable.chatupgrade$content());
            };
            if (updated != null) {
                trim.set(j, new GuiMessage.Line(parent, updated, readable.chatupgrade$endOfEntry()));
            }
        }
    }

    private static void applyHoverRefreshOnTextLines(
            List<GuiMessage.Line> trim,
            GuiMessage parent,
            InlineResourceType type,
            String url) {
        for (int j = 0; j < trim.size(); j++) {
            GuiMessage.Line line = trim.get(j);
            if (!line.parent().equals(parent) || isPhantomLine(line)) {
                continue;
            }
            ImageAttachable attachable = (ImageAttachable) (Object) line;
            FormattedCharSequence updated = UpgradeBracketCodec.refreshVisiblePlaceholderHover(
                    ((GuiMessageLineReadable) (Object) line).chatupgrade$content(),
                    type,
                    url,
                    attachable.chatupgrade$getResourceName());
            if (updated != null) {
                trim.set(j, new GuiMessage.Line(parent, updated, ((GuiMessageLineReadable) (Object) line).chatupgrade$endOfEntry()));
            }
        }
    }
}
