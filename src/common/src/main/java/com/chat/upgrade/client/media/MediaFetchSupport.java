package com.chat.upgrade.client.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import org.jetbrains.annotations.Nullable;

import com.chat.upgrade.ChatUpgrade;
import com.chat.upgrade.client.ChatUpgradeConfig;

public final class MediaFetchSupport {
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_REDIRECT_BODY_BYTES = 8 * 1_024;
    private static final ProxySelector NO_PROXY = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException failure) {
        }
    };
    private static final HttpClient HTTP_DIRECT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(NO_PROXY)
            .build();
    private static final HttpClient HTTP_SYSTEM_PROXY = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private MediaFetchSupport() {
    }

    public static @Nullable FetchPayload fetch(String url, int timeoutSeconds, String typeLabel, int maxBytes) {
        if (maxBytes <= 0 || maxBytes > ChatUpgradeConfig.ABSOLUTE_MAX_RECEIVE_BYTES) {
            throw new IllegalArgumentException("invalid media receive limit");
        }
        URI current = null;
        try {
            current = RemoteMediaPolicy.validate(url);
            long timeoutNanos = Duration.ofSeconds(Math.clamp(timeoutSeconds, 1, 60)).toNanos();
            long deadline = saturatingAdd(System.nanoTime(), timeoutNanos);
            for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0L) {
                    throw new IOException("media request timed out");
                }
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(current)
                        .timeout(Duration.ofNanos(remainingNanos))
                        .header("Accept", acceptedTypes(typeLabel))
                        .GET()
                        .build();
                HttpResponse<byte[]> response = httpClient().send(
                        request,
                        info -> new CappedBodySubscriber(
                                isRedirect(info.statusCode())
                                        ? Math.min(maxBytes, MAX_REDIRECT_BODY_BYTES)
                                        : maxBytes,
                                info.headers().firstValueAsLong("Content-Length")));
                if (isRedirect(response.statusCode())) {
                    if (redirects == MAX_REDIRECTS) {
                        throw new IOException("too many media redirects");
                    }
                    String location = response.headers().firstValue("Location")
                            .orElseThrow(() -> new IOException("redirect without Location"));
                    current = RemoteMediaPolicy.validate(current.resolve(location).toString());
                    continue;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return null;
                }
                byte[] body = response.body();
                OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
                int declaredLength = contentLength.isPresent()
                        ? (int) Math.min(contentLength.getAsLong(), Integer.MAX_VALUE)
                        : -1;
                return new FetchPayload(
                        body,
                        response.headers().firstValue("Content-Type").orElse(null),
                        declaredLength,
                        md5Hex(body));
            }
        } catch (Exception exception) {
            ResponseBodyTooLarge tooLarge = findCause(exception, ResponseBodyTooLarge.class);
            if (tooLarge != null) {
                throw tooLarge;
            }
            String host = current == null ? "invalid" : current.getHost();
            ChatUpgrade.LOGGER.warn(
                    "chat-upgrade: blocked or failed {} fetch from {}: {}",
                    typeLabel,
                    host,
                    exception.getMessage());
        }
        return null;
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private static HttpClient httpClient() {
        return ChatUpgradeConfig.get().remoteMediaNetworkMode == ChatUpgradeConfig.RemoteMediaNetworkMode.SYSTEM_PROXY
                ? HTTP_SYSTEM_PROXY
                : HTTP_DIRECT;
    }

    private static String acceptedTypes(String typeLabel) {
        return switch (typeLabel == null ? "" : typeLabel) {
            case "image", "emoji image" -> "image/png,image/jpeg,image/gif,image/webp";
            case "audio" -> "audio/*,application/ogg";
            case "video" -> "video/*";
            case "emoji catalog" -> "application/json";
            default -> "application/octet-stream";
        };
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        if (((left ^ result) & (right ^ result)) < 0) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    private static @Nullable String md5Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            return null;
        }
    }

    private static <T extends Throwable> @Nullable T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    public record FetchPayload(byte[] body, @Nullable String contentType, int declaredLength, @Nullable String md5Hex) {
    }

    public static final class ResponseBodyTooLarge extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static final class CappedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maxBytes;
        private final ByteArrayOutputStream output;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int receivedBytes;

        CappedBodySubscriber(int maxBytes, OptionalLong declaredLength) {
            this.maxBytes = maxBytes;
            boolean declaredTooLarge = declaredLength.isPresent() && declaredLength.getAsLong() > maxBytes;
            if (declaredTooLarge) {
                body.completeExceptionally(new ResponseBodyTooLarge());
            }
            int initialSize = declaredTooLarge
                    ? 0
                    : declaredLength.isPresent()
                            ? (int) Math.min(declaredLength.getAsLong(), maxBytes)
                            : Math.min(maxBytes, 64 * 1_024);
            this.output = new ByteArrayOutputStream(Math.max(0, initialSize));
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (body.isDone()) {
                subscription.cancel();
                return;
            }
            subscription.request(1L);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            try {
                for (ByteBuffer buffer : buffers) {
                    int length = buffer.remaining();
                    if (receivedBytes + (long) length > maxBytes) {
                        throw new ResponseBodyTooLarge();
                    }
                    byte[] chunk = new byte[length];
                    buffer.get(chunk);
                    output.writeBytes(chunk);
                    receivedBytes += length;
                }
                subscription.request(1L);
            } catch (Throwable throwable) {
                subscription.cancel();
                body.completeExceptionally(throwable);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }
    }
}