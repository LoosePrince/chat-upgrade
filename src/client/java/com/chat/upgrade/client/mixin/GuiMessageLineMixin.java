package com.chat.upgrade.client.mixin;

import com.chat.upgrade.client.UpgradePhantomCoordinator;
import com.chat.upgrade.client.InlineResourceType;
import com.chat.upgrade.client.mixininterface.GuiMessageLineReadable;
import com.chat.upgrade.client.mixininterface.ImageAttachable;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMessage.Line.class)
public abstract class GuiMessageLineMixin implements ImageAttachable, GuiMessageLineReadable {
    @Shadow
    public abstract FormattedCharSequence content();

    @Shadow
    public abstract boolean endOfEntry();
    @Unique
    private @Nullable String chatupgrade$imageUrl;

    @Unique
    private boolean chatupgrade$imageIsContinuation;
    @Unique
    private InlineResourceType chatupgrade$resourceType = InlineResourceType.IMAGE;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void chatupgrade$captureImageData(CallbackInfo ci) {
        this.chatupgrade$resourceType = UpgradePhantomCoordinator.nextPhantomTopType;
        if (UpgradePhantomCoordinator.nextPhantomTopUrl != null) {
            this.chatupgrade$imageUrl = UpgradePhantomCoordinator.nextPhantomTopUrl;
            UpgradePhantomCoordinator.nextPhantomTopUrl = null;
            UpgradePhantomCoordinator.nextPhantomTopType = InlineResourceType.IMAGE;
        }
        this.chatupgrade$imageIsContinuation = UpgradePhantomCoordinator.nextPhantomContinuation;
        UpgradePhantomCoordinator.nextPhantomContinuation = false;
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
    public boolean chatupgrade$isImageContinuation() {
        return chatupgrade$imageIsContinuation;
    }

    @Override
    public InlineResourceType chatupgrade$getResourceType() {
        return chatupgrade$resourceType;
    }
}
