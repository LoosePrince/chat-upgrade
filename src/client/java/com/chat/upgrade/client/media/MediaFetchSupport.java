package com.chat.upgrade.client.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.OptionalLong;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.ChatUpgrade;

public final class MediaFetchSupport {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private MediaFetchSupport() {
    }

    public static @Nullable HttpResponse<InputStream> sendGet(String url, int timeoutSeconds, String typeLabel) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .GET()
                    .build();
            return HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            ChatUpgrade.LOGGER.warn("chat-upgrade: failed to fetch {} {}: {}", typeLabel, url, e.getMessage());
            return null;
        }
    }

    public static FetchPayload readPayload(HttpResponse<InputStream> response, int maxBytes)
            throws IOException, ResponseBodyTooLarge {
        try (InputStream raw = response.body()) {
            OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
            if (contentLength.isPresent() && contentLength.getAsLong() > maxBytes) {
                throw new ResponseBodyTooLarge();
            }
            byte[] body = readBodyCapped(raw, maxBytes);
            int declaredLength = -1;
            if (contentLength.isPresent()) {
                declaredLength = (int) Math.min(contentLength.getAsLong(), Integer.MAX_VALUE);
            }
            return new FetchPayload(
                    body,
                    response.headers().firstValue("Content-Type").orElse(null),
                    declaredLength,
                    md5Hex(body));
        }
    }

    private static byte[] readBodyCapped(InputStream is, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 65536));
        byte[] buf = new byte[8192];
        long total = 0;
        while (true) {
            int n = is.read(buf);
            if (n < 0) {
                break;
            }
            if (total + n > maxBytes) {
                throw new ResponseBodyTooLarge();
            }
            out.write(buf, 0, n);
            total += n;
        }
        return out.toByteArray();
    }

    private static @Nullable String md5Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return null;
        }
    }

    public record FetchPayload(byte[] body, @Nullable String contentType, int declaredLength, @Nullable String md5Hex) {
    }

    public static final class ResponseBodyTooLarge extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
