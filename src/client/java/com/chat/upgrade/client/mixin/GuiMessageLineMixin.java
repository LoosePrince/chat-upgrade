package com.chat.upgrade.client.mixin;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.chat.upgrade.client.media.model.InlineResourceType;
import com.chat.upgrade.client.media.model.RichAttachment;
import com.chat.upgrade.client.ui.chat.UpgradePhantomCoordinator;
import com.chat.upgrade.client.ui.chat.InlineEmojiCoordinator;
import com.chat.upgrade.client.ui.chat.InlineEmojiSlot;
import com.chat.upgrade.client.mixininterface.GuiMessageLineReadable;
import com.chat.upgrade.client.mixininterface.ImageAttachable;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;

@Mixin(GuiMessage.Line.class)
public abstract class GuiMessageLineMixin implements ImageAttachable, GuiMessageLineReadable {
    @Shadow
    public abstract FormattedCharSequence content();

    @Shadow
    public abstract boolean endOfEntry();

    @Unique
    private @Nullable RichAttachment chatupgrade$attachment;
    @Unique
    private @Nullable String chatupgrade$imageUrl;
    @Unique
    private @Nullable String chatupgrade$resourceName;

    @Unique
    private boolean chatupgrade$imageIsPhantomTop;
    @Unique
    private boolean chatupgrade$imageIsContinuation;
    @Unique
    private InlineResourceType chatupgrade$resourceType = InlineResourceType.IMAGE;
    @Unique
    private List<InlineEmojiSlot> chatupgrade$inlineEmojiSlots = List.of();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void chatupgrade$captureImageData(CallbackInfo ci) {
        UpgradePhantomCoordinator.PhantomLineHints hints = UpgradePhantomCoordinator.consumePhantomLineHints();
        this.chatupgrade$attachment = hints.attachment();
        this.chatupgrade$resourceType = hints.topType();
        this.chatupgrade$resourceName = hints.topName();
        this.chatupgrade$imageUrl = hints.topUrl();
        this.chatupgrade$imageIsPhantomTop = hints.phantomTop();
        this.chatupgrade$imageIsContinuation = hints.continuation();
        this.chatupgrade$inlineEmojiSlots = InlineEmojiCoordinator.consumeForLine(content());
    }

    @Override
    public FormattedCharSequence chatupgrade$content() {
        return content();
    }

    @Override
    public boolean chatupgrade$endOfEntry() {
        return endOfEntry();
    }

    @Override
    public @Nullable RichAttachment chatupgrade$getAttachment() {
        return chatupgrade$attachment;
    }

    @Override
    public @Nullable String chatupgrade$getImageUrl() {
        return chatupgrade$imageUrl;
    }

    @Override
    public @Nullable String chatupgrade$getResourceName() {
        return chatupgrade$resourceName;
    }

    @Override
    public boolean chatupgrade$isImageContinuation() {
        return chatupgrade$imageIsContinuation;
    }

    @Override
    public boolean chatupgrade$isImagePhantomTop() {
        return chatupgrade$imageIsPhantomTop;
    }

    @Override
    public InlineResourceType chatupgrade$getResourceType() {
        return chatupgrade$resourceType;
    }

    @Override
    public List<InlineEmojiSlot> chatupgrade$getInlineEmojiSlots() {
        return chatupgrade$inlineEmojiSlots;
    }
}
