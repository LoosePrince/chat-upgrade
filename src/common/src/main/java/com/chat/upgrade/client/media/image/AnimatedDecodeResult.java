package com.chat.upgrade.client.media.image;
import com.mojang.blaze3d.platform.NativeImage;

/**
 * Multi-frame image decode for chat preview (GIF / WebP / APNG).
 */
public record AnimatedDecodeResult(NativeImage[] frames, int[] delayMs) {}
