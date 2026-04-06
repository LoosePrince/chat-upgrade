package com.chat.upgrade.client;

import com.chat.upgrade.ChatUpgrade;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Anonymous upload to Litterbox ({@code reqtype=fileupload}, {@code time=1h} retention).
 *
 * @see <a href="https://litterbox.catbox.moe/tools.php">Litterbox tools / API</a>
 */
public final class CatboxUploader {
    /** Official API endpoint (the {@code tools.php} page is documentation only). */
    public static final URI API_URI = URI.create("https://litterbox.catbox.moe/resources/internals/api.php");

    /** Allowed: {@code 1h}, {@code 12h}, {@code 24h}, {@code 72h}; we use 1 hour. */
    public static final String RETENTION_TIME = "1h";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private CatboxUploader() {}

    public static CompletableFuture<Optional<String>> uploadFile(Path file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!Files.isRegularFile(file)) {
                    ChatUpgrade.LOGGER.warn("ChatUpgrade upload: not a regular file: {}", file);
                    return Optional.<String>empty();
                }
                long size = Files.size(file);
                int maxUpload = ChatUpgradeConfig.get().maxUploadBytes;
                if (size > maxUpload) {
                    ChatUpgrade.LOGGER.warn(
                            "ChatUpgrade upload: file too large ({} bytes; limit {})",
                            size,
                            maxUpload);
                    return Optional.empty();
                }
                if (size == 0) {
                    return Optional.empty();
                }
                byte[] body = Files.readAllBytes(file);
                String filename = file.getFileName().toString();
                return uploadBytesSync(body, filename);
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("ChatUpgrade upload: {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    public static CompletableFuture<Optional<String>> uploadBytes(byte[] data, String filename) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (data == null || data.length == 0) {
                    return Optional.<String>empty();
                }
                int maxUpload = ChatUpgradeConfig.get().maxUploadBytes;
                if (data.length > maxUpload) {
                    ChatUpgrade.LOGGER.warn(
                            "ChatUpgrade upload: payload too large ({} bytes; limit {})",
                            data.length,
                            maxUpload);
                    return Optional.empty();
                }
                return uploadBytesSync(data, sanitizeFilename(filename));
            } catch (Exception e) {
                ChatUpgrade.LOGGER.warn("ChatUpgrade upload: {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    private static Optional<String> uploadBytesSync(byte[] fileBytes, String filename) throws Exception {
        String boundary = "ChatUpgrade-" + System.currentTimeMillis();
        byte[] body = buildMultipart(boundary, fileBytes, filename);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(API_URI)
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            ChatUpgrade.LOGGER.warn("ChatUpgrade upload: HTTP {} — {}", response.statusCode(), response.body());
            return Optional.empty();
        }
        String text = response.body().trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return Optional.of(text);
        }
        ChatUpgrade.LOGGER.warn("ChatUpgrade upload: API error: {}", text);
        return Optional.empty();
    }

    private static byte[] buildMultipart(String boundary, byte[] fileBytes, String filename) {
        String prefix = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n"
                + "fileupload\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"time\"\r\n\r\n"
                + RETENTION_TIME + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\""
                + escapeQuotedFilename(filename)
                + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        String suffix = "\r\n--" + boundary + "--\r\n";

        byte[] p = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] s = suffix.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[p.length + fileBytes.length + s.length];
        System.arraycopy(p, 0, out, 0, p.length);
        System.arraycopy(fileBytes, 0, out, p.length, fileBytes.length);
        System.arraycopy(s, 0, out, p.length + fileBytes.length, s.length);
        return out;
    }

    private static String escapeQuotedFilename(String name) {
        return name.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "image.png";
        }
        String n = Path.of(name).getFileName().toString();
        if (n.isBlank()) {
            return "image.png";
        }
        return n;
    }
}
