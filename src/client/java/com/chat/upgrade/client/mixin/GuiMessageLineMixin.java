package com.chat.upgrade.client.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.chat.upgrade.client.InlineResourceType;
import com.chat.upgrade.client.UpgradePhantomCoordinator;
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
    private @Nullable String chatupgrade$imageUrl;
    @Unique
    private @Nullable String chatupgrade$resourceName;

    @Unique
    private boolean chatupgrade$imageIsContinuation;
    @Unique
    private InlineResourceType chatupgrade$resourceType = InlineResourceType.IMAGE;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void chatupgrade$captureImageData(CallbackInfo ci) {
        UpgradePhantomCoordinator.PhantomLineHints hints = UpgradePhantomCoordinator.consumePhantomLineHints();
        this.chatupgrade$resourceType = hints.topType();
        this.chatupgrade$resourceName = hints.topName();
        this.chatupgrade$imageUrl = hints.topUrl();
        this.chatupgrade$imageIsContinuation = hints.continuation();
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
    public InlineResourceType chatupgrade$getResourceType() {
        return chatupgrade$resourceType;
    }
}
