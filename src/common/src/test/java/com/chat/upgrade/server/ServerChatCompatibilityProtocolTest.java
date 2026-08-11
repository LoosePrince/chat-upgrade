package com.chat.upgrade.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ServerChatCompatibilityProtocolTest {
    @Test
    void leavesIncompleteOrMalformedPayloadsAsOrdinaryChat() {
        assertNull(ServerChatRouteService.descriptorForBracketProtocol("hello [[ChatUpgrade,name=photo]]"));
        assertNull(ServerChatRouteService.descriptorForBracketProtocol("hello [[ChatUpgrade,url=]]"));
        assertNull(ServerChatRouteService.descriptorForBracketProtocol(
                "hello [[ChatUpgrade,url=http://cdn.example.com/a.png]]"));
        assertNull(ServerChatRouteService.descriptorForBracketProtocol(
                "hello [[ChatUpgrade,url=https://cdn.example.com/a.png,unknown=value]]"));
        assertNull(ServerChatRouteService.descriptorForBracketProtocol(
                "hello [[ChatUpgrade,url=https://cdn.example.com/a.png,type=document]]"));
    }

    @Test
    void rejectsAmbiguousDuplicateOrMultiplePayloads() {
        assertNull(ServerChatRouteService.descriptorForBracketProtocol(
                "[[ChatUpgrade,url=https://a.example/a.png,url=https://b.example/b.png]]"));
        assertNull(ServerChatRouteService.descriptorForBracketProtocol(
                "[[ChatUpgrade,url=https://a.example/a.png]] [[ChatUpgrade,url=https://b.example/b.png]]"));
    }

    @Test
    void acceptsSingleCanonicalHttpsPayloadWithoutDroppingVisibleText() {
        AttachmentRouteDescriptor descriptor = ServerChatRouteService.descriptorForBracketProtocol(
                "hello world [[ChatUpgrade,url=https://cdn.example.com/a.png,name=photo,type=image]]");

        assertTrue(descriptor != null);
        assertEquals("hello world", descriptor.visibleText());
        assertEquals("image", descriptor.typeWire());
        assertEquals("photo", descriptor.name());
        assertEquals("https://cdn.example.com/a.png", descriptor.url());
    }
}