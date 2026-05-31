package com.chat.upgrade.client.mixin;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.ui.chat.input.AttachmentComposerState;
import com.chat.upgrade.client.ui.chat.input.AttachmentDraft;
import com.chat.upgrade.client.ui.chat.input.AttachmentDraftResolver;
import com.chat.upgrade.client.ui.chat.input.AttachmentSendController;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

@Mixin(ChatScreen.class)
public abstract class ChatScreenRichInputMixin extends Screen {
    @Shadow
    protected EditBox input;

    @Shadow
    protected String initial;

    @Shadow
    protected boolean isDraft;

    @Shadow
    private boolean closeOnSubmit;

    @Shadow
    private CommandSuggestions commandSuggestions;

    @Shadow
    public abstract String normalizeChatMessage(String message);

    @Unique
    private final AttachmentComposerState chatupgrade$attachmentState = new AttachmentComposerState();

    @Unique
    private Button chatupgrade$imageButton;

    @Unique
    private Button chatupgrade$audioButton;

    @Unique
    private Button chatupgrade$videoButton;

    @Unique
    private Button chatupgrade$clearButton;

    protected ChatScreenRichInputMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void chatupgrade$initAttachmentControls(CallbackInfo ci) {
        int y = Math.max(2, this.height - 34);
        chatupgrade$imageButton = chatupgrade$attachButton(
                Component.translatable("chatupgrade.input.button.image"),
                Component.translatable("chatupgrade.input.button.image.tooltip"),
                4,
                y,
                InlineResourceType.IMAGE);
        chatupgrade$audioButton = chatupgrade$attachButton(
                Component.translatable("chatupgrade.input.button.audio"),
                Component.translatable("chatupgrade.input.button.audio.tooltip"),
                42,
                y,
                InlineResourceType.AUDIO);
        chatupgrade$videoButton = chatupgrade$attachButton(
                Component.translatable("chatupgrade.input.button.video"),
                Component.translatable("chatupgrade.input.button.video.tooltip"),
                80,
                y,
                InlineResourceType.VIDEO);
        chatupgrade$clearButton = Button.builder(
                Component.translatable("chatupgrade.input.button.clear"),
                button -> chatupgrade$clearDraft())
                .bounds(Math.max(4, this.width - 24), y, 20, 16)
                .tooltip(Tooltip.create(Component.translatable("chatupgrade.input.button.clear.tooltip")))
                .build();
        this.addRenderableWidget(chatupgrade$imageButton);
        this.addRenderableWidget(chatupgrade$audioButton);
        this.addRenderableWidget(chatupgrade$videoButton);
        this.addRenderableWidget(chatupgrade$clearButton);
        chatupgrade$refreshControls();
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$pasteAttachment(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!event.isPaste()) {
            return;
        }
        AttachmentDraftResolver.ResolveResult result = AttachmentDraftResolver.fromClipboard();
        if (result.draft().isPresent()) {
            chatupgrade$setDraft(result.draft().get());
            cir.setReturnValue(true);
            return;
        }
        if (result.consumesInput()) {
            result.message().ifPresent(this::chatupgrade$systemMessage);
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/input/KeyEvent;isConfirmation()Z"),
            cancellable = true)
    private void chatupgrade$sendAttachmentOnEnter(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!event.isConfirmation()) {
            return;
        }
        if (chatupgrade$shouldSendPlainTextTakeover()) {
            if (!commandSuggestions.hasAllowedInput()) {
                cir.setReturnValue(true);
                return;
            }
            String normalizedMessage = normalizeChatMessage(input.getValue());
            if (AttachmentSendController.sendTextOnlyTakeover(normalizedMessage)) {
                chatupgrade$finishSuccessfulSubmit(normalizedMessage);
            } else {
                chatupgrade$systemMessage(Component.translatable("chatupgrade.error.not_connected").withStyle(ChatFormatting.RED));
            }
            cir.setReturnValue(true);
            return;
        }
        if (!chatupgrade$shouldHandleSubmitInCurrentMode()) {
            return;
        }
        if (!commandSuggestions.hasAllowedInput()) {
            cir.setReturnValue(true);
            return;
        }
        AttachmentSendController.SendStartResult result = AttachmentSendController.sendCurrentDraft(
                chatupgrade$attachmentState,
                input.getValue(),
                (finish, message) -> {
                    message.ifPresent(this::chatupgrade$systemMessage);
                    chatupgrade$refreshControls();
                    if (finish == AttachmentSendController.SendFinishResult.SENT) {
                        chatupgrade$finishSuccessfulSubmit(input.getValue());
                    }
                });
        chatupgrade$handleSendStartResult(result);
        chatupgrade$refreshControls();
        cir.setReturnValue(true);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
    private void chatupgrade$renderAttachmentChip(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        chatupgrade$refreshControls();
        Optional<AttachmentDraft> draftOpt = chatupgrade$attachmentState.draft();
        if (draftOpt.isEmpty()) {
            return;
        }
        AttachmentDraft draft = draftOpt.get();
        int y = Math.max(2, this.height - 34);
        int x = 122;
        int right = Math.max(x + 24, this.width - 28);
        if (right <= x + 12) {
            return;
        }
        int background = draft.status() == AttachmentDraft.Status.FAILED ? 0xB0551010 : 0xB0101010;
        int outline = switch (draft.status()) {
            case READY -> 0xFF66CC66;
            case UPLOADING -> 0xFFFFCC66;
            case UPLOADED -> 0xFF66A3FF;
            case FAILED -> 0xFFFF6666;
        };
        graphics.fill(x, y, right, y + 16, background);
        graphics.outline(x, y, right - x, 16, outline);
        String label = chatupgrade$chipLabel(draft);
        int maxTextWidth = Math.max(12, right - x - 8);
        if (this.font.width(label) > maxTextWidth) {
            label = this.font.plainSubstrByWidth(label, maxTextWidth - this.font.width("…")) + "…";
        }
        int textColor = draft.status() == AttachmentDraft.Status.FAILED ? 0xFFFFBBBB : 0xFFE6E6E6;
        graphics.text(this.font, label, x + 4, y + 4, textColor, false);
    }

    @Unique
    private boolean chatupgrade$shouldSendPlainTextTakeover() {
        if (chatupgrade$attachmentState.hasDraft()) {
            return false;
        }
        if (ChatUpgradeConfig.get().chatInputMode != ChatUpgradeConfig.ChatInputMode.TAKEOVER) {
            return false;
        }
        String value = input == null ? "" : input.getValue().trim();
        return !value.isEmpty() && !value.startsWith("/");
    }

    @Unique
    private boolean chatupgrade$shouldHandleSubmitInCurrentMode() {
        if (!chatupgrade$attachmentState.hasDraft()) {
            return false;
        }
        ChatUpgradeConfig.ChatInputMode mode = ChatUpgradeConfig.get().chatInputMode;
        if (mode == ChatUpgradeConfig.ChatInputMode.COMPAT_TEXT_VANILLA) {
            return true;
        }
        return true;
    }

    @Unique
    private Button chatupgrade$attachButton(Component label, Component tooltip, int x, int y, InlineResourceType type) {
        return Button.builder(label, button -> chatupgrade$pickAttachment(type))
                .bounds(x, y, 34, 16)
                .tooltip(Tooltip.create(tooltip))
                .build();
    }

    @Unique
    private void chatupgrade$pickAttachment(InlineResourceType type) {
        chatupgrade$systemMessage(switch (type) {
            case IMAGE -> Component.translatable("chatupgrade.upload.open_image_picker").withStyle(ChatFormatting.GRAY);
            case AUDIO -> Component.translatable("chatupgrade.upload.open_audio_picker").withStyle(ChatFormatting.GRAY);
            case VIDEO -> Component.translatable("chatupgrade.upload.open_video_picker").withStyle(ChatFormatting.GRAY);
        });
        CompletableFuture.supplyAsync(() -> AttachmentDraftResolver.pickFile(type))
                .thenAccept(result -> this.minecraft.execute(() -> chatupgrade$applyResolveResult(result)));
    }

    @Unique
    private void chatupgrade$applyResolveResult(AttachmentDraftResolver.ResolveResult result) {
        result.draft().ifPresent(this::chatupgrade$setDraft);
        result.message().ifPresent(this::chatupgrade$systemMessage);
    }

    @Unique
    private void chatupgrade$setDraft(AttachmentDraft draft) {
        chatupgrade$attachmentState.setDraft(draft);
        chatupgrade$systemMessage(Component.translatable(
                "chatupgrade.input.attached",
                chatupgrade$typeName(draft.type()),
                draft.displayName()).withStyle(ChatFormatting.GRAY));
        chatupgrade$refreshControls();
    }

    @Unique
    private void chatupgrade$clearDraft() {
        chatupgrade$attachmentState.clearDraft();
        chatupgrade$refreshControls();
    }

    @Unique
    private void chatupgrade$handleSendStartResult(AttachmentSendController.SendStartResult result) {
        switch (result) {
            case STARTED -> chatupgrade$systemMessage(AttachmentSendController.uploadHint().copy().withStyle(ChatFormatting.GRAY));
            case NOT_CONNECTED -> chatupgrade$systemMessage(Component.translatable("chatupgrade.error.not_connected")
                    .withStyle(ChatFormatting.RED));
            case UPLOAD_IN_PROGRESS -> chatupgrade$systemMessage(Component.translatable("chatupgrade.input.error.uploading")
                    .withStyle(ChatFormatting.GRAY));
            case NOT_SENDABLE -> chatupgrade$systemMessage(Component.translatable("chatupgrade.input.error.not_sendable")
                    .withStyle(ChatFormatting.RED));
            case TOO_LARGE -> chatupgrade$attachmentState.draft()
                    .flatMap(AttachmentDraft::failureMessage)
                    .map(message -> Component.literal(message).withStyle(ChatFormatting.RED))
                    .ifPresent(this::chatupgrade$systemMessage);
            case NO_ATTACHMENT -> {
            }
        }
    }

    @Unique
    private void chatupgrade$finishSuccessfulSubmit(String recentMessage) {
        String normalized = recentMessage == null ? "" : normalizeChatMessage(recentMessage);
        if (!normalized.isEmpty()) {
            this.minecraft.gui.getChat().addRecentChat(normalized);
        }
        this.initial = "";
        this.input.setValue("");
        this.isDraft = false;
        if (this.closeOnSubmit) {
            if (this.minecraft.screen == (Object) this) {
                this.minecraft.setScreen(null);
            }
            return;
        }
        this.minecraft.gui.getChat().resetChatScroll();
    }

    @Unique
    private void chatupgrade$refreshControls() {
        boolean hasDraft = chatupgrade$attachmentState.hasDraft();
        if (chatupgrade$clearButton != null) {
            chatupgrade$clearButton.visible = hasDraft;
            chatupgrade$clearButton.active = hasDraft;
        }
    }

    @Unique
    private String chatupgrade$chipLabel(AttachmentDraft draft) {
        String status = switch (draft.status()) {
            case READY -> Component.translatable("chatupgrade.input.status.ready").getString();
            case UPLOADING -> Component.translatable("chatupgrade.input.status.uploading").getString();
            case UPLOADED -> Component.translatable("chatupgrade.input.status.uploaded").getString();
            case FAILED -> draft.failureMessage()
                    .orElseGet(() -> Component.translatable("chatupgrade.input.status.failed").getString());
        };
        return Component.translatable(
                "chatupgrade.input.chip",
                chatupgrade$typeName(draft.type()),
                draft.displayName(),
                status).getString();
    }

    @Unique
    private Component chatupgrade$typeName(InlineResourceType type) {
        return switch (type) {
            case IMAGE -> Component.translatable("chatupgrade.type.image");
            case AUDIO -> Component.translatable("chatupgrade.type.audio");
            case VIDEO -> Component.translatable("chatupgrade.type.video");
        };
    }

    @Unique
    private void chatupgrade$systemMessage(Component message) {
        if (this.minecraft != null && this.minecraft.gui != null) {
            this.minecraft.gui.getChat().addClientSystemMessage(message);
        }
    }
}