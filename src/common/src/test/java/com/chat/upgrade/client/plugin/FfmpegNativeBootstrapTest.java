package com.chat.upgrade.client.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FfmpegNativeBootstrapTest {
    private static final FfmpegNativeBootstrap.Artifact FIXTURE = new FfmpegNativeBootstrap.Artifact(
            "fixture",
            "1",
            3L,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsOnlyExactVerifiedArtifactBytes() throws Exception {
        Path artifact = temporaryDirectory.resolve("fixture.jar");
        Files.writeString(artifact, "abc", StandardCharsets.US_ASCII);

        assertTrue(FfmpegNativeBootstrap.isVerifiedArtifact(artifact, FIXTURE));

        Files.writeString(artifact, "abd", StandardCharsets.US_ASCII);
        assertFalse(FfmpegNativeBootstrap.isVerifiedArtifact(artifact, FIXTURE));
    }

    @Test
    void rejectsSymbolicLinkArtifactEvenWhenTargetIsVerified() throws Exception {
        Path target = temporaryDirectory.resolve("verified.jar");
        Path link = temporaryDirectory.resolve("artifact.jar");
        Files.writeString(target, "abc", StandardCharsets.US_ASCII);
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | IOException exception) {
            Assumptions.abort("symbolic links are unavailable for this test runtime");
        }

        assertFalse(FfmpegNativeBootstrap.isVerifiedArtifact(link, FIXTURE));
    }

    @Test
    void rejectsUnsafeAndDuplicateProneNativeArchivePaths() throws Exception {
        assertEquals("jniavutil.dll", FfmpegNativeBootstrap.safeNativeFilename(
                "org/bytedeco/ffmpeg/windows-x86_64/jniavutil.dll", ".dll"));
        assertThrows(IOException.class, () -> FfmpegNativeBootstrap.safeNativeFilename(
                "org/bytedeco/ffmpeg/windows-x86_64/../jniavutil.dll", ".dll"));
        assertThrows(IOException.class, () -> FfmpegNativeBootstrap.safeNativeFilename(
                "org/bytedeco/ffmpeg/windows-x86_64/jniavutil.so", ".dll"));
        assertThrows(IOException.class, () -> FfmpegNativeBootstrap.safeNativeFilename(
                "org/bytedeco/ffmpeg/windows-x86_64/jni avutil.dll", ".dll"));
    }
}