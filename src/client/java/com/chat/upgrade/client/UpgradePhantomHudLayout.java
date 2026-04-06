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
    private static final ConcurrentHashMap<String, Set<GuiMessage>> URL_TO_MESSAGE_PARENTS = new ConcurrentHashMap<>();

    private UpgradePhantomHudLayout() {}

    public static void clearLayoutRegistrations() {
        URL_TO_MESSAGE_PARENTS.clear();
    }

    public static void dispatchLinePaint(GuiMessage.Line line, int messageY, float opacity) {
        UpgradeHudInlinePaint.paintLinePreview(line, messageY, opacity);
    }

    public static void onUrlMessageCommitted(String url, GuiMessage parent, List<GuiMessage.Line> trimmedMessages) {
        registerUrlParent(url, parent);
        syncLayoutForUrl(url, trimmedMessages);
    }

    private static void registerUrlParent(String url, GuiMessage parent) {
        URL_TO_MESSAGE_PARENTS.computeIfAbsent(url, k -> ConcurrentHashMap.newKeySet()).add(parent);
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

    private static void ensurePreviewPhantoms(String url, List<GuiMessage.Line> trimmedMessages, boolean loaded) {
        Set<GuiMessage> parents = URL_TO_MESSAGE_PARENTS.get(url);
        if (parents == null || parents.isEmpty()) {
            return;
        }
        for (GuiMessage parent : new HashSet<>(parents)) {
            if (!hasPhantomTopForUrl(trimmedMessages, parent, url)) {
                insertPhantomBlock(trimmedMessages, parent, url);
            }
        }
        if (loaded) {
            URL_TO_MESSAGE_PARENTS.remove(url);
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
            stripPhantomBlock(trimmedMessages, parent, url);
            applyFailureOnTextLines(trimmedMessages, parent, failureKind);
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
            if (url.equals(a.chatupgrade$getImageUrl())) {
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

    private static void insertPhantomBlock(List<GuiMessage.Line> trim, GuiMessage parent, String url) {
        int lastText = lastTextLineIndex(trim, parent);
        if (lastText < 0) {
            return;
        }
        int insertAt = lastText + 1;
        for (int i = 0; i < ImageLoader.PHANTOM_COUNT - 1; i++) {
            UpgradePhantomCoordinator.nextPhantomContinuation = true;
            trim.add(insertAt, new GuiMessage.Line(parent, FormattedCharSequence.EMPTY, false));
        }
        UpgradePhantomCoordinator.nextPhantomTopUrl = url;
        trim.add(insertAt + ImageLoader.PHANTOM_COUNT - 1, new GuiMessage.Line(parent, FormattedCharSequence.EMPTY, false));
    }

    private static void stripPhantomBlock(List<GuiMessage.Line> trim, GuiMessage parent, String url) {
        for (int j = trim.size() - 1; j >= 0; j--) {
            GuiMessage.Line line = trim.get(j);
            if (!line.parent().equals(parent)) {
                continue;
            }
            ImageAttachable a = (ImageAttachable) (Object) line;
            if (url.equals(a.chatupgrade$getImageUrl()) || a.chatupgrade$isImageContinuation()) {
                trim.remove(j);
            }
        }
    }

    private static void applyFailureOnTextLines(List<GuiMessage.Line> trim, GuiMessage parent, ImageEntry.FailureKind failureKind) {
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
}
