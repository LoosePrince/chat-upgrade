package com.chat.upgrade.client.ui.chat.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.media.model.RichMessageDraft;
import com.chat.upgrade.client.net.servermedia.ServerMediaClient;
import com.chat.upgrade.client.ui.chat.UpgradeBracketCodec;
import com.chat.upgrade.client.ui.chat.state.ChatReplySummary;
import com.chat.upgrade.client.upload.UploadRouter;
import com.chat.upgrade.net.ServerMediaPayloads;
import com.chat.upgrade.net.ServerMediaUrl;
import com.chat.upgrade.net.StructuredAttachment;
import com.chat.upgrade.net.StructuredChatMessage;
import com.chat.upgrade.net.StructuredChatProtocolLimits;
import com.chat.upgrade.net.StructuredChatSubmission;

import com.chat.upgrade.platform.net.Net;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

public final class AttachmentSendController {
    public enum SendStartResult {
        STARTED,
        NO_ATTACHMENT,
        NOT_CONNECTED,
        UPLOAD_IN_PROGRESS,
        NOT_SENDABLE,
        TOO_LARGE
    }

    public enum SendFinishResult {
        SENT,
        UPLOAD_FAILED,
        NOT_CONNECTED,
        UNSUPPORTED,
        STALE_DRAFT
    }

    @FunctionalInterface
    public interface ResultSink {
        void accept(SendFinishResult result, Optional<Component> message);
    }

    private AttachmentSendController() {
    }

    public static SendStartResult sendCurrentDraft(
            ChatComposerState state,
            String typedMessage,
            ResultSink resultSink) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(resultSink, "resultSink");

        List<AttachmentDraft> drafts = state.drafts();
        if (drafts.isEmpty()) {
            return SendStartResult.NO_ATTACHMENT;
        }
        ChatReplySummary replyTarget = state.replyTarget().orElse(null);
        if (drafts.stream().anyMatch(draft -> draft.status() == AttachmentDraft.Status.UPLOADING)) {
            return SendStartResult.UPLOAD_IN_PROGRESS;
        }
        if (drafts.stream().anyMatch(draft -> !draft.isSendable())) {
            return SendStartResult.NOT_SENDABLE;
        }
        if (!isConnected()) {
            return SendStartResult.NOT_CONNECTED;
        }
        long totalBytes = drafts.stream().mapToLong(AttachmentDraft::sizeBytes).sum();
        if (drafts.stream().anyMatch(draft -> draft.sizeBytes() > ChatUpgradeConfig.get().maxUploadBytes)) {
            for (AttachmentDraft draft : drafts) {
                if (draft.sizeBytes() > ChatUpgradeConfig.get().maxUploadBytes) {
                    state.replaceIfCurrent(draft, draft.failed(Component.translatable(
                            "chatupgrade.upload.too_large",
                            ChatUpgradeConfig.formatBytesHuman(ChatUpgradeConfig.get().maxUploadBytes),
                            ChatUpgradeConfig.formatBytesHuman(draft.sizeBytes())).getString()));
                }
            }
            return SendStartResult.TOO_LARGE;
        }
        if (totalBytes <= 0) {
            return SendStartResult.NOT_SENDABLE;
        }

        List<AttachmentDraft> snapshot = List.copyOf(drafts);
        Optional<List<AttachmentDraft>> uploadingBatch = state.beginUploadBatch(snapshot);
        if (uploadingBatch.isEmpty()) {
            return SendStartResult.NOT_SENDABLE;
        }
        List<AttachmentDraft> uploadingDrafts = uploadingBatch.get();
        List<CompletableFuture<Optional<String>>> uploads = uploadingDrafts.stream()
                .map(draft -> draft.uploadedUrl().isPresent()
                        ? CompletableFuture.completedFuture(Optional.of(draft.uploadedUrl().orElseThrow()))
                        : UploadRouter.uploadBytes(
                                draft.type(),
                                draft.data(),
                                draft.fileName(),
                                draft.contentType().orElse(null))
                                .handle((urlOpt, error) -> error == null && urlOpt != null
                                        ? urlOpt
                                        : Optional.<String>empty()))
                .toList();
        CompletableFuture.allOf(uploads.toArray(new CompletableFuture<?>[0]))
                .thenRun(() -> runOnClient(() -> finishUploads(
                        state,
                        uploadingDrafts,
                        typedMessage,
                        replyTarget,
                        uploads,
                        resultSink)));
        return SendStartResult.STARTED;
    }

    public static String buildBracketFallbackMessage(AttachmentDraft draft, String uploadedUrl, String typedMessage) {
        return buildBracketFallbackMessage(List.of(draft), List.of(uploadedUrl), typedMessage);
    }

    public static String buildBracketFallbackMessage(
            List<AttachmentDraft> drafts,
            List<String> uploadedUrls,
            String typedMessage) {
        if (drafts == null || uploadedUrls == null || drafts.size() != uploadedUrls.size()) {
            throw new IllegalArgumentException("drafts and uploadedUrls must have the same size");
        }
        StringBuilder payload = new StringBuilder(normalizeTypedMessage(typedMessage));
        for (int i = 0; i < drafts.size(); i++) {
            RichAttachment attachment = RichAttachment.localDraft(
                    uploadedUrls.get(i),
                    drafts.get(i).displayName(),
                    drafts.get(i).type());
            String bracket = UpgradeBracketCodec.buildSendPayload(
                    attachment.requireRenderableUrl(),
                    attachment.displayName(),
                    attachment.type());
            if (payload.length() > 0) {
                payload.append(' ');
            }
            payload.append(bracket);
        }
        return payload.toString();
    }

    public static String buildBracketFallbackMessage(RichMessageDraft messageDraft) {
        StringBuilder payload = new StringBuilder(normalizeTypedMessage(messageDraft.text()));
        for (RichAttachment attachment : messageDraft.attachments()) {
            String bracket = UpgradeBracketCodec.buildSendPayload(
                    attachment.requireRenderableUrl(),
                    attachment.displayName(),
                    attachment.type());
            if (payload.length() > 0) {
                payload.append(' ');
            }
            payload.append(bracket);
        }
        return payload.toString();
    }

    public static Component uploadHint() {
        ChatUpgradeConfig.UploadMode mode = ChatUpgradeConfig.get().uploadMode;
        boolean serverCap = ServerMediaClient.capability().enabled();
        return switch (mode) {
            case THIRD_PARTY -> Component.translatable("chatupgrade.upload.hint.third_party");
            case SERVER -> Component.translatable("chatupgrade.upload.hint.server");
            case AUTO -> serverCap
                    ? Component.translatable("chatupgrade.upload.hint.server")
                    : Component.translatable("chatupgrade.upload.hint.third_party");
        };
    }

    public static Component uploadFailedMessage() {
        ChatUpgradeConfig.UploadMode mode = ChatUpgradeConfig.get().uploadMode;
        boolean serverAttempted = switch (mode) {
            case SERVER -> true;
            case AUTO -> ServerMediaClient.capability().enabled();
            case THIRD_PARTY -> false;
        };
        Component action = Component.translatable("chatupgrade.upload.action.upload");
        if (serverAttempted) {
            return Component.translatable("chatupgrade.upload.failed.server", action).withStyle(ChatFormatting.RED);
        }
        return Component.translatable("chatupgrade.upload.failed.third_party", action).withStyle(ChatFormatting.RED);
    }

    private static void finishUploads(
            ChatComposerState state,
            List<AttachmentDraft> uploadingDrafts,
            String typedMessage,
            ChatReplySummary replyTarget,
            List<CompletableFuture<Optional<String>>> uploads,
            ResultSink resultSink) {
        if (!state.containsAll(uploadingDrafts)) {
            state.endUploadBatch();
            resultSink.accept(SendFinishResult.STALE_DRAFT, Optional.empty());
            return;
        }
        List<String> urls = new ArrayList<>();
        boolean failedUpload = false;
        for (int i = 0; i < uploads.size(); i++) {
            Optional<String> url = uploads.get(i).join().filter(value -> !value.isBlank());
            if (url.isEmpty()) {
                failedUpload = true;
                urls.add("");
                continue;
            }
            urls.add(url.get());
        }
        if (failedUpload) {
            for (int i = 0; i < uploadingDrafts.size(); i++) {
                AttachmentDraft current = uploadingDrafts.get(i);
                if (urls.get(i).isBlank()) {
                    state.replaceIfCurrent(current, current.failed(uploadFailedMessage().getString()));
                } else {
                    state.replaceIfCurrent(current, current.uploaded(urls.get(i)));
                }
            }
            state.endUploadBatch();
            resultSink.accept(SendFinishResult.UPLOAD_FAILED, Optional.of(uploadFailedMessage()));
            return;
        }
        SendFinishResult sendResult = sendRichMessage(
                uploadingDrafts,
                urls,
                typedMessage,
                replyMessageId(replyTarget));
        if (sendResult != SendFinishResult.SENT) {
            for (int i = 0; i < uploadingDrafts.size(); i++) {
                AttachmentDraft current = uploadingDrafts.get(i);
                state.replaceIfCurrent(current, current.uploaded(urls.get(i)));
            }
            Component failure = sendResult == SendFinishResult.UNSUPPORTED
                    ? Component.translatable("chatupgrade.input.error.structured_required").withStyle(ChatFormatting.RED)
                    : Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED);
            state.endUploadBatch();
            resultSink.accept(sendResult, Optional.of(failure));
            return;
        }
        for (AttachmentDraft draft : uploadingDrafts) {
            state.clearIfCurrent(draft);
        }
        state.clearReplyIfCurrent(replyTarget);
        for (int i = 0; i < uploadingDrafts.size(); i++) {
            submitMetadataIfAvailable(uploadingDrafts.get(i), urls.get(i));
        }
        state.endUploadBatch();
        resultSink.accept(SendFinishResult.SENT, Optional.empty());
    }

    public static boolean sendTextOnlyTakeover(String typedMessage) {
        return sendTextOnlyTakeover(typedMessage, "");
    }

    public static boolean sendTextOnlyTakeover(String typedMessage, String replyToMessageId) {
        if (!isConnected()) {
            return false;
        }
        String text = normalizeTypedMessage(typedMessage);
        if (text.isEmpty()) {
            return true;
        }
        StructuredChatMessage legacy = StructuredChatMessage.textOnly(nextClientNonce(), text);
        String replyId = replyToMessageId == null ? "" : replyToMessageId.trim();
        if (sendStructuredSubmission(StructuredChatSubmission.fromLegacy(legacy).replyingTo(replyId))) {
            return true;
        }
        if (!replyId.isBlank()) {
            return false;
        }
        if (shouldUseLegacyStructuredSend() && sendStructuredChatMessage(legacy)) {
            return true;
        }
        return sendChat(text);
    }

    public static boolean retractMessage(String messageId) {
        if (!isConnected() || messageId == null || messageId.isBlank()) {
            return false;
        }
        try {
            if (!Net.canSendToServer(ServerMediaPayloads.C2SRetractChatMessage.TYPE)) {
                return false;
            }
            Net.sendToServer(new ServerMediaPayloads.C2SRetractChatMessage(messageId));
            return true;
        } catch (Exception ex) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to request message retraction: {}", ex.getMessage());
            return false;
        }
    }

    private static SendFinishResult sendRichMessage(
            List<AttachmentDraft> drafts,
            List<String> uploadedUrls,
            String typedMessage,
            String replyToMessageId) {
        String replyId = replyToMessageId == null ? "" : replyToMessageId.trim();
        try {
            List<StructuredAttachment> attachments = buildStructuredAttachments(drafts, uploadedUrls);
            String fallback = buildBracketFallbackMessage(drafts, uploadedUrls, typedMessage);
            StructuredChatMessage message = StructuredChatMessage.withAttachments(
                    nextClientNonce(),
                    typedMessage,
                    attachments,
                    fallback);
            StructuredChatSubmission submission = StructuredChatSubmission.fromLegacy(message).replyingTo(replyId);
            if (!StructuredChatProtocolLimits.accepts(submission)) {
                return SendFinishResult.UNSUPPORTED;
            }
            if (sendStructuredSubmission(submission)) {
                return SendFinishResult.SENT;
            }
            if (replyId.isBlank()
                    && shouldUseLegacyStructuredSend()
                    && sendStructuredChatMessage(message)) {
                return SendFinishResult.SENT;
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: cannot build structured chat message: {}", ex.getMessage());
        }
        if (drafts.size() > 1 || !replyId.isBlank()) {
            return isConnected() ? SendFinishResult.UNSUPPORTED : SendFinishResult.NOT_CONNECTED;
        }
        return sendChat(buildBracketFallbackMessage(drafts, uploadedUrls, typedMessage))
                ? SendFinishResult.SENT
                : SendFinishResult.NOT_CONNECTED;
    }

    private static boolean shouldUseLegacyStructuredSend() {
        if (ChatUpgradeConfig.get().chatInputMode != ChatUpgradeConfig.ChatInputMode.TAKEOVER) {
            return false;
        }
        try {
            return Net.canSendToServer(ServerMediaPayloads.C2SStructuredChatMessage.TYPE);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean sendStructuredSubmission(StructuredChatSubmission submission) {
        if (ChatUpgradeConfig.get().chatInputMode != ChatUpgradeConfig.ChatInputMode.TAKEOVER) {
            return false;
        }
        try {
            if (!Net.canSendToServer(ServerMediaPayloads.C2SStructuredChatV2.TYPE)) {
                return false;
            }
            Net.sendToServer(ServerMediaPayloads.C2SStructuredChatV2.fromSubmission(submission));
            return true;
        } catch (Exception ex) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to send structured chat v2 submission: {}", ex.getMessage());
            return false;
        }
    }

    private static boolean sendStructuredChatMessage(StructuredChatMessage message) {
        try {
            Net.sendToServer(ServerMediaPayloads.C2SStructuredChatMessage.fromMessage(message));
            return true;
        } catch (Exception ex) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to send structured chat message: {}", ex.getMessage());
            return false;
        }
    }

    private static String replyMessageId(ChatReplySummary replyTarget) {
        return replyTarget == null ? "" : replyTarget.messageId();
    }

    private static String nextClientNonce() {
        return UUID.randomUUID().toString();
    }

    private static void submitMetadataIfAvailable(AttachmentDraft draft, String uploadedUrl) {
        if (!ServerMediaClient.capability().attachmentMetadataEnabled()) {
            return;
        }
        try {
            StructuredAttachment attachment = buildStructuredAttachment(draft, uploadedUrl);
            ServerMediaClient.submitAttachment(attachment)
                    .exceptionally(error -> {
                        ChatUpgrade.LOGGER.warn("chat-upgrade: failed to submit attachment metadata: {}",
                                error == null ? "unknown" : error.getMessage());
                        return Optional.empty();
                    });
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: cannot submit attachment metadata: {}", ex.getMessage());
        }
    }

    private static List<StructuredAttachment> buildStructuredAttachments(
            List<AttachmentDraft> drafts,
            List<String> uploadedUrls) {
        if (drafts == null || uploadedUrls == null || drafts.size() != uploadedUrls.size()) {
            throw new IllegalArgumentException("drafts and uploadedUrls must have the same size");
        }
        List<StructuredAttachment> attachments = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            attachments.add(buildStructuredAttachment(drafts.get(i), uploadedUrls.get(i)));
        }
        return List.copyOf(attachments);
    }

    private static StructuredAttachment buildStructuredAttachment(AttachmentDraft draft, String uploadedUrl) {
        Optional<ServerMediaUrl.Parsed> serverMediaOpt = ServerMediaUrl.parse(uploadedUrl);
        if (serverMediaOpt.isPresent()) {
            return StructuredAttachment.serverMedia(
                    null,
                    serverMediaOpt.get().mediaId(),
                    draft.type().toWire(),
                    draft.displayName());
        }
        return StructuredAttachment.externalUrl(
                null,
                draft.type().toWire(),
                draft.displayName(),
                uploadedUrl);
    }

    private static boolean sendChat(String message) {
        ClientPacketListener connection = connection();
        if (connection == null) {
            return false;
        }
        connection.sendChat(message);
        return true;
    }

    private static boolean isConnected() {
        return connection() != null;
    }

    private static ClientPacketListener connection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return null;
        }
        return mc.player.connection;
    }

    private static void runOnClient(Runnable action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(action);
    }

    private static String normalizeTypedMessage(String typedMessage) {
        if (typedMessage == null) {
            return "";
        }
        return typedMessage.trim();
    }
}