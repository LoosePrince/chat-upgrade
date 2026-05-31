package com.chat.upgrade.client.ui.chat.input;

import java.util.Objects;
import java.util.Optional;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.media.model.RichMessageDraft;
import com.chat.upgrade.client.net.servermedia.ServerMediaClient;
import com.chat.upgrade.client.ui.chat.UpgradeBracketCodec;
import com.chat.upgrade.client.upload.UploadRouter;
import com.chat.upgrade.net.ServerMediaUrl;
import com.chat.upgrade.net.StructuredAttachment;

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
        STALE_DRAFT
    }

    @FunctionalInterface
    public interface ResultSink {
        void accept(SendFinishResult result, Optional<Component> message);
    }

    private AttachmentSendController() {
    }

    public static SendStartResult sendCurrentDraft(
            AttachmentComposerState state,
            String typedMessage,
            ResultSink resultSink) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(resultSink, "resultSink");

        Optional<AttachmentDraft> draftOpt = state.draft();
        if (draftOpt.isEmpty()) {
            return SendStartResult.NO_ATTACHMENT;
        }
        AttachmentDraft draft = draftOpt.get();
        if (draft.status() == AttachmentDraft.Status.UPLOADING) {
            return SendStartResult.UPLOAD_IN_PROGRESS;
        }
        if (!draft.isSendable()) {
            return SendStartResult.NOT_SENDABLE;
        }
        if (!isConnected()) {
            return SendStartResult.NOT_CONNECTED;
        }
        if (draft.sizeBytes() > ChatUpgradeConfig.get().maxUploadBytes) {
            state.replaceIfCurrent(draft, draft.failed(Component.translatable(
                    "chatupgrade.upload.too_large",
                    ChatUpgradeConfig.formatBytesHuman(ChatUpgradeConfig.get().maxUploadBytes),
                    ChatUpgradeConfig.formatBytesHuman(draft.sizeBytes())).getString()));
            return SendStartResult.TOO_LARGE;
        }

        if (draft.uploadedUrl().isPresent()) {
            finishAlreadyUploaded(state, draft, typedMessage, resultSink);
            return SendStartResult.STARTED;
        }

        Optional<AttachmentDraft> uploadingDraftOpt = state.markUploading(draft);
        if (uploadingDraftOpt.isEmpty()) {
            return SendStartResult.NOT_SENDABLE;
        }
        AttachmentDraft uploadingDraft = uploadingDraftOpt.get();
        UploadRouter.uploadBytes(
                uploadingDraft.type(),
                uploadingDraft.data(),
                uploadingDraft.fileName(),
                uploadingDraft.contentType().orElse(null))
                .handle((urlOpt, error) -> {
                    Optional<String> safeUrlOpt = error == null && urlOpt != null ? urlOpt : Optional.empty();
                    runOnClient(() -> finishUpload(state, uploadingDraft, typedMessage, safeUrlOpt, resultSink));
                    return null;
                });
        return SendStartResult.STARTED;
    }

    public static String buildLegacyFallbackMessage(AttachmentDraft draft, String uploadedUrl, String typedMessage) {
        RichAttachment attachment = RichAttachment.localDraft(uploadedUrl, draft.displayName(), draft.type());
        return buildLegacyFallbackMessage(RichMessageDraft.withAttachment(typedMessage, attachment));
    }

    public static String buildLegacyFallbackMessage(RichMessageDraft messageDraft) {
        RichAttachment attachment = messageDraft.attachment().orElseThrow();
        String payload = UpgradeBracketCodec.buildSendPayload(
                attachment.requireRenderableUrl(),
                attachment.displayName(),
                attachment.type());
        String prefix = normalizeTypedMessage(messageDraft.text());
        return prefix.isEmpty() ? payload : prefix + " " + payload;
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

    private static void finishAlreadyUploaded(
            AttachmentComposerState state,
            AttachmentDraft draft,
            String typedMessage,
            ResultSink resultSink) {
        String url = draft.uploadedUrl().orElseThrow();
        runOnClient(() -> {
            if (!state.isCurrent(draft)) {
                resultSink.accept(SendFinishResult.STALE_DRAFT, Optional.empty());
                return;
            }
            if (sendChat(buildLegacyFallbackMessage(draft, url, typedMessage))) {
                submitMetadataIfAvailable(draft, url);
                state.clearIfCurrent(draft);
                resultSink.accept(SendFinishResult.SENT, Optional.empty());
                return;
            }
            resultSink.accept(
                    SendFinishResult.NOT_CONNECTED,
                    Optional.of(Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED)));
        });
    }

    private static void finishUpload(
            AttachmentComposerState state,
            AttachmentDraft uploadingDraft,
            String typedMessage,
            Optional<String> urlOpt,
            ResultSink resultSink) {
        if (!state.isCurrent(uploadingDraft)) {
            resultSink.accept(SendFinishResult.STALE_DRAFT, Optional.empty());
            return;
        }
        if (urlOpt.isEmpty()) {
            state.replaceIfCurrent(uploadingDraft, uploadingDraft.failed(uploadFailedMessage().getString()));
            resultSink.accept(SendFinishResult.UPLOAD_FAILED, Optional.of(uploadFailedMessage()));
            return;
        }
        AttachmentDraft uploadedDraft = uploadingDraft.uploaded(urlOpt.get());
        if (!sendChat(buildLegacyFallbackMessage(uploadedDraft, urlOpt.get(), typedMessage))) {
            state.replaceIfCurrent(uploadingDraft, uploadedDraft);
            resultSink.accept(
                    SendFinishResult.NOT_CONNECTED,
                    Optional.of(Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED)));
            return;
        }
        submitMetadataIfAvailable(uploadedDraft, urlOpt.get());
        state.clearIfCurrent(uploadingDraft);
        resultSink.accept(SendFinishResult.SENT, Optional.empty());
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