package com.chat.upgrade.client.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Inet6Address;
import java.net.InetAddress;

import com.chat.upgrade.client.ChatUpgradeConfig;
import org.junit.jupiter.api.Test;

final class RemoteMediaPolicyTest {
    @Test
    void blocksPrivateMetadataAndSpecialPurposeIpv4Ranges() throws Exception {
        assertFalse(publicAddress("0.0.0.0"));
        assertFalse(publicAddress("10.0.0.1"));
        assertFalse(publicAddress("100.64.0.1"));
        assertFalse(publicAddress("127.0.0.1"));
        assertFalse(publicAddress("169.254.169.254"));
        assertFalse(publicAddress("172.16.0.1"));
        assertFalse(publicAddress("192.0.0.1"));
        assertFalse(publicAddress("192.0.2.1"));
        assertFalse(publicAddress("192.168.1.1"));
        assertFalse(publicAddress("198.18.0.1"));
        assertFalse(publicAddress("198.51.100.1"));
        assertFalse(publicAddress("203.0.113.1"));
        assertFalse(publicAddress("224.0.0.1"));
        assertTrue(publicAddress("8.8.8.8"));
        assertTrue(publicAddress("1.1.1.1"));
    }

    @Test
    void blocksLocalAndSpecialPurposeIpv6Ranges() throws Exception {
        assertFalse(publicAddress("::"));
        assertFalse(publicAddress("::1"));
        assertFalse(publicAddress("fe80::1"));
        assertFalse(publicAddress("fc00::1"));
        assertFalse(publicAddress("2001:db8::1"));
        assertFalse(publicAddress("2001::1"));
        assertFalse(publicAddress("2002:0808:0808::1"));
        assertFalse(publicAddress("3fff::1"));
        assertTrue(publicAddress("2606:4700:4700::1111"));
    }

    @Test
    void permitsOnlySyntheticProxyAddressesInTransparentProxyMode() throws Exception {
        var synthetic = InetAddress.getByName("198.18.0.142");
        var privateAddress = InetAddress.getByName("10.0.0.1");

        assertFalse(RemoteMediaPolicy.allowsResolvedAddress(
                synthetic,
                ChatUpgradeConfig.RemoteMediaNetworkMode.STRICT));
        assertTrue(RemoteMediaPolicy.allowsResolvedAddress(
                synthetic,
                ChatUpgradeConfig.RemoteMediaNetworkMode.TRANSPARENT_PROXY));
        assertFalse(RemoteMediaPolicy.allowsResolvedAddress(
                privateAddress,
                ChatUpgradeConfig.RemoteMediaNetworkMode.TRANSPARENT_PROXY));
    }

    @Test
    void appliesIpv4RulesToIpv4MappedIpv6() throws Exception {
        assertFalse(RemoteMediaPolicy.isPublic(mappedIpv6(10, 0, 0, 1)));
        assertFalse(RemoteMediaPolicy.isPublic(mappedIpv6(169, 254, 169, 254)));
        assertTrue(RemoteMediaPolicy.isPublic(mappedIpv6(8, 8, 8, 8)));
    }

    private static boolean publicAddress(String value) throws Exception {
        return RemoteMediaPolicy.isPublic(InetAddress.getByName(value));
    }

    private static Inet6Address mappedIpv6(int a, int b, int c, int d) throws Exception {
        byte[] bytes = new byte[16];
        bytes[10] = (byte) 0xFF;
        bytes[11] = (byte) 0xFF;
        bytes[12] = (byte) a;
        bytes[13] = (byte) b;
        bytes[14] = (byte) c;
        bytes[15] = (byte) d;
        return Inet6Address.getByAddress(null, bytes, -1);
    }
}