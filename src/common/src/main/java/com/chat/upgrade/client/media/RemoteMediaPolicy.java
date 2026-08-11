package com.chat.upgrade.client.media;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

import com.chat.upgrade.net.ExternalMediaUrlPolicy;

final class RemoteMediaPolicy {
    private RemoteMediaPolicy() {
    }

    static URI validate(String rawUrl) throws Exception {
        URI uri = ExternalMediaUrlPolicy.parseHttps(rawUrl)
                .orElseThrow(() -> new IllegalArgumentException("invalid HTTPS media URL"));
        String host = uri.getHost();
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.equals("localhost")
                || normalizedHost.endsWith(".localhost")
                || normalizedHost.endsWith(".local")
                || normalizedHost.endsWith(".internal")) {
            throw new IllegalArgumentException("local media hosts are not allowed");
        }
        int port = uri.getPort();
        if (port != -1 && port != 443) {
            throw new IllegalArgumentException("non-standard media ports are not allowed");
        }
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) {
            throw new IllegalArgumentException("media host did not resolve");
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new IllegalArgumentException("media host resolves to a non-public address");
            }
        }
        return uri;
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            return isPublicIpv4(bytes);
        }
        if (address instanceof Inet6Address) {
            if ((bytes[0] & 0xFE) == 0xFC) {
                return false;
            }
            if (isIpv4Mapped(bytes)) {
                return isPublicIpv4(new byte[] { bytes[12], bytes[13], bytes[14], bytes[15] });
            }
            if (!hasPrefix(bytes, new int[] { 0x20 }, 3)
                    || hasPrefix(bytes, new int[] { 0x20, 0x01, 0x00, 0x00 }, 32)
                    || hasPrefix(bytes, new int[] { 0x20, 0x01, 0x00, 0x02 }, 48)
                    || hasPrefix(bytes, new int[] { 0x20, 0x01, 0x00, 0x10 }, 28)
                    || hasPrefix(bytes, new int[] { 0x20, 0x01, 0x00, 0x20 }, 28)
                    || hasPrefix(bytes, new int[] { 0x20, 0x01, 0x0D, 0xB8 }, 32)
                    || hasPrefix(bytes, new int[] { 0x20, 0x02 }, 16)
                    || hasPrefix(bytes, new int[] { 0x3F, 0xFF, 0x00 }, 20)) {
                return false;
            }
            return true;
        }
        return false;
    }

    private static boolean isPublicIpv4(byte[] bytes) {
        int first = bytes[0] & 0xFF;
        int second = bytes[1] & 0xFF;
        int third = bytes[2] & 0xFF;
        if (first == 0 || first == 10 || first == 127 || first >= 224) {
            return false;
        }
        if (first == 100 && second >= 64 && second <= 127) {
            return false;
        }
        if (first == 169 && second == 254) {
            return false;
        }
        if (first == 172 && second >= 16 && second <= 31) {
            return false;
        }
        if (first == 192 && second == 168) {
            return false;
        }
        if (first == 192 && second == 0 && third == 0) {
            return false;
        }
        if (first == 192 && second == 0 && third == 2) {
            return false;
        }
        if (first == 198 && (second == 18 || second == 19)) {
            return false;
        }
        if (first == 198 && second == 51 && third == 100) {
            return false;
        }
        return !(first == 203 && second == 0 && third == 113);
    }

    private static boolean hasPrefix(byte[] address, int[] prefix, int prefixBits) {
        if (prefixBits < 0 || prefixBits > address.length * 8) {
            return false;
        }
        int fullBytes = prefixBits / 8;
        int remainingBits = prefixBits % 8;
        if (prefix.length < fullBytes + (remainingBits == 0 ? 0 : 1)) {
            return false;
        }
        for (int index = 0; index < fullBytes; index++) {
            if ((address[index] & 0xFF) != prefix[index]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return ((address[fullBytes] & 0xFF) & mask) == (prefix[fullBytes] & mask);
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }
}