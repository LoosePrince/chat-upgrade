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
import com.chat.upgrade.client.mixininterface.ChatComposerAttachmentDragAccess;
import com.chat.upgrade.client.mixininterface.ChatSettingsOverlayAccess;
import com.chat.upgrade.client.ui.chat.AudioFloatingWindow;
import com.chat.upgrade.client.ui.chat.ChatUpgradeChatPipelineGate;
import com.chat.upgrade.client.ChatUpgradeConfig;
import com.chat.upgrade.client.ui.chat.input.ChatComposerRenderer;
import com.chat.upgrade.client.ui.chat.input.ChatComposerState;
import com.chat.upgrade.client.ui.chat.input.ChatComposerToolbar;
import com.chat.upgrade.client.ui.chat.input.ChatCommandBridge;
import com.chat.upgrade.client.ui.chat.input.AttachmentDraft;
import com.chat.upgrade.client.ui.chat.input.AttachmentDraftResolver;
import com.chat.upgrade.client.ui.chat.input.AttachmentSendController;
import com.chat.upgrade.client.ui.chat.input.EmojiPickerPopover;
import com.chat.upgrade.client.ui.chat.interaction.ChatContextMenu;
import com.chat.upgrade.client.ui.chat.interaction.ChatGesture;
import com.chat.upgrade.client.ui.chat.interaction.ChatGestureArena;
import com.chat.upgrade.client.ui.chat.interaction.ChatGestureTarget;
import com.chat.upgrade.client.ui.chat.interaction.ChatMessageActionExecutor;
import com.chat.upgrade.client.ui.chat.interaction.ChatTextSelectionState;
import com.chat.upgrade.client.ui.chat.state.ChatReplySummary;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceController;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceRenderer;
import com.chat.upgrade.client.ui.chat.surface.ChatSurfaceState;
import com.chat.upgrade.client.ui.settings.ChatSettingsOverlay;
import com.chat.upgrade.client.ui.chat.viewport.RichChatBounds;
import com.chat.upgrade.client.ui.chat.viewport.RichChatInteractionRouter;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@Mixin(ChatScreen.class)
public abstract class ChatScreenRichInputMixin extends Screen
        implements ChatComposerAttachmentDragAccess, ChatSettingsOverlayAccess {
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

    @Shadow
    protected abstract void handleChatInput(String message, boolean addToHistory);

    @Unique
    private final ChatComposerState chatupgrade$composerState = new ChatComposerState();

    @Unique
    private final ChatContextMenu chatupgrade$contextMenu = new ChatContextMenu();

    @Unique
    private final ChatSettingsOverlay chatupgrade$settingsOverlay = new ChatSettingsOverlay();

    @Unique
    private ChatComposerToolbar.State chatupgrade$toolbarState = ChatComposerToolbar.State.idle();

    @Unique
    private final EmojiPickerPopover chatupgrade$emojiPopover = new EmojiPickerPopover();

    @Unique
    private @org.jetbrains.annotations.Nullable AttachmentDraft chatupgrade$draggedAttachment;

    @Unique
    private EditBox chatupgrade$emojiSearchBox;

    @Unique
    private boolean chatupgrade$inputVisibleBeforeRender;

    @Unique
    private boolean chatupgrade$emojiSearchVisibleBeforeRender;

    protected ChatScreenRichInputMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void chatupgrade$initAttachmentControls(CallbackInfo ci) {
        ChatSurfaceController.onChatScreenOpened(this.width, this.height);
        chatupgrade$emojiSearchBox = new EditBox(
                this.font,
                0,
                0,
                100,
                16,
                Component.translatable("chatupgrade.emoji.picker.search_hint"));
        chatupgrade$emojiSearchBox.setBordered(false);
        chatupgrade$emojiSearchBox.setHint(Component.translatable("chatupgrade.emoji.picker.search_hint"));
        chatupgrade$emojiSearchBox.setVisible(false);
        chatupgrade$emojiSearchBox.setResponder(chatupgrade$emojiPopover::setSearchQuery);
        this.addRenderableWidget(chatupgrade$emojiSearchBox);
        chatupgrade$refreshControls();
        chatupgrade$layoutComposerControls();
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$closeOverlayOnEscape(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (chatupgrade$settingsOverlay.isOpen()) {
            boolean wasOpen = true;
            chatupgrade$settingsOverlay.keyPressed(event);
            if (wasOpen && !chatupgrade$settingsOverlay.isOpen()) {
                chatupgrade$restoreComposerFocus();
                chatupgrade$refreshControls();
            }
            cir.setReturnValue(true);
            return;
        }
        if (!event.isEscape()) {
            if (ChatUpgradeChatPipelineGate.isTakeoverMode()
                    && event.isCopy()
                    && ChatTextSelectionState.hasSelection()
                    && (input == null || !input.isFocused())) {
                ChatTextSelectionState.copySelection(this.minecraft);
                cir.setReturnValue(true);
            } else if (ChatUpgradeChatPipelineGate.isTakeoverMode()
                    && chatupgrade$contextMenu.isOpen()) {
                cir.setReturnValue(true);
            }
            return;
        }
        RichChatInteractionRouter.cancelAllPointerCapture();
        if (chatupgrade$contextMenu.isOpen()) {
            chatupgrade$contextMenu.close();
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
            chatupgrade$restoreComposerFocus();
            cir.setReturnValue(true);
            return;
        }
        if (chatupgrade$emojiPopover.isVisible()) {
            chatupgrade$emojiPopover.close();
            chatupgrade$hideEmojiSearchBox();
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$handleEmojiPopoverMouseClick(
            MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir) {
        if (chatupgrade$settingsOverlay.isOpen()) {
            boolean wasOpen = true;
            chatupgrade$settingsOverlay.mouseClicked(event, this.width, this.height);
            if (wasOpen && !chatupgrade$settingsOverlay.isOpen()) {
                chatupgrade$restoreComposerFocus();
                chatupgrade$refreshControls();
            }
            cir.setReturnValue(true);
            return;
        }
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()
                && event.button() == 0
                && ChatSurfaceRenderer.settingsButtonBounds(ChatSurfaceController.state().frame())
                        .contains((int) Math.round(event.x()), (int) Math.round(event.y()))) {
            chatupgrade$openSettingsOverlay();
            cir.setReturnValue(true);
            return;
        }
        if (event.button() == 0) {
            ChatComposerToolbar.Action toolbarAction = ChatComposerToolbar.actionAt(
                    chatupgrade$toolbarLayout(),
                    chatupgrade$toolbarState,
                    event.x(),
                    event.y());
            if (toolbarAction != null) {
                chatupgrade$activateToolbarAction(toolbarAction);
                cir.setReturnValue(true);
                return;
            }
        }
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            if (event.button() == 0
                    && !chatupgrade$contextMenu.isOpen()
                    && chatupgrade$composerBounds()
                            .contains((int) Math.round(event.x()), (int) Math.round(event.y()))) {
                ChatTextSelectionState.clear();
            }
            if (chatupgrade$emojiPopover.isVisible() && !chatupgrade$isEmojiButtonClick(event.x(), event.y())) {
                if (event.button() != 0) {
                    chatupgrade$emojiPopover.close();
                    chatupgrade$hideEmojiSearchBox();
                    ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
                    cir.setReturnValue(true);
                    return;
                }
                if (chatupgrade$emojiPopover.isSearchFocused(
                        event.x(),
                        event.y(),
                        this.width,
                        this.height,
                        chatupgrade$emojiButtonX(),
                        chatupgrade$buttonRowY(),
                        chatupgrade$emojiButtonWidth())) {
                    if (chatupgrade$emojiSearchBox != null) {
                        chatupgrade$focusEmojiSearchBox();
                    }
                    cir.setReturnValue(true);
                    return;
                }
                EmojiPickerPopover.ClickResult emojiResult = chatupgrade$emojiPopover.mouseClicked(
                        event,
                        this.width,
                        this.height,
                        chatupgrade$emojiButtonX(),
                        chatupgrade$buttonRowY(),
                        chatupgrade$emojiButtonWidth());
                if (emojiResult.insertionText() != null) {
                    chatupgrade$insertInputText(emojiResult.insertionText());
                }
                if (emojiResult.close()) {
                    chatupgrade$emojiPopover.close();
                    chatupgrade$hideEmojiSearchBox();
                    ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
                }
                if (emojiResult.handled() || emojiResult.close()) {
                    cir.setReturnValue(true);
                    return;
                }
            }
            if (chatupgrade$contextMenu.isOpen() && event.button() != 0) {
                chatupgrade$closeContextMenu();
                cir.setReturnValue(true);
                return;
            }
            ChatContextMenu.ClickResult contextResult = chatupgrade$contextMenu.mouseClicked(
                    event.x(), event.y(), event.button());
            if (contextResult.handled()) {
                if (contextResult.selection() != null) {
                    chatupgrade$applyContextSelection(contextResult.selection());
                } else {
                    ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
                    chatupgrade$restoreComposerFocus();
                }
                cir.setReturnValue(true);
                return;
            }
            if (AudioFloatingWindow.contains(
                    event.x(), event.y(), this.width, this.height)) {
                return;
            }
            if (ChatGestureArena.hasCapture()) {
                cir.setReturnValue(true);
                return;
            }
            if (event.button() == 0 && chatupgrade$removeAttachmentAt(event.x(), event.y())) {
                cir.setReturnValue(true);
                return;
            }
            if (event.button() == 0 && ChatUpgradeChatPipelineGate.isTakeoverMode()) {
                java.util.Optional<AttachmentDraft> chip = ChatComposerRenderer.attachmentChipAt(
                        this.font,
                        chatupgrade$composerState.drafts(),
                        chatupgrade$attachmentChipX(),
                        Math.max(chatupgrade$attachmentChipX() + 24, chatupgrade$attachmentChipRight()),
                        chatupgrade$buttonRowY(),
                        event.x(),
                        event.y());
                if (chip.isPresent()
                        && ChatGestureArena.tryCapture(
                                ChatGestureArena.Owner.ATTACHMENT_TRAY,
                                this::chatupgrade$cancelAttachmentDrag)) {
                    chatupgrade$draggedAttachment = chip.get();
                    cir.setReturnValue(true);
                    return;
                }
            }
            if (event.button() == 0) {
                java.util.Optional<ChatContextMenu.Selection> hoverSelection =
                        RichChatInteractionRouter.hoverActionAtScreen(
                                (int) Math.round(event.x()),
                                (int) Math.round(event.y()));
                if (hoverSelection.isPresent()) {
                    chatupgrade$applyContextSelection(hoverSelection.get());
                    cir.setReturnValue(true);
                    return;
                }
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
                RichChatInteractionRouter.cancelPointerCapture();
                chatupgrade$closeContextMenu();
                cir.setReturnValue(true);
                return;
            }
            if (event.button() == 0
                    && RichChatInteractionRouter.beginPointerAtScreen(
                            (int) Math.round(event.x()),
                            (int) Math.round(event.y()))) {
                this.setFocused(null);
                if (input != null) {
                    input.setFocused(false);
                }
                ChatSurfaceController.setFocusOwner(ChatSurfaceState.FocusOwner.TIMELINE);
                cir.setReturnValue(true);
                return;
            }
        }
        if (!ChatUpgradeChatPipelineGate.isTakeoverMode()
                && event.button() == 0
                && chatupgrade$removeAttachmentAt(event.x(), event.y())) {
            cir.setReturnValue(true);
            return;
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
            chatupgrade$hideEmojiSearchBox();
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
        }
        if (result.handled()) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean chatupgrade$isSettingsOverlayOpen() {
        return chatupgrade$settingsOverlay.isOpen();
    }

    @Override
    public boolean chatupgrade$updateSettingsDrag(double mouseX, double mouseY, int button) {
        return chatupgrade$settingsOverlay.mouseDragged(mouseX, mouseY, button);
    }

    @Override
    public boolean chatupgrade$releaseSettingsDrag(int button) {
        return chatupgrade$settingsOverlay.mouseReleased(button);
    }

    @Override
    public boolean chatupgrade$updateAttachmentDrag(double mouseX, double mouseY, int button) {
        if (button != 0 || chatupgrade$draggedAttachment == null
                || !ChatGestureArena.isCapturedBy(ChatGestureArena.Owner.ATTACHMENT_TRAY)
                || !ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            return false;
        }
        ChatComposerRenderer.attachmentChipAt(
                this.font,
                chatupgrade$composerState.drafts(),
                chatupgrade$attachmentChipX(),
                Math.max(chatupgrade$attachmentChipX() + 24, chatupgrade$attachmentChipRight()),
                chatupgrade$buttonRowY(),
                mouseX,
                mouseY)
                .ifPresent(target -> chatupgrade$composerState.moveDraftBefore(chatupgrade$draggedAttachment, target));
        if (mouseX > chatupgrade$attachmentChipRight()) {
            chatupgrade$composerState.moveDraftToEnd(chatupgrade$draggedAttachment);
        }
        return true;
    }

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$handleEmojiPopoverMouseScroll(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY,
            CallbackInfoReturnable<Boolean> cir) {
        if (chatupgrade$settingsOverlay.isOpen()) {
            chatupgrade$settingsOverlay.mouseScrolled(
                    mouseX,
                    mouseY,
                    scrollY,
                    this.width,
                    this.height);
            cir.setReturnValue(true);
            return;
        }
        if (ChatUpgradeChatPipelineGate.isTakeoverMode() && chatupgrade$contextMenu.isOpen()) {
            cir.setReturnValue(true);
            return;
        }
        if (!chatupgrade$emojiPopover.isVisible()) {
            return;
        }
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
            return;
        }
        // An open popover owns the wheel even when the pointer is outside its panel.
        cir.setReturnValue(true);
    }

    @Inject(method = "removed()V", at = @At("HEAD"))
    private void chatupgrade$closeOverlaysOnRemoved(CallbackInfo ci) {
        chatupgrade$settingsOverlay.cancel();
        chatupgrade$emojiPopover.close();
        chatupgrade$contextMenu.close();
        RichChatInteractionRouter.cancelAllPointerCapture();
        ChatTextSelectionState.clear();
        chatupgrade$composerState.clearForScreenClose();
        ChatSurfaceController.onChatScreenClosed();
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void chatupgrade$pasteAttachment(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!event.isPaste()) {
            return;
        }
        if (chatupgrade$emojiPopover.isVisible()
                && chatupgrade$emojiSearchBox != null
                && chatupgrade$emojiSearchBox.isFocused()) {
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
            return;
        }
        if (chatupgrade$emojiPopover.isVisible()) {
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
        if (chatupgrade$emojiPopover.isVisible()
                && chatupgrade$emojiSearchBox != null
                && chatupgrade$emojiSearchBox.isFocused()) {
            cir.setReturnValue(true);
            return;
        }
        if (chatupgrade$composerState.isUploading()) {
            cir.setReturnValue(true);
            return;
        }
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()
                && ChatCommandBridge.isCommand(input == null ? "" : input.getValue())
                && (input == null || input.getValue().trim().length() <= 1)) {
            cir.setReturnValue(true);
            return;
        }
        if (chatupgrade$shouldSendPlainTextTakeover()) {
            ChatTextSelectionState.clear();
            if (commandSuggestions == null || !commandSuggestions.hasAllowedInput()) {
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
        ChatTextSelectionState.clear();
        String submittedMessage = input.getValue();
        AttachmentSendController.SendStartResult result = AttachmentSendController.sendCurrentDraft(
                chatupgrade$composerState,
                submittedMessage,
                (finish, message) -> {
                    message.ifPresent(this::chatupgrade$systemMessage);
                    chatupgrade$refreshControls();
                    if (finish == AttachmentSendController.SendFinishResult.SENT) {
                        chatupgrade$finishSuccessfulAttachmentSubmit(submittedMessage);
                    }
                });
        chatupgrade$handleSendStartResult(result);
        chatupgrade$refreshControls();
        cir.setReturnValue(true);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"))
    private void chatupgrade$hideVanillaInputDuringTakeoverRender(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        chatupgrade$refreshControls();
        chatupgrade$layoutComposerControls();
        chatupgrade$layoutEmojiSearchBox();
        if (chatupgrade$emojiSearchBox != null) {
            chatupgrade$emojiSearchVisibleBeforeRender = chatupgrade$emojiSearchBox.visible;
            chatupgrade$emojiSearchBox.visible = false;
        }
        if (!ChatUpgradeChatPipelineGate.isTakeoverMode()
                || chatupgrade$usesVanillaStyleInput()
                || input == null) {
            return;
        }
        chatupgrade$inputVisibleBeforeRender = input.visible;
        input.visible = false;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
    private void chatupgrade$renderComposerOverlays(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        boolean takeover = ChatUpgradeChatPipelineGate.isTakeoverMode();
        boolean vanillaStyleInput = chatupgrade$usesVanillaStyleInput();
        if (takeover && !vanillaStyleInput && input != null) {
            input.visible = chatupgrade$inputVisibleBeforeRender;
        }
        if (chatupgrade$emojiSearchBox != null) {
            chatupgrade$emojiSearchBox.visible = chatupgrade$emojiSearchVisibleBeforeRender;
        }
        if (takeover) {
            RichChatBounds composer = chatupgrade$composerBounds();
            chatupgrade$composerState.replyTarget().ifPresent(target -> ChatComposerRenderer.paintReplyPreview(
                    graphics,
                    this.font,
                    ChatSurfaceController.state().appearance(),
                    composer,
                    target));
            if (!vanillaStyleInput && input != null) {
                ChatComposerRenderer.paintInput(
                        graphics,
                        this.font,
                        ChatSurfaceController.state().appearance(),
                        composer,
                        input.getValue(),
                        input.isFocused(),
                        input.getCursorPosition(),
                        ((EditBoxHighlightAccessor) (Object) input).chatupgrade$getHighlightPos());
            }
        }
        int y = chatupgrade$buttonRowY();
        ChatComposerToolbar.render(
                graphics,
                this.font,
                ChatSurfaceController.state().appearance(),
                chatupgrade$toolbarLayout(),
                chatupgrade$toolbarState,
                mouseX,
                mouseY);
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
        if (chatupgrade$emojiSearchBox != null && chatupgrade$emojiSearchBox.visible) {
            chatupgrade$emojiSearchBox.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
        }
        ChatComposerRenderer.paintAttachmentChips(
                graphics,
                this.font,
                ChatSurfaceController.state().appearance(),
                chatupgrade$composerState.drafts(),
                chatupgrade$attachmentChipX(),
                Math.max(chatupgrade$attachmentChipX() + 24, chatupgrade$attachmentChipRight()),
                y);
        chatupgrade$contextMenu.render(
                graphics,
                this.font,
                ChatSurfaceController.state().appearance(),
                mouseX,
                mouseY);
        chatupgrade$settingsOverlay.render(
                graphics,
                this.font,
                mouseX,
                mouseY,
                this.width,
                this.height);
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
        String value = input == null ? "" : input.getValue().trim();
        return !value.startsWith("/");
    }

    @Unique
    private void chatupgrade$submitFromOwnedButton() {
        if (!ChatUpgradeChatPipelineGate.isTakeoverMode() || input == null || commandSuggestions == null
                || !commandSuggestions.hasAllowedInput() || chatupgrade$composerState.isUploading()) {
            return;
        }
        String submittedMessage = input.getValue();
        if (submittedMessage.trim().isEmpty() && !chatupgrade$composerState.hasDraft()) {
            return;
        }
        ChatTextSelectionState.clear();
        if (ChatCommandBridge.isCommand(submittedMessage)) {
            if (ChatCommandBridge.execute(submittedMessage, value -> handleChatInput(value, true))) {
                chatupgrade$finishSuccessfulCommandSubmit();
            }
            return;
        }
        if (chatupgrade$composerState.hasDraft()) {
            AttachmentSendController.SendStartResult result = AttachmentSendController.sendCurrentDraft(
                    chatupgrade$composerState,
                    submittedMessage,
                    (finish, message) -> this.minecraft.execute(() -> {
                        message.ifPresent(this::chatupgrade$systemMessage);
                        chatupgrade$refreshControls();
                        if (finish == AttachmentSendController.SendFinishResult.SENT) {
                            chatupgrade$finishSuccessfulAttachmentSubmit(submittedMessage);
                        }
                    }));
            chatupgrade$handleSendStartResult(result);
            chatupgrade$refreshControls();
            return;
        }
        ChatReplySummary replyTarget = chatupgrade$composerState.replyTarget().orElse(null);
        String replyMessageId = replyTarget == null ? "" : replyTarget.messageId();
        if (AttachmentSendController.sendTextOnlyTakeover(
                normalizeChatMessage(submittedMessage),
                replyMessageId)) {
            chatupgrade$composerState.clearReplyIfCurrent(replyTarget);
            chatupgrade$finishSuccessfulSubmit(submittedMessage);
        } else {
            chatupgrade$systemMessage(Component.translatable("chatupgrade.error.not_connected")
                    .withStyle(ChatFormatting.RED));
        }
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
        if (!chatupgrade$composerState.addDraft(draft)) {
            chatupgrade$systemMessage(Component.translatable(
                    "chatupgrade.input.error.too_many_attachments",
                    ChatComposerState.MAX_DRAFTS).withStyle(ChatFormatting.RED));
            return;
        }
        chatupgrade$systemMessage(Component.translatable(
                "chatupgrade.input.attached",
                ChatComposerRenderer.typeName(draft.type()),
                draft.displayName()).withStyle(ChatFormatting.GRAY));
        chatupgrade$refreshControls();
    }

    @Unique
    private void chatupgrade$cancelAttachmentDrag() {
        chatupgrade$draggedAttachment = null;
        chatupgrade$refreshControls();
    }

    @Unique
    private void chatupgrade$clearDraft() {
        chatupgrade$composerState.clearDraft();
        chatupgrade$refreshControls();
    }

    @Unique
    private boolean chatupgrade$removeAttachmentAt(double mouseX, double mouseY) {
        return ChatComposerRenderer.attachmentAt(
                this.font,
                chatupgrade$composerState.drafts(),
                chatupgrade$attachmentChipX(),
                Math.max(chatupgrade$attachmentChipX() + 24, chatupgrade$attachmentChipRight()),
                chatupgrade$buttonRowY(),
                mouseX,
                mouseY)
                .filter(chatupgrade$composerState::removeDraft)
                .map(draft -> {
                    chatupgrade$refreshControls();
                    return true;
                })
                .orElse(false);
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
            case TOO_LARGE -> chatupgrade$composerState.drafts().stream()
                    .filter(draft -> draft.status() == AttachmentDraft.Status.FAILED)
                    .map(AttachmentDraft::failureMessage)
                    .flatMap(java.util.Optional::stream)
                    .findFirst()
                    .map(message -> Component.literal(message).withStyle(ChatFormatting.RED))
                    .ifPresent(this::chatupgrade$systemMessage);
            case NO_ATTACHMENT -> {
            }
        }
    }

    @Unique
    private void chatupgrade$finishSuccessfulAttachmentSubmit(String submittedMessage) {
        if (this.input != null && !this.input.getValue().equals(submittedMessage)) {
            chatupgrade$addRecentMessage(submittedMessage);
            return;
        }
        boolean canClose = !chatupgrade$composerState.hasDraft()
                && !chatupgrade$composerState.hasReplyTarget();
        chatupgrade$finishSuccessfulSubmit(submittedMessage, canClose);
    }

    @Unique
    private void chatupgrade$finishSuccessfulCommandSubmit() {
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
    private void chatupgrade$finishSuccessfulSubmit(String recentMessage) {
        chatupgrade$finishSuccessfulSubmit(recentMessage, true);
    }

    @Unique
    private void chatupgrade$finishSuccessfulSubmit(String recentMessage, boolean allowClose) {
        chatupgrade$addRecentMessage(recentMessage);
        this.initial = "";
        this.input.setValue("");
        this.isDraft = false;
        if (allowClose && this.closeOnSubmit) {
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
    private void chatupgrade$addRecentMessage(String recentMessage) {
        String normalized = recentMessage == null ? "" : normalizeChatMessage(recentMessage);
        if (normalized.isEmpty()) {
            return;
        }
        ChatComponent chat = MinecraftGuiBridge.chat(this.minecraft);
        if (chat != null) {
            chat.addRecentChat(normalized);
        }
    }

    @Unique
    private boolean chatupgrade$usesVanillaStyleInput() {
        return ChatSurfaceController.state().appearance().vanillaStyleInput();
    }

    @Unique
    private RichChatBounds chatupgrade$composerBounds() {
        if (ChatUpgradeChatPipelineGate.isTakeoverMode() && !chatupgrade$usesVanillaStyleInput()) {
            return ChatSurfaceController.state().frame().composerBounds();
        }
        int height = chatupgrade$composerState.hasReplyTarget() ? 58 : 38;
        return RichChatBounds.ofSize(0, Math.max(0, this.height - height), this.width, height);
    }

    @Unique
    private int chatupgrade$buttonRowY() {
        RichChatBounds composer = chatupgrade$composerBounds();
        return composer.top() + (chatupgrade$composerState.hasReplyTarget() ? 22 : 4);
    }

    @Unique
    private int chatupgrade$composerLeft() {
        if (ChatUpgradeChatPipelineGate.isTakeoverMode() && !chatupgrade$usesVanillaStyleInput()) {
            return chatupgrade$composerBounds().left() + 6;
        }
        return 4;
    }

    @Unique
    private int chatupgrade$composerRight() {
        if (ChatUpgradeChatPipelineGate.isTakeoverMode() && !chatupgrade$usesVanillaStyleInput()) {
            return chatupgrade$composerBounds().right() - 6;
        }
        return this.width - 4;
    }

    @Unique
    private ChatComposerToolbar.Layout chatupgrade$toolbarLayout() {
        return ChatComposerToolbar.layout(
                chatupgrade$composerLeft(),
                chatupgrade$composerRight(),
                chatupgrade$buttonRowY(),
                chatupgrade$toolbarState);
    }

    @Unique
    private void chatupgrade$layoutComposerControls() {
        if (input == null) {
            return;
        }
        if (ChatUpgradeChatPipelineGate.isTakeoverMode() && !chatupgrade$usesVanillaStyleInput()) {
            RichChatBounds composer = chatupgrade$composerBounds();
            input.setBordered(false);
            input.setX(composer.left() + 6);
            input.setY(composer.bottom() - 20);
            input.setWidth(Math.max(40, composer.width() - 12));
            return;
        }
        input.setBordered(true);
        input.setX(4);
        input.setY(Math.max(0, this.height - 14));
        input.setWidth(Math.max(40, this.width - 8));
    }

    @Unique
    private void chatupgrade$layoutEmojiSearchBox() {
        if (chatupgrade$emojiSearchBox == null) {
            return;
        }
        boolean visible = chatupgrade$emojiPopover.isVisible();
        chatupgrade$emojiSearchBox.visible = visible;
        if (!visible) {
            chatupgrade$emojiSearchBox.setFocused(false);
            return;
        }
        RichChatBounds bounds = chatupgrade$emojiPopover.searchBounds(
                this.width,
                this.height,
                chatupgrade$emojiButtonX(),
                chatupgrade$buttonRowY(),
                chatupgrade$emojiButtonWidth());
        chatupgrade$emojiSearchBox.setX(bounds.left() + 2);
        chatupgrade$emojiSearchBox.setY(bounds.top() + 1);
        chatupgrade$emojiSearchBox.setWidth(Math.max(20, bounds.width() - 4));
    }

    @Unique
    private int chatupgrade$emojiButtonX() {
        return chatupgrade$toolbarLayout().emoji().left();
    }

    @Unique
    private int chatupgrade$emojiButtonWidth() {
        return chatupgrade$toolbarLayout().emoji().width();
    }

    @Unique
    private int chatupgrade$attachmentChipX() {
        return chatupgrade$toolbarLayout().attachmentTray().left();
    }

    @Unique
    private int chatupgrade$attachmentChipRight() {
        return chatupgrade$toolbarLayout().attachmentTray().right();
    }

    @Unique
    private void chatupgrade$activateToolbarAction(ChatComposerToolbar.Action action) {
        if (action != ChatComposerToolbar.Action.EMOJI && chatupgrade$emojiPopover.isVisible()) {
            chatupgrade$emojiPopover.close();
            chatupgrade$hideEmojiSearchBox();
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
            chatupgrade$refreshControls();
        }
        switch (action) {
            case ATTACHMENT -> {
                if (chatupgrade$toolbarState.attachmentEnabled()) {
                    chatupgrade$pickAttachment();
                }
            }
            case EMOJI -> chatupgrade$toggleEmojiPopover();
            case CLEAR -> {
                if (chatupgrade$toolbarState.clearEnabled()) {
                    chatupgrade$clearDraft();
                }
            }
            case SEND -> {
                if (chatupgrade$toolbarState.sendEnabled()) {
                    chatupgrade$submitFromOwnedButton();
                }
            }
        }
    }

    @Unique
    private void chatupgrade$openSettingsOverlay() {
        chatupgrade$emojiPopover.close();
        chatupgrade$hideEmojiSearchBox();
        chatupgrade$contextMenu.close();
        RichChatInteractionRouter.cancelAllPointerCapture();
        ChatGestureArena.resetPointerState();
        this.setFocused(null);
        if (input != null) {
            input.setFocused(false);
        }
        chatupgrade$settingsOverlay.open(this.width, this.height);
    }

    @Unique
    private boolean chatupgrade$openContextMenu(ChatGestureTarget target, double mouseX, double mouseY) {
        chatupgrade$emojiPopover.close();
        chatupgrade$hideEmojiSearchBox();
        boolean opened = chatupgrade$contextMenu.open(
                target.message(),
                (int) Math.round(mouseX),
                (int) Math.round(mouseY),
                this.width,
                this.height,
                this.font);
        if (opened) {
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.CONTEXT_MENU);
            ChatSurfaceController.setFocusOwner(ChatSurfaceState.FocusOwner.OVERLAY);
            this.setFocused(null);
            if (input != null) {
                input.setFocused(false);
            }
        }
        return opened;
    }

    @Unique
    private void chatupgrade$applyContextSelection(ChatContextMenu.Selection selection) {
        ChatMessageActionExecutor.execute(
                selection,
                chatupgrade$composerState,
                this.minecraft,
                this::chatupgrade$insertInputText)
                .ifPresent(this::chatupgrade$systemMessage);
        ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
        chatupgrade$restoreComposerFocus();
    }

    @Unique
    private void chatupgrade$closeContextMenu() {
        boolean wasOpen = chatupgrade$contextMenu.isOpen();
        chatupgrade$contextMenu.close();
        if (ChatSurfaceController.state().overlay() == ChatSurfaceState.Overlay.CONTEXT_MENU) {
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
        }
        if (wasOpen) {
            chatupgrade$restoreComposerFocus();
        }
    }

    @Unique
    private void chatupgrade$restoreComposerFocus() {
        if (input != null) {
            this.setFocused(input);
            input.setFocused(true);
        }
        ChatSurfaceController.setFocusOwner(ChatSurfaceState.FocusOwner.COMPOSER);
    }

    @Unique
    private boolean chatupgrade$isReplyCancelClick(double mouseX, double mouseY) {
        if (!ChatUpgradeChatPipelineGate.isTakeoverMode() || !chatupgrade$composerState.hasReplyTarget()) {
            return false;
        }
        RichChatBounds composer = chatupgrade$composerBounds();
        return ChatComposerRenderer.isReplyCancelClick(this.font, composer, mouseX, mouseY);
    }

    @Unique
    private void chatupgrade$toggleEmojiPopover() {
        chatupgrade$closeContextMenu();
        if (ChatUpgradeChatPipelineGate.isTakeoverMode()) {
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.EMOJI_PICKER);
        }
        chatupgrade$emojiPopover.toggle();
        if (chatupgrade$emojiSearchBox != null) {
            chatupgrade$emojiSearchBox.visible = chatupgrade$emojiPopover.isVisible();
            if (chatupgrade$emojiPopover.isVisible()) {
                chatupgrade$focusEmojiSearchBox();
            }
        }
        if (!chatupgrade$emojiPopover.isVisible()) {
            chatupgrade$hideEmojiSearchBox();
            ChatSurfaceController.setOverlay(ChatSurfaceState.Overlay.NONE);
        }
        chatupgrade$refreshControls();
    }

    @Unique
    private boolean chatupgrade$isEmojiButtonClick(double mouseX, double mouseY) {
        return chatupgrade$toolbarLayout().emoji().contains(
                (int) Math.round(mouseX),
                (int) Math.round(mouseY));
    }

    @Unique
    private void chatupgrade$hideEmojiSearchBox() {
        if (chatupgrade$emojiSearchBox != null) {
            chatupgrade$emojiSearchBox.visible = false;
            chatupgrade$emojiSearchBox.setFocused(false);
        }
        if (input != null && !chatupgrade$emojiPopover.isVisible()) {
            this.setFocused(input);
            input.setFocused(true);
            ChatSurfaceController.setFocusOwner(ChatSurfaceState.FocusOwner.COMPOSER);
        }
    }

    @Unique
    private void chatupgrade$focusEmojiSearchBox() {
        if (chatupgrade$emojiSearchBox == null) {
            return;
        }
        this.setFocused(chatupgrade$emojiSearchBox);
        chatupgrade$emojiSearchBox.setFocused(true);
        if (input != null) {
            input.setFocused(false);
        }
        ChatSurfaceController.setFocusOwner(ChatSurfaceState.FocusOwner.OVERLAY);
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
        boolean takeover = ChatUpgradeChatPipelineGate.isTakeoverMode();
        boolean hasDraft = chatupgrade$composerState.hasDraft();
        String inputValue = input == null ? "" : input.getValue().trim();
        boolean hasText = !inputValue.isEmpty();
        boolean commandReady = !ChatCommandBridge.isCommand(inputValue)
                || inputValue.length() > 1;
        boolean uploading = chatupgrade$composerState.isUploading();
        chatupgrade$toolbarState = new ChatComposerToolbar.State(
                chatupgrade$composerState.canAddDraft(),
                chatupgrade$emojiPopover.isVisible(),
                hasDraft,
                hasDraft && !uploading,
                takeover,
                takeover && !uploading && commandReady && (hasDraft || hasText));
    }

    @Unique
    private void chatupgrade$systemMessage(Component message) {
        ChatComponent chat = MinecraftGuiBridge.chat(this.minecraft);
        if (chat != null) {
            chat.addClientSystemMessage(message);
        }
    }
}