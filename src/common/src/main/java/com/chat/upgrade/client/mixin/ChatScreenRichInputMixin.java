package com.chat.upgrade.client.mixin;

import java.util.concurrent.CompletableFuture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.chat.upgrade.client.MinecraftGuiBridge;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatPipelineGate;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.ui.chat.input.ChatComposerRenderer;
import com.chat.upgrade.client.ui.chat.input.ChatComposerState;
import com.chat.upgrade.client.ui.chat.input.AttachmentDraft;
import com.chat.upgrade.client.ui.chat.input.AttachmentDraftResolver;
import com.chat.upgrade.client.ui.chat.input.AttachmentSendController;
import com.chat.upgrade.client.ui.chat.input.EmojiPickerPopover;
import com.chat.upgrade.client.ui.chat.interaction.ChatContextMenu;
import com.chat.upgrade.client.ui.chat.interaction.ChatGesture;
import com.chat.upgrade.client.ui.chat.interaction.ChatGestureTarget;
import com.chat.upgrade.client.ui.chat.interaction.ChatMessageActionExecutor;
import com.chat.upgrade.client.ui.chat.state.ChatReplySummary;
import com.chat.upgrade.client.ui.chat.surface.ChatPanelGeometry;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceController;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceState;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.chat.viewport.RichChatInteractionRouter;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
    private final ChatComposerState chatupgrade$composerState = new ChatComposerState();

    @Unique
    private final ChatContextMenu chatupgrade$contextMenu = new ChatContextMenu();

    @Unique
    private Button chatupgrade$attachmentButton;

    @Unique
    private int chatupgrade$attachmentButtonWidth;

    @Unique
    private Button chatupgrade$emojiButton;

    @Unique
    private int chatupgrade$emojiButtonWidth;

    @Unique
    private final EmojiPickerPopover chatupgrade$emojiPopover = new EmojiPickerPopover();

    @Unique
    private Button chatupgrade$clearButton;

    protected ChatScreenRichInputMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void chatupgrade$initAttachmentControls(CallbackInfo ci) {
        ChatSurfaceController.onChatScreenOpened(this.width, this.height);
        ChatPanelGeometry panel = ChatSurfaceController.panelGeometry(this.width, this.height);
        int y = ChatUpgradeConfig.get().chatInputMode == ChatUpgradeConfig.ChatInputMode.TAKEOVER
                ? panel.composerBounds().top() + 4
                : Math.max(2, this.height - 34);
        Component attachmentLabel = Component.translatable("chatupgrade.input.button.attachment");
        chatupgrade$attachmentButtonWidth = chatupgrade$buttonWidthFor(attachmentLabel);
        chatupgrade$attachmentButton = Button.builder(
                attachmentLabel,
                button -> chatupgrade$pickAttachment())
                .bounds(4, y, chatupgrade$attachmentButtonWidth, 16)
                .tooltip(Tooltip.create(Component.translatable("chatupgrade.input.button.attachment.tooltip")))
                .build();
        Component emojiLabel = Component.translatable("chatupgrade.input.button.emoji");
        chatupgrade$emojiButtonWidth = chatupgrade$buttonWidthFor(emojiLabel);
        chatupgrade$emojiButton = Button.builder(
                emojiLabel,
                button -> chatupgrade$toggleEmojiPopover())
                .bounds(chatupgrade$emojiButtonX(), y, chatupgrade$emojiButtonWidth, 16)
                .tooltip(Tooltip.create(Component.translatable("chatupgrade.input.button.emoji.tooltip")))
                .build();
        chatupgrade$clearButton = Button.builder(
                Component.translatable("chatupgrade.input.button.clear"),
                button -> chatupgrade$clearDraft())
                .bounds(Math.max(4, this.width - 24), y, 20, 16)
                .tooltip(Tooltip.create(Component.translatable("chatupgrade.input.button.clear.tooltip")))
                .build();
        this.addRenderableWidget(chatupgrade$attachmentButton);
        this.addRenderableWidget(chatupgrade$emojiButton);
        this.addRenderableWidget(chatupgrade$clearButton);
        chatupgrade$refreshControls();
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$closeOverlayOnEscape(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!event.isEscape()) {
            return;
        }
        if (chatupgrade$contextMenu.isOpen()) {
            chatupgrade$contextMenu.close();
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
            cir.setReturnValue(true);
            return;
        }
        if (chatupgrade$emojiPopover.isVisible()) {
            chatupgrade$emojiPopover.close();
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$handleEmojiPopoverMouseClick(
            MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir) {
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            ChatContextMenu.ClickResult contextResult = chatupgrade$contextMenu.mouseClicked(
                    event.x(), event.y(), event.button());
            if (contextResult.handled()) {
                if (contextResult.selection() != null) {
                    chatupgrade$applyContextSelection(contextResult.selection());
                } else {
                    ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
                }
                cir.setReturnValue(true);
                return;
            }
            if (event.button() == 1) {
                chatupgrade$contextMenu.close();
                ChatGestureTarget target = RichChatInteractionRouter.targetForScreenGesture(
                        (int) Math.round(event.x()),
                        (int) Math.round(event.y()),
                        ChatGesture.SECONDARY);
                if (target != null && chatupgrade$openContextMenu(target, event.x(), event.y())) {
                    cir.setReturnValue(true);
                    return;
                }
                ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
            }
            if (event.button() == 0 && chatupgrade$isReplyCancelClick(event.x(), event.y())) {
                chatupgrade$composerState.clearReplyTarget();
                cir.setReturnValue(true);
                return;
            }
            if (ChatSurfaceController.pointerPressed(event.x(), event.y(), event.button())) {
                chatupgrade$closeContextMenu();
                cir.setReturnValue(true);
                return;
            }
        }
        if (!chatupgrade$emojiPopover.isVisible() || chatupgrade$isEmojiButtonClick(event.x(), event.y())) {
            return;
        }
        EmojiPickerPopover.ClickResult result = chatupgrade$emojiPopover.mouseClicked(
                event,
                this.width,
                this.height,
                chatupgrade$emojiButtonX(),
                chatupgrade$buttonRowY(),
                chatupgrade$emojiButtonWidth());
        if (result.insertionText() != null) {
            chatupgrade$insertInputText(result.insertionText());
        }
        if (result.close()) {
            chatupgrade$emojiPopover.close();
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
        }
        if (result.handled()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$handleEmojiPopoverMouseScroll(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY,
            CallbackInfoReturnable<Boolean> cir) {
        if (chatupgrade$emojiPopover.mouseScrolled(
                mouseX,
                mouseY,
                scrollY,
                this.width,
                this.height,
                chatupgrade$emojiButtonX(),
                chatupgrade$buttonRowY(),
                chatupgrade$emojiButtonWidth())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "removed()V", at = @At("HEAD"))
    private void chatupgrade$closeOverlaysOnRemoved(CallbackInfo ci) {
        chatupgrade$emojiPopover.close();
        chatupgrade$contextMenu.close();
        ChatSurfaceController.onChatScreenClosed();
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
            ChatReplySummary replyTarget = chatupgrade$composerState.replyTarget().orElse(null);
            String replyMessageId = replyTarget == null ? "" : replyTarget.messageId();
            if (AttachmentSendController.sendTextOnlyTakeover(normalizedMessage, replyMessageId)) {
                chatupgrade$composerState.clearReplyIfCurrent(replyTarget);
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
                chatupgrade$composerState,
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
    private void chatupgrade$renderComposerOverlays(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        chatupgrade$refreshControls();
        chatupgrade$layoutTakeoverBridgeControls();
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            RichChatBounds composer = ChatSurfaceController.panelGeometry(this.width, this.height).composerBounds();
            chatupgrade$composerState.replyTarget().ifPresent(target -> ChatComposerRenderer.paintReplyPreview(
                    graphics,
                    this.font,
                    ChatSurfaceController.state().theme(),
                    composer,
                    target));
        }
        int y = chatupgrade$buttonRowY();
        chatupgrade$emojiPopover.render(
                graphics,
                this.font,
                mouseX,
                mouseY,
                this.width,
                this.height,
                chatupgrade$emojiButtonX(),
                y,
                chatupgrade$emojiButtonWidth());
        chatupgrade$composerState.draft().ifPresent(draft -> ChatComposerRenderer.paintAttachmentChip(
                graphics,
                this.font,
                ChatSurfaceController.state().theme(),
                draft,
                chatupgrade$attachmentChipX(),
                Math.max(chatupgrade$attachmentChipX() + 24, chatupgrade$composerRight() - 28),
                y));
        chatupgrade$contextMenu.render(
                graphics,
                this.font,
                ChatSurfaceController.state().theme(),
                mouseX,
                mouseY);
    }

    @Unique
    private boolean chatupgrade$shouldSendPlainTextTakeover() {
        if (chatupgrade$composerState.hasDraft()) {
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
        if (!chatupgrade$composerState.hasDraft()) {
            return false;
        }
        ChatUpgradeConfig.ChatInputMode mode = ChatUpgradeConfig.get().chatInputMode;
        if (mode == ChatUpgradeConfig.ChatInputMode.COMPAT_TEXT_VANILLA) {
            return true;
        }
        return true;
    }

    @Unique
    private void chatupgrade$pickAttachment() {
        chatupgrade$systemMessage(Component.translatable("chatupgrade.upload.open_attachment_picker")
                .withStyle(ChatFormatting.GRAY));
        CompletableFuture.supplyAsync(AttachmentDraftResolver::pickFile)
                .thenAccept(result -> this.minecraft.execute(() -> chatupgrade$applyResolveResult(result)));
    }

    @Unique
    private void chatupgrade$applyResolveResult(AttachmentDraftResolver.ResolveResult result) {
        result.draft().ifPresent(this::chatupgrade$setDraft);
        result.message().ifPresent(this::chatupgrade$systemMessage);
    }

    @Unique
    private void chatupgrade$setDraft(AttachmentDraft draft) {
        chatupgrade$composerState.setDraft(draft);
        chatupgrade$systemMessage(Component.translatable(
                "chatupgrade.input.attached",
                ChatComposerRenderer.typeName(draft.type()),
                draft.displayName()).withStyle(ChatFormatting.GRAY));
        chatupgrade$refreshControls();
    }

    @Unique
    private void chatupgrade$clearDraft() {
        chatupgrade$composerState.clearDraft();
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
            case TOO_LARGE -> chatupgrade$composerState.draft()
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
            ChatComponent chat = MinecraftGuiBridge.chat(this.minecraft);
            if (chat != null) {
                chat.addRecentChat(normalized);
            }
        }
        this.initial = "";
        this.input.setValue("");
        this.isDraft = false;
        if (this.closeOnSubmit) {
            if (MinecraftGuiBridge.isCurrentScreen(this.minecraft, (Screen) (Object) this)) {
                MinecraftGuiBridge.setScreen(this.minecraft, null);
            }
            return;
        }
        ChatComponent chat = MinecraftGuiBridge.chat(this.minecraft);
        if (chat != null) {
            chat.resetChatScroll();
        }
    }

    @Unique
    private int chatupgrade$buttonWidthFor(Component label) {
        String text = label == null ? "" : label.getString();
        return Math.max(34, this.font.width(text) + 12);
    }

    @Unique
    private int chatupgrade$buttonRowY() {
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            RichChatBounds composer = ChatSurfaceController.panelGeometry(this.width, this.height).composerBounds();
            return composer.top() + (chatupgrade$composerState.hasReplyTarget() ? 22 : 10);
        }
        return Math.max(2, this.height - 34);
    }

    @Unique
    private int chatupgrade$composerLeft() {
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            return ChatSurfaceController.panelGeometry(this.width, this.height).composerBounds().left() + 6;
        }
        return 4;
    }

    @Unique
    private int chatupgrade$composerRight() {
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            return ChatSurfaceController.panelGeometry(this.width, this.height).composerBounds().right() - 6;
        }
        return this.width - 4;
    }

    @Unique
    private void chatupgrade$layoutTakeoverBridgeControls() {
        if (!ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            return;
        }
        ChatPanelGeometry panel = ChatSurfaceController.panelGeometry(this.width, this.height);
        int toolbarY = chatupgrade$buttonRowY();
        int left = panel.composerBounds().left() + 6;
        int right = panel.composerBounds().right() - 6;
        chatupgrade$attachmentButton.setX(left);
        chatupgrade$attachmentButton.setY(toolbarY);
        chatupgrade$emojiButton.setX(left + chatupgrade$attachmentButtonWidth + 6);
        chatupgrade$emojiButton.setY(toolbarY);
        chatupgrade$clearButton.setX(Math.max(left, right - 20));
        chatupgrade$clearButton.setY(toolbarY);
        if (input != null) {
            input.setX(left);
            input.setY(panel.composerBounds().bottom() - 20);
            input.setWidth(Math.max(40, panel.composerBounds().width() - 12));
        }
    }

    @Unique
    private int chatupgrade$emojiButtonX() {
        int width = chatupgrade$attachmentButtonWidth > 0
                ? chatupgrade$attachmentButtonWidth
                : chatupgrade$buttonWidthFor(Component.translatable("chatupgrade.input.button.attachment"));
        return chatupgrade$composerLeft() + width + 6;
    }

    @Unique
    private int chatupgrade$emojiButtonWidth() {
        return chatupgrade$emojiButtonWidth > 0
                ? chatupgrade$emojiButtonWidth
                : chatupgrade$buttonWidthFor(Component.translatable("chatupgrade.input.button.emoji"));
    }

    @Unique
    private int chatupgrade$attachmentChipX() {
        return chatupgrade$emojiButtonX() + chatupgrade$emojiButtonWidth() + 6;
    }

    @Unique
    private boolean chatupgrade$openContextMenu(ChatGestureTarget target, double mouseX, double mouseY) {
        chatupgrade$emojiPopover.close();
        boolean opened = chatupgrade$contextMenu.open(
                target.message(),
                (int) Math.round(mouseX),
                (int) Math.round(mouseY),
                this.width,
                this.height,
                this.font);
        if (opened) {
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.CONTEXT_MENU);
        }
        return opened;
    }

    @Unique
    private void chatupgrade$applyContextSelection(ChatContextMenu.Selection selection) {
        ChatMessageActionExecutor.execute(selection, chatupgrade$composerState, this.minecraft)
                .ifPresent(this::chatupgrade$systemMessage);
        ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
    }

    @Unique
    private void chatupgrade$closeContextMenu() {
        chatupgrade$contextMenu.close();
        if (ChatSurfaceController.state().overlay() == ChatSurfaceState.Overlay.CONTEXT_MENU) {
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
        }
    }

    @Unique
    private boolean chatupgrade$isReplyCancelClick(double mouseX, double mouseY) {
        if (!ChatUpgradeChatPipelineGate.isTakeoverMode() || !chatupgrade$composerState.hasReplyTarget()) {
            return false;
        }
        RichChatBounds composer = ChatSurfaceController.panelGeometry(this.width, this.height).composerBounds();
        return ChatComposerRenderer.isReplyCancelClick(this.font, composer, mouseX, mouseY);
    }

    @Unique
    private void chatupgrade$toggleEmojiPopover() {
        chatupgrade$closeContextMenu();
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.EMOJI_PICKER);
        }
        chatupgrade$emojiPopover.toggle();
        if (!chatupgrade$emojiPopover.isVisible()) {
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
        }
    }

    @Unique
    private boolean chatupgrade$isEmojiButtonClick(double mouseX, double mouseY) {
        int x = chatupgrade$emojiButtonX();
        int y = chatupgrade$buttonRowY();
        return mouseX >= x && mouseX < x + chatupgrade$emojiButtonWidth()
                && mouseY >= y && mouseY < y + 16;
    }

    @Unique
    private void chatupgrade$insertInputText(String text) {
        if (input == null || text == null || text.isEmpty()) {
            return;
        }
        input.insertText(text);
    }

    @Unique
    private void chatupgrade$refreshControls() {
        boolean hasDraft = chatupgrade$composerState.hasDraft();
        if (chatupgrade$clearButton != null) {
            chatupgrade$clearButton.visible = hasDraft;
            chatupgrade$clearButton.active = hasDraft;
        }
    }

    @Unique
    private void chatupgrade$systemMessage(Component message) {
        ChatComponent chat = MinecraftGuiBridge.chat(this.minecraft);
        if (chat != null) {
            chat.addClientSystemMessage(message);
        }
    }
}